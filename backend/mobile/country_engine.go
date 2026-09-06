package mobile

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
)

// DiscoverCountryWorld is a resilient worldwide country discovery fan-out.
// It separates candidate collection from live validation and records individual
// source failures instead of failing the complete run.
func DiscoverCountryWorld(country string, maxResults int, providerConfigJSON string) string {
	country = strings.ToLower(strings.TrimSpace(country))
	if !isCountryCode(country) {
		return mustJSON(map[string]any{"country": country, "domains": []string{}, "sources": []string{}, "errors": map[string]string{"country": "country must be ISO-3166 alpha-2"}})
	}
	if maxResults < 1 {
		maxResults = 500
	}
	if maxResults > 500 {
		maxResults = 500
	}

	cfg := struct{ Censys, Netlas, Shodan string }{}
	_ = json.Unmarshal([]byte(providerConfigJSON), &cfg)
	client := newHTTPClient()

	type result struct {
		name    string
		domains []string
		err     error
	}
	results := make(chan result, 16)
	var wg sync.WaitGroup
	run := func(name string, fn func() ([]string, error)) {
		wg.Add(1)
		go func() { defer wg.Done(); d, e := fn(); results <- result{name, d, e} }()
	}

	// Independent country-level sources. URLScan is only one leg.
	run("urlscan-country", func() ([]string, error) { return urlscanCountry(client, country, maxResults) })
	run("ripe-asn-urlscan", func() ([]string, error) { return countryASNHostSeeds(client, country, maxResults) })
	run("ct-country-tld-crtsh", func() ([]string, error) { return crtsh(client, "%."+country, maxResults) })
	run("ct-country-tld-ctlogs", func() ([]string, error) { return ctlogsCountryTLD(client, country, maxResults) })

	if strings.TrimSpace(cfg.Censys) != "" {
		run("censys", func() ([]string, error) { return censysCountry(client, country, cfg.Censys, maxResults) })
	}
	if strings.TrimSpace(cfg.Netlas) != "" {
		run("netlas", func() ([]string, error) { return netlasCountry(client, country, cfg.Netlas, maxResults) })
	}
	if strings.TrimSpace(cfg.Shodan) != "" {
		run("shodan", func() ([]string, error) { return shodanCountry(client, country, cfg.Shodan, maxResults) })
	}

	go func() { wg.Wait(); close(results) }()

	combined := make([]string, 0, maxResults*3)
	errors := map[string]string{}
	counts := map[string]int{}
	sources := []string{}
	for r := range results {
		sources = append(sources, r.name)
		if r.err != nil {
			errors[r.name] = r.err.Error()
			continue
		}
		counts[r.name] = len(r.domains)
		combined = append(combined, r.domains...)
	}

	// Expand observed apexes through independent passive datasets. This is a
	// second stage, not a URLScan dependency, and never invents hostnames.
	apexes := make([]string, 0, 48)
	seenApex := map[string]bool{}
	for _, host := range combined {
		apex := apexDomain(host)
		if apex == "" || seenApex[apex] {
			continue
		}
		seenApex[apex] = true
		apexes = append(apexes, apex)
		if len(apexes) >= 48 {
			break
		}
	}
	var expandWG sync.WaitGroup
	expandSem := make(chan struct{}, 8)
	var mu sync.Mutex
	for _, apex := range apexes {
		apex := apex
		expandWG.Add(1)
		go func() {
			defer expandWG.Done()
			expandSem <- struct{}{}
			defer func() { <-expandSem }()
			sourceFns := []func(*http.Client, string, int) ([]string, error){
				crtsh, crtName, ctLogs, certspotter, rapiddns, anubis,
				subdomainCenter, hackerTarget, wayback, threatMiner,
				commonCrawl, otxPassiveDNS, subdomainAPI, sonar, riddler,
				jldc, sublist3r,
			}
			for _, sourceFn := range sourceFns {
				found, err := sourceFn(client, apex, min(maxResults, 100))
				if err != nil {
					continue
				}
				mu.Lock()
				combined = append(combined, found...)
				size := len(combined)
				mu.Unlock()
				if size >= maxResults*3 {
					return
				}
			}
		}()
	}
	expandWG.Wait()

	combined = uniqueLimited(combined, maxResults)
	if len(combined) == 0 && len(errors) > 0 {
		errors["engine"] = "all enabled country discovery sources failed or returned no candidates"
	}
	return mustJSON(map[string]any{
		"country": country, "domains": combined, "sources": sources,
		"source_counts": counts, "errors": errors,
		"mode": "worldwide-country-fanout", "candidate_limit": maxResults,
	})
}

