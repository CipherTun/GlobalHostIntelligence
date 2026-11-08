package mobile

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Keyed passive-subdomain providers.
//
// These mirror the providers that ProjectDiscovery's subfinder reads from its
// provider-config.yaml (Chaos, VirusTotal, SecurityTrails, Shodan). The user
// supplies their own keys; nothing is embedded in the application and keys are
// kept out of every error string that reaches the UI.

// Base URLs are variables only so tests can point them at httptest servers.
var (
	chaosBaseURL          = "https://dns.projectdiscovery.io/dns"
	virusTotalBaseURL     = "https://www.virustotal.com/api/v3"
	securityTrailsBaseURL = "https://api.securitytrails.com/v1"
	shodanBaseURL         = "https://api.shodan.io"

	// keylessSubdomains is the anonymous passive fan-out; replaceable in tests so
	// they never touch the internet.
	keylessSubdomains = DiscoverSubdomains
)

// providerKeys is the JSON accepted by DiscoverSubdomainsWithKeys. "chaos" and
// "projectdiscovery" are aliases for the same ProjectDiscovery Cloud key.
type providerKeys struct {
	Chaos            string `json:"chaos"`
	ProjectDiscovery string `json:"projectdiscovery"`
	VirusTotal       string `json:"virustotal"`
	SecurityTrails   string `json:"securitytrails"`
	Shodan           string `json:"shodan"`
}

func (k providerKeys) chaosKey() string {
	if v := strings.TrimSpace(k.Chaos); v != "" {
		return v
	}
	return strings.TrimSpace(k.ProjectDiscovery)
}

// sanitizeNetErr drops the request URL that *url.Error wraps around the real
// cause. Shodan only accepts its key as a query parameter, so the raw error
// text would otherwise carry the key into the JSON the UI displays.
func sanitizeNetErr(err error) error {
	var ue *url.Error
	if errors.As(err, &ue) && ue.Err != nil {
		return ue.Err
	}
	return err
}

// providerGET performs an authenticated GET and maps the common failure
// statuses to messages the user can act on.
func providerGET(c *http.Client, req *http.Request, maxBytes int64) ([]byte, error) {
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", CurrentBrowserUserAgent)
	resp, err := c.Do(req)
	if err != nil {
		return nil, sanitizeNetErr(err)
	}
	defer resp.Body.Close()
	switch {
	case resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden:
		return nil, fmt.Errorf("authentication failed (HTTP %d): check the API key", resp.StatusCode)
	case resp.StatusCode == http.StatusTooManyRequests:
		return nil, errors.New("rate limited (HTTP 429)")
	case resp.StatusCode/100 != 2:
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, maxBytes))
}

// expandLabels turns provider output into in-scope hostnames. Chaos,
// SecurityTrails and Shodan return bare labels ("www"), VirusTotal returns
// FQDNs; both are accepted. The apex itself and wildcard markers are dropped.
func expandLabels(domain string, names []string, limit int) []string {
	out := make([]string, 0, len(names))
	for _, raw := range names {
		name := strings.Trim(strings.ToLower(strings.TrimSpace(raw)), ".")
		if name == "" || name == "*" {
			continue
		}
		host := name
		if host != domain && !strings.HasSuffix(host, "."+domain) {
			host = name + "." + domain
		}
		host = normalizeDomain(host)
		if host == domain || !matchesScope(host, domain) || !isHostname(host) {
			continue
		}
		out = append(out, host)
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit)
}

