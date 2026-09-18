package mobile

import (
	"net/http"
	"strings"
)

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
