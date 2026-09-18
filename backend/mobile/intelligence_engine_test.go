package mobile

import (
	"strings"
	"testing"
)

func TestGhiAgentEmpty(t *testing.T) {
	raw := GhiAgent("")
	if !strings.Contains(raw, `"ok":false`) {
		t.Fatalf("unexpected empty-agent response: %s", raw)
	}
}

func TestAgentTargetExtraction(t *testing.T) {
	got := extractAgentTarget("investigate https://example.com/path")
	if got != "https://example.com/path" {
		t.Fatalf("target = %q", got)
	}
}

func TestAgentRoutesMissingTargetToSearch(t *testing.T) {
	raw := GhiAgent("search ProjectDiscovery")
	if !strings.Contains(raw, `"agent":"internet-search"`) {
		t.Fatalf("unexpected route: %s", raw)
	}
}
