package mobile

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"sync/atomic"
	"testing"
)

func withBaseURL(t *testing.T, target *string, value string) {
	t.Helper()
	old := *target
	*target = value
	t.Cleanup(func() { *target = old })
}

func TestChaosSubdomainsUsesDocumentedEndpoint(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/dns/example.com/subdomains" {
			t.Errorf("path = %q", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "key-123" {
			t.Errorf("Authorization = %q, want the bare key", got)
		}
		_, _ = w.Write([]byte(`{"domain":"example.com","subdomains":["www","api","*","dev.stage","www"]}`))
	}))
	defer srv.Close()
	withBaseURL(t, &chaosBaseURL, srv.URL+"/dns")

	got, err := chaosSubdomains(srv.Client(), "example.com", "key-123", 50)
	if err != nil {
		t.Fatalf("chaosSubdomains: %v", err)
	}
	want := []string{"www.example.com", "api.example.com", "dev.stage.example.com"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestChaosSubdomainsReportsAuthFailure(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()
	withBaseURL(t, &chaosBaseURL, srv.URL+"/dns")

	_, err := chaosSubdomains(srv.Client(), "example.com", "bad", 10)
	if err == nil || !strings.Contains(err.Error(), "authentication") {
		t.Fatalf("expected an authentication error, got %v", err)
	}
}

func TestDiscoverProjectDiscoveryKeepsCompatibleShape(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"domain":"example.com","subdomains":["www","api"]}`))
	}))
	defer srv.Close()
	withBaseURL(t, &chaosBaseURL, srv.URL+"/dns")

	var out struct {
		OK      bool     `json:"ok"`
		Source  string   `json:"source"`
		Query   string   `json:"query"`
		Domains []string `json:"domains"`
		Results []struct {
			Domain  string   `json:"domain"`
			Sources []string `json:"sources"`
		} `json:"results"`
		UniqueCount int `json:"unique_count"`
	}
	raw := DiscoverProjectDiscovery("https://x", "k", 10)
	if !strings.Contains(raw, `"ok":false`) {
		t.Fatalf("a non-hostname must be rejected, got %s", raw)
	}
	raw = DiscoverProjectDiscovery("example.com", "k", 10)
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		t.Fatalf("decode %s: %v", raw, err)
	}
	if !out.OK || out.Source != "projectdiscovery" || out.Query != "example.com" || out.UniqueCount != 2 {
		t.Fatalf("unexpected envelope: %s", raw)
	}
	if len(out.Domains) != 2 || len(out.Results) != 2 {
		t.Fatalf("expected 2 domains and 2 results: %s", raw)
	}
	if out.Results[0].Domain != "www.example.com" || !reflect.DeepEqual(out.Results[0].Sources, []string{"chaos"}) {
		t.Fatalf("unexpected first result: %+v", out.Results[0])
	}
}

func TestDiscoverProjectDiscoveryRequiresKey(t *testing.T) {
	raw := DiscoverProjectDiscovery("example.com", "   ", 10)
	if !strings.Contains(raw, `"ok":false`) || !strings.Contains(raw, "API key") {
		t.Fatalf("unexpected %s", raw)
	}
}

func TestVirusTotalFollowsSameHostPagination(t *testing.T) {
	var requests atomic.Int32
	var srvURL string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		if r.Header.Get("x-apikey") != "vt-key" {
			t.Errorf("x-apikey = %q", r.Header.Get("x-apikey"))
		}
		if r.URL.Query().Get("cursor") == "2" {
			_, _ = w.Write([]byte(`{"data":[{"id":"c.example.com"}],"links":{}}`))
			return
		}
		_, _ = w.Write([]byte(`{"data":[{"id":"a.example.com"},{"id":"b.example.com"}],"links":{"next":"` +
			srvURL + `/api/v3/domains/example.com/subdomains?cursor=2&limit=40"}}`))
	}))
	defer srv.Close()
	srvURL = srv.URL
	withBaseURL(t, &virusTotalBaseURL, srv.URL+"/api/v3")

	got, err := virusTotalSubdomains(srv.Client(), "example.com", "vt-key", 100)
	if err != nil {
		t.Fatalf("virusTotalSubdomains: %v", err)
	}
	if want := []string{"a.example.com", "b.example.com", "c.example.com"}; !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
	if requests.Load() != 2 {
		t.Fatalf("requests = %d, want 2", requests.Load())
	}
}

// The API key header must never be sent to a host a response merely names.
func TestVirusTotalDoesNotFollowCrossHostNextLink(t *testing.T) {
	var requests atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		_, _ = w.Write([]byte(`{"data":[{"id":"a.example.com"}],"links":{"next":"http://attacker.invalid/steal?cursor=2"}}`))
	}))
	defer srv.Close()
	withBaseURL(t, &virusTotalBaseURL, srv.URL+"/api/v3")

	got, err := virusTotalSubdomains(srv.Client(), "example.com", "vt-key", 100)
	if err != nil {
		t.Fatalf("virusTotalSubdomains: %v", err)
	}
	if !reflect.DeepEqual(got, []string{"a.example.com"}) || requests.Load() != 1 {
		t.Fatalf("got %v after %d requests", got, requests.Load())
	}
}