func ctlogsCountryTLD(c *http.Client, country string, limit int) ([]string, error) {
	endpoint := "https://ctlogs.dev/search?q=" + url.QueryEscape("%."+country) + "&output=json"
	data, err := getJSON(c, endpoint, 20<<20)
	if err != nil {
		return nil, err
	}
	var rows []struct {
		Match string `json:"match"`
	}
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	out := make([]string, 0, limit)
	for _, row := range rows {
		h := normalizeDomain(row.Match)
		if isHostname(h) && strings.HasSuffix(h, "."+country) {
			out = append(out, h)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func countryASNHostSeeds(c *http.Client, country string, limit int) ([]string, error) {
	endpoint := "https://stat.ripe.net/data/country-asns/data.json?resource=" + url.QueryEscape(country) + "&lod=1&sourceapp=globalhostintelligence"
	data, err := getJSON(c, endpoint, 8<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Data struct {
			ASNs []any `json:"asns"`
		} `json:"data"`
	}
	if err := json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	asns := make([]string, 0, 48)
	for _, raw := range obj.Data.ASNs {
		var s string
		switch v := raw.(type) {
		case string:
			s = strings.ToUpper(strings.TrimSpace(v))
		case float64:
			s = fmt.Sprintf("AS%.0f", v)
		}
		if s == "" {
			continue
		}
		if !strings.HasPrefix(s, "AS") {
			s = "AS" + s
		}
		asns = append(asns, s)
		if len(asns) >= 48 {
			break
		}
	}
	out := make([]string, 0, limit)
	sem := make(chan struct{}, 8)
	var wg sync.WaitGroup
	var mu sync.Mutex
	for _, asn := range asns {
		asn := asn
		wg.Add(1)
		go func() {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			found, e := urlscanASN(c, asn, min(limit, 120))
			if e != nil {
				return
			}
			mu.Lock()
			out = append(out, found...)
			mu.Unlock()
		}()
	}
	wg.Wait()
	return uniqueLimited(out, limit), nil
}

func censysCountry(c *http.Client, country, token string, limit int) ([]string, error) {
	body := map[string]any{"query": fmt.Sprintf("host.location.country_code = \"%s\"", strings.ToUpper(country)), "fields": []string{"host.ip", "host.dns.names"}, "page_size": min(limit, 100)}
	raw, _ := json.Marshal(body)
	req, err := http.NewRequest(http.MethodPost, "https://api.platform.censys.io/v3/global/search/query", strings.NewReader(string(raw)))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	resp, err := c.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	raw, err = io.ReadAll(io.LimitReader(resp.Body, 16<<20))
	if err != nil {
		return nil, err
	}
	var obj struct {
		Result struct {
			Hits []struct {
				Host struct {
					DNS struct {
						Names []string `json:"names"`
					} `json:"dns"`
				} `json:"host"`
			} `json:"hits"`
		} `json:"result"`
	}
	if err := json.Unmarshal(raw, &obj); err != nil {
		return nil, err
	}
	var out []string
	for _, hit := range obj.Result.Hits {
		out = append(out, hit.Host.DNS.Names...)
	}
	return uniqueLimited(out, limit), nil
}

func netlasCountry(c *http.Client, country, token string, limit int) ([]string, error) {
	endpoint := "https://app.netlas.io/api/responses/?q=" + url.QueryEscape("geo.country:"+strings.ToUpper(country)) + "&start=0&fields=host"
	req, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "application/json")
	resp, err := c.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 16<<20))
	if err != nil {
		return nil, err
	}
	var obj struct {
		Items []struct {
			Data struct {
				Host string `json:"host"`
			} `json:"data"`
			Highlight struct {
				Host string `json:"host"`
			} `json:"highlight"`
		} `json:"items"`
	}
	if err := json.Unmarshal(raw, &obj); err != nil {
		return nil, err
	}
	out := make([]string, 0, len(obj.Items))
	for _, item := range obj.Items {
		if item.Data.Host != "" {
			out = append(out, item.Data.Host)
		} else if item.Highlight.Host != "" {
			out = append(out, item.Highlight.Host)
		}
	}
	return uniqueLimited(out, limit), nil
}

func shodanCountry(c *http.Client, country, key string, limit int) ([]string, error) {
	endpoint := "https://api.shodan.io/shodan/host/search?key=" + url.QueryEscape(key) + "&query=" + url.QueryEscape("country:"+strings.ToUpper(country)) + "&page=1&minify=true"
	raw, err := getJSON(c, endpoint, 20<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Matches []struct {
			Hostnames []string `json:"hostnames"`
		} `json:"matches"`
	}
	if err := json.Unmarshal(raw, &obj); err != nil {
		return nil, err
	}
	var out []string
	for _, m := range obj.Matches {
		out = append(out, m.Hostnames...)
	}
	return uniqueLimited(out, limit), nil
}
