package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestVersionEndpoint(t *testing.T) {
	router := NewRouter(Deps{Version: "1.2.3"})
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/v1/version", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["version"] != "1.2.3" {
		t.Fatalf("version = %q", body["version"])
	}
}

// Without dependencies the health endpoint must degrade (503) instead of
// panicking on a nil pool, and must not echo internal error text.
func TestHealthDegradesWithoutDependencies(t *testing.T) {
	router := NewRouter(Deps{})
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/v1/health", nil))

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want 503", rec.Code)
	}
	var body healthResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body.Status != "degraded" {
		t.Fatalf("status field = %q", body.Status)
	}
	for name, value := range body.Checks {
		if value != "not configured" {
			t.Fatalf("check %s = %q, want \"not configured\"", name, value)
		}
		if strings.Contains(value, ":") {
			t.Fatalf("check %s leaks detail: %q", name, value)
		}
	}
	if len(body.Checks) != 2 {
		t.Fatalf("expected postgres and redis checks, got %v", body.Checks)
	}
}
