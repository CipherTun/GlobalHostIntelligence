package mobile

import (
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// This file is the DNS layer of the probe engine. It borrows the ideas of
// puredns / massdns / dnsx, which all resolve through a *pool* of resolvers:
//
//   - queries are spread over several public resolvers (and avoid ISP-level
//     filtering or a broken system resolver; Go has no /etc/resolv.conf on
//     Android),
//   - resolvers that keep failing are benched for a while,
//   - identical in-flight lookups are collapsed (single flight) and answers are
//     cached, so a hostname is never resolved twice,
//   - the platform resolver is the last fallback, so private / split-horizon
//     networks keep working.
//
// Everything is standard library only: the pro tools cannot be linked into a
// gomobile library, but their techniques can.

var pxDefaultResolvers = []string{
	"1.1.1.1:53", "8.8.8.8:53", "9.9.9.9:53",
	"1.0.0.1:53", "8.8.4.4:53", "149.112.112.112:53",
}

// pxResolverPool is shared by the probe engine and by every HTTP request the
// library makes (see init below).
var pxResolverPool = newPxDNS(pxDefaultResolvers)

func init() {
	// All passive-source requests use http.DefaultTransport. Route its dialing
	// through the pool and give it room for parallel requests to the same API
	// host (the stock limit is 2 idle connections per host).
	if tr, ok := http.DefaultTransport.(*http.Transport); ok {
		tr.DialContext = pxDialContext
		tr.MaxIdleConns = 256
		tr.MaxIdleConnsPerHost = 16
		tr.IdleConnTimeout = 60 * time.Second
	}
}

// SetDNSServers replaces the resolver pool. Pass a comma separated list such
// as "1.1.1.1,8.8.8.8:53"; an empty string restores the built-in pool.
func SetDNSServers(csv string) string {
	servers := []string{}
	for _, part := range strings.Split(csv, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		if _, _, err := net.SplitHostPort(part); err != nil {
			part = net.JoinHostPort(strings.Trim(part, "[]"), "53")
		}
		if host, _, err := net.SplitHostPort(part); err != nil || net.ParseIP(host) == nil {
			return mustJSON(map[string]any{"ok": false, "error": "invalid DNS server: " + part})
		}
		servers = append(servers, part)
	}
	if len(servers) == 0 {
		servers = pxDefaultResolvers
	}
	pxResolverPool.setServers(servers)
	return mustJSON(map[string]any{"ok": true, "servers": servers})
}

type pxResolver struct {
	addr      string
	r         *net.Resolver
	fails     atomic.Int32
	downUntil atomic.Int64 // unix nanoseconds
}

func newPxResolver(addr string) *pxResolver {
	rv := &pxResolver{addr: addr}
	rv.r = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			d := net.Dialer{Timeout: 2 * time.Second}
			return d.DialContext(ctx, network, addr)
		},
	}
	return rv
}

type pxDNSEntry struct {
	ips []net.IP
	nx  bool
	exp time.Time
}

type pxDNSCall struct {
	done chan struct{}
	ips  []net.IP
	nx   bool
	err  error
}

type pxDNS struct {
	mu        sync.Mutex
	pool      []*pxResolver
	cache     map[string]pxDNSEntry
	inflight  map[string]*pxDNSCall
	next      atomic.Uint32
	resolveFn func(ctx context.Context, host string) ([]net.IP, bool, error)
}

func newPxDNS(servers []string) *pxDNS {
	d := &pxDNS{
		cache:    map[string]pxDNSEntry{},
		inflight: map[string]*pxDNSCall{},
	}
	d.setServers(servers)
	d.resolveFn = d.resolve
	return d
}

func (d *pxDNS) setServers(servers []string) {
	pool := make([]*pxResolver, 0, len(servers))
	for _, s := range servers {
		pool = append(pool, newPxResolver(s))
	}
	d.mu.Lock()
	d.pool = pool
	d.cache = map[string]pxDNSEntry{}
	d.mu.Unlock()
}

func (d *pxDNS) snapshotPool() []*pxResolver {
	d.mu.Lock()
	defer d.mu.Unlock()
	return d.pool
}

