package mobile

import "testing"

func TestNormalizeDomain(t *testing.T) {
	// normalizeDomain intentionally strips only hostname decoration and path;
	// URL schemes are removed by AnalyzeHost, not by this helper.
	if got := normalizeDomain("*.Example.COM."); got != "example.com" {
		t.Fatalf("normalizeDomain() = %q", got)
	}
	if got := normalizeDomain("api.example.com/path"); got != "api.example.com" {
		t.Fatalf("normalizeDomain(path) = %q", got)
	}
}

func TestHostnameQuality(t *testing.T) {
	valid := []string{"example.com", "portal.example.online", "a.b.example.co.za"}
	for _, v := range valid {
		if !isHostname(v) {
			t.Fatalf("expected valid hostname: %s", v)
		}
	}
	invalid := []string{
		"uponmponmlkjimail.cstage.8backup.43notexistsinternal-portal.accessfitness24.online",
		"localhost.example.com",
		"foo.invalid.local",
		"-bad.example.com",
	}
	for _, v := range invalid {
		if isHostname(v) {
			t.Fatalf("expected rejected hostname: %s", v)
		}
	}
}

func TestGenerateNetworkRequestDefaults(t *testing.T) {
	got := GenerateNetworkRequest("HTTP", "GET", "example.com", "", "")
	if got == "" {
		t.Fatal("expected generated request")
	}
	for _, want := range []string{"GET / HTTP/1.1", "Host: example.com"} {
		if !contains(got, want) {
			t.Fatalf("generated request missing %q: %q", want, got)
		}
	}
}

func contains(s, sub string) bool {
	return len(sub) == 0 || (len(s) >= len(sub) && indexOf(s, sub) >= 0)
}

func indexOf(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}

func TestApexDomain(t *testing.T) {
	cases := map[string]string{
		"www.example.com":      "example.com",
		"portal.example.co.za": "example.co.za",
		"api.example.org":      "example.org",
	}
	for input, want := range cases {
		if got := apexDomain(input); got != want {
			t.Fatalf("apexDomain(%q) = %q, want %q", input, got, want)
		}
	}
}

func TestQualityAllowsGlobalTLDs(t *testing.T) {
	for _, host := range []string{"portal.example.online", "service.example.africa", "api.example.net"} {
		if !isHostname(host) {
			t.Fatalf("expected legitimate global hostname: %s", host)
		}
	}
}

func TestAnalyzeHostWithTimeoutAPI(t *testing.T) {
	raw := AnalyzeHostWithTimeout("example.com", 3)
	if raw == "" { t.Fatal("expected structured analyze response") }
}
