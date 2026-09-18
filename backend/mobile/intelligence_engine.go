package mobile

import (
	"crypto/tls"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptrace"
	"net/url"
	"regexp"
	"strings"
	"time"
)

// Investigation runs the real GHI engines concurrently. It is intentionally
// bounded so one mobile investigation cannot create an unbounded network fan-out.
func Investigate(target string, timeoutSeconds int) string {
	target = normalizeDomain(target)
	if target == "" {
		return mustJSON(map[string]any{"ok": false, "error": "host or domain is required"})
	}
	timeoutSeconds = clampInt(timeoutSeconds, 3, 30)

	type result struct {
		name string
		raw  string
	}
	jobs := []struct {
		name string
		fn   func() string
	}{
		{"host", func() string { return AnalyzeHostWithOptions(target, timeoutSeconds, "GlobalHostIntelligence/4.0") }},
		{"dns", func() string { return InspectDNS(target) }},
		{"tls", func() string { return AnalyzeTLS(target) }},
		{"headers", func() string { return SecurityHeaders(target, timeoutSeconds) }},
		{"redirects", func() string { return RedirectMap(target, timeoutSeconds) }},
		{"timing", func() string { return NetworkTiming(target, timeoutSeconds) }},
		{"technology", func() string { return TechnologyFingerprint(target, timeoutSeconds) }},
		{"certificates", func() string { return SearchCertificates(target) }},
	}

	out := make(chan result, len(jobs))
	for _, job := range jobs {
		job := job
		go func() { out <- result{job.name, job.fn()} }()
	}

	report := map[string]any{
		"ok":     true,
		"target": target,
		"engine": "GHI investigation",
	}
	for range jobs {
		item := <-out
		var decoded any
		if json.Unmarshal([]byte(item.raw), &decoded) == nil {
			report[item.name] = decoded
		} else {
			report[item.name] = map[string]any{"ok": false, "error": item.raw}
		}
	}
	return mustJSON(report)
}

func SecurityHeaders(target string, timeoutSeconds int) string {
	host := normalizeDomain(target)
	if host == "" {
		return mustJSON(map[string]any{"ok": false, "error": "host is required"})
	}
	timeoutSeconds = clampInt(timeoutSeconds, 3, 30)

	client := &http.Client{
		Timeout: time.Duration(timeoutSeconds) * time.Second,
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	req, err := http.NewRequest(http.MethodGet, "https://"+host+"/", nil)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "host": host, "error": err.Error()})
	}
	req.Header.Set("User-Agent", "GlobalHostIntelligence/4.0")
	req.Header.Set("Range", "bytes=0-0")

	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "host": host, "error": err.Error(), "elapsed_ms": time.Since(start).Milliseconds()})
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 1024))

	names := []string{
		"Strict-Transport-Security",
		"Content-Security-Policy",
		"X-Content-Type-Options",
		"Referrer-Policy",
		"Permissions-Policy",
		"X-Frame-Options",
		"Cross-Origin-Opener-Policy",
		"Cross-Origin-Resource-Policy",
		"Cross-Origin-Embedder-Policy",
	}
	headers := make(map[string]any, len(names))
	present := 0
	for _, name := range names {
		value := strings.TrimSpace(resp.Header.Get(name))
		headers[name] = map[string]any{"present": value != "", "value": value}
		if value != "" {
			present++
		}
	}

	return mustJSON(map[string]any{
		"ok":         true,
		"host":       host,
		"status":     resp.StatusCode,
		"server":     resp.Header.Get("Server"),
		"cdn":        detectCDN(resp.Header),
		"headers":    headers,
		"present":    present,
		"total":      len(names),
		"elapsed_ms": time.Since(start).Milliseconds(),
	})
}