// lookup returns the addresses of host. nx is true for a definitive "does not
// exist / has no address" answer; err is set only when the question could not
// be answered (all resolvers failed or the context ended).
func (d *pxDNS) lookup(ctx context.Context, host string) (ips []net.IP, nx bool, err error) {
	host = strings.ToLower(strings.TrimSuffix(host, "."))
	if ip := net.ParseIP(host); ip != nil {
		return []net.IP{ip}, false, nil
	}
	for attempt := 0; attempt < 2; attempt++ {
		d.mu.Lock()
		if e, ok := d.cache[host]; ok && time.Now().Before(e.exp) {
			d.mu.Unlock()
			return e.ips, e.nx, nil
		}
		if c, ok := d.inflight[host]; ok {
			d.mu.Unlock()
			select {
			case <-c.done:
				// The leader's own context may have been cancelled; if ours is
				// still alive, ask again instead of inheriting that failure.
				if c.err != nil && ctx.Err() == nil && (errors.Is(c.err, context.Canceled) || errors.Is(c.err, context.DeadlineExceeded)) {
					continue
				}
				return c.ips, c.nx, c.err
			case <-ctx.Done():
				return nil, false, ctx.Err()
			}
		}
		c := &pxDNSCall{done: make(chan struct{})}
		d.inflight[host] = c
		d.mu.Unlock()

		c.ips, c.nx, c.err = d.resolveFn(ctx, host)

		d.mu.Lock()
		delete(d.inflight, host)
		if c.err == nil {
			if len(d.cache) > 100000 {
				d.cache = make(map[string]pxDNSEntry, 1024)
			}
			ttl := 5 * time.Minute
			if c.nx {
				ttl = time.Minute
			}
			d.cache[host] = pxDNSEntry{ips: c.ips, nx: c.nx, exp: time.Now().Add(ttl)}
		}
		d.mu.Unlock()
		close(c.done)
		return c.ips, c.nx, c.err
	}
	return nil, false, ctx.Err()
}

func pxAddrsToIPs(addrs []net.IPAddr) []net.IP {
	ips := make([]net.IP, 0, len(addrs))
	for _, a := range addrs {
		ips = append(ips, a.IP)
	}
	return ips
}

func pxIsNotFound(err error) bool {
	var de *net.DNSError
	return errors.As(err, &de) && de.IsNotFound
}

// resolve asks up to three healthy pool resolvers, then the platform resolver.
func (d *pxDNS) resolve(ctx context.Context, host string) ([]net.IP, bool, error) {
	pool := d.snapshotPool()
	var lastErr error
	tried := 0
	start := int(d.next.Add(1))
	for i := 0; i < len(pool) && tried < 3; i++ {
		rv := pool[(start+i)%len(pool)]
		if time.Now().UnixNano() < rv.downUntil.Load() {
			continue
		}
		tried++
		cctx, cancel := context.WithTimeout(ctx, 3*time.Second)
		addrs, err := rv.r.LookupIPAddr(cctx, host+".")
		cancel()
		if err == nil {
			rv.fails.Store(0)
			if len(addrs) == 0 {
				return nil, true, nil
			}
			return pxAddrsToIPs(addrs), false, nil
		}
		if pxIsNotFound(err) {
			rv.fails.Store(0)
			return nil, true, nil
		}
		if ctx.Err() != nil {
			return nil, false, ctx.Err()
		}
		lastErr = err
		if rv.fails.Add(1) >= 8 {
			rv.fails.Store(0)
			rv.downUntil.Store(time.Now().Add(30 * time.Second).UnixNano())
		}
	}

	cctx, cancel := context.WithTimeout(ctx, 4*time.Second)
	defer cancel()
	addrs, err := net.DefaultResolver.LookupIPAddr(cctx, host)
	if err == nil {
		if len(addrs) == 0 {
			return nil, true, nil
		}
		return pxAddrsToIPs(addrs), false, nil
	}
	if pxIsNotFound(err) {
		return nil, true, nil
	}
	if lastErr == nil {
		lastErr = err
	}
	return nil, false, lastErr
}

func pxOrderIPs(ips []net.IP, network string) []net.IP {
	v4 := make([]net.IP, 0, len(ips))
	v6 := make([]net.IP, 0, len(ips))
	for _, ip := range ips {
		if ip.To4() != nil {
			v4 = append(v4, ip)
		} else {
			v6 = append(v6, ip)
		}
	}
	switch network {
	case "tcp4":
		return v4
	case "tcp6":
		return v6
	}
	return append(v4, v6...)
}

// pxDialContext dials through the resolver pool and falls back to the stock
// dialer (platform resolver) whenever the pool cannot help.
func pxDialContext(ctx context.Context, network, addr string) (net.Conn, error) {
	d := &net.Dialer{Timeout: 5 * time.Second, KeepAlive: 30 * time.Second}
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}
	if net.ParseIP(host) != nil {
		return d.DialContext(ctx, network, addr)
	}
	ips, nx, lerr := pxResolverPool.lookup(ctx, host)
	if lerr != nil || nx || len(ips) == 0 {
		return d.DialContext(ctx, network, addr)
	}
	var lastErr error
	for _, ip := range pxOrderIPs(ips, network) {
		conn, derr := d.DialContext(ctx, network, net.JoinHostPort(ip.String(), port))
		if derr == nil {
			return conn, nil
		}
		lastErr = derr
		if ctx.Err() != nil {
			break
		}
	}
	if lastErr == nil {
		return d.DialContext(ctx, network, addr)
	}
	return nil, lastErr
}
