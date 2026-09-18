package mobile

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

// CheckResponse is the full, mobile-safe response checker. It intentionally
// returns structured JSON so the Android UI can render a clean result without
// exposing backend worker/runtime logs.
func CheckResponse(mode, targets, proxy, method, path, headers, body string, followRedirects, allowInsecure bool, timeoutSeconds int, payloadMode bool, dnsTransport, resolver, authoritative string) string {
	mode = strings.ToUpper(strings.TrimSpace(mode))
	if timeoutSeconds < 2 {
		timeoutSeconds = 10
	}
	if timeoutSeconds > 30 {
		timeoutSeconds = 30
	}
	switch mode {
	case "UDP":
		return checkUDP(targets, timeoutSeconds)
	case "DNS":
		return checkDNS(targets, resolver, authoritative, dnsTransport, timeoutSeconds)
	default:
		return checkHTTPTargets(targets, proxy, method, path, headers, body, followRedirects, allowInsecure, timeoutSeconds, payloadMode)
	}
}

type responseHTTPResult struct {
	Target          string              `json:"target"`
	Method          string              `json:"method,omitempty"`
	Status          int                 `json:"status"`
	StatusText      string              `json:"status_text,omitempty"`
	LatencyMs       int64               `json:"latency_ms"`
	FinalURL        string              `json:"final_url,omitempty"`
	Redirects       []string            `json:"redirects,omitempty"`
	Addresses       []string            `json:"addresses,omitempty"`
	ContentType     string              `json:"content_type,omitempty"`
	ContentLength   int64               `json:"content_length"`
	BodySize        int64               `json:"body_size"`
	BodySHA256      string              `json:"body_sha256,omitempty"`
	Server          string              `json:"server,omitempty"`
	CDN             string              `json:"cdn,omitempty"`
	TLSValid        bool                `json:"tls_valid"`
	TLSVersion      string              `json:"tls_version,omitempty"`
	TLSCipher       string              `json:"tls_cipher,omitempty"`
	TLSServerName   string              `json:"tls_server_name,omitempty"`
	HTTPProtocol    string              `json:"http_protocol,omitempty"`
	Title           string              `json:"title,omitempty"`
	Technologies    []string            `json:"technologies,omitempty"`
	SecurityHeaders []string            `json:"security_headers,omitempty"`
	Headers         map[string][]string `json:"headers,omitempty"`
	Body            string              `json:"body,omitempty"`
	Error           string              `json:"error,omitempty"`
}

func checkHTTPTargets(targets, proxy, method, path, headersText, body string, followRedirects, allowInsecure bool, timeoutSeconds int, payloadMode bool) string {
	lines := splitNonEmptyLines(targets)
	if len(lines) > 50 {
		lines = lines[:50]
	}
	if len(lines) == 0 {
		return mustJSON(map[string]any{"mode": "HTTP", "error": "enter at least one target URL"})
	}
	results := make([]responseHTTPResult, len(lines))
	type item struct {
		i int
		r responseHTTPResult
	}
	ch := make(chan item, len(lines))
	sem := make(chan struct{}, 6)
	for i, target := range lines {
		i, target := i, target
		go func() {
			sem <- struct{}{}
			defer func() { <-sem }()
			ch <- item{i, doHTTPCheck(target, proxy, method, path, headersText, body, followRedirects, allowInsecure, timeoutSeconds, payloadMode)}
		}()
	}
	for range lines {
		x := <-ch
		results[x.i] = x.r
	}
	ok := 0
	for _, r := range results {
		if r.Error == "" && r.Status >= 200 && r.Status < 400 {
			ok++
		}
	}
	return mustJSON(map[string]any{
		"mode": "HTTP", "targets": len(lines), "successful": ok, "results": results,
	})
}

