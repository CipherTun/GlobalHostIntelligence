package mobile

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

func discoverRaw(c *http.Client, q, source string, limit int) ([]string, error) {
	switch strings.ToLower(strings.TrimSpace(source)) {
	case "crt.sh", "crt":
		return crtsh(c, q, limit)
	case "certspotter":
		return certspotter(c, q, limit)
	case "crt.name":
		return crtName(c, q, limit)
	case "ctlogs.dev":
		return ctLogs(c, q, limit)
	case "anubis":
		return anubis(c, q, limit)
	case "subdomain.center":
		return subdomainCenter(c, q, limit)
	case "hackertarget":
		return hackerTarget(c, q, limit)
	case "wayback":
		return wayback(c, q, limit)
	case "threatminer":
		return threatMiner(c, q, limit)
	case "urlscan-country":
		return urlscanCountrySource(c, q, limit)
	case "urlscan":
		return urlscan(c, q, limit)
	case "rapiddns":
		return rapiddns(c, q, limit)
	case "commoncrawl":
		return commonCrawl(c, q, limit)
	case "otx":
		return otxPassiveDNS(c, q, limit)
	case "subdomain.app":
		return subdomainAPI(c, q, limit)
	case "sonar":
		return sonar(c, q, limit)
	case "riddler":
		return riddler(c, q, limit)
	case "jldc":
		return jldc(c, q, limit)
	case "sublist3r":
		return sublist3r(c, q, limit)
	case "projectdiscovery", "chaos":
		return nil, fmt.Errorf("ProjectDiscovery Chaos requires an API key; use DiscoverProjectDiscovery or DiscoverSubdomainsWithKeys")
	case "country":
		return countryDiscovery(c, q, limit)
	case "asn":
		return asnDiscovery(c, q, limit)
	default:
		return nil, fmt.Errorf("unknown discovery source: %s", source)
	}
}

