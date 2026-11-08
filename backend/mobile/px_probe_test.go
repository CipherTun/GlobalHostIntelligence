package mobile

import (
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestPxParseHosts(t *testing.T) {
	got := pxParseHosts("Example.com\nhttps://www.example.com/path\n  example.com ,bad host\n\n10.0.0.1", 0)
	want := []string{"example.com", "www.example.com", "10.0.0.1"}
	if len(got) != len(want) {
		t.Fatalf("got %v want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("got %v want %v", got, want)
		}
	}
}

func TestPxGateBacksOffAndRecovers(t *testing.T) {
	g := newPxGate(16, true)
	for i := 0; i < 32; i++ {
		g.record(true)
	}
	if g.current() >= 16 {
		t.Fatalf("expected back-off, still %d", g.current())
	}
	low := g.current()
	for i := 0; i < 32*4; i++ {
		g.record(false)
	}
	if g.current() <= low {
		t.Fatalf("expected recovery above %d, got %d", low, g.current())
	}
	if g.current() > 16 {
		t.Fatalf("gate grew beyond its maximum: %d", g.current())
	}
}

func TestPxDNSCachesAndCollapsesLookups(t *testing.T) {
	d := newPxDNS([]string{"192.0.2.1:53"})
	var calls atomic.Int32
	d.resolveFn = func(ctx context.Context, host string) ([]net.IP, bool, error) {
		calls.Add(1)
		time.Sleep(30 * time.Millisecond)
		return []net.IP{net.ParseIP("203.0.113.7")}, false, nil
	}
	var wg sync.WaitGroup
	for i := 0; i < 40; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			ips, nx, err := d.lookup(context.Background(), "Host.Example.com.")
			if err != nil || nx || len(ips) != 1 {
				t.Errorf("lookup = %v %v %v", ips, nx, err)
			}
		}()
	}
	wg.Wait()
	d.lookup(context.Background(), "host.example.com")
	if n := calls.Load(); n != 1 {
		t.Fatalf("resolver called %d times, want 1", n)
	}
}

func TestSetDNSServersValidates(t *testing.T) {
	defer SetDNSServers("")
	if out := SetDNSServers("not-an-ip"); !strings.Contains(out, `"ok":false`) {
		t.Fatalf("expected failure, got %s", out)
	}
	if out := SetDNSServers("9.9.9.9, 1.1.1.1:5353"); !strings.Contains(out, `"ok":true`) {
		t.Fatalf("expected success, got %s", out)
	}
}

type pollOut struct {
	OK       bool       `json:"ok"`
	Done     bool       `json:"done"`
	Cursor   int        `json:"cursor"`
	Results  []pxResult `json:"results"`
	Progress struct {
		Total   int `json:"total"`
		Checked int `json:"checked"`
		Live    int `json:"live"`
	} `json:"progress"`
}

func runProbe(t *testing.T, hosts []string, options string) []pxResult {
	t.Helper()
	var start struct {
		OK  bool   `json:"ok"`
		Job string `json:"job"`
	}
	if err := json.Unmarshal([]byte(StartProbeJob(strings.Join(hosts, "\n"), options)), &start); err != nil || !start.OK {
		t.Fatalf("start failed: %v", err)
	}
	var all []pxResult
	cursor := 0
	deadline := time.Now().Add(20 * time.Second)
	for time.Now().Before(deadline) {
		var out pollOut
		if err := json.Unmarshal([]byte(PollProbeJob(start.Job, cursor)), &out); err != nil || !out.OK {
			t.Fatalf("poll failed: %v", err)
		}
		all = append(all, out.Results...)
		cursor = out.Cursor
		if out.Done {
			return all
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("probe job did not finish in time")
	return nil
}

// Hosts are IP:port literals, so the test never touches DNS or the internet.
func TestProbeJobFindsLiveHostsOnly(t *testing.T) {
	ok := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(200) }))
	defer ok.Close()
	forbidden := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(403) }))
	defer forbidden.Close()
	dead := httptest.NewServer(http.NotFoundHandler())
	deadAddr := strings.TrimPrefix(dead.URL, "http://")
	dead.Close() // nothing listens here any more

	hosts := []string{strings.TrimPrefix(ok.URL, "http://"), strings.TrimPrefix(forbidden.URL, "http://"), deadAddr}

	strict := runProbe(t, hosts, `{"workers":4,"timeout_sec":3}`)
	if len(strict) != 1 || strict[0].Status != 200 || strict[0].Scheme != "http" {
		t.Fatalf("strict mode results = %+v", strict)
	}

	any := runProbe(t, hosts, `{"workers":4,"timeout_sec":3,"accept_any":true}`)
	if len(any) != 2 {
		t.Fatalf("accept_any results = %+v", any)
	}
}

func TestProbeJobStopsAtLimit(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(200) }))
	defer srv.Close()
	host := strings.TrimPrefix(srv.URL, "http://")
	_, port, _ := net.SplitHostPort(host)
	hosts := []string{host, "127.0.0.2:" + port, "127.0.0.3:" + port, "127.0.0.4:" + port}
	res := runProbe(t, hosts, `{"workers":1,"timeout_sec":3,"limit":1}`)
	if len(res) < 1 || len(res) > 2 {
		t.Fatalf("limit=1 returned %d results", len(res))
	}
}

func TestPollUnknownJob(t *testing.T) {
	if out := PollProbeJob("nope", 0); !strings.Contains(out, `"ok":false`) {
		t.Fatalf("unexpected %s", out)
	}
}