func doHTTPCheck(target, proxyText, method, path, headersText, body string, followRedirects, allowInsecure bool, timeoutSeconds int, payloadMode bool) responseHTTPResult {
	target = strings.TrimSpace(target)
	if !strings.Contains(target, "://") {
		target = "http://" + target
	}
	u, err := url.Parse(target)
	if err != nil || u.Hostname() == "" {
		return responseHTTPResult{Target: target, Error: "invalid target URL"}
	}
	method = strings.ToUpper(strings.TrimSpace(method))
	if method == "" {
		method = http.MethodGet
	}
	reqPath := path
	if reqPath == "" {
		reqPath = u.RequestURI()
		if reqPath == "" {
			reqPath = "/"
		}
	}
	if !strings.HasPrefix(reqPath, "/") {
		reqPath = "/" + reqPath
	}
	var reqBody io.Reader
	if method != http.MethodGet && method != http.MethodHead && body != "" {
		reqBody = strings.NewReader(body)
	}
	req, err := http.NewRequest(method, u.Scheme+"://"+u.Host+reqPath, reqBody)
	if err != nil {
		return responseHTTPResult{Target: target, Method: method, Error: err.Error()}
	}
	req.Header.Set("User-Agent", "GlobalHostIntelligence/2.0")
	req.Header.Set("Accept", "*/*")
	if payloadMode && strings.TrimSpace(headersText) != "" {
		parseRawPayload(req, headersText, proxyText)
	} else {
		applyHeaderText(req, strings.ReplaceAll(headersText, "{proxy}", proxyText), proxyText)
	}
	// A raw payload may intentionally provide a different Host header.
	if h := req.Header.Get("Host"); h != "" {
		req.Host = h
		req.Header.Del("Host")
	}
	transport := &http.Transport{
		TLSClientConfig: &tls.Config{
			ServerName:         u.Hostname(),
			MinVersion:         tls.VersionTLS12,
			InsecureSkipVerify: allowInsecure,
		},
		ForceAttemptHTTP2: true,
	}
	if proxyText != "" {
		configureProxyTransport(transport, proxyText, timeoutSeconds)
	}
	client := &http.Client{
		Timeout:   time.Duration(timeoutSeconds) * time.Second,
		Transport: transport,
	}
	var redirects []string
	if followRedirects {
		client.CheckRedirect = func(next *http.Request, via []*http.Request) error {
			if len(via) >= 10 {
				return http.ErrUseLastResponse
			}
			redirects = append(redirects, next.URL.String())
			return nil
		}
	} else {
		client.CheckRedirect = func(next *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		}
	}
	start := time.Now()
	resp, err := client.Do(req)
	r := responseHTTPResult{Target: target, Method: method, Redirects: redirects}
	if err != nil {
		r.Error = err.Error()
		r.LatencyMs = time.Since(start).Milliseconds()
		return r
	}
	defer resp.Body.Close()
	r.LatencyMs = time.Since(start).Milliseconds()
	r.Status = resp.StatusCode
	r.StatusText = resp.Status
	r.FinalURL = resp.Request.URL.String()
	r.ContentType = resp.Header.Get("Content-Type")
	r.ContentLength = resp.ContentLength
	r.Server = resp.Header.Get("Server")
	r.CDN = detectCDN(resp.Header)
	r.HTTPProtocol = resp.Proto
	r.Headers = cloneHeaders(resp.Header)
	if resp.TLS != nil {
		r.TLSValid = !allowInsecure || resp.TLS.HandshakeComplete
		r.TLSVersion = tlsVersionName(resp.TLS.Version)
		r.TLSCipher = tlsCipherName(resp.TLS.CipherSuite)
		r.TLSServerName = resp.TLS.ServerName
	}
	if ips, e := net.LookupHost(u.Hostname()); e == nil {
		r.Addresses = ips
	}
	limited := io.LimitReader(resp.Body, 256<<10)
	data, readErr := io.ReadAll(limited)
	if readErr != nil {
		r.Error = readErr.Error()
	}
	r.BodySize = int64(len(data))
	sum := sha256.Sum256(data)
	r.BodySHA256 = hex.EncodeToString(sum[:])
	r.Body = sanitizeBody(data)
	r.Title = htmlTitle(r.Body)
	r.Technologies = fingerprintTechnologies(resp.Header, r.Body)
	r.SecurityHeaders = securityHeaderNames(resp.Header)
	return r
}

