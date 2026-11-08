package mobile

import (
	"context"
	"net"
	"time"
)

// This file lets every DNS record lookup in the engine use the same resolver
// pool as the probe engine (see px_dns.go).
//
// Why it matters on Android: Go reads /etc/resolv.conf, which does not exist on
// Android, so the pure-Go resolver silently falls back to 127.0.0.1:53. cgo's
// getaddrinfo covers plain address lookups, but MX, NS, TXT, SRV and CNAME
// queries always take the pure-Go path and therefore came back empty. Pool
// resolvers talk to public DNS servers directly, which is also how dnsx treats
// its resolver list.

const pxLookupAttemptTimeout = 3 * time.Second

// pxResolvers returns the resolvers to try for one lookup: up to three healthy
// pool members (rotated to spread the load) followed by the platform resolver,
// so private / split-horizon networks keep working.
func pxResolvers() []*net.Resolver {
	pool := pxResolverPool.snapshotPool()
	out := make([]*net.Resolver, 0, 4)
	if n := uint32(len(pool)); n > 0 {
		// uint32 arithmetic: int is 32 bits on armeabi-v7a and could go negative.
		start := pxResolverPool.next.Add(1)
		now := time.Now().UnixNano()
		for i := uint32(0); i < n && len(out) < 3; i++ {
			rv := pool[(start+i)%n]
			if now < rv.downUntil.Load() {
				continue
			}
			out = append(out, rv.r)
		}
	}
	return append(out, net.DefaultResolver)
}

// pxLookup runs fn against each candidate resolver until one answers. A
// definitive "not found" is returned immediately (asking another resolver
// would only repeat the question); any other failure moves on to the next
// resolver. Every attempt gets its own timeout so one dead resolver cannot eat
// the caller's whole deadline.
func pxLookup[T any](ctx context.Context, fn func(context.Context, *net.Resolver) (T, error)) (T, error) {
	var zero T
	var lastErr error
	for _, r := range pxResolvers() {
		if ctx.Err() != nil {
			break
		}
		cctx, cancel := context.WithTimeout(ctx, pxLookupAttemptTimeout)
		v, err := fn(cctx, r)
		cancel()
		if err == nil {
			return v, nil
		}
		if pxIsNotFound(err) {
			return zero, err
		}
		lastErr = err
	}
	if lastErr == nil {
		lastErr = ctx.Err()
	}
	return zero, lastErr
}
