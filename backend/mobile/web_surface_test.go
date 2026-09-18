package mobile

import (
	"net/url"
	"testing"
)

func TestNormalizeSurfaceURL(t *testing.T) {
	u, err := normalizeSurfaceURL("example.com/path?q=1")
	if err != nil {
		t.Fatal(err)
	}
	if u.Scheme != "https" || u.Hostname() != "example.com" || u.Path != "/" || u.RawQuery != "" {
		t.Fatalf("unexpected URL: %s", u.String())
	}
}

func TestExtractSameOriginLinks(t *testing.T) {
	base, _ := url.Parse("https://example.com/")
	body := []byte(`<a href="/one">one</a><a href="https://example.com/two#x">two</a><a href="https://other.example/skip">skip</a>`)
	got := extractSameOriginLinks(body, base, 10)
	if len(got) != 2 || got[0] != "https://example.com/one" || got[1] != "https://example.com/two" {
		t.Fatalf("unexpected links: %#v", got)
	}
}

func TestExtractSitemaps(t *testing.T) {
	got := extractSitemaps([]byte("User-agent: *\nSitemap: https://example.com/sitemap.xml\n"))
	if len(got) != 1 || got[0] != "https://example.com/sitemap.xml" {
		t.Fatalf("unexpected sitemaps: %#v", got)
	}
}