func applyHeaderText(req *http.Request, text, proxyText string) {
	for _, line := range strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		k, v, ok := strings.Cut(line, ":")
		if !ok {
			continue
		}
		k = strings.TrimSpace(k)
		v = strings.TrimSpace(v)
		if k != "" {
			req.Header.Add(k, expandToken(v, req, proxyText))
		}
	}
}

func parseRawPayload(req *http.Request, raw, proxyText string) {
	raw = strings.ReplaceAll(raw, `\r\n`, "\n")
	raw = strings.ReplaceAll(raw, `\n`, "\n")
	parts := strings.SplitN(raw, "\n\n", 2)
	head := parts[0]
	for i, line := range strings.Split(head, "\n") {
		line = strings.TrimSpace(line)
		if i == 0 {
			fields := strings.Fields(line)
			if len(fields) >= 2 {
				req.Method = strings.ToUpper(fields[0])
				if p := fields[1]; p != "" {
					if strings.HasPrefix(p, "http://") || strings.HasPrefix(p, "https://") {
						if u, e := url.Parse(p); e == nil {
							req.URL.Scheme, req.URL.Host, req.URL.Path, req.URL.RawQuery = u.Scheme, u.Host, u.Path, u.RawQuery
						}
					} else {
						req.URL.Path = p
					}
				}
			}
			continue
		}
		k, v, ok := strings.Cut(line, ":")
		if ok {
			req.Header.Set(strings.TrimSpace(k), expandToken(strings.TrimSpace(v), req, proxyText))
		}
	}
	if len(parts) == 2 && req.Method != http.MethodGet && req.Method != http.MethodHead {
		req.Body = io.NopCloser(strings.NewReader(parts[1]))
	}
}

func expandToken(s string, req *http.Request, proxyText string) string {
	host := req.Host
	if host == "" {
		host = req.URL.Host
	}
	proxy := strings.TrimSpace(proxyText)
	return strings.ReplaceAll(strings.ReplaceAll(strings.ReplaceAll(s, "{host}", host), "{path}", req.URL.RequestURI()), "{proxy}", proxy)
}

func configureProxyTransport(t *http.Transport, proxyText string, timeoutSeconds int) {
	p := strings.TrimSpace(proxyText)
	if !strings.Contains(p, "://") {
		p = "http://" + p
	}
	u, err := url.Parse(p)
	if err != nil || u.Host == "" {
		return
	}
	switch strings.ToLower(u.Scheme) {
	case "http", "https":
		t.Proxy = http.ProxyURL(u)
	case "socks5", "socks5h":
		t.DialContext = socks5DialContext(u, time.Duration(timeoutSeconds)*time.Second)
	case "socks4", "socks4a":
		t.DialContext = socks4DialContext(u, time.Duration(timeoutSeconds)*time.Second)
	}
}