func TestSecurityTrailsSubdomains(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/domain/example.com/subdomains" {
			t.Errorf("path = %q", r.URL.Path)
		}
		if r.Header.Get("APIKEY") != "st-key" {
			t.Errorf("APIKEY = %q", r.Header.Get("APIKEY"))
		}
		_, _ = w.Write([]byte(`{"subdomains":["www","mail"]}`))
	}))
	defer srv.Close()
	withBaseURL(t, &securityTrailsBaseURL, srv.URL+"/v1")

	got, err := securityTrailsSubdomains(srv.Client(), "example.com", "st-key", 10)
	if err != nil {
		t.Fatalf("securityTrailsSubdomains: %v", err)
	}
	if want := []string{"www.example.com", "mail.example.com"}; !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestShodanSubdomains(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/dns/domain/example.com" || r.URL.Query().Get("key") != "sh-key" {
			t.Errorf("unexpected request %s", r.URL.String())
		}
		_, _ = w.Write([]byte(`{"domain":"example.com","subdomains":["www","vpn"],"data":[]}`))
	}))
	defer srv.Close()
	withBaseURL(t, &shodanBaseURL, srv.URL)

	got, err := shodanSubdomains(srv.Client(), "example.com", "sh-key", 10)
	if err != nil {
		t.Fatalf("shodanSubdomains: %v", err)
	}
	if want := []string{"www.example.com", "vpn.example.com"}; !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

// Shodan takes its key in the query string, so a transport error must not echo it.
func TestProviderErrorsDoNotLeakAPIKeys(t *testing.T) {
	srv := httptest.NewServer(http.NotFoundHandler())
	closedURL := srv.URL
	srv.Close()
	withBaseURL(t, &shodanBaseURL, closedURL)

	_, err := shodanSubdomains(&http.Client{}, "example.com", "super-secret-key", 10)
	if err == nil {
		t.Fatal("expected a connection error")
	}
	if strings.Contains(err.Error(), "super-secret-key") {
		t.Fatalf("error leaks the API key: %v", err)
	}
}

func TestExpandLabelsHandlesFQDNsAndWildcards(t *testing.T) {
	got := expandLabels("example.com", []string{"www", "API.example.com.", "*", "", "example.com", "*.dev", "other.test"}, 50)
	want := []string{"www.example.com", "api.example.com", "dev.example.com", "other.test.example.com"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestDiscoverSubdomainsWithKeysMergesKeyedAndKeylessResults(t *testing.T) {
	old := keylessSubdomains
	keylessSubdomains = func(domain string, maxResults int) string {
		return `{"domain":"example.com","subdomains":["a.example.com","b.example.com"],` +
			`"source_counts":{"crt.sh":2},"errors":{"anubis":"HTTP 500"},"sources":["crt.sh","anubis"]}`
	}
	t.Cleanup(func() { keylessSubdomains = old })

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"subdomains":["b","c"]}`))
	}))
	defer srv.Close()
	withBaseURL(t, &chaosBaseURL, srv.URL+"/dns")

	// Both key names address the same ProjectDiscovery provider.
	for _, cfg := range []string{`{"chaos":"k"}`, `{"projectdiscovery":"k"}`} {
		var out struct {
			OK           bool              `json:"ok"`
			Subdomains   []string          `json:"subdomains"`
			SourceCounts map[string]int    `json:"source_counts"`
			Errors       map[string]string `json:"errors"`
			KeyedSources []string          `json:"keyed_sources"`
		}
		raw := DiscoverSubdomainsWithKeys("example.com", 100, cfg)
		if err := json.Unmarshal([]byte(raw), &out); err != nil {
			t.Fatalf("decode %s: %v", raw, err)
		}
		if !out.OK {
			t.Fatalf("not ok: %s", raw)
		}
		if want := []string{"a.example.com", "b.example.com", "c.example.com"}; !reflect.DeepEqual(out.Subdomains, want) {
			t.Fatalf("subdomains = %v want %v", out.Subdomains, want)
		}
		if out.SourceCounts["chaos"] != 2 || out.SourceCounts["crt.sh"] != 2 {
			t.Fatalf("source_counts = %v", out.SourceCounts)
		}
		if out.Errors["anubis"] != "HTTP 500" {
			t.Fatalf("keyless errors were dropped: %v", out.Errors)
		}
		if !reflect.DeepEqual(out.KeyedSources, []string{"chaos"}) {
			t.Fatalf("keyed_sources = %v", out.KeyedSources)
		}
	}
}

func TestDiscoverSubdomainsWithKeysRejectsBadInput(t *testing.T) {
	old := keylessSubdomains
	keylessSubdomains = func(string, int) string { return `{}` }
	t.Cleanup(func() { keylessSubdomains = old })

	if raw := DiscoverSubdomainsWithKeys("not a domain", 10, "{}"); !strings.Contains(raw, `"ok":false`) {
		t.Fatalf("invalid domain accepted: %s", raw)
	}
	if raw := DiscoverSubdomainsWithKeys("example.com", 10, "{oops"); !strings.Contains(raw, "invalid provider config") {
		t.Fatalf("bad config accepted: %s", raw)
	}
}
