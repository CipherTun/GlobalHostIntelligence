package mobile

import (
	"encoding/json"
	"fmt"
	"net/url"
	"strings"
	"sync"
)

func DiscoverCandidates(country string, source string, maxResults int) string {
	country = strings.ToLower(strings.TrimSpace(country))
	source = strings.ToLower(strings.TrimSpace(source))
	if maxResults < 1 {
		maxResults = 100
	}
	if maxResults > 2000 {
		maxResults = 2000
	}
	c := newHTTPClient()
	var domains []string
	var err error

	switch source {
	case "carrier", "country":
		endpoint := "https://stat.ripe.net/data/country-asns/data.json?resource=" + url.QueryEscape(country) + "&lod=1&sourceapp=globalhostintelligence"
		data, e := getJSON(c, endpoint, 8<<20)
		if e != nil {
			err = e
			break
		}
		var obj struct {
			Data struct {
				ASNs []any `json:"asns"`
			} `json:"data"`
		}
		if e = json.Unmarshal(data, &obj); e != nil {
			err = e
			break
		}
		asns := make([]string, 0, len(obj.Data.ASNs))
		for _, raw := range obj.Data.ASNs {
			switch v := raw.(type) {
			case string:
				v = strings.ToUpper(strings.TrimSpace(v))
				if !strings.HasPrefix(v, "AS") {
					v = "AS" + v
				}
				asns = append(asns, v)
			case float64:
				asns = append(asns, fmt.Sprintf("AS%.0f", v))
			}
		}
		if len(asns) > 64 {
			asns = asns[:64]
		}
		var mu sync.Mutex
		var wg sync.WaitGroup
		for _, asn := range asns {
			asn := asn
			wg.Add(1)
			go func() {
				defer wg.Done()
				prefixURL := "https://stat.ripe.net/data/announced-prefixes/data.json?resource=" + url.QueryEscape(asn) + "&sourceapp=globalhostintelligence"
				if _, e := getJSON(c, prefixURL, 16<<20); e != nil {
					return
				}
				found, e := urlscanASN(c, asn, maxResults)
				if e != nil {
					return
				}
				mu.Lock()
				domains = append(domains, found...)
				mu.Unlock()
			}()
		}
		wg.Wait()
		if len(domains) < maxResults {
			if found, e := urlscanCountry(c, country, maxResults-len(domains)); e == nil {
				domains = append(domains, found...)
			}
		}
		domains = uniqueLimited(domains, maxResults)
	case "urlscan":
		domains, err = urlscanCountry(c, country, maxResults)
	case "asn":
		domains, err = asnDiscovery(c, country, maxResults)
	default:
		return mustJSON(map[string]any{"query": country, "source": source, "error": "unknown candidate source"})
	}
	result := map[string]any{"query": country, "source": source, "domains": domains}
	if err != nil {
		result["error"] = err.Error()
	}
	return mustJSON(result)
}

func DiscoverCarrier(country string, maxResults int) string {
	raw := DiscoverCandidates(country, "carrier", maxResults)
	var obj struct {
		Domains []string `json:"domains"`
	}
	if err := json.Unmarshal([]byte(raw), &obj); err != nil {
		return raw
	}
	c := newHTTPClient()
	out := make([]domainResult, 0, maxResults)
	seen := map[string]bool{}
	for _, d := range obj.Domains {
		d = normalizeDomain(d)
		if d == "" || seen[d] || !isHostname(d) {
			continue
		}
		seen[d] = true
		if r, ok := validateHost(c, d); ok {
			out = append(out, r)
		}
		if len(out) >= maxResults {
			break
		}
	}
	return mustJSON(map[string]any{"query": country, "source": "carrier", "results": out})
}

func GenerateNetworkRequest(network, method, host, path, body string) string {
	network = strings.ToUpper(strings.TrimSpace(network))
	method = strings.ToUpper(strings.TrimSpace(method))
	if method == "" {
		method = "GET"
	}
	if host == "" {
		return ""
	}
	if path == "" {
		path = "/"
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	if network == "WEBSOCKET" && method == "GET" {
		return fmt.Sprintf("GET %s HTTP/1.1\\r\\nHost: %s\\r\\nUpgrade: websocket\\r\\nConnection: Upgrade\\r\\nSec-WebSocket-Version: 13\\r\\n\\r\\n", path, host)
	}
	extra := ""
	if network == "HTTP UPGRADE" || method == "UPGRADE" {
		extra = "Connection: Upgrade\\r\\nUpgrade: websocket\\r\\n"
	}
	return fmt.Sprintf("%s %s HTTP/1.1\\r\\nHost: %s\\r\\nUser-Agent: GlobalHostIntelligence/1.0\\r\\nAccept: */*\\r\\n%sContent-Length: %d\\r\\n\\r\\n%s", method, path, host, extra, len(body), body)
}
