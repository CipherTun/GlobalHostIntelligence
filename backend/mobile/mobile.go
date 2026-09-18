package mobile

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

type dnsResult struct {
	FQDN      string   `json:"fqdn"`
	Addresses []string `json:"addresses"`
	Error     string   `json:"error,omitempty"`
	ElapsedMs int64    `json:"elapsed_ms"`
}
type discoveryResult struct {
	Query     string            `json:"query"`
	Domains   []string          `json:"domains"`
	Sources   []string          `json:"sources"`
	Errors    map[string]string `json:"errors,omitempty"`
	ElapsedMs int64             `json:"elapsed_ms"`
}
type domainResult struct {
	Domain    string `json:"domain"`
	LatencyMs int64  `json:"latency_ms"`
	Status    int    `json:"status"`
}
type hostResult struct {
	Host        string   `json:"host"`
	Addresses   []string `json:"addresses"`
	HTTPStatus  int      `json:"http_status,omitempty"`
	HTTPSStatus int      `json:"https_status,omitempty"`
	HTTPServer  string   `json:"http_server,omitempty"`
	HTTPSServer string   `json:"https_server,omitempty"`
	ContentType string   `json:"content_type,omitempty"`
	CDN         string   `json:"cdn,omitempty"`
	TLSValid    bool     `json:"tls_valid"`
	TLSVersion  string   `json:"tls_version,omitempty"`
	Error       string   `json:"error,omitempty"`
	ElapsedMs   int64    `json:"elapsed_ms"`
}

func Ping() string { return "ghi-mobile-core alive" }

func ResolveDomain(fqdn string) string {
	start := time.Now()
	fqdn = strings.TrimSpace(fqdn)
	addrs, err := net.LookupHost(fqdn)
	r := dnsResult{FQDN: fqdn, Addresses: addrs, ElapsedMs: time.Since(start).Milliseconds()}
	if err != nil {
		r.Error = err.Error()
	}
	return mustJSON(r)
}

// Discover performs passive discovery and validates candidates before they reach the UI.
// A candidate is emitted only when HTTP/HTTPS returns 2xx or 3xx.
func Discover(query string, maxResults int) string {
	return DiscoverSource(query, "all", maxResults)
}

// DiscoverSource is the mobile streaming-friendly unit used by Android. It keeps
// source work independent so the UI can run several calls concurrently and show
// each validated host as soon as that source completes.
func DiscoverSource(query, source string, maxResults int) string {
	start := time.Now()
	query = strings.TrimSpace(strings.ToLower(query))
	if maxResults < 1 {
		maxResults = 100
	}
	if maxResults > 2000 {
		maxResults = 2000
	}
	client := newHTTPClient()
	var domains []string
	var err error
	source = strings.ToLower(strings.TrimSpace(source))
	switch source {
	case "all":
		// Fan out passive sources concurrently with a small bounded worker pool.
		// Validation happens once after aggregation, avoiding repeated probes of
		// the same hostname when several sources report it.
		type sourceResult struct {
			name    string
			domains []string
			err     error
		}
		names := discoverySourceNames(query)
		ch := make(chan sourceResult, len(names))
		sem := make(chan struct{}, 6)
		for _, name := range names {
			name := name
			go func() {
				sem <- struct{}{}
				defer func() { <-sem }()
				d, e := discoverRaw(client, query, name, maxResults)
				ch <- sourceResult{name: name, domains: d, err: e}
			}()
		}
		var combined []string
		errors := map[string]string{}
		for range names {
			r := <-ch
			if r.err != nil {
				errors[r.name] = r.err.Error()
				continue
			}
			combined = append(combined, r.domains...)
		}
		combined = uniqueLimited(combined, maxResults*4)
		validated := validateHostsConcurrent(client, combined, maxResults, 32)
		out := make([]string, 0, len(validated))
		for _, r := range validated {
			out = append(out, r.Domain)
		}
		return mustJSON(map[string]any{"query": query, "source": "all", "domains": out, "sources": names, "errors": errors, "elapsed_ms": time.Since(start).Milliseconds()})
	case "country":
		domains, err = countryDiscovery(client, query, maxResults)
	case "asn":
		domains, err = asnDiscovery(client, query, maxResults)
	default:
		domains, err = discoverRaw(client, query, source, maxResults)
	}
	result := map[string]any{"query": query, "source": source, "results": []domainResult{}, "elapsed_ms": time.Since(start).Milliseconds()}
	if err != nil {
		result["error"] = err.Error()
		return mustJSON(result)
	}
	out := validateHostsConcurrent(client, domains, maxResults, 32)
	result["results"] = out
	result["elapsed_ms"] = time.Since(start).Milliseconds()
	return mustJSON(result)
}