func socks5DialContext(proxyURL *url.URL, timeout time.Duration) func(context.Context, string, string) (net.Conn, error) {
	return func(ctx context.Context, network, address string) (net.Conn, error) {
		d := &net.Dialer{Timeout: timeout}
		conn, err := d.DialContext(ctx, "tcp", proxyURL.Host)
		if err != nil {
			return nil, err
		}
		fail := func(e error) (net.Conn, error) { conn.Close(); return nil, e }
		if _, err = conn.Write([]byte{5, 2, 0, 2}); err != nil {
			return fail(err)
		}
		var hello [2]byte
		if _, err = io.ReadFull(conn, hello[:]); err != nil {
			return fail(err)
		}
		if hello[0] != 5 {
			return fail(fmt.Errorf("invalid SOCKS5 response"))
		}
		if hello[1] == 2 {
			user := ""
			pass := ""
			if proxyURL.User != nil {
				user = proxyURL.User.Username()
				pass, _ = proxyURL.User.Password()
			}
			if len(user) > 255 || len(pass) > 255 {
				return fail(fmt.Errorf("proxy credentials too long"))
			}
			msg := append([]byte{1, byte(len(user))}, []byte(user)...)
			msg = append(msg, byte(len(pass)))
			msg = append(msg, []byte(pass)...)
			if _, err = conn.Write(msg); err != nil {
				return fail(err)
			}
			var auth [2]byte
			if _, err = io.ReadFull(conn, auth[:]); err != nil || auth[1] != 0 {
				return fail(fmt.Errorf("SOCKS5 authentication failed"))
			}
		} else if hello[1] != 0 {
			return fail(fmt.Errorf("SOCKS5 proxy requires unsupported authentication"))
		}
		ip := net.ParseIP(address)
		var atyp byte
		var dst []byte
		if ip4 := ip.To4(); ip4 != nil {
			atyp, dst = 1, ip4
		} else if ip6 := ip.To16(); ip6 != nil {
			atyp, dst = 4, ip6
		} else {
			host, port, e := net.SplitHostPort(address)
			if e != nil {
				return fail(e)
			}
			dst = append([]byte{byte(len(host))}, []byte(host)...)
			atyp = 3
			address = net.JoinHostPort(host, port)
		}
		_, port, e := net.SplitHostPort(address)
		if e != nil {
			return fail(e)
		}
		portNum, _ := strconv.Atoi(port)
		req := []byte{5, 1, 0, atyp}
		req = append(req, dst...)
		req = append(req, byte(portNum>>8), byte(portNum))
		if _, err = conn.Write(req); err != nil {
			return fail(err)
		}
		var head [4]byte
		if _, err = io.ReadFull(conn, head[:]); err != nil {
			return fail(err)
		}
		if head[1] != 0 {
			return fail(fmt.Errorf("SOCKS5 connect failed: %d", head[1]))
		}
		n := 0
		switch head[3] {
		case 1:
			n = 4
		case 4:
			n = 16
		case 3:
			var l [1]byte
			if _, err = io.ReadFull(conn, l[:]); err != nil {
				return fail(err)
			}
			n = int(l[0])
		default:
			return fail(fmt.Errorf("invalid SOCKS5 address type"))
		}
		buf := make([]byte, n+2)
		if _, err = io.ReadFull(conn, buf); err != nil {
			return fail(err)
		}
		return conn, nil
	}
}

func socks4DialContext(proxyURL *url.URL, timeout time.Duration) func(context.Context, string, string) (net.Conn, error) {
	return func(ctx context.Context, network, address string) (net.Conn, error) {
		d := &net.Dialer{Timeout: timeout}
		conn, err := d.DialContext(ctx, "tcp", proxyURL.Host)
		if err != nil {
			return nil, err
		}
		user := ""
		if proxyURL.User != nil {
			user = proxyURL.User.Username()
		}
		host, port, err := net.SplitHostPort(address)
		if err != nil {
			conn.Close()
			return nil, err
		}
		portNum, _ := strconv.Atoi(port)
		ip := net.ParseIP(host)
		if ip == nil {
			if strings.EqualFold(proxyURL.Scheme, "socks4") {
				conn.Close()
				return nil, fmt.Errorf("SOCKS4 requires an IPv4 destination")
			}
			ip = net.ParseIP("0.0.0.1")
		}
		buf := bytes.NewBuffer([]byte{4, 1, byte(portNum >> 8), byte(portNum)})
		buf.Write(ip.To4())
		buf.WriteString(user)
		buf.WriteByte(0)
		if net.ParseIP(host) == nil {
			buf.WriteString(host)
			buf.WriteByte(0)
		}
		if _, err = conn.Write(buf.Bytes()); err != nil {
			conn.Close()
			return nil, err
		}
		var reply [8]byte
		if _, err = io.ReadFull(conn, reply[:]); err != nil {
			conn.Close()
			return nil, err
		}
		if reply[1] != 90 {
			conn.Close()
			return nil, fmt.Errorf("SOCKS4 connect failed: %d", reply[1])
		}
		return conn, nil
	}
}

