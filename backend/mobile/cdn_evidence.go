package mobile

import (
	"net"
	"net/http"
	"strings"
	"sync"

	"github.com/projectdiscovery/cdncheck"
)

var (
	cdnCheckOnce   sync.Once
	cdnCheckClient *cdncheck.Client
)

// cdnFromAddresses classifies resolved IPs against the CDN and WAF ranges that
// ProjectDiscovery's cdncheck embeds. It needs no network access and is only a
// fallback for when response headers do not identify a provider.
func cdnFromAddresses(addresses []string) string {
	cdnCheckOnce.Do(func() { cdnCheckClient = cdncheck.New() })
	for _, raw := range addresses {
		ip := net.ParseIP(raw)
		if ip == nil {
			continue
		}
		if ok, name, err := cdnCheckClient.CheckCDN(ip); err == nil && ok {
			return name + " (IP range)"
		}
		if ok, name, err := cdnCheckClient.CheckWAF(ip); err == nil && ok {
			return name + " (IP range)"
		}
	}
	return ""
}

// detectCDNEvidence uses multiple observable indicators. It intentionally
// reports unknown instead of guessing when evidence is insufficient.
func detectCDNEvidence(h http.Header, server string, addresses []string, hostname string) string {
	type rule struct {
		name   string
		keys   []string
		values []string
	}
	rules := []rule{
		{"Cloudflare", []string{"cf-ray", "cf-cache-status", "cf-mitigated", "server"}, []string{"cloudflare"}},
		{"Amazon CloudFront", []string{"x-amz-cf-id", "x-amz-cf-pop", "via"}, []string{"cloudfront"}},
		{"Fastly", []string{"x-served-by", "x-cache", "fastly-debug-digest"}, []string{"fastly"}},
		{"Akamai", []string{"x-akamai-transformed", "akamai-grn", "x-akamai-request-id"}, []string{"akamai"}},
		{"Sucuri", []string{"x-sucuri-id", "x-sucuri-cache"}, []string{"sucuri"}},
		{"Imperva", []string{"x-iinfo", "x-cdn"}, []string{"imperva", "incapsula"}},
	}
	for _, r := range rules {
		for _, k := range r.keys {
			if h.Get(k) != "" {
				return r.name + " (high)"
			}
		}
		joined := strings.ToLower(server + " " + hostname + " " + strings.Join(h.Values("via"), " "))
		for _, v := range r.values {
			if strings.Contains(joined, v) {
				return r.name + " (medium)"
			}
		}
	}
	// Some CDN headers are vendor-neutral. Treat them as weak evidence only.
	for _, k := range []string{"x-cache", "age", "x-cdn", "x-edge-location"} {
		if h.Get(k) != "" {
			return "CDN/edge (low)"
		}
	}
	_ = addresses
	return ""
}
