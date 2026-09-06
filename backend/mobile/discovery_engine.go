package mobile

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// DiscoverRawSource returns passive candidates only. Validation is deliberately
// separated so every candidate is probed exactly once by the Android session.
func DiscoverRawSource(query, source string, maxResults int) string {
	query = strings.TrimSpace(strings.ToLower(query))
	maxResults = clampDiscoveryLimit(maxResults)
	client := &http.Client{Timeout: 8 * time.Second}
	if strings.EqualFold(source, "country-fast") {
		return DiscoverCountryFast(query, maxResults)
	}
	domains, err := discoverRaw(client, query, source, maxResults)
	out := map[string]any{"query": query, "source": source, "domains": uniqueLimited(domains, maxResults)}
	if err != nil {
		out["error"] = err.Error()
	}
	return mustJSON(out)
}

// DiscoverCountryFast uses independent country and ASN observations. A timeout
// from URLScan, RIPEstat, or an individual ASN never fails the whole run.
func DiscoverCountryFast(country string, maxResults int) string {
	country = strings.ToLower(strings.TrimSpace(country))
	maxResults = clampDiscoveryLimit(maxResults)
	if !isCountryCode(country) {
		return mustJSON(map[string]any{"country": country, "domains": []string{}, "error": "country must be ISO-3166 alpha-2"})
	}
	client := &http.Client{Timeout: 8 * time.Second}
	type result struct {
		name    string
		domains []string
		err     error
	}
	ch := make(chan result, 64)
	var wg sync.WaitGroup
	run := func(name string, fn func() ([]string, error)) {
		wg.Add(1)
		go func() { defer wg.Done(); d, e := fn(); ch <- result{name, d, e} }()
	}
	run("urlscan-country", func() ([]string, error) { return urlscanCountry(client, country, maxResults) })
	run("ripe-asns", func() ([]string, error) {
		data, err := getJSON(client, "https://stat.ripe.net/data/country-asns/data.json?resource="+url.QueryEscape(country)+"&lod=1&sourceapp=globalhostintelligence", 8<<20)
		if err != nil {
			return nil, err
		}
		var obj struct {
			Data struct {
				ASNs []any `json:"asns"`
			} `json:"data"`
		}
		if err = json.Unmarshal(data, &obj); err != nil {
			return nil, err
		}
		asns := make([]string, 0, 24)
		for _, raw := range obj.Data.ASNs {
			var s string
			switch v := raw.(type) {
			case string:
				s = v
			case float64:
				s = fmt.Sprintf("%.0f", v)
			}
			s = strings.ToUpper(strings.TrimSpace(s))
			if s == "" {
				continue
			}
			if !strings.HasPrefix(s, "AS") {
				s = "AS" + s
			}
			asns = append(asns, s)
			if len(asns) >= 24 {
				break
			}
		}
		out := make([]string, 0, maxResults)
		var mu sync.Mutex
		var awg sync.WaitGroup
		sem := make(chan struct{}, 8)
		for _, asn := range asns {
			asn := asn
			awg.Add(1)
			go func() {
				defer awg.Done()
				sem <- struct{}{}
				defer func() { <-sem }()
				d, e := urlscanASN(client, asn, min(maxResults, 120))
				if e == nil {
					mu.Lock()
					out = append(out, d...)
					mu.Unlock()
				}
			}()
		}
		awg.Wait()
		return uniqueLimited(out, maxResults), nil
	})
	go func() { wg.Wait(); close(ch) }()
	combined := make([]string, 0, maxResults*2)
	errors := map[string]string{}
	sources := make([]string, 0, 32)
	for r := range ch {
		sources = append(sources, r.name)
		if r.err != nil {
			errors[r.name] = r.err.Error()
			continue
		}
		combined = append(combined, r.domains...)
	}
	combined = uniqueLimited(combined, maxResults)
	return mustJSON(map[string]any{"country": country, "domains": combined, "sources": sources, "errors": errors})
}

// DiscoverSubdomains fans out across independent passive sources and returns
// observed names without requiring the host to be live.
func DiscoverSubdomains(domain string, maxResults int) string {
	domain = normalizeDomain(domain)
	maxResults = clampDiscoveryLimit(maxResults)
	if !isHostname(domain) {
		return mustJSON(map[string]any{"domain": domain, "subdomains": []string{}, "error": "invalid domain"})
	}
	client := &http.Client{Timeout: 8 * time.Second}
	names := []string{"crt.sh", "crt.name", "ctlogs.dev", "certspotter", "rapiddns", "anubis", "subdomain.center", "hackertarget", "wayback", "threatminer", "commoncrawl", "otx", "subdomain.app", "sonar", "riddler", "jldc", "sublist3r", "urlscan"}
	type result struct {
		name    string
		domains []string
		err     error
	}
	ch := make(chan result, len(names))
	sem := make(chan struct{}, 10)
	var wg sync.WaitGroup
	for _, name := range names {
		name := name
		wg.Add(1)
		go func() {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			d, e := discoverRaw(client, domain, name, maxResults)
			ch <- result{name, d, e}
		}()
	}
	go func() { wg.Wait(); close(ch) }()
	out := make([]string, 0, maxResults*2)
	errors := map[string]string{}
	counts := map[string]int{}
	for r := range ch {
		if r.err != nil {
			errors[r.name] = r.err.Error()
			continue
		}
		for _, d := range r.domains {
			d = normalizeDomain(d)
			if d != domain && matchesScope(d, domain) && isHostname(d) {
				out = append(out, d)
			}
		}
		counts[r.name] = len(r.domains)
	}
	return mustJSON(map[string]any{"domain": domain, "subdomains": uniqueLimited(out, maxResults), "source_counts": counts, "errors": errors, "sources": names})
}

func clampDiscoveryLimit(n int) int {
	if n < 1 {
		n = 500
	}
	if n > 500 {
		n = 500
	}
	return n
}
