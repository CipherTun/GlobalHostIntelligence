package mobile

import (
	"context"
	"io"
	"net"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/projectdiscovery/cdncheck"
)

// cdnSignal is one independent piece of evidence about the edge in front of a
// host. Signals are kept separate so the caller can see why a verdict was
// reached instead of trusting a single heuristic.
type cdnSignal struct {
	Signal     string `json:"signal"`   // header | ip_range | cname
	Kind       string `json:"kind"`     // cdn | waf | cloud
	Provider   string `json:"provider"` // human readable provider name
	Confidence string `json:"confidence"`
	Evidence   string `json:"evidence,omitempty"`
}

// cdnClient returns the shared cdncheck client (the same one cdn_evidence.go
// uses), built lazily because loading its provider ranges is not free on a
// phone.
func cdnClient() *cdncheck.Client {
	cdnCheckOnce.Do(func() { cdnCheckClient = cdncheck.New() })
	return cdnCheckClient
}

// cloudFromAddresses names the cloud provider (AWS, GCP, Azure, ...) whose
// published ranges contain one of the addresses. It needs no network access.
func cloudFromAddresses(addresses []string) string {
	client := cdnClient()
	for _, raw := range addresses {
		ip := net.ParseIP(raw)
		if ip == nil {
			continue
		}
		if ok, name, err := client.CheckCloud(ip); err == nil && ok {
			return name
		}
	}
	return ""
}

// ipRangeSignals checks one address against the CDN, WAF and cloud ranges that
// cdncheck ships. Range membership is strong evidence: an address inside a
// provider's published range is operated by that provider.
func ipRangeSignals(ip net.IP) []cdnSignal {
	client := cdnClient()
	var out []cdnSignal
	if ok, name, err := client.CheckCDN(ip); err == nil && ok {
		out = append(out, cdnSignal{Signal: "ip_range", Kind: "cdn", Provider: name, Confidence: "high", Evidence: ip.String()})
	}
	if ok, name, err := client.CheckWAF(ip); err == nil && ok {
		out = append(out, cdnSignal{Signal: "ip_range", Kind: "waf", Provider: name, Confidence: "high", Evidence: ip.String()})
	}
	if ok, name, err := client.CheckCloud(ip); err == nil && ok {
		out = append(out, cdnSignal{Signal: "ip_range", Kind: "cloud", Provider: name, Confidence: "high", Evidence: ip.String()})
	}
	return out
}

// cnameSignal uses cdncheck's CNAME fallback: a host whose CNAME chain ends in
// a known CDN/WAF zone is fronted by it even when the answering IP is not in
// any published range. The library resolves DNS itself and has no context, so
// it runs in a goroutine bounded by timeout.
func cnameSignal(host string, timeout time.Duration) *cdnSignal {
	if net.ParseIP(host) != nil {
		return nil
	}
	type answer struct {
		ok       bool
		provider string
		kind     string
	}
	ch := make(chan answer, 1)
	go func() {
		ok, provider, kind, err := cdnClient().CheckDomainWithFallback(host)
		if err != nil || !ok {
			ch <- answer{}
			return
		}
		ch <- answer{ok: true, provider: provider, kind: kind}
	}()
	timer := time.NewTimer(timeout)
	defer timer.Stop()
	select {
	case a := <-ch:
		if !a.ok {
			return nil
		}
		kind := strings.ToLower(strings.TrimSpace(a.kind))
		if kind == "" {
			kind = "cdn"
		}
		return &cdnSignal{Signal: "cname", Kind: kind, Provider: a.provider, Confidence: "high", Evidence: "CNAME chain"}
	case <-timer.C:
		return nil
	}
}

// parseEvidenceLabel splits the labels produced by detectCDNEvidence
// ("Cloudflare (high)") into provider and confidence.
func parseEvidenceLabel(label string) (name, confidence string) {
	label = strings.TrimSpace(label)
	if i := strings.LastIndex(label, " ("); i > 0 && strings.HasSuffix(label, ")") {
		return label[:i], label[i+2 : len(label)-1]
	}
	return label, "low"
}

func headerSignals(h http.Header, host string, addresses []string) []cdnSignal {
	label := detectCDNEvidence(h, h.Get("Server"), addresses, host)
	if label == "" {
		return nil
	}
	name, confidence := parseEvidenceLabel(label)
	return []cdnSignal{{Signal: "header", Kind: "cdn", Provider: name, Confidence: confidence, Evidence: "HTTP response headers"}}
}