func RedirectMap(target string, timeoutSeconds int) string {
	raw := strings.TrimSpace(target)
	if raw == "" {
		return mustJSON(map[string]any{"ok": false, "error": "target is required"})
	}
	if !strings.Contains(raw, "://") {
		raw = "https://" + raw
	}
	startURL, err := url.Parse(raw)
	if err != nil || startURL.Hostname() == "" {
		return mustJSON(map[string]any{"ok": false, "error": "invalid URL"})
	}
	timeoutSeconds = clampInt(timeoutSeconds, 3, 30)

	type hop struct {
		URL      string `json:"url"`
		Status   int    `json:"status"`
		Location string `json:"location,omitempty"`
	}
	hops := make([]hop, 0, 10)
	current := startURL

	for i := 0; i < 10; i++ {
		client := &http.Client{
			Timeout: time.Duration(timeoutSeconds) * time.Second,
			CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
				return http.ErrUseLastResponse
			},
		}
		req, err := http.NewRequest(http.MethodGet, current.String(), nil)
		if err != nil {
			break
		}
		req.Header.Set("User-Agent", "GlobalHostIntelligence/4.0")
		req.Header.Set("Range", "bytes=0-0")
		resp, err := client.Do(req)
		if err != nil {
			break
		}
		location := strings.TrimSpace(resp.Header.Get("Location"))
		hops = append(hops, hop{URL: current.String(), Status: resp.StatusCode, Location: location})
		resp.Body.Close()

		if location == "" || resp.StatusCode < 300 || resp.StatusCode >= 400 {
			break
		}
		next, err := current.Parse(location)
		if err != nil || next.Hostname() == "" {
			break
		}
		current = next
	}

	return mustJSON(map[string]any{
		"ok":        true,
		"input":     raw,
		"final_url": current.String(),
		"hops":      hops,
		"hop_count": len(hops),
	})
}

func NetworkTiming(target string, timeoutSeconds int) string {
	host := normalizeDomain(target)
	if host == "" {
		return mustJSON(map[string]any{"ok": false, "error": "host is required"})
	}
	timeoutSeconds = clampInt(timeoutSeconds, 3, 30)

	result := map[string]any{"ok": true, "host": host}
	total := time.Now()

	dnsStart := time.Now()
	addresses, dnsErr := net.LookupHost(host)
	result["dns_ms"] = time.Since(dnsStart).Milliseconds()
	result["addresses"] = addresses
	if dnsErr != nil {
		result["dns_error"] = dnsErr.Error()
	}

	var dnsConnect, tlsHandshake, firstByte time.Time
	var status int
	var protocol string
	var tlsVersion string

	req, err := http.NewRequest(http.MethodGet, "https://"+host+"/", nil)
	if err != nil {
		result["error"] = err.Error()
		result["total_ms"] = time.Since(total).Milliseconds()
		return mustJSON(result)
	}
	req.Header.Set("User-Agent", "GlobalHostIntelligence/4.0")
	req.Header.Set("Range", "bytes=0-0")

	trace := &httptrace.ClientTrace{
		DNSStart: func(httptrace.DNSStartInfo) { dnsStart = time.Now() },
		DNSDone: func(httptrace.DNSDoneInfo) {
			if !dnsStart.IsZero() {
				result["http_dns_ms"] = time.Since(dnsStart).Milliseconds()
			}
		},
		ConnectStart: func(_, _ string) { dnsConnect = time.Now() },
		ConnectDone: func(_, _ string, _ error) {
			if !dnsConnect.IsZero() {
				result["tcp_ms"] = time.Since(dnsConnect).Milliseconds()
			}
		},
		TLSHandshakeStart: func() { tlsHandshake = time.Now() },
		TLSHandshakeDone: func(state tls.ConnectionState, _ error) {
			if !tlsHandshake.IsZero() {
				result["tls_ms"] = time.Since(tlsHandshake).Milliseconds()
			}
			tlsVersion = tlsVersionName(state.Version)
		},
		GotFirstResponseByte: func() {
			if !firstByte.IsZero() {
				return
			}
			firstByte = time.Now()
		},
	}
	req = req.WithContext(httptrace.WithClientTrace(req.Context(), trace))

	client := &http.Client{
		Timeout: time.Duration(timeoutSeconds) * time.Second,
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		result["error"] = err.Error()
		result["total_ms"] = time.Since(total).Milliseconds()
		return mustJSON(result)
	}
	defer resp.Body.Close()
	status = resp.StatusCode
	protocol = resp.Proto
	if firstByte.IsZero() {
		firstByte = time.Now()
	}
	result["status"] = status
	result["http_protocol"] = protocol
	result["tls_version"] = tlsVersion
	result["ttfb_ms"] = firstByte.Sub(start).Milliseconds()
	result["request_ms"] = time.Since(start).Milliseconds()
	result["total_ms"] = time.Since(total).Milliseconds()
	return mustJSON(result)
}