// chaosSubdomains queries ProjectDiscovery's Chaos dataset:
//
//	GET https://dns.projectdiscovery.io/dns/{domain}/subdomains
//	Authorization: <API key>        (the bare key, no "Bearer" prefix)
//
// The response is {"domain": "...", "subdomains": ["www", "api"]}.
func chaosSubdomains(c *http.Client, domain, apiKey string, limit int) ([]string, error) {
	if strings.TrimSpace(apiKey) == "" {
		return nil, errors.New("ProjectDiscovery Chaos API key is not configured")
	}
	req, err := http.NewRequest(http.MethodGet, chaosBaseURL+"/"+url.PathEscape(domain)+"/subdomains", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", strings.TrimSpace(apiKey))
	data, err := providerGET(c, req, 32<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Subdomains []string `json:"subdomains"`
	}
	if err := json.Unmarshal(data, &obj); err != nil {
		return nil, fmt.Errorf("unexpected Chaos response: %w", err)
	}
	return expandLabels(domain, obj.Subdomains, limit), nil
}

// virusTotalSubdomains reads up to three pages of the v3 subdomains relationship.
// The public API allows only 4 requests per minute, so paging is kept short.
// The "next" link is only followed when it stays on the same host, because the
// API key header would otherwise be sent to whatever host a response names.
func virusTotalSubdomains(c *http.Client, domain, apiKey string, limit int) ([]string, error) {
	base, err := url.Parse(virusTotalBaseURL)
	if err != nil {
		return nil, err
	}
	next := virusTotalBaseURL + "/domains/" + url.PathEscape(domain) + "/subdomains?limit=40"
	var names []string
	for page := 0; page < 3 && next != "" && len(names) < limit; page++ {
		req, err := http.NewRequest(http.MethodGet, next, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("x-apikey", strings.TrimSpace(apiKey))
		data, err := providerGET(c, req, 8<<20)
		if err != nil {
			if len(names) > 0 {
				break
			}
			return nil, err
		}
		var obj struct {
			Data []struct {
				ID string `json:"id"`
			} `json:"data"`
			Links struct {
				Next string `json:"next"`
			} `json:"links"`
		}
		if err := json.Unmarshal(data, &obj); err != nil {
			if len(names) > 0 {
				break
			}
			return nil, fmt.Errorf("unexpected VirusTotal response: %w", err)
		}
		for _, d := range obj.Data {
			names = append(names, d.ID)
		}
		next = ""
		if obj.Links.Next != "" {
			if u, e := url.Parse(obj.Links.Next); e == nil && strings.EqualFold(u.Host, base.Host) && u.Scheme == base.Scheme {
				next = u.String()
			}
		}
	}
	return expandLabels(domain, names, limit), nil
}

// securityTrailsSubdomains: GET /domain/{domain}/subdomains with an APIKEY header.
func securityTrailsSubdomains(c *http.Client, domain, apiKey string, limit int) ([]string, error) {
	endpoint := securityTrailsBaseURL + "/domain/" + url.PathEscape(domain) + "/subdomains?children_only=false&include_inactive=true"
	req, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("APIKEY", strings.TrimSpace(apiKey))
	data, err := providerGET(c, req, 16<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Subdomains []string `json:"subdomains"`
	}
	if err := json.Unmarshal(data, &obj); err != nil {
		return nil, fmt.Errorf("unexpected SecurityTrails response: %w", err)
	}
	return expandLabels(domain, obj.Subdomains, limit), nil
}

// shodanSubdomains: GET /dns/domain/{domain}?key=... (Shodan has no header auth).
func shodanSubdomains(c *http.Client, domain, apiKey string, limit int) ([]string, error) {
	endpoint := shodanBaseURL + "/dns/domain/" + url.PathEscape(domain) + "?key=" + url.QueryEscape(strings.TrimSpace(apiKey))
	req, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	data, err := providerGET(c, req, 16<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Subdomains []string `json:"subdomains"`
	}
	if err := json.Unmarshal(data, &obj); err != nil {
		return nil, fmt.Errorf("unexpected Shodan response: %w", err)
	}
	return expandLabels(domain, obj.Subdomains, limit), nil
}

// DiscoverSubdomainsWithKeys runs the anonymous passive fan-out of
// DiscoverSubdomains and, for every key present in providerConfigJSON, also
// queries the matching keyed provider (subfinder's provider set). Results are
// merged and de-duplicated; a failing provider is reported under "errors"
// without affecting the others.
//
// providerConfigJSON example:
//
//	{"chaos":"...","virustotal":"...","securitytrails":"...","shodan":"..."}
func DiscoverSubdomainsWithKeys(domain string, maxResults int, providerConfigJSON string) string {
	domain = normalizeDomain(queryDomain(domain))
	maxResults = clampDiscoveryLimit(maxResults)
	if !isHostname(domain) {
		return mustJSON(map[string]any{"ok": false, "domain": domain, "subdomains": []string{}, "error": "invalid domain"})
	}
	var keys providerKeys
	if strings.TrimSpace(providerConfigJSON) != "" {
		if err := json.Unmarshal([]byte(providerConfigJSON), &keys); err != nil {
			return mustJSON(map[string]any{"ok": false, "domain": domain, "subdomains": []string{}, "error": "invalid provider config: " + err.Error()})
		}
	}

	var base struct {
		Subdomains   []string          `json:"subdomains"`
		SourceCounts map[string]int    `json:"source_counts"`
		Errors       map[string]string `json:"errors"`
		Sources      []string          `json:"sources"`
	}
	_ = json.Unmarshal([]byte(keylessSubdomains(domain, maxResults)), &base)

	type keyed struct {
		name string
		key  string
		fn   func(*http.Client, string, string, int) ([]string, error)
	}
	providers := []keyed{
		{"chaos", keys.chaosKey(), chaosSubdomains},
		{"virustotal", strings.TrimSpace(keys.VirusTotal), virusTotalSubdomains},
		{"securitytrails", strings.TrimSpace(keys.SecurityTrails), securityTrailsSubdomains},
		{"shodan", strings.TrimSpace(keys.Shodan), shodanSubdomains},
	}

	type outcome struct {
		name  string
		hosts []string
		err   error
	}
	client := &http.Client{Timeout: 20 * time.Second}
	ch := make(chan outcome, len(providers))
	used := []string{}
	for _, p := range providers {
		if p.key == "" {
			continue
		}
		used = append(used, p.name)
		go func() {
			hosts, err := p.fn(client, domain, p.key, maxResults)
			ch <- outcome{p.name, hosts, err}
		}()
	}

	merged := append([]string(nil), base.Subdomains...)
	counts := map[string]int{}
	for k, v := range base.SourceCounts {
		counts[k] = v
	}
	errs := map[string]string{}
	for k, v := range base.Errors {
		errs[k] = v
	}
	for range used {
		o := <-ch
		if o.err != nil {
			errs[o.name] = o.err.Error()
			continue
		}
		counts[o.name] = len(o.hosts)
		merged = append(merged, o.hosts...)
	}

	sources := append(append([]string{}, base.Sources...), used...)
	return mustJSON(map[string]any{
		"ok":            true,
		"domain":        domain,
		"subdomains":    uniqueLimited(merged, maxResults),
		"source_counts": counts,
		"errors":        errs,
		"sources":       sources,
		"keyed_sources": used,
	})
}