func discoverySourceNames(query string) []string {
	// Country searches use sources that can actually scope observations by
	// country/ASN. Domain searches additionally fan out across passive datasets.
	if isCountryCode(query) {
		return []string{"country", "urlscan"}
	}
	return []string{
		"urlscan", "crt.sh", "crt.name", "ctlogs.dev",
		"certspotter", "rapiddns", "anubis", "subdomain.center",
		"hackertarget", "wayback", "threatminer", "commoncrawl", "otx", "subdomain.app", "sonar", "riddler", "jldc", "sublist3r",
	}
}

func isCountryCode(s string) bool {
	s = strings.TrimSpace(strings.ToLower(s))
	return len(s) == 2 && s[0] >= 'a' && s[0] <= 'z' && s[1] >= 'a' && s[1] <= 'z'
}

func newHTTPClient() *http.Client {
	return &http.Client{Timeout: 12 * time.Second, CheckRedirect: func(req *http.Request, via []*http.Request) error { return http.ErrUseLastResponse }}
}

func validateHostsConcurrent(c *http.Client, domains []string, limit, workers int) []domainResult {
	if limit < 1 {
		return nil
	}
	if workers < 1 {
		workers = 1
	}
	if workers > 64 {
		workers = 64
	}
	unique := uniqueLimited(domains, min(limit*4, 8000))
	type item struct {
		index  int
		result domainResult
		ok     bool
	}
	jobs := make(chan int)
	results := make(chan item, len(unique))
	var wg sync.WaitGroup
	for w := 0; w < min(workers, len(unique)); w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := range jobs {
				r, ok := validateHost(c, unique[i])
				results <- item{index: i, result: r, ok: ok}
			}
		}()
	}
	go func() {
		for i := range unique {
			jobs <- i
		}
		close(jobs)
		wg.Wait()
		close(results)
	}()
	out := make([]domainResult, 0, limit)
	// Preserve deterministic source order while still probing concurrently.
	buffer := make(map[int]item, len(unique))
	next := 0
	for r := range results {
		buffer[r.index] = r
		for {
			x, ok := buffer[next]
			if !ok {
				break
			}
			delete(buffer, next)
			next++
			if x.ok {
				out = append(out, x.result)
				if len(out) >= limit {
					return out
				}
			}
		}
	}
	return out
}

func validateHost(c *http.Client, host string) (domainResult, bool) {
	for _, scheme := range []string{"https", "http"} {
		start := time.Now()
		req, err := http.NewRequest(http.MethodGet, scheme+"://"+host, nil)
		if err != nil {
			continue
		}
		req.Header.Set("User-Agent", "GlobalHostIntelligence/2.3")
		req.Header.Set("Range", "bytes=0-0")
		resp, err := c.Do(req)
		if err != nil {
			continue
		}
		status := resp.StatusCode
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 1024))
		resp.Body.Close()
		if status >= 200 && status < 400 {
			return domainResult{Domain: host, LatencyMs: time.Since(start).Milliseconds(), Status: status}, true
		}
	}
	return domainResult{}, false
}