func TechnologyFingerprint(target string, timeoutSeconds int) string {
	host := normalizeDomain(target)
	if host == "" {
		return mustJSON(map[string]any{"ok": false, "error": "host is required"})
	}
	timeoutSeconds = clampInt(timeoutSeconds, 3, 30)

	client := &http.Client{
		Timeout: time.Duration(timeoutSeconds) * time.Second,
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	req, err := http.NewRequest(http.MethodGet, "https://"+host+"/", nil)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "error": err.Error()})
	}
	req.Header.Set("User-Agent", "GlobalHostIntelligence/4.0")
	req.Header.Set("Range", "bytes=0-65535")
	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "host": host, "error": err.Error()})
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	lower := strings.ToLower(string(body))
	server := strings.ToLower(resp.Header.Get("Server"))
	powered := strings.ToLower(resp.Header.Get("X-Powered-By"))
	all := server + " " + powered + " " + lower

	type tech struct {
		Name     string `json:"name"`
		Evidence string `json:"evidence"`
	}
	found := make([]tech, 0, 12)
	seen := map[string]bool{}
	add := func(name, evidence string) {
		if !seen[name] {
			seen[name] = true
			found = append(found, tech{Name: name, Evidence: evidence})
		}
	}

	signatures := []struct {
		token, name, evidence string
	}{
		{"cf-ray", "Cloudflare", "CF-RAY header"},
		{"cf-cache-status", "Cloudflare", "CF-Cache-Status header"},
		{"x-amz-cf-", "Amazon CloudFront", "Amazon CDN header"},
		{"x-cache: ", "HTTP cache", "X-Cache header"},
		{"nginx", "nginx", "Server header"},
		{"apache", "Apache HTTP Server", "Server header"},
		{"caddy", "Caddy", "Server header"},
		{"envoy", "Envoy", "Server header"},
		{"x-powered-by: express", "Express", "X-Powered-By header"},
		{"next.js", "Next.js", "HTML marker"},
		{"/_next/static/", "Next.js", "HTML asset path"},
		{"wp-content", "WordPress", "HTML asset path"},
		{"drupal-settings-json", "Drupal", "HTML marker"},
		{"laravel_session", "Laravel", "Cookie marker"},
		{"react", "React", "HTML marker"},
		{"vue", "Vue.js", "HTML marker"},
		{"ng-version", "Angular", "HTML marker"},
	}
	for _, sig := range signatures {
		if strings.Contains(all, sig.token) {
			add(sig.name, sig.evidence)
		}
	}
	if cdn := detectCDN(resp.Header); cdn != "" {
		add(cdn, "CDN response headers")
	}

	return mustJSON(map[string]any{
		"ok":           true,
		"host":         host,
		"status":       resp.StatusCode,
		"server":       resp.Header.Get("Server"),
		"powered_by":   resp.Header.Get("X-Powered-By"),
		"content_type": resp.Header.Get("Content-Type"),
		"cdn":          detectCDN(resp.Header),
		"technologies": found,
		"elapsed_ms":   time.Since(start).Milliseconds(),
	})
}

// InternetSearch performs a real public web search without an application API key.
// It returns source URLs and snippets; GHI does not fabricate answers from the results.
func InternetSearch(query string, maxResults int) string {
	query = strings.TrimSpace(query)
	if query == "" {
		return mustJSON(map[string]any{"ok": false, "error": "query is required"})
	}
	maxResults = clampInt(maxResults, 1, 20)

	req, err := http.NewRequest(http.MethodGet, "https://html.duckduckgo.com/html/?q="+url.QueryEscape(query), nil)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "error": err.Error()})
	}
	req.Header.Set("User-Agent", "GlobalHostIntelligence/4.0")
	resp, err := (&http.Client{Timeout: 15 * time.Second}).Do(req)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "query": query, "error": err.Error()})
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "query": query, "error": err.Error()})
	}

	linkRe := regexp.MustCompile(`(?s)<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>`)
	snippetRe := regexp.MustCompile(`(?s)<a[^>]+class="result__snippet"[^>]*>(.*?)</a>`)
	links := linkRe.FindAllStringSubmatch(string(data), maxResults)
	snippets := snippetRe.FindAllStringSubmatch(string(data), maxResults)
	results := make([]map[string]string, 0, len(links))
	for i, match := range links {
		if len(match) < 3 {
			continue
		}
		snippet := ""
		if i < len(snippets) {
			snippet = stripHTML(snippets[i][1])
		}
		results = append(results, map[string]string{
			"title":   stripHTML(match[2]),
			"url":     htmlUnescape(match[1]),
			"snippet": snippet,
		})
	}
	return mustJSON(map[string]any{"ok": true, "query": query, "results": results})
}

