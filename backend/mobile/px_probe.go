package mobile

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/rand/v2"
	"net"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// Probe engine: validates thousands of candidate hosts inside Go.
//
// Pipeline (modelled on puredns -> dnsx -> httpx):
//
//	candidates -> DNS stage -> wildcard filter -> HTTP stage -> live results
//
// * DNS stage   : pooled, cached, single-flight resolution; dead names are
//                 dropped in milliseconds instead of costing an HTTP timeout.
// * Wildcard    : names that only resolve because their parent has a *.parent
//                 record are dropped (random-label probe, cached per parent).
// * HTTP stage  : HTTPS first, HTTP fallback (httpx behaviour), shared tuned
//                 transport, optional rate limit, retries on timeout and an
//                 adaptive concurrency gate that backs off when probes time out.
//
// The Android app talks to it with three string-only calls (gomobile friendly):
// StartProbeJob, PollProbeJob and CancelProbeJob. Polling returns only the
// results added since the caller's cursor, so the UI streams live hosts.

type pxOptions struct {
	Workers        int    `json:"workers"`
	DNSWorkers     int    `json:"dns_workers"`
	TimeoutSec     int    `json:"timeout_sec"`
	Retries        int    `json:"retries"`
	RatePerSec     int    `json:"rate_per_sec"`
	Limit          int    `json:"limit"`
	UserAgent      string `json:"user_agent"`
	AcceptAny      bool   `json:"accept_any"`
	Adaptive       *bool  `json:"adaptive"`
	WildcardFilter *bool  `json:"wildcard_filter"`
}

func pxClamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func (o *pxOptions) normalize() {
	if o.Workers == 0 {
		o.Workers = 64
	}
	o.Workers = pxClamp(o.Workers, 1, 256)
	// Keep total sockets well below Android's per-process descriptor limit.
	if o.DNSWorkers == 0 {
		o.DNSWorkers = pxClamp(o.Workers*2, 16, 192)
	}
	o.DNSWorkers = pxClamp(o.DNSWorkers, 1, 384)
	if o.TimeoutSec == 0 {
		o.TimeoutSec = 8
	}
	o.TimeoutSec = pxClamp(o.TimeoutSec, 2, 60)
	o.Retries = pxClamp(o.Retries, 0, 2)
	o.RatePerSec = pxClamp(o.RatePerSec, 0, 2000)
	o.Limit = pxClamp(o.Limit, 0, 50000)
}

func pxBool(p *bool, def bool) bool {
	if p == nil {
		return def
	}
	return *p
}

// pxResult is one live host.
type pxResult struct {
	Host      string `json:"host"`
	Status    int    `json:"status"`
	Scheme    string `json:"scheme"`
	LatencyMs int64  `json:"latency_ms"`
	IP        string `json:"ip,omitempty"`
	Server    string `json:"server,omitempty"`
	CDN       string `json:"cdn,omitempty"`
	TLS       string `json:"tls_version,omitempty"`
	Location  string `json:"location,omitempty"`
}

type pxCandidate struct {
	host string
	ips  []net.IP
}

// pxGate limits concurrent probes and removes permits when many probes time
// out (congested or throttled network), then restores them as timeouts fade.
type pxGate struct {
	sem      chan struct{}
	max      int
	adaptive bool
	mu       sync.Mutex
	held     int
	window   int
	bad      int
}

func newPxGate(max int, adaptive bool) *pxGate {
	g := &pxGate{sem: make(chan struct{}, max), max: max, adaptive: adaptive}
	for i := 0; i < max; i++ {
		g.sem <- struct{}{}
	}
	return g
}

func (g *pxGate) acquire(ctx context.Context) bool {
	select {
	case <-g.sem:
		return true
	case <-ctx.Done():
		return false
	}
}

func (g *pxGate) release() { g.sem <- struct{}{} }

func (g *pxGate) current() int {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.max - g.held
}