func checkUDP(target string, timeoutSeconds int) string {
	target = strings.TrimSpace(target)
	if target == "" {
		start := time.Now()
		conn, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 0})
		if err != nil {
			return mustJSON(map[string]any{"mode": "UDP", "ok": false, "error": err.Error()})
		}
		defer conn.Close()
		addr := conn.LocalAddr().(*net.UDPAddr)
		payload := []byte("GHI-UDP-LOCAL-CHECK")
		_, writeErr := conn.WriteToUDP(payload, addr)
		_ = conn.SetReadDeadline(time.Now().Add(time.Duration(timeoutSeconds) * time.Second))
		buf := make([]byte, 64)
		n, _, readErr := conn.ReadFromUDP(buf)
		return mustJSON(map[string]any{
			"mode": "UDP", "ok": writeErr == nil && readErr == nil,
			"scope": "local", "bytes_sent": len(payload), "bytes_received": n,
			"latency_ms":  time.Since(start).Milliseconds(),
			"write_error": errorString(writeErr), "read_error": errorString(readErr),
		})
	}
	targets := sampleUDPTargets(target, 3)
	results := make([]map[string]any, 0, len(targets))
	for _, addr := range targets {
		start := time.Now()
		conn, err := net.DialTimeout("udp", addr, time.Duration(timeoutSeconds)*time.Second)
		if err != nil {
			results = append(results, map[string]any{"target": addr, "ok": false, "error": err.Error()})
			continue
		}
		payload := []byte("GHI-UDP-CHECK")
		_, writeErr := conn.Write(payload)
		_ = conn.SetReadDeadline(time.Now().Add(time.Duration(timeoutSeconds) * time.Second))
		buf := make([]byte, 512)
		n, readErr := conn.Read(buf)
		conn.Close()
		result := map[string]any{
			"target": addr, "ok": writeErr == nil && readErr == nil,
			"write_ok": writeErr == nil, "bytes_sent": len(payload),
			"latency_ms": time.Since(start).Milliseconds(), "bytes_received": n,
		}
		if writeErr != nil {
			result["write_error"] = writeErr.Error()
		}
		if readErr != nil {
			result["read_error"] = readErr.Error()
		}
		results = append(results, result)
	}
	return mustJSON(map[string]any{"mode": "UDP", "results": results})
}

func sampleUDPTargets(spec string, max int) []string {
	var out []string
	seen := map[string]bool{}
	for _, part := range strings.Split(spec, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		host, port, err := net.SplitHostPort(part)
		if err != nil {
			host, port = strings.TrimSpace(part), "53"
		}
		if strings.Contains(port, "-") {
			ab := strings.SplitN(port, "-", 2)
			a, _ := strconv.Atoi(ab[0])
			b, _ := strconv.Atoi(ab[1])
			if a > 0 && b >= a {
				ports := []int{a, b}
				if b-a > 1 {
					ports = append(ports, a+(b-a)/2)
				}
				for _, p := range ports {
					if len(out) >= max {
						return out
					}
					addr := net.JoinHostPort(host, strconv.Itoa(p))
					if !seen[addr] {
						seen[addr] = true
						out = append(out, addr)
						if len(out) >= max {
							return out
						}
					}
				}
				continue
			}
		}
		addr := net.JoinHostPort(host, port)
		if !seen[addr] {
			seen[addr] = true
			out = append(out, addr)
		}
		if len(out) >= max {
			return out
		}
	}
	return out
}