// summarizeCDNSignals picks the strongest CDN/WAF provider as the verdict and
// lists every provider seen. Cloud-range matches are reported as providers but
// never as the verdict: running on AWS says where a host lives, not that a CDN
// fronts it.
func summarizeCDNSignals(signals []cdnSignal) (verdict string, providers []string) {
	rank := map[string]int{"high": 3, "medium": 2, "low": 1}
	best := 0
	seen := map[string]bool{}
	for _, s := range signals {
		if s.Provider == "" {
			continue
		}
		key := strings.ToLower(s.Provider)
		if !seen[key] {
			seen[key] = true
			providers = append(providers, s.Provider)
		}
		if s.Kind == "cloud" {
			continue
		}
		if r := rank[s.Confidence]; r > best {
			best = r
			verdict = s.Provider
		}
	}
	sort.Strings(providers)
	return verdict, providers
}

func hostForURL(host string) string {
	if strings.Contains(host, ":") && !strings.HasPrefix(host, "[") {
		return "[" + host + "]"
	}
	return host
}

func headerProbe(ctx context.Context, host string, addresses []string) (int, string, []cdnSignal) {
	client := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	for _, scheme := range []string{"https", "http"} {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, scheme+"://"+hostForURL(host)+"/", nil)
		if err != nil {
			return 0, "", nil
		}
		req.Header.Set("User-Agent", CurrentBrowserUserAgent)
		req.Header.Set("Range", "bytes=0-0")
		resp, err := client.Do(req)
		if err != nil {
			continue
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 512))
		resp.Body.Close()
		return resp.StatusCode, resp.Header.Get("Server"), headerSignals(resp.Header, host, addresses)
	}
	return 0, "", nil
}

// InspectCDN combines three independent signals into one verdict, in the spirit
// of httpx's -cdn flag and ProjectDiscovery's cdncheck:
//
//   - cdncheck IP ranges (CDN, WAF and cloud) for every resolved address,
//   - cdncheck's CNAME fallback for hosts fronted by a CDN on foreign IPs,
//   - response-header evidence with an explicit confidence level.
//
// It only sends one ordinary GET request and a few DNS queries.
func InspectCDN(target string, timeoutSeconds int) string {
	start := time.Now()
	host := normalizeToolHost(target)
	if h, _, err := net.SplitHostPort(host); err == nil {
		host = h
	}
	host = normalizeDomain(host)
	if host == "" {
		return mustJSON(map[string]any{"ok": false, "error": "host is required"})
	}
	timeoutSeconds = clampInt(timeoutSeconds, 3, 30)
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutSeconds)*time.Second)
	defer cancel()

	ips, _, lookupErr := pxResolverPool.lookup(ctx, host)
	addresses := make([]string, 0, len(ips))
	for _, ip := range ips {
		addresses = append(addresses, ip.String())
	}

	var (
		wg          sync.WaitGroup
		cname       *cdnSignal
		headerSigs  []cdnSignal
		status      int
		serverValue string
	)
	wg.Add(2)
	go func() {
		defer wg.Done()
		cname = cnameSignal(host, time.Duration(clampInt(timeoutSeconds, 3, 8))*time.Second)
	}()
	go func() {
		defer wg.Done()
		status, serverValue, headerSigs = headerProbe(ctx, host, addresses)
	}()
	var rangeSigs []cdnSignal
	for i, ip := range ips {
		if i >= 8 {
			break
		}
		rangeSigs = append(rangeSigs, ipRangeSignals(ip)...)
	}
	wg.Wait()

	signals := make([]cdnSignal, 0, len(rangeSigs)+len(headerSigs)+1)
	if cname != nil {
		signals = append(signals, *cname)
	}
	signals = append(signals, rangeSigs...)
	signals = append(signals, headerSigs...)

	verdict, providers := summarizeCDNSignals(signals)
	if providers == nil {
		providers = []string{}
	}
	result := map[string]any{
		"ok":         true,
		"host":       host,
		"addresses":  addresses,
		"status":     status,
		"server":     serverValue,
		"behind_cdn": verdict != "",
		"verdict":    verdict,
		"providers":  providers,
		"signals":    signals,
		"elapsed_ms": time.Since(start).Milliseconds(),
	}
	if len(addresses) == 0 && lookupErr != nil {
		result["dns_error"] = lookupErr.Error()
	}
	return mustJSON(result)
}