func (g *pxGate) record(timedOut bool) {
	if !g.adaptive {
		return
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	g.window++
	if timedOut {
		g.bad++
	}
	if g.window < 32 {
		return
	}
	ratio := float64(g.bad) / float64(g.window)
	switch {
	case ratio > 0.6:
		target := (g.max - g.held) / 2
		for removed := 0; removed < target && g.max-g.held > 4; removed++ {
			select {
			case <-g.sem:
				g.held++
			default:
				removed = target
			}
		}
	case ratio < 0.25:
		for restored := 0; restored < 4 && g.held > 0; restored++ {
			g.sem <- struct{}{}
			g.held--
		}
	}
	g.window, g.bad = 0, 0
}

type pxJob struct {
	id       string
	opt      pxOptions
	adaptive bool
	wildcard bool
	ctx      context.Context
	cancel   context.CancelFunc
	client   *http.Client
	transport *http.Transport
	rate     *time.Ticker
	gate     *pxGate
	started  time.Time
	total    int

	checked, dnsFailed, wildcardHits, timeouts, live atomic.Int64
	done atomic.Bool

	mu      sync.Mutex
	results []pxResult
	status  string
	wild    sync.Map // parent -> *pxWildcard
}

type pxWildcard struct {
	once sync.Once
	ips  map[string]struct{}
}

var (
	pxJobs   sync.Map
	pxJobSeq atomic.Uint64
)

// pxParseHosts normalises, validates and de-duplicates a newline / comma
// separated candidate list.
func pxParseHosts(text string, limit int) []string {
	seen := map[string]struct{}{}
	out := []string{}
	fields := strings.FieldsFunc(text, func(r rune) bool {
		return r == '\n' || r == '\r' || r == ',' || r == ' ' || r == '\t'
	})
	for _, raw := range fields {
		h := normalizeDomain(normalizeToolHost(raw))
		if h == "" || !isHostname(h) {
			continue
		}
		if _, dup := seen[h]; dup {
			continue
		}
		seen[h] = struct{}{}
		out = append(out, h)
		if limit > 0 && len(out) >= limit {
			break
		}
	}
	return out
}

// StartProbeJob validates the newline separated candidates in the background
// and returns {"ok":true,"job":ID,"total":N}. optionsJSON may carry workers,
// dns_workers, timeout_sec, retries, rate_per_sec, limit, user_agent,
// accept_any, adaptive and wildcard_filter. Starting a job cancels any job
// that is still running.
func StartProbeJob(hostsText, optionsJSON string) string {
	var opt pxOptions
	if strings.TrimSpace(optionsJSON) != "" {
		if err := json.Unmarshal([]byte(optionsJSON), &opt); err != nil {
			return mustJSON(map[string]any{"ok": false, "error": "invalid options: " + err.Error()})
		}
	}
	opt.normalize()
	hosts := pxParseHosts(hostsText, 50000)
	if len(hosts) == 0 {
		return mustJSON(map[string]any{"ok": false, "error": "no valid hosts supplied"})
	}
	pxJobs.Range(func(_, v any) bool {
		if old, ok := v.(*pxJob); ok && !old.done.Load() {
			old.setStatus("cancelled")
			old.cancel()
		}
		return true
	})

	ctx, cancel := context.WithCancel(context.Background())
	timeout := time.Duration(opt.TimeoutSec) * time.Second
	tr := &http.Transport{
		DialContext:            pxDialContext,
		TLSClientConfig:        &tls.Config{InsecureSkipVerify: true, MinVersion: tls.VersionTLS10}, // reachability probe, like httpx
		TLSHandshakeTimeout:    timeout,
		ResponseHeaderTimeout:  timeout,
		DisableKeepAlives:      true,
		DisableCompression:     true,
		ForceAttemptHTTP2:      true,
		MaxResponseHeaderBytes: 64 << 10,
	}
	j := &pxJob{
		id:        fmt.Sprintf("p%d-%x", pxJobSeq.Add(1), time.Now().UnixNano()&0xffff),
		opt:       opt,
		adaptive:  pxBool(opt.Adaptive, true),
		wildcard:  pxBool(opt.WildcardFilter, true),
		ctx:       ctx,
		cancel:    cancel,
		transport: tr,
		client: &http.Client{
			Transport:     tr,
			Timeout:       timeout,
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		},
		started: time.Now(),
		total:   len(hosts),
	}
	if opt.RatePerSec > 0 {
		j.rate = time.NewTicker(time.Second / time.Duration(opt.RatePerSec))
	}
	pxJobs.Store(j.id, j)
	go j.run(hosts)
	return mustJSON(map[string]any{"ok": true, "job": j.id, "total": j.total})
}

// PollProbeJob returns the live hosts found since cursor plus progress.
func PollProbeJob(jobID string, cursor int) string {
	v, ok := pxJobs.Load(jobID)
	if !ok {
		return mustJSON(map[string]any{"ok": false, "error": "unknown probe job"})
	}
	j := v.(*pxJob)
	j.mu.Lock()
	if cursor < 0 {
		cursor = 0
	}
	if cursor > len(j.results) {
		cursor = len(j.results)
	}
	end := cursor + 500
	if end > len(j.results) {
		end = len(j.results)
	}
	batch := append([]pxResult(nil), j.results[cursor:end]...)
	status := j.status
	j.mu.Unlock()

	done := j.done.Load()
	workers := 0
	if g := j.currentGate(); g != nil {
		workers = g.current()
	}
	return mustJSON(map[string]any{
		"ok":      true,
		"job":     j.id,
		"done":    done && end == cursor+len(batch) && !j.hasMore(end),
		"cursor":  end,
		"results": batch,
		"progress": map[string]any{
			"total":       j.total,
			"checked":     j.checked.Load(),
			"dns_failed":  j.dnsFailed.Load(),
			"wildcard":    j.wildcardHits.Load(),
			"timeouts":    j.timeouts.Load(),
			"live":        j.live.Load(),
			"workers":     workers,
			"status":      status,
			"elapsed_ms":  time.Since(j.started).Milliseconds(),
		},
	})
}

// CancelProbeJob stops a running job; results found so far stay pollable.
func CancelProbeJob(jobID string) string {
	v, ok := pxJobs.Load(jobID)
	if !ok {
		return mustJSON(map[string]any{"ok": false, "error": "unknown probe job"})
	}
	j := v.(*pxJob)
	j.setStatus("cancelled")
	j.cancel()
	return mustJSON(map[string]any{"ok": true})
}

func (j *pxJob) hasMore(end int) bool {
	j.mu.Lock()
	defer j.mu.Unlock()
	return end < len(j.results)
}

func (j *pxJob) currentGate() *pxGate {
	j.mu.Lock()
	defer j.mu.Unlock()
	return j.gate
}

func (j *pxJob) setStatus(s string) {
	j.mu.Lock()
	if j.status == "" {
		j.status = s
	}
	j.mu.Unlock()
}

func (j *pxJob) addResult(r pxResult) {
	j.mu.Lock()
	j.results = append(j.results, r)
	n := len(j.results)
	j.mu.Unlock()
	j.live.Store(int64(n))
	if j.opt.Limit > 0 && n >= j.opt.Limit {
		j.setStatus("limit")
		j.cancel()
	}
}

func (j *pxJob) finish() {
	if j.ctx.Err() != nil {
		j.setStatus("cancelled")
	} else {
		j.setStatus("completed")
	}
	if j.rate != nil {
		j.rate.Stop()
	}
	j.transport.CloseIdleConnections()
	j.cancel()
	j.done.Store(true)
	time.AfterFunc(10*time.Minute, func() { pxJobs.Delete(j.id) })
}

func (j *pxJob) run(hosts []string) {
	defer j.finish()
	ctx := j.ctx
	gate := newPxGate(j.opt.Workers, j.adaptive)
	j.mu.Lock()
	j.gate = gate
	j.mu.Unlock()

	dnsCh := make(chan string, j.opt.DNSWorkers*2)
	httpCh := make(chan pxCandidate, j.opt.Workers*2)
	var dnsWG, httpWG sync.WaitGroup

	for i := 0; i < j.opt.DNSWorkers; i++ {
		dnsWG.Add(1)
		go func() {
			defer dnsWG.Done()
			for h := range dnsCh {
				if ctx.Err() != nil {
					continue
				}
				ips, ok := j.resolveHost(ctx, h)
				if !ok {
					j.dnsFailed.Add(1)
					j.checked.Add(1)
					continue
				}
				if j.wildcard && j.isWildcard(ctx, h, ips) {
					j.wildcardHits.Add(1)
					j.checked.Add(1)
					continue
				}
				select {
				case httpCh <- pxCandidate{host: h, ips: ips}:
				case <-ctx.Done():
				}
			}
		}()
	}
	for i := 0; i < j.opt.Workers; i++ {
		httpWG.Add(1)
		go func() {
			defer httpWG.Done()
			for c := range httpCh {
				if ctx.Err() != nil || !gate.acquire(ctx) {
					continue
				}
				res, ok, timedOut := j.probe(ctx, c)
				gate.release()
				gate.record(timedOut)
				if timedOut {
					j.timeouts.Add(1)
				}
				j.checked.Add(1)
				if ok {
					j.addResult(res)
				}
			}
		}()
	}

feed:
	for _, h := range hosts {
		select {
		case dnsCh <- h:
		case <-ctx.Done():
			break feed
		}
	}
	close(dnsCh)
	dnsWG.Wait()
	close(httpCh)
	httpWG.Wait()
}

// pxHostOnly strips an optional :port (used by tests and explicit targets).
func pxHostOnly(host string) string {
	if h, _, err := net.SplitHostPort(host); err == nil {
		return h
	}
	return host
}

// resolveHost reports the addresses of h; ok is false for dead / unresolvable names.
func (j *pxJob) resolveHost(ctx context.Context, h string) ([]net.IP, bool) {
	ips, nx, err := pxResolverPool.lookup(ctx, pxHostOnly(h))
	if err != nil || nx || len(ips) == 0 {
		return nil, false
	}
	return ips, true
}

// isWildcard is true when h resolves only because its parent has a wildcard
// record, i.e. every address of h is one the parent returns for random labels.
func (j *pxJob) isWildcard(ctx context.Context, h string, ips []net.IP) bool {
	name := pxHostOnly(h)
	if net.ParseIP(name) != nil {
		return false
	}
	labels := strings.Split(name, ".")
	if len(labels) < 3 {
		return false // an apex: nothing to be a wildcard of
	}
	parent := strings.Join(labels[1:], ".")
	v, _ := j.wild.LoadOrStore(parent, &pxWildcard{})
	w := v.(*pxWildcard)
	w.once.Do(func() {
		w.ips = map[string]struct{}{}
		for i := 0; i < 2; i++ {
			probe := fmt.Sprintf("ghi-%08x.%s", rand.Uint32(), parent)
			pips, nx, err := pxResolverPool.lookup(ctx, probe)
			if err != nil || nx {
				continue
			}
			for _, ip := range pips {
				w.ips[ip.String()] = struct{}{}
			}
		}
	})
	if len(w.ips) == 0 || len(ips) == 0 {
		return false
	}
	for _, ip := range ips {
		if _, in := w.ips[ip.String()]; !in {
			return false
		}
	}
	return true
}

func pxIsTimeout(err error) bool {
	var ne net.Error
	return errors.Is(err, context.DeadlineExceeded) || (errors.As(err, &ne) && ne.Timeout())
}

func (j *pxJob) waitRate(ctx context.Context) bool {
	if j.rate == nil {
		return ctx.Err() == nil
	}
	select {
	case <-j.rate.C:
		return true
	case <-ctx.Done():
		return false
	}
}

// probe tries HTTPS then HTTP, retrying timeouts. A 2xx-3xx answer is live;
// with accept_any every other HTTP answer counts too.
func (j *pxJob) probe(ctx context.Context, c pxCandidate) (pxResult, bool, bool) {
	for attempt := 0; ; attempt++ {
		res, ok, timedOut := j.probeOnce(ctx, c)
		if ok {
			return res, true, false
		}
		if timedOut && attempt < j.opt.Retries && ctx.Err() == nil {
			continue
		}
		return pxResult{}, false, timedOut
	}
}

func (j *pxJob) probeOnce(ctx context.Context, c pxCandidate) (pxResult, bool, bool) {
	var fallback *pxResult
	timedOut := false
	ip := ""
	if len(c.ips) > 0 {
		ip = c.ips[0].String()
	}
	for _, scheme := range []string{"https", "http"} {
		if !j.waitRate(ctx) {
			break
		}
		start := time.Now()
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, scheme+"://"+c.host+"/", nil)
		if err != nil {
			continue
		}
		req.Header.Set("User-Agent", effectiveUserAgent(j.opt.UserAgent))
		req.Header.Set("Range", "bytes=0-0")
		req.Header.Set("Accept", "*/*")
		resp, err := j.client.Do(req)
		if err != nil {
			if pxIsTimeout(err) {
				timedOut = true
			}
			continue
		}
		res := pxResult{
			Host:      c.host,
			Status:    resp.StatusCode,
			Scheme:    scheme,
			LatencyMs: time.Since(start).Milliseconds(),
			IP:        ip,
			Server:    resp.Header.Get("Server"),
			CDN:       detectCDN(resp.Header),
			Location:  resp.Header.Get("Location"),
		}
		if resp.TLS != nil {
			res.TLS = tlsVersionName(resp.TLS.Version)
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 512))
		resp.Body.Close()
		if res.Status >= 200 && res.Status < 400 {
			return res, true, false
		}
		if fallback == nil {
			copyRes := res
			fallback = &copyRes
		}
	}
	if j.opt.AcceptAny && fallback != nil {
		return *fallback, true, false
	}
	return pxResult{}, false, timedOut
}