func checkDNS(target, resolver, authoritative, transport string, timeoutSeconds int) string {
	domain := normalizeDomain(target)
	if !isHostname(domain) {
		return mustJSON(map[string]any{"mode": "DNS", "ok": false, "error": "enter a valid DNS domain"})
	}
	transport = strings.ToUpper(strings.TrimSpace(transport))
	if transport == "" {
		transport = "UDP"
	}
	server := strings.TrimSpace(resolver)
	if authoritative != "" {
		server = strings.TrimSpace(authoritative)
	}
	if server == "" {
		server = "system"
	}
	start := time.Now()
	var (
		resp []byte
		err  error
	)
	switch transport {
	case "UDP", "TCP":
		resp, err = dnsWireQuery(domain, transport, server, time.Duration(timeoutSeconds)*time.Second)
	case "DOT":
		resp, err = dnsTLSQuery(domain, server, time.Duration(timeoutSeconds)*time.Second)
	case "DOH":
		resp, err = dnsHTTPSQuery(domain, server, time.Duration(timeoutSeconds)*time.Second)
	default:
		err = fmt.Errorf("unsupported DNS transport: %s", transport)
	}
	result := map[string]any{
		"mode": "DNS", "domain": domain, "transport": transport, "resolver": server,
		"latency_ms": time.Since(start).Milliseconds(), "ok": err == nil,
	}
	if err != nil {
		result["error"] = err.Error()
		return mustJSON(result)
	}
	result["bytes_received"] = len(resp)
	if len(resp) >= 12 {
		result["rcode"] = int(resp[3] & 0x0f)
		result["answers"] = int(binary.BigEndian.Uint16(resp[6:8]))
		result["authority_records"] = int(binary.BigEndian.Uint16(resp[8:10]))
		result["additional_records"] = int(binary.BigEndian.Uint16(resp[10:12]))
		result["answer_ips"] = dnsARecords(resp)
	}
	return mustJSON(result)
}

// dnsARecords extracts IPv4 A answers from a DNS wire response. It is deliberately
// small and bounded because the mobile response checker only needs diagnostic IPs.
func dnsARecords(msg []byte) []string {
	if len(msg) < 12 {
		return nil
	}
	qd := int(binary.BigEndian.Uint16(msg[4:6]))
	an := int(binary.BigEndian.Uint16(msg[6:8]))
	off := 12
	skipName := func(p int) (int, bool) {
		seen := 0
		for p < len(msg) && seen < 128 {
			n := int(msg[p])
			if n == 0 {
				return p + 1, true
			}
			if n&0xc0 == 0xc0 {
				if p+1 >= len(msg) {
					return 0, false
				}
				return p + 2, true
			}
			if n > 63 || p+1+n > len(msg) {
				return 0, false
			}
			p += n + 1
			seen++
		}
		return 0, false
	}
	var ok bool
	for i := 0; i < qd; i++ {
		off, ok = skipName(off)
		if !ok || off+4 > len(msg) {
			return nil
		}
		off += 4
	}
	out := make([]string, 0, an)
	seenIPs := map[string]bool{}
	for i := 0; i < an && off < len(msg); i++ {
		var next int
		next, ok = skipName(off)
		if !ok || next+10 > len(msg) {
			break
		}
		typ := binary.BigEndian.Uint16(msg[next : next+2])
		class := binary.BigEndian.Uint16(msg[next+2 : next+4])
		rdlen := int(binary.BigEndian.Uint16(msg[next+8 : next+10]))
		dataStart := next + 10
		if rdlen < 0 || dataStart+rdlen > len(msg) {
			break
		}
		if typ == 1 && class == 1 && rdlen == 4 {
			ip := net.IP(msg[dataStart : dataStart+4]).String()
			if !seenIPs[ip] {
				seenIPs[ip] = true
				out = append(out, ip)
			}
		}
		off = dataStart + rdlen
	}
	return out
}