// commonCrawl uses the public CDXJ index and the newest published crawl.
// It extracts observed hostnames rather than guessing names from an IP range.
func commonCrawl(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" || !strings.Contains(d, ".") {
		return nil, fmt.Errorf("Common Crawl requires a domain scope")
	}
	info, err := getJSON(c, "https://index.commoncrawl.org/collinfo.json", 4<<20)
	if err != nil {
		return nil, err
	}
	var crawls []struct {
		ID string `json:"id"`
	}
	if err = json.Unmarshal(info, &crawls); err != nil || len(crawls) == 0 {
		return nil, fmt.Errorf("Common Crawl index listing unavailable")
	}
	crawl := strings.TrimSpace(crawls[0].ID)
	if crawl == "" {
		return nil, fmt.Errorf("Common Crawl returned no crawl ID")
	}
	endpoint := "https://index.commoncrawl.org/" + url.PathEscape(crawl) +
		"-index?url=" + url.QueryEscape("*."+d+"/*") +
		"&matchType=domain&output=json&filter=status%3A200&collapse=urlkey&limit=" +
		fmt.Sprint(min(limit*4, 2000))
	data, err := getJSON(c, endpoint, 24<<20)
	if err != nil {
		return nil, err
	}
	var out []string
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		var row struct {
			URL string `json:"url"`
		}
		if json.Unmarshal([]byte(line), &row) != nil {
			continue
		}
		if u, e := url.Parse(row.URL); e == nil {
			host := normalizeDomain(u.Hostname())
			if matchesScope(host, q) && isHostname(host) {
				out = append(out, host)
			}
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

// OTX is optional/passive. Some anonymous OTX deployments rate-limit or require
// authentication; the source simply degrades when that happens.
func otxPassiveDNS(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://otx.alienvault.com/api/v1/indicators/domain/" +
		url.PathEscape(d) + "/passive_dns"
	req, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")
	resp, err := c.Do(req)
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
	var obj struct {
		PassiveDNS []struct {
			Hostname string `json:"hostname"`
		} `json:"passive_dns"`
	}
	if err = json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	out := make([]string, 0, limit)
	for _, row := range obj.PassiveDNS {
		host := normalizeDomain(row.Hostname)
		if matchesScope(host, q) && isHostname(host) {
			out = append(out, host)
		}
		if len(out) >= limit {
			break
		}
	}
	return uniqueLimited(out, limit), nil
}

func subdomainAPI(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.subdomain.app/v1/query?domain=" + url.QueryEscape(d)
	data, err := getJSON(c, endpoint, 24<<20)
	if err != nil {
		return nil, err
	}
	var obj struct {
		Subdomains []string `json:"subdomains"`
	}
	if err := json.Unmarshal(data, &obj); err != nil {
		return nil, err
	}
	out := make([]string, 0, min(limit, len(obj.Subdomains)))
	for _, name := range obj.Subdomains {
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

func sonar(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	data, err := getJSON(c, "https://sonar.omnisint.io/subdomains/"+url.PathEscape(d), 16<<20)
	if err != nil {
		return nil, err
	}
	var rows []string
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	out := make([]string, 0, min(limit, len(rows)))
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

func riddler(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://riddler.io/search/exportcsv?q=" + url.QueryEscape("pld:"+d)
	data, err := getJSON(c, endpoint, 16<<20)
	if err != nil {
		return nil, err
	}
	re := regexp.MustCompile(`(?i)\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+` + regexp.QuoteMeta(d) + `\b`)
	matches := re.FindAllString(string(data), limit*4)
	out := make([]string, 0, limit)
	for _, name := range matches {
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

func jldc(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	data, err := getJSON(c, "https://jldc.me/anubis/subdomains/"+url.PathEscape(d), 16<<20)
	if err != nil {
		return nil, err
	}
	var rows []string
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	out := make([]string, 0, min(limit, len(rows)))
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

func sublist3r(c *http.Client, q string, limit int) ([]string, error) {
	d := queryDomain(q)
	if d == "" {
		return nil, fmt.Errorf("no domain scope")
	}
	endpoint := "https://api.sublist3r.com/search.php?domain=" + url.QueryEscape(d)
	data, err := getJSON(c, endpoint, 16<<20)
	if err != nil {
		return nil, err
	}
	var rows []string
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	out := make([]string, 0, min(limit, len(rows)))
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

// DiscoverProjectDiscovery queries ProjectDiscovery's Chaos dataset with the
// user's own API key (nothing is embedded in the application).
//
// The previous implementation called api.projectdiscovery.io/v1/domain/associated
// with an X-API-Key header, which Chaos does not document, so it could never
// succeed. The documented API is
//
//	GET https://dns.projectdiscovery.io/dns/{domain}/subdomains
//	Authorization: <API key>
//
// The JSON envelope keeps the old keys (ok, source, query, results,
// unique_count) and adds a plain "domains" array, the shape every other raw
// discovery source returns.
func DiscoverProjectDiscovery(domain, apiKey string, maxResults int) string {
	domain = normalizeDomain(queryDomain(domain))
	apiKey = strings.TrimSpace(apiKey)
	maxResults = clampDiscoveryLimit(maxResults)
	if domain == "" || !isHostname(domain) {
		return mustJSON(map[string]any{"ok": false, "source": "projectdiscovery", "error": "a valid domain is required"})
	}
	if apiKey == "" {
		return mustJSON(map[string]any{"ok": false, "source": "projectdiscovery", "error": "ProjectDiscovery API key is not configured"})
	}

	hosts, err := chaosSubdomains(&http.Client{Timeout: 20 * time.Second}, domain, apiKey, maxResults)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "source": "projectdiscovery", "error": err.Error()})
	}
	if hosts == nil {
		hosts = []string{}
	}
	results := make([]map[string]any, 0, len(hosts))
	for _, host := range hosts {
		results = append(results, map[string]any{"domain": host, "sources": []string{"chaos"}})
	}
	return mustJSON(map[string]any{
		"ok":           true,
		"source":       "projectdiscovery",
		"query":        domain,
		"domains":      hosts,
		"results":      results,
		"unique_count": len(hosts),
	})
}