func countryDiscovery(c *http.Client, country string, limit int) ([]string, error) {
	country = strings.TrimSpace(strings.ToLower(country))
	if len(country) != 2 {
		return nil, fmt.Errorf("country must be ISO-3166 alpha-2")
	}
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
	if err = json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	var asns []string
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
	// Keep the current routed ASN set broad enough for global countries while
	// bounding fan-out so mobile data is not overwhelmed.
	if len(asns) > 48 {
		asns = asns[:48]
	}
	var seeds []string
	var mu sync.Mutex
	var wg sync.WaitGroup
	for _, asn := range asns {
		asn := asn
		wg.Add(1)
		go func() {
			defer wg.Done()
			// RIPEstat is the routing authority used to confirm that this ASN
			// currently announces prefixes. The prefix data is intentionally not
			// converted into guessed hostnames; only observed hostnames are used.
			prefixURL := "https://stat.ripe.net/data/announced-prefixes/data.json?resource=" + url.QueryEscape(asn) + "&sourceapp=globalhostintelligence"
			if _, e := getJSON(c, prefixURL, 24<<20); e != nil {
				return
			}
			found, e := urlscanASN(c, asn, min(limit, 200))
			if e != nil {
				return
			}
			mu.Lock()
			seeds = append(seeds, found...)
			mu.Unlock()
		}()
	}
	wg.Wait()
	seeds = uniqueLimited(seeds, min(limit*2, 1200))

	// Expand a bounded set of observed apexes through independent passive
	// datasets. This avoids making certificates the sole discovery source and
	// keeps non-.za/global TLDs eligible.
	apexes := make([]string, 0, 32)
	seenApex := map[string]bool{}
	for _, seed := range seeds {
		apex := apexDomain(seed)
		if apex == "" || seenApex[apex] {
			continue
		}
		seenApex[apex] = true
		apexes = append(apexes, apex)
		if len(apexes) >= 32 {
			break
		}
	}
	var expanded []string
	var emu sync.Mutex
	var ewg sync.WaitGroup
	for _, apex := range apexes {
		apex := apex
		ewg.Add(1)
		go func() {
			defer ewg.Done()
			sources := []func(*http.Client, string, int) ([]string, error){crtsh, crtName, ctLogs, certspotter, rapiddns, anubis, subdomainCenter, hackerTarget, wayback, threatMiner}
			for _, sourceFn := range sources {
				found, e := sourceFn(c, apex, min(limit, 150))
				if e != nil {
					continue
				}
				emu.Lock()
				expanded = append(expanded, found...)
				emu.Unlock()
				if len(expanded) >= limit*3 {
					return
				}
			}
		}()
	}
	ewg.Wait()
	all := append(seeds, expanded...)
	// URLScan country observations are useful even when an ASN has no indexed
	// host results of its own.
	if len(all) < limit {
		if d, e := urlscanCountry(c, country, limit-len(all)); e == nil {
			all = append(all, d...)
		}
	}
	return uniqueLimited(all, limit), nil
}

func apexDomain(host string) string {
	host = normalizeDomain(host)
	parts := strings.Split(host, ".")
	if len(parts) < 2 {
		return host
	}
	// Public suffix handling is intentionally conservative: for ccTLDs with a
	// common second-level suffix, retain three labels; otherwise retain two.
	if len(parts) >= 3 && len(parts[len(parts)-1]) == 2 {
		second := parts[len(parts)-2]
		if second == "co" || second == "com" || second == "net" || second == "org" || second == "gov" || second == "ac" {
			return strings.Join(parts[len(parts)-3:], ".")
		}
	}
	return strings.Join(parts[len(parts)-2:], ".")
}

func asnDiscovery(c *http.Client, asn string, limit int) ([]string, error) {
	asn = strings.TrimSpace(strings.ToUpper(asn))
	if !strings.HasPrefix(asn, "AS") {
		asn = "AS" + asn
	}
	return urlscanASN(c, asn, limit)
}

func urlscanASN(c *http.Client, asn string, limit int) ([]string, error) {
	return urlscanSearch(c, "asn:"+asn, limit)
}
func urlscanCountry(c *http.Client, country string, limit int) ([]string, error) {
	return urlscanSearch(c, "country:"+strings.ToUpper(country), limit)
}
func urlscanSearch(c *http.Client, term string, limit int) ([]string, error) {
	if limit < 1 {
		return nil, nil
	}
	if limit > 2000 {
		limit = 2000
	}
	out := make([]string, 0, limit)
	seen := map[string]bool{}
	searchAfter := ""
	for page := 0; page < 20 && len(out) < limit; page++ {
		u := "https://urlscan.io/api/v1/search/?q=" + url.QueryEscape(term) + "&size=100"
		if searchAfter != "" {
			u += "&search_after=" + url.QueryEscape(searchAfter)
		}
		data, err := getJSON(c, u, 12<<20)
		if err != nil {
			if len(out) > 0 {
				return uniqueLimited(out, limit), nil
			}
			return nil, err
		}
		var obj struct {
			Results []struct {
				Page struct {
					Domain string `json:"domain"`
				} `json:"page"`
				Sort []any `json:"sort"`
			} `json:"results"`
			HasMore bool `json:"has_more"`
		}
		if err = json.Unmarshal(data, &obj); err != nil {
			return uniqueLimited(out, limit), err
		}
		before := len(out)
		for _, r := range obj.Results {
			if d := normalizeDomain(r.Page.Domain); isHostname(d) && !seen[d] {
				seen[d] = true
				out = append(out, d)
				if len(out) >= limit {
					break
				}
			}
		}
		if len(out) >= limit || !obj.HasMore || len(obj.Results) == 0 || len(out) == before {
			break
		}
		last := obj.Results[len(obj.Results)-1].Sort
		if len(last) == 0 {
			break
		}
		parts := make([]string, 0, len(last))
		for _, v := range last {
			parts = append(parts, fmt.Sprint(v))
		}
		searchAfter = strings.Join(parts, ",")
	}
	return uniqueLimited(out, limit), nil
}