func dnsWireQuery(domain, transport, server string, timeout time.Duration) ([]byte, error) {
	if server == "system" {
		server = "1.1.1.1:53"
	}
	if !strings.Contains(server, ":") {
		server = net.JoinHostPort(server, "53")
	}
	query := buildDNSQuery(domain)
	if transport == "UDP" {
		conn, err := net.DialTimeout("udp", server, timeout)
		if err != nil {
			return nil, err
		}
		defer conn.Close()
		if _, err = conn.Write(query); err != nil {
			return nil, err
		}
		_ = conn.SetReadDeadline(time.Now().Add(timeout))
		buf := make([]byte, 4096)
		n, err := conn.Read(buf)
		return buf[:n], err
	}
	conn, err := net.DialTimeout("tcp", server, timeout)
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	frame := make([]byte, 2+len(query))
	binary.BigEndian.PutUint16(frame, uint16(len(query)))
	copy(frame[2:], query)
	if _, err = conn.Write(frame); err != nil {
		return nil, err
	}
	_ = conn.SetReadDeadline(time.Now().Add(timeout))
	var nbuf [2]byte
	if _, err = io.ReadFull(conn, nbuf[:]); err != nil {
		return nil, err
	}
	n := int(binary.BigEndian.Uint16(nbuf[:]))
	if n <= 0 || n > 65535 {
		return nil, fmt.Errorf("invalid DNS TCP response length")
	}
	buf := make([]byte, n)
	_, err = io.ReadFull(conn, buf)
	return buf, err
}

func dnsTLSQuery(domain, server string, timeout time.Duration) ([]byte, error) {
	if server == "" || server == "system" {
		server = "1.1.1.1:853"
	}
	if !strings.Contains(server, ":") {
		server = net.JoinHostPort(server, "853")
	}
	host, _, _ := net.SplitHostPort(server)
	conn, err := tls.DialWithDialer(&net.Dialer{Timeout: timeout}, "tcp", server, &tls.Config{ServerName: host, MinVersion: tls.VersionTLS12})
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	query := buildDNSQuery(domain)
	frame := make([]byte, 2+len(query))
	binary.BigEndian.PutUint16(frame, uint16(len(query)))
	copy(frame[2:], query)
	if _, err = conn.Write(frame); err != nil {
		return nil, err
	}
	_ = conn.SetReadDeadline(time.Now().Add(timeout))
	var nbuf [2]byte
	if _, err = io.ReadFull(conn, nbuf[:]); err != nil {
		return nil, err
	}
	n := int(binary.BigEndian.Uint16(nbuf[:]))
	buf := make([]byte, n)
	_, err = io.ReadFull(conn, buf)
	return buf, err
}

