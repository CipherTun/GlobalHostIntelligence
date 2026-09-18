package mobile

import (
	"strings"
	"testing"
)

func TestCurrentBrowserUserAgent(t *testing.T) {
	want := "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36"
	if CurrentBrowserUserAgent != want {
		t.Fatalf("unexpected UA: %q", CurrentBrowserUserAgent)
	}
	if got := effectiveUserAgent("GlobalHostIntelligence/4.0"); got != want {
		t.Fatalf("legacy UA was not migrated: %q", got)
	}
	if got := effectiveUserAgent("CustomAgent/1.0"); got != "CustomAgent/1.0" {
		t.Fatalf("custom UA was not preserved: %q", got)
	}
}

func TestGenerateRequestUsesRealCRLFAndByteLength(t *testing.T) {
	body := "héllo"
	got := GenerateRequest("POST", "example.com", "/submit", body)
	if got == "" || !strings.Contains(got, "POST /submit HTTP/1.1\r\n") {
		t.Fatalf("invalid request line: %q", got)
	}
	if !strings.Contains(got, "User-Agent: "+CurrentBrowserUserAgent+"\r\n") {
		t.Fatalf("current browser UA missing")
	}
	if !strings.Contains(got, "Content-Length: 6\r\n") {
		t.Fatalf("UTF-8 Content-Length missing or incorrect: %q", got)
	}
	if !strings.Contains(got, "\r\n\r\nhéllo") {
		t.Fatalf("body framing is not CRLF-delimited: %q", got)
	}
}

func TestGenerateNetworkRequestWebSocket(t *testing.T) {
	got := GenerateNetworkRequest("WEBSOCKET", "GET", "example.com", "/socket", "")
	for _, part := range []string{
		"GET /socket HTTP/1.1\r\n",
		"Upgrade: websocket\r\n",
		"Connection: Upgrade\r\n",
		"Sec-WebSocket-Version: 13\r\n",
		"Sec-WebSocket-Key:",
	} {
		if !strings.Contains(got, part) {
			t.Fatalf("missing %q in %q", part, got)
		}
	}
}
