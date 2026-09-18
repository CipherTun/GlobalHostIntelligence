package mobile

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

// InspectWebSurface performs a bounded, non-crawling web surface inspection.
// It fetches the landing page once, extracts same-origin links, and checks a
// small set of standard metadata/security discovery files. It does not guess
// paths beyond the fixed standards list and never follows redirects.
func InspectWebSurface(target string, timeoutSeconds int) string {
	start := time.Now()
	base, err := normalizeSurfaceURL(target)
	if err != nil {
		return mustJSON(map[string]any{"ok": false, "error": err.Error()})
	}
	if timeoutSeconds < 2 {
		timeoutSeconds = 2
	}
	if timeoutSeconds > 30 {
		timeoutSeconds = 30
	}
	client := &http.Client{
		Timeout: time.Duration(timeoutSeconds) * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}

	landing, err := fetchSurface(client, base)
	if err != nil {
		return mustJSON(map[string]any{
			"ok":         false,
			"target":     base.String(),
			"error":      err.Error(),
			"elapsed_ms": time.Since(start).Milliseconds(),
		})
	}

	result := map[string]any{
		"ok":             true,
		"target":         base.String(),
		"status":         landing.Status,
		"server":         landing.Server,
		"content_type":   landing.ContentType,
		"content_length": landing.Bytes,
		"title":          extractHTMLTitle(landing.Body),
		"canonical":      extractCanonical(landing.Body, base),
		"links":          extractSameOriginLinks(landing.Body, base, 100),
		"body_sha256":    landing.BodySHA256,
		"endpoints":      []any{},
		"elapsed_ms":     time.Since(start).Milliseconds(),
	}

	paths := []string{
		"/robots.txt",
		"/sitemap.xml",
		"/.well-known/security.txt",
		"/.well-known/assetlinks.json",
		"/.well-known/apple-app-site-association",
		"/manifest.json",
	}
	endpoints := make([]map[string]any, 0, len(paths))
	for _, path := range paths {
		u := *base
		u.Path = path
		u.RawQuery = ""
		item := map[string]any{"path": path, "url": u.String()}
		row, fetchErr := fetchSurface(client, &u)
		if fetchErr != nil {
			item["error"] = fetchErr.Error()
			endpoints = append(endpoints, item)
			continue
		}
		item["status"] = row.Status
		item["content_type"] = row.ContentType
		item["bytes"] = row.Bytes
		item["location"] = row.Location
		item["sha256"] = row.BodySHA256
		item["preview"] = previewText(row.Body, 1600)
		if strings.EqualFold(path, "/robots.txt") {
			item["sitemaps"] = extractSitemaps(row.Body)
			item["disallow_count"] = countDirective(row.Body, "disallow")
		}
		endpoints = append(endpoints, item)
	}
	result["endpoints"] = endpoints
	result["elapsed_ms"] = time.Since(start).Milliseconds()
	return mustJSON(result)
}

type surfaceResponse struct {
	Status      int
	Server      string
	ContentType string
	Bytes       int
	Location    string
	Body        []byte
	BodySHA256  string
}

func fetchSurface(client *http.Client, target *url.URL) (surfaceResponse, error) {
	req, err := http.NewRequest(http.MethodGet, target.String(), nil)
	if err != nil {
		return surfaceResponse{}, err
	}
	req.Header.Set("User-Agent", CurrentBrowserUserAgent)
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/json,text/plain;q=0.9,*/*;q=0.7")
	resp, err := client.Do(req)
	if err != nil {
		return surfaceResponse{}, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 512<<10))
	if err != nil {
		return surfaceResponse{}, err
	}
	sum := sha256.Sum256(body)
	return surfaceResponse{
		Status:      resp.StatusCode,
		Server:      resp.Header.Get("Server"),
		ContentType: resp.Header.Get("Content-Type"),
		Bytes:       len(body),
		Location:    resp.Header.Get("Location"),
		Body:        body,
		BodySHA256:  hex.EncodeToString(sum[:]),
	}, nil
}

func normalizeSurfaceURL(target string) (*url.URL, error) {
	target = strings.TrimSpace(target)
	if target == "" {
		return nil, fmt.Errorf("target is required")
	}
	if !strings.Contains(target, "://") {
		target = "https://" + target
	}
	u, err := url.Parse(target)
	if err != nil || u.Hostname() == "" {
		return nil, fmt.Errorf("invalid target URL")
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return nil, fmt.Errorf("only HTTP and HTTPS targets are supported")
	}
	u.Path = "/"
	u.RawQuery = ""
	u.Fragment = ""
	return u, nil
}

var titleRE = regexp.MustCompile(`(?is)<title[^>]*>(.*?)</title>`)
var canonicalRE = regexp.MustCompile(`(?is)<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']`)
var hrefRE = regexp.MustCompile(`(?is)\bhref\s*=\s*["']([^"']+)["']`)

func extractHTMLTitle(body []byte) string {
	m := titleRE.FindSubmatch(body)
	if len(m) < 2 {
		return ""
	}
	return strings.Join(strings.Fields(stripHTML(string(m[1]))), " ")
}

func extractCanonical(body []byte, base *url.URL) string {
	m := canonicalRE.FindSubmatch(body)
	if len(m) < 2 {
		return ""
	}
	ref, err := url.Parse(strings.TrimSpace(string(m[1])))
	if err != nil {
		return ""
	}
	return base.ResolveReference(ref).String()
}

func extractSameOriginLinks(body []byte, base *url.URL, limit int) []string {
	seen := map[string]bool{}
	out := make([]string, 0, limit)
	for _, m := range hrefRE.FindAllSubmatch(body, limit*4) {
		ref, err := url.Parse(strings.TrimSpace(string(m[1])))
		if err != nil || ref.IsAbs() && !strings.EqualFold(ref.Host, base.Host) {
			continue
		}
		u := base.ResolveReference(ref)
		if !strings.EqualFold(u.Scheme, base.Scheme) || !strings.EqualFold(u.Host, base.Host) {
			continue
		}
		u.Fragment = ""
		value := u.String()
		if seen[value] || len(value) > 2048 {
			continue
		}
		seen[value] = true
		out = append(out, value)
		if len(out) >= limit {
			break
		}
	}
	return out
}

func extractSitemaps(body []byte) []string {
	out := []string{}
	for _, line := range strings.Split(string(body), "\n") {
		line = strings.TrimSpace(line)
		if len(line) >= 8 && strings.EqualFold(line[:8], "sitemap:") {
			value := strings.TrimSpace(line[8:])
			if value != "" {
				out = append(out, value)
			}
		}
	}
	return out
}

func countDirective(body []byte, directive string) int {
	count := 0
	for _, line := range strings.Split(string(body), "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(strings.ToLower(line), strings.ToLower(directive)+":") {
			count++
		}
	}
	return count
}

func previewText(body []byte, limit int) string {
	text := strings.TrimSpace(string(body))
	text = strings.Join(strings.Fields(text), " ")
	if len(text) <= limit {
		return text
	}
	return text[:limit] + "…"
}