func dnsHTTPSQuery(domain, server string, timeout time.Duration) ([]byte, error) {
	if server == "" || server == "system" {
		server = "https://cloudflare-dns.com/dns-query"
	}
	if !strings.Contains(server, "://") {
		server = "https://" + server + "/dns-query"
	}
	u, err := url.Parse(server)
	if err != nil {
		return nil, err
	}
	query := buildDNSQuery(domain)
	req, err := http.NewRequest(http.MethodPost, u.String(), bytes.NewReader(query))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")
	client := &http.Client{Timeout: timeout}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("DoH HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 64<<10))
}

func buildDNSQuery(domain string) []byte {
	id := uint16(time.Now().UnixNano())
	b := make([]byte, 12)
	binary.BigEndian.PutUint16(b[0:2], id)
	binary.BigEndian.PutUint16(b[2:4], 0x0100)
	binary.BigEndian.PutUint16(b[4:6], 1)
	for _, label := range strings.Split(strings.TrimSuffix(domain, "."), ".") {
		if len(label) > 63 {
			continue
		}
		b = append(b, byte(len(label)))
		b = append(b, label...)
	}
	b = append(b, 0, 0, 1, 0, 1)
	return b
}

func errorString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

func splitNonEmptyLines(s string) []string {
	var out []string
	for _, line := range strings.Split(strings.ReplaceAll(s, "\r\n", "\n"), "\n") {
		if v := strings.TrimSpace(line); v != "" {
			out = append(out, v)
		}
	}
	return out
}

func cloneHeaders(h http.Header) map[string][]string {
	out := make(map[string][]string, len(h))
	for k, values := range h {
		out[k] = append([]string(nil), values...)
	}
	return out
}

func sanitizeBody(data []byte) string {
	s := string(bytes.ToValidUTF8(data, []byte("�")))
	s = strings.ReplaceAll(s, "\x00", "")
	if len(s) > 65536 {
		s = s[:65536]
	}
	return s
}

func htmlTitle(body string) string {
	l := strings.ToLower(body)
	a := strings.Index(l, "<title")
	if a < 0 {
		return ""
	}
	b := strings.Index(l[a:], ">")
	if b < 0 {
		return ""
	}
	b += a + 1
	e := strings.Index(l[b:], "</title>")
	if e < 0 {
		return ""
	}
	return strings.TrimSpace(stripTags(body[b : b+e]))
}

func stripTags(s string) string {
	var out strings.Builder
	in := false
	for _, r := range s {
		if r == '<' {
			in = true
			continue
		}
		if r == '>' {
			in = false
			continue
		}
		if !in {
			out.WriteRune(r)
		}
	}
	return strings.Join(strings.Fields(out.String()), " ")
}

func fingerprintTechnologies(h http.Header, body string) []string {
	var out []string
	l := strings.ToLower(body)
	server := strings.ToLower(h.Get("Server"))
	powered := strings.ToLower(h.Get("X-Powered-By"))
	checks := []struct{ name, token string }{
		{"Cloudflare", "cf-ray"}, {"nginx", "nginx"}, {"Apache", "apache"},
		{"PHP", "php"}, {"WordPress", "wp-content"}, {"WordPress", "wordpress"},
		{"Next.js", "__next"}, {"React", "react"}, {"Vue", "vue"},
		{"Laravel", "laravel"}, {"ASP.NET", "asp.net"}, {"Express", "express"},
	}
	for _, c := range checks {
		if strings.Contains(server, strings.ToLower(c.name)) || strings.Contains(paidHeaderValue(h), c.token) || strings.Contains(l, c.token) {
			if !containsString(out, c.name) {
				out = append(out, c.name)
			}
		}
	}
	if powered != "" && !containsString(out, powered) {
		out = append(out, powered)
	}
	return out
}

func paidHeaderValue(h http.Header) string {
	var b strings.Builder
	for k, v := range h {
		b.WriteString(strings.ToLower(k))
		for _, x := range v {
			b.WriteString(strings.ToLower(x))
		}
	}
	return b.String()
}

func securityHeaderNames(h http.Header) []string {
	names := []string{"Strict-Transport-Security", "Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy", "Permissions-Policy"}
	var out []string
	for _, n := range names {
		if h.Get(n) != "" {
			out = append(out, n)
		}
	}
	return out
}

func tlsCipherName(id uint16) string {
	switch id {
	case tls.TLS_AES_128_GCM_SHA256:
		return "TLS_AES_128_GCM_SHA256"
	case tls.TLS_AES_256_GCM_SHA384:
		return "TLS_AES_256_GCM_SHA384"
	case tls.TLS_CHACHA20_POLY1305_SHA256:
		return "TLS_CHACHA20_POLY1305_SHA256"
	case tls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:
		return "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
	case tls.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:
		return "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
	default:
		return fmt.Sprintf("0x%04x", id)
	}
}

func containsString(in []string, s string) bool {
	for _, x := range in {
		if x == s {
			return true
		}
	}
	return false
}

// Keep bufio imported by old gomobile-generated code paths and for compatibility
// with downstream build tooling that may reference it through source scanning.
var _ = bufio.ErrInvalidUnreadByte