// GhiAgent is a deterministic local agent. It has no remote model/API.
// It executes actual GHI operations or performs an actual public web search.
func GhiAgent(query string) string {
	q := strings.TrimSpace(query)
	lower := strings.ToLower(q)
	if q == "" {
		return mustJSON(map[string]any{"ok": false, "error": "enter a request"})
	}

	if target := agentTarget(lower, q, "investigate", "attack surface", "attack-surface", "investigation", "analyze"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "investigate", "target": target, "result": mustJSONObject(Investigate(target, 10))})
	}
	if target := agentTarget(lower, q, "dns", "dns records"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "dns", "target": target, "result": mustJSONObject(InspectDNS(target))})
	}
	if target := agentTarget(lower, q, "tls", "certificate", "ssl"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "tls", "target": target, "result": mustJSONObject(AnalyzeTLS(target))})
	}
	if target := agentTarget(lower, q, "headers", "security headers"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "security-headers", "target": target, "result": mustJSONObject(SecurityHeaders(target, 10))})
	}
	if target := agentTarget(lower, q, "redirect", "redirects"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "redirects", "target": target, "result": mustJSONObject(RedirectMap(target, 10))})
	}
	if target := agentTarget(lower, q, "technology", "technologies", "fingerprint"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "technology", "target": target, "result": mustJSONObject(TechnologyFingerprint(target, 10))})
	}
	if target := agentTarget(lower, q, "subdomains", "subdomain", "discover"); target != "" {
		return mustJSON(map[string]any{"ok": true, "agent": "discovery", "target": target, "result": mustJSONObject(DiscoverSubdomains(target, 500))})
	}

	search := q
	for _, prefix := range []string{"search ", "find ", "look up ", "lookup "} {
		if strings.HasPrefix(lower, prefix) {
			search = strings.TrimSpace(q[len(prefix):])
			break
		}
	}
	return mustJSON(map[string]any{"ok": true, "agent": "internet-search", "query": search, "result": mustJSONObject(InternetSearch(search, 10))})
}

func agentTarget(lower, original string, words ...string) string {
	for _, word := range words {
		if strings.Contains(lower, word) {
			if target := extractAgentTarget(original); target != "" {
				return target
			}
		}
	}
	return ""
}

func extractAgentTarget(value string) string {
	re := regexp.MustCompile(`(?i)(https?://[a-z0-9.-]+(?::\d+)?(?:/[^\s]*)?|(?:[a-z0-9-]+\.)+[a-z]{2,63})`)
	return strings.TrimSpace(re.FindString(value))
}

func mustJSONObject(raw string) any {
	var value any
	if json.Unmarshal([]byte(raw), &value) == nil {
		return value
	}
	return map[string]any{"raw": raw}
}

func stripHTML(value string) string {
	value = regexp.MustCompile(`(?s)<[^>]*>`).ReplaceAllString(value, " ")
	value = htmlUnescape(value)
	return strings.Join(strings.Fields(value), " ")
}

func htmlUnescape(value string) string {
	return strings.NewReplacer(
		"&amp;", "&",
		"&quot;", `"`,
		"&#x27;", "'",
		"&#39;", "'",
		"&lt;", "<",
		"&gt;", ">",
	).Replace(value)
}

func clampInt(value, low, high int) int {
	if value < low {
		return low
	}
	if value > high {
		return high
	}
	return value
}
