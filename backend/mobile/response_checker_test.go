package mobile

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestCheckResponseHTTPStructured(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Server", "ghi-test")
		w.Header().Set("Content-Type", "text/plain")
		w.Header().Set("X-Frame-Options", "DENY")
		if r.Header.Get("X-Test") != "ok" {
			http.Error(w, "missing header", http.StatusBadRequest)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	raw := CheckResponse("HTTP", srv.URL, "", "GET", "/", "X-Test: ok", "", false, false, 5, false, "UDP", "", "")
	var obj struct {
		Mode       string `json:"mode"`
		Successful int    `json:"successful"`
		Results    []struct {
			Status     int      `json:"status"`
			Server     string   `json:"server"`
			Security   []string `json:"security_headers"`
			BodySHA256 string   `json:"body_sha256"`
			FinalURL   string   `json:"final_url"`
		} `json:"results"`
	}
	if err := json.Unmarshal([]byte(raw), &obj); err != nil {
		t.Fatal(err)
	}
	if obj.Mode != "HTTP" || obj.Successful != 1 || len(obj.Results) != 1 {
		t.Fatalf("unexpected response: %s", raw)
	}
	if obj.Results[0].Status != http.StatusNoContent {
		t.Fatalf("status = %d", obj.Results[0].Status)
	}
	if obj.Results[0].Server != "ghi-test" {
		t.Fatalf("server = %q", obj.Results[0].Server)
	}
	if obj.Results[0].FinalURL == "" || obj.Results[0].BodySHA256 == "" {
		t.Fatalf("missing response metadata: %+v", obj.Results[0])
	}
	if !containsString(obj.Results[0].Security, "X-Frame-Options") {
		t.Fatalf("security header missing: %+v", obj.Results[0].Security)
	}
}

func TestCheckResponseRawPayload(t *testing.T) {
	var got bool
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r.Method == "POST" && r.Host != "" && r.Header.Get("X-Payload") == "yes"
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	host := strings.TrimPrefix(srv.URL, "http://")
	payload := "POST / HTTP/1.1\\r\\nHost: " + host + "\\r\\nX-Payload: yes\\r\\n\\r\\nhello"
	raw := CheckResponse("HTTP", srv.URL, "", "GET", "/", payload, "", false, false, 5, true, "UDP", "", "")
	if !got || !strings.Contains(raw, `"status":200`) {
		t.Fatalf("raw payload failed: got=%v raw=%s", got, raw)
	}
}

func TestCheckResponseUDPEmptyUsesLocalSocket(t *testing.T) {
	raw := CheckResponse("UDP", "", "", "", "", "", "", false, false, 2, false, "", "", "")
	if !strings.Contains(raw, `"mode":"UDP"`) || !strings.Contains(raw, `"scope":"local"`) {
		t.Fatalf("unexpected UDP local result: %s", raw)
	}
}

func TestUDPRangeSampling(t *testing.T) {
	got := sampleUDPTargets("127.0.0.1:1000-1010,127.0.0.1:2000", 3)
	if len(got) != 3 {
		t.Fatalf("expected 3 samples, got %d: %v", len(got), got)
	}
}

func TestDetectCDN(t *testing.T) {
	h := make(http.Header)
	h.Set("CF-RAY", "abc")
	if got := detectCDN(h); got != "Cloudflare" {
		t.Fatalf("detectCDN() = %q", got)
	}
	h = make(http.Header)
	h.Set("X-Amz-Cf-Id", "abc")
	if got := detectCDN(h); got != "Amazon CloudFront" {
		t.Fatalf("detectCDN() = %q", got)
	}
}
