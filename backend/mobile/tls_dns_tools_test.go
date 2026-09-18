package mobile

import (
	"encoding/json"
	"testing"
)

func TestInspectDNS(t *testing.T) {
	raw := InspectDNS("localhost")
	var obj map[string]any
	if err := json.Unmarshal([]byte(raw), &obj); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if obj["ok"] != true {
		t.Fatalf("expected ok result: %v", obj)
	}
}

func TestAnalyzeTLSEmptyHost(t *testing.T) {
	raw := AnalyzeTLS("")
	var obj map[string]any
	if err := json.Unmarshal([]byte(raw), &obj); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if obj["ok"] != false {
		t.Fatalf("expected failed empty-host result: %v", obj)
	}
}

func TestNormalizeToolHost(t *testing.T) {
	if got := normalizeToolHost("https://Example.COM/path"); got != "Example.COM" {
		t.Fatalf("unexpected normalized host: %q", got)
	}
}