func getJSON(c *http.Client, endpoint string, max int) ([]byte, error) {
	resp, err := c.Get(endpoint)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, int64(max)))
}
func uniqueLimited(in []string, limit int) []string {
	seen := map[string]bool{}
	out := make([]string, 0, limit)
	for _, d := range in {
		d = normalizeDomain(d)
		if d == "" || !isHostname(d) || seen[d] {
			continue
		}
		seen[d] = true
		out = append(out, d)
		if len(out) >= limit {
			break
		}
	}
	return out
}
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func crtName(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://crt.name/v1/search?apex=" + url.QueryEscape(d)
	resp, err := c.Get(endpoint)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 12<<20))
	if err != nil {
		return nil, err
	}
	lines := strings.Split(string(data), "\n")
	out := make([]string, 0, limit)
	for _, line := range lines {
		if d := normalizeDomain(line); matchesScope(d, q) && isHostname(d) {
			out = append(out, d)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func ctLogs(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.ctlogs.dev/v1/subdomains/" + url.PathEscape(d)
	data, err := getJSON(c, endpoint, 16<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Rows []struct {
			Domains []string `json:"domains"`
			Match   string   `json:"match"`
		} `json:"rows"`
	}
	if err = json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	out := make([]string, 0, limit)
	for _, row := range obj.Rows {
		if d := normalizeDomain(row.Match); matchesScope(d, q) && isHostname(d) {
			out = append(out, d)
		}
		for _, name := range row.Domains {
			d = normalizeDomain(name)
			if matchesScope(d, q) && isHostname(d) {
				out = append(out, d)
			}
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func anubis(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://anubisdb.com/subdomains/" + url.PathEscape(d)
	data, err := getJSON(c, endpoint, 16<<20)
	if err != nil {
		return nil, err
	}
	var rows []string
	if err = json.Unmarshal(data, &rows); err != nil {
		var obj struct {
			Subdomains []string `json:"subdomains"`
		}
		if e := json.Unmarshal(data, &obj); e != nil {
			return nil, err
		}
		rows = obj.Subdomains
	}
	out := make([]string, 0, limit)
	for _, name := range rows {
		name = normalizeDomain(name)
		if matchesScope(name, q) && isHostname(name) {
			out = append(out, name)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func subdomainCenter(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.subdomain.center/?domain=" + url.QueryEscape(d)
	data, err := getJSON(c, endpoint, 16<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Subdomains []string `json:"subdomains"`
		Results    []string `json:"results"`
	}
	if err = json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	rows := obj.Subdomains
	if len(rows) == 0 {
		rows = obj.Results
	}
	out := make([]string, 0, limit)
	for _, name := range rows {
		name = normalizeDomain(name)
		if matchesScope(name, q) && isHostname(name) {
			out = append(out, name)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func hackerTarget(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.hackertarget.com/hostsearch/?q=" + url.QueryEscape(d)
	data, err := getJSON(c, endpoint, 8<<20)
	if err != nil {
		return nil, err
	}
	out := make([]string, 0, limit)
	for _, line := range strings.Split(string(data), "\n") {
		parts := strings.SplitN(strings.TrimSpace(line), ",", 2)
		if len(parts) != 2 {
			continue
		}
		name := normalizeDomain(parts[0])
		if matchesScope(name, q) && isHostname(name) {
			out = append(out, name)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func wayback(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://web.archive.org/cdx/search/cdx?url=" + url.QueryEscape("*."+d+"/*") + "&matchType=domain&output=json&fl=original&collapse=urlkey&limit=" + fmt.Sprint(min(limit*3, 1500))
	data, err := getJSON(c, endpoint, 20<<20)
	if err != nil {
		return nil, err
	}
	var rows [][]string
	if err = json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	out := make([]string, 0, limit)
	for _, row := range rows {
		if len(row) == 0 || strings.EqualFold(row[0], "original") {
			continue
		}
		u, e := url.Parse(row[0])
		if e != nil {
			continue
		}
		name := normalizeDomain(u.Hostname())
		if matchesScope(name, q) && isHostname(name) {
			out = append(out, name)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func threatMiner(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.threatminer.org/v2/domain.php?q=" + url.QueryEscape(d) + "&rt=5"
	data, err := getJSON(c, endpoint, 12<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Results []string `json:"results"`
	}
	if err = json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	out := make([]string, 0, limit)
	for _, name := range obj.Results {
		name = normalizeDomain(name)
		if matchesScope(name, q) && isHostname(name) {
			out = append(out, name)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func crtsh(c *http.Client, q string, limit int) ([]string, error) {
	endpoint := "https://crt.sh/?q=" + url.QueryEscape(crtQuery(q)) + "&output=json"
	resp, err := c.Get(endpoint)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 16<<20))
	if err != nil {
		return nil, err
	}
	var rows []struct {
		NameValue string `json:"name_value"`
	}
	if err = json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	var out []string
	for _, row := range rows {
		for _, n := range strings.Split(row.NameValue, "\n") {
			out = append(out, normalizeDomain(n))
			if len(out) >= limit {
				return out, nil
			}
		}
	}
	return out, nil
}

func certspotter(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.certspotter.com/v1/issuances?domain=" + url.QueryEscape(d) + "&include_subdomains=true&expand=dns_names"
	resp, err := c.Get(endpoint)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 16<<20))
	if err != nil {
		return nil, err
	}
	var rows []struct {
		DNSNames []string `json:"dns_names"`
	}
	if err = json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	var out []string
	for _, r := range rows {
		for _, n := range r.DNSNames {
			if matchesScope(n, q) {
				out = append(out, n)
			}
			if len(out) >= limit {
				return out, nil
			}
		}
	}
	return out, nil
}

func urlscan(c *http.Client, q string, limit int) ([]string, error) {
	term := urlscanQuery(q)
	if term == "" {
		return nil, fmt.Errorf("empty query")
	}
	return urlscanSearch(c, term, limit)
}

func rapiddns(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://rapiddns.io/subdomain/" + url.PathEscape(d) + "?full=1"
	req, _ := http.NewRequest(http.MethodGet, endpoint, nil)
	req.Header.Set("User-Agent", "GlobalHostIntelligence/2.3")
	resp, err := c.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 12<<20))
	if err != nil {
		return nil, err
	}
	re := regexp.MustCompile(`(?i)\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}\b`)
	matches := re.FindAllString(string(data), limit*3)
	out := make([]string, 0, limit)
	seen := map[string]bool{}
	for _, m := range matches {
		m = normalizeDomain(m)
		if matchesScope(m, q) && !seen[m] {
			seen[m] = true
			out = append(out, m)
		}
		if len(out) >= limit {
			break
		}
	}
	return out, nil
}

func crtQuery(q string) string {
	q = strings.TrimSpace(q)
	if strings.HasPrefix(q, "%.") {
		return "%" + q[1:]
	}
	return q
}
func queryDomain(q string) string {
	q = strings.TrimSpace(strings.Trim(q, "%*"))
	q = strings.TrimPrefix(q, ".")
	if strings.Contains(q, ".") {
		return q
	}
	return q
}
func urlscanQuery(q string) string {
	q = strings.TrimSpace(q)
	if strings.HasPrefix(q, "%.") {
		return "domain:*" + q[1:]
	}
	return q
}
func matchesScope(d, q string) bool {
	d = normalizeDomain(d)
	q = strings.TrimSpace(strings.ToLower(q))
	if d == "" {
		return false
	}
	if strings.HasPrefix(q, "%.") {
		return strings.HasSuffix(d, q[1:])
	}
	q = strings.Trim(q, "%*")
	if q == "" || q == "." {
		return true
	}
	return d == q || strings.HasSuffix(d, "."+q)
}
func normalizeDomain(d string) string {
	d = strings.ToLower(strings.TrimSpace(strings.TrimPrefix(d, "*.")))
	d = strings.TrimPrefix(d, ".")
	d = strings.TrimSuffix(d, ".")
	if i := strings.IndexByte(d, '/'); i >= 0 {
		d = d[:i]
	}
	return d
}
func isHostname(s string) bool {
	if len(s) < 3 || len(s) > 253 || strings.ContainsAny(s, " /\\@") || !strings.Contains(s, ".") {
		return false
	}
	labels := strings.Split(s, ".")
	for _, p := range labels {
		if p == "" || len(p) > 63 || strings.HasPrefix(p, "-") || strings.HasSuffix(p, "-") {
			return false
		}
	}
	// Reject obvious synthetic/internal placeholders while keeping legitimate
	// global TLDs (including .online) fully eligible.
	lower := strings.ToLower(s)
	for _, bad := range []string{"localhost", "notexists", "no-such-host", "example.invalid", "invalid.local"} {
		if strings.Contains(lower, bad) {
			return false
		}
	}
	// Reject highly synthetic hostnames without blacklisting legitimate TLDs.
	suspicious := 0
	for _, token := range []string{"internal-portal", "cstage", "backup", "accessfitness", "test-internal", "placeholder"} {
		if strings.Contains(lower, token) {
			suspicious++
		}
	}
	if suspicious >= 2 || (len(s) > 120 && suspicious >= 1) {
		return false
	}
	for _, label := range labels {
		digits := 0
		letters := 0
		for _, r := range label {
			if r >= '0' && r <= '9' {
				digits++
			}
			if r >= 'a' && r <= 'z' {
				letters++
			}
		}
		if digits > 20 && letters < 4 {
			return false
		}
	}
	return true
}

func AnalyzeHost(host string) string {
	return AnalyzeHostWithOptions(host, 10, "GlobalHostIntelligence/2.3")
}

func AnalyzeHostWithTimeout(host string, timeoutSeconds int) string {
	return AnalyzeHostWithOptions(host, timeoutSeconds, "GlobalHostIntelligence/2.3")
}

func AnalyzeHostWithOptions(host string, timeoutSeconds int, userAgent string) string {
	if timeoutSeconds < 2 {
		timeoutSeconds = 2
	}
	if timeoutSeconds > 60 {
		timeoutSeconds = 60
	}
	start := time.Now()
	host = normalizeDomain(strings.TrimPrefix(strings.TrimPrefix(strings.TrimSpace(host), "https://"), "http://"))
	r := hostResult{Host: host}
	r.Addresses, _ = net.LookupHost(host)
	client := &http.Client{Timeout: time.Duration(timeoutSeconds) * time.Second, CheckRedirect: func(req *http.Request, via []*http.Request) error { return http.ErrUseLastResponse }}
	for _, scheme := range []string{"https", "http"} {
		req, _ := http.NewRequest(http.MethodGet, scheme+"://"+host+"/", nil)
		req.Header.Set("User-Agent", userAgent)
		req.Header.Set("Range", "bytes=0-0")
		resp, err := client.Do(req)
		if err != nil {
			if r.Error == "" {
				r.Error = err.Error()
			}
			continue
		}
		server := resp.Header.Get("Server")
		if r.ContentType == "" {
			r.ContentType = resp.Header.Get("Content-Type")
		}
		if r.CDN == "" {
			r.CDN = detectCDN(resp.Header)
		}
		if scheme == "http" {
			r.HTTPStatus = resp.StatusCode
			r.HTTPServer = server
		} else {
			r.HTTPSStatus = resp.StatusCode
			r.HTTPSServer = server
		}
		resp.Body.Close()
	}
	if conn, err := tls.DialWithDialer(&net.Dialer{Timeout: 8 * time.Second}, "tcp", net.JoinHostPort(host, "443"), &tls.Config{ServerName: host, MinVersion: tls.VersionTLS12}); err == nil {
		r.TLSValid = conn.ConnectionState().HandshakeComplete
		r.TLSVersion = tlsVersionName(conn.ConnectionState().Version)
		conn.Close()
	}
	r.ElapsedMs = time.Since(start).Milliseconds()
	return mustJSON(r)
}

// CheckHost performs a real HTTP/HTTPS response check. It is intentionally
// independent of Wi-Fi: Android's normal active network (including cellular
// data) is used by Go's net/http stack.
func CheckHost(host, method string, allowInsecure, followRedirects bool) string {
	// Legacy API retained for existing Android callers. The richer response
	// engine powers the implementation without changing this public signature.
	return CheckResponse(
		"HTTP", host, "", method, "/", "", "", followRedirects, allowInsecure,
		12, false, "UDP", "", "",
	)
}

func ResolveIP(value string) string {
	value = strings.TrimSpace(value)
	result := map[string]any{"input": value, "addresses": []string{}, "hostnames": []string{}}
	if net.ParseIP(value) == nil {
		return ResolveDomain(value)
	}
	if names, err := net.LookupAddr(value); err == nil {
		result["hostnames"] = names
	}
	result["addresses"] = []string{value}
	return mustJSON(result)
}

func detectCDN(h http.Header) string {
	checks := []struct {
		name string
		keys []string
	}{
		{"Cloudflare", []string{"CF-RAY", "CF-Cache-Status", "CF-Connecting-IP", "Server-Timing"}},
		{"Akamai", []string{"Akamai-Cache-Status", "X-Akamai-Transformed", "X-Akamai-Request-ID"}},
		{"Fastly", []string{"X-Served-By", "X-Cache-Hits", "Fastly-Debug-Digest", "X-Timer"}},
		{"Amazon CloudFront", []string{"X-Amz-Cf-Id", "X-Amz-Cf-Pop", "Via"}},
		{"Imperva", []string{"X-Iinfo", "X-CDN"}},
		{"Bunny", []string{"Bunny-CDN-Server", "Bunny-Cache"}},
		{"Azure Front Door", []string{"X-Azure-Ref", "X-FD-HealthProbe"}},
		{"Sucuri", []string{"X-Sucuri-ID", "X-Sucuri-Cache"}},
		{"KeyCDN", []string{"X-Edge-Location", "X-Edge-Server"}},
	}
	for _, c := range checks {
		for _, k := range c.keys {
			if h.Get(k) != "" {
				return c.name
			}
		}
	}
	server := strings.ToLower(h.Get("Server"))
	all := strings.ToLower(paidHeaderValue(h))
	for _, item := range []struct{ token, name string }{
		{"cloudflare", "Cloudflare"}, {"akamaighost", "Akamai"},
		{"fastly", "Fastly"}, {"cloudfront", "Amazon CloudFront"},
		{"imperva", "Imperva"}, {"bunny", "Bunny"}, {"sucuri", "Sucuri"},
	} {
		if strings.Contains(server, item.token) || strings.Contains(all, item.token) {
			return item.name
		}
	}
	if strings.Contains(all, "cdn-cache") || strings.Contains(all, "server-timing") && strings.Contains(all, "cdn") {
		return "CDN (generic)"
	}
	return ""
}

func GenerateRequest(method, host, path, body string) string {
	method = strings.ToUpper(strings.TrimSpace(method))
	host = strings.TrimSpace(host)
	path = strings.TrimSpace(path)
	if path == "" {
		path = "/"
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	lines := []string{method + " " + path + " HTTP/1.1", "Host: " + host, "User-Agent: GlobalHostIntelligence/1.0", "Accept: */*"}
	if method == "UPGRADE" {
		lines = append(lines, "Connection: Upgrade", "Upgrade: websocket")
	}
	if method == "CONNECT" {
		lines[0] = "CONNECT " + host + " HTTP/1.1"
	}
	if body != "" && method != "GET" && method != "HEAD" {
		lines = append(lines, "Content-Type: application/json", fmt.Sprintf("Content-Length: %d", len([]byte(body))))
	}
	return strings.Join(append(lines, "", body), "\r\n")
}
func mustJSON(v any) string { b, _ := json.Marshal(v); return string(b) }
func tlsVersionName(v uint16) string {
	switch v {
	case tls.VersionTLS13:
		return "TLS 1.3"
	case tls.VersionTLS12:
		return "TLS 1.2"
	default:
		return fmt.Sprintf("TLS 0x%04x", v)
	}
}
