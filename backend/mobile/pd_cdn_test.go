package mobile

import (
	"reflect"
	"strings"
	"testing"
)

func TestParseEvidenceLabel(t *testing.T) {
	cases := []struct{ in, name, conf string }{
		{"Cloudflare (high)", "Cloudflare", "high"},
		{"Amazon CloudFront (medium)", "Amazon CloudFront", "medium"},
		{"CDN/edge (low)", "CDN/edge", "low"},
		{"Plain", "Plain", "low"},
	}
	for _, c := range cases {
		name, conf := parseEvidenceLabel(c.in)
		if name != c.name || conf != c.conf {
			t.Fatalf("parseEvidenceLabel(%q) = %q,%q want %q,%q", c.in, name, conf, c.name, c.conf)
		}
	}
}

func TestSummarizeCDNSignalsPicksStrongestEdgeProvider(t *testing.T) {
	signals := []cdnSignal{
		{Signal: "header", Kind: "cdn", Provider: "CDN/edge", Confidence: "low"},
		{Signal: "ip_range", Kind: "cloud", Provider: "aws", Confidence: "high"},
		{Signal: "cname", Kind: "cdn", Provider: "Akamai", Confidence: "high"},
	}
	verdict, providers := summarizeCDNSignals(signals)
	if verdict != "Akamai" {
		t.Fatalf("verdict = %q", verdict)
	}
	if want := []string{"Akamai", "CDN/edge", "aws"}; !reflect.DeepEqual(providers, want) {
		t.Fatalf("providers = %v, want %v", providers, want)
	}
}

func TestSummarizeCDNSignalsFallsBackToWeakEvidence(t *testing.T) {
	verdict, _ := summarizeCDNSignals([]cdnSignal{{Signal: "header", Kind: "cdn", Provider: "CDN/edge", Confidence: "low"}})
	if verdict != "CDN/edge" {
		t.Fatalf("verdict = %q", verdict)
	}
}

// Hosting on a cloud provider is not the same as being fronted by a CDN.
func TestSummarizeCDNSignalsCloudIsNeverTheVerdict(t *testing.T) {
	verdict, providers := summarizeCDNSignals([]cdnSignal{{Signal: "ip_range", Kind: "cloud", Provider: "aws", Confidence: "high"}})
	if verdict != "" {
		t.Fatalf("verdict = %q, want none", verdict)
	}
	if !reflect.DeepEqual(providers, []string{"aws"}) {
		t.Fatalf("providers = %v", providers)
	}
}

func TestSummarizeCDNSignalsEmpty(t *testing.T) {
	verdict, providers := summarizeCDNSignals(nil)
	if verdict != "" || len(providers) != 0 {
		t.Fatalf("got %q %v", verdict, providers)
	}
}

func TestCloudFromAddressesIgnoresInvalidInput(t *testing.T) {
	if got := cloudFromAddresses([]string{"", "not-an-ip"}); got != "" {
		t.Fatalf("cloudFromAddresses = %q", got)
	}
}

func TestInspectCDNRequiresHost(t *testing.T) {
	if out := InspectCDN("   ", 5); !strings.Contains(out, `"ok":false`) {
		t.Fatalf("unexpected %s", out)
	}
}

func TestHostForURLBracketsIPv6(t *testing.T) {
	if got := hostForURL("2606:4700::1111"); got != "[2606:4700::1111]" {
		t.Fatalf("hostForURL = %q", got)
	}
	if got := hostForURL("example.com"); got != "example.com" {
		t.Fatalf("hostForURL = %q", got)
	}
}
