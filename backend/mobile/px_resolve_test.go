package mobile

import (
	"context"
	"errors"
	"net"
	"testing"
)

func TestPxResolversEndsWithPlatformResolver(t *testing.T) {
	list := pxResolvers()
	if len(list) < 1 || len(list) > 4 {
		t.Fatalf("unexpected resolver count %d", len(list))
	}
	if list[len(list)-1] != net.DefaultResolver {
		t.Fatal("platform resolver must be the last fallback")
	}
}

func TestPxLookupReturnsFirstSuccess(t *testing.T) {
	calls := 0
	v, err := pxLookup(context.Background(), func(context.Context, *net.Resolver) (string, error) {
		calls++
		return "ok", nil
	})
	if err != nil || v != "ok" || calls != 1 {
		t.Fatalf("got %q %v after %d calls", v, err, calls)
	}
}

// NXDOMAIN / NODATA is an answer: asking more resolvers would not change it.
func TestPxLookupStopsOnNotFound(t *testing.T) {
	calls := 0
	_, err := pxLookup(context.Background(), func(context.Context, *net.Resolver) ([]string, error) {
		calls++
		return nil, &net.DNSError{Err: "no such host", Name: "x.example", IsNotFound: true}
	})
	if err == nil || calls != 1 {
		t.Fatalf("err=%v calls=%d, want an error after exactly 1 call", err, calls)
	}
}

func TestPxLookupTriesEveryResolverOnFailure(t *testing.T) {
	want := len(pxResolvers())
	calls := 0
	_, err := pxLookup(context.Background(), func(context.Context, *net.Resolver) (int, error) {
		calls++
		return 0, errors.New("timeout")
	})
	if err == nil {
		t.Fatal("expected the last error to be returned")
	}
	if calls != want {
		t.Fatalf("tried %d resolvers, want %d", calls, want)
	}
}

func TestPxLookupHonoursCancelledContext(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	calls := 0
	_, err := pxLookup(ctx, func(context.Context, *net.Resolver) (int, error) {
		calls++
		return 1, nil
	})
	if err == nil || calls != 0 {
		t.Fatalf("err=%v calls=%d, want an error and no calls", err, calls)
	}
}
