package mobile

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"net"
	"net/url"
	"strings"
	"sync"
	"time"
)

// AnalyzeTLS performs a bounded TLS handshake and extracts the peer
// certificate without sending application data.
//
// The handshake deliberately does not abort on an untrusted chain: expired,
// self-signed or mismatched certificates are exactly what an investigator
// wants to inspect. Trust is evaluated explicitly afterwards and reported in
// the "trusted" / "trust_error" fields. ALPN is offered (h2, http/1.1) so the
// negotiated protocol is actually populated.
func AnalyzeTLS(host string) string {
	start := time.Now()
	host = normalizeToolHost(host)
	if host == "" {
		return mustJSON(map[string]any{"ok": false, "error": "host is required"})
	}

	port := "443"
	serverName := host
	if h, p, err := net.SplitHostPort(host); err == nil {
		host, port, serverName = h, p, h
	}
	if net.ParseIP(serverName) != nil {
		serverName = ""
	}

	conn, err := tls.DialWithDialer(
		&net.Dialer{Timeout: 8 * time.Second},
		"tcp",
		net.JoinHostPort(host, port),
		&tls.Config{
			ServerName:         serverName,
			InsecureSkipVerify: true, // inspection tool: trust is verified manually below
			MinVersion:         tls.VersionTLS10,
			NextProtos:         []string{"h2", "http/1.1"},
		},
	)
	if err != nil {
		return mustJSON(map[string]any{
			"ok":         false,
			"host":       host,
			"port":       port,
			"error":      err.Error(),
			"elapsed_ms": time.Since(start).Milliseconds(),
		})
	}
	defer conn.Close()

	state := conn.ConnectionState()
	trusted, trustErr := verifyPeerChain(state.PeerCertificates, serverName)
	result := map[string]any{
		"ok":                 true,
		"host":               host,
		"port":               port,
		"server_name":        serverName,
		"tls_version":        tlsVersionName(state.Version),
		"legacy_protocol":    state.Version < tls.VersionTLS12,
		"cipher_suite":       tls.CipherSuiteName(state.CipherSuite),
		"alpn":               state.NegotiatedProtocol,
		"handshake_complete": state.HandshakeComplete,
		"trusted":            trusted,
		"trust_error":        trustErr,
		"verified_chains":    len(state.VerifiedChains),
		"peer_certificates":  len(state.PeerCertificates),
		"elapsed_ms":         time.Since(start).Milliseconds(),
	}
	if len(state.PeerCertificates) > 0 {
		leaf := state.PeerCertificates[0]
		sum := sha256.Sum256(leaf.Raw)
		chainPEM := strings.Builder{}
		for _, cert := range state.PeerCertificates {
			chainPEM.Write(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw}))
		}
		now := time.Now()
		result["certificate_chain_pem"] = chainPEM.String()
		result["certificate"] = map[string]any{
			"subject":              leaf.Subject.String(),
			"issuer":               leaf.Issuer.String(),
			"common_name":          leaf.Subject.CommonName,
			"dns_names":            leaf.DNSNames,
			"ip_addresses":         certificateIPs(leaf.IPAddresses),
			"serial":               leaf.SerialNumber.Text(16),
			"not_before":           leaf.NotBefore.UTC().Format(time.RFC3339),
			"not_after":            leaf.NotAfter.UTC().Format(time.RFC3339),
			"days_until_expiry":    int(leaf.NotAfter.Sub(now).Hours() / 24),
			"signature_algorithm":  leaf.SignatureAlgorithm.String(),
			"public_key_algorithm": leaf.PublicKeyAlgorithm.String(),
			"is_ca":                leaf.IsCA,
			"self_signed":          leaf.Subject.String() == leaf.Issuer.String(),
			"sha256":               strings.ToUpper(hex.EncodeToString(sum[:])),
			"valid_now":            !now.Before(leaf.NotBefore) && !now.After(leaf.NotAfter),
		}
		result["certificate_pem"] = string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: leaf.Raw}))
	}
	return mustJSON(result)
}

// verifyPeerChain checks the presented chain against the system roots.
func verifyPeerChain(certs []*x509.Certificate, serverName string) (bool, string) {
	if len(certs) == 0 {
		return false, "no certificate presented"
	}
	opts := x509.VerifyOptions{Intermediates: x509.NewCertPool()}
	if serverName != "" {
		opts.DNSName = serverName
	}
	for _, c := range certs[1:] {
		opts.Intermediates.AddCert(c)
	}
	if _, err := certs[0].Verify(opts); err != nil {
		return false, err.Error()
	}
	return true, ""
}

func certificateIPs(ips []net.IP) []string {
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		out = append(out, ip.String())
	}
	return out
}

// InspectDNS queries common record types using the platform resolver.
//
// All lookups run in parallel under one shared deadline, so a single slow
// record type can no longer stall the whole inspection.
func InspectDNS(name string) string {
	start := time.Now()
	name = normalizeToolHost(name)
	if name == "" {
		return mustJSON(map[string]any{"ok": false, "error": "hostname is required"})
	}
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()

	resolver := net.DefaultResolver
	records := map[string]any{}
	var mu sync.Mutex
	set := func(key string, value any) {
		mu.Lock()
		records[key] = value
		mu.Unlock()
	}
	var wg sync.WaitGroup
	run := func(fn func()) {
		wg.Add(1)
		go func() {
			defer wg.Done()
			fn()
		}()
	}

	run(func() {
		ips, err := resolver.LookupIPAddr(ctx, name)
		if err != nil {
			set("A_error", err.Error())
			return
		}
		a, aaaa := []string{}, []string{}
		for _, ip := range ips {
			if ip.IP.To4() != nil {
				a = append(a, ip.IP.String())
			} else {
				aaaa = append(aaaa, ip.IP.String())
			}
		}
		set("A", a)
		set("AAAA", aaaa)
	})
	run(func() {
		if cname, err := resolver.LookupCNAME(ctx, name); err == nil {
			set("CNAME", strings.TrimSuffix(cname, "."))
		}
	})
	run(func() {
		if mx, err := resolver.LookupMX(ctx, name); err == nil {
			values := make([]map[string]any, 0, len(mx))
			for _, item := range mx {
				values = append(values, map[string]any{
					"host":     strings.TrimSuffix(item.Host, "."),
					"priority": item.Pref,
				})
			}
			set("MX", values)
		}
	})
	run(func() {
		if ns, err := resolver.LookupNS(ctx, name); err == nil {
			values := make([]string, 0, len(ns))
			for _, item := range ns {
				values = append(values, strings.TrimSuffix(item.Host, "."))
			}
			set("NS", values)
		}
	})
	run(func() {
		if txt, err := resolver.LookupTXT(ctx, name); err == nil {
			set("TXT", txt)
		}
	})
	run(func() {
		if txt, err := resolver.LookupTXT(ctx, "_dmarc."+name); err == nil {
			set("DMARC", txt)
		}
	})
	run(func() {
		if _, srv, err := resolver.LookupSRV(ctx, "sip", "tcp", name); err == nil {
			values := make([]map[string]any, 0, len(srv))
			for _, item := range srv {
				values = append(values, map[string]any{
					"host":     strings.TrimSuffix(item.Target, "."),
					"port":     item.Port,
					"priority": item.Priority,
					"weight":   item.Weight,
				})
			}
			set("SRV", values)
		}
	})
	if ip := net.ParseIP(name); ip != nil {
		run(func() {
			if names, err := resolver.LookupAddr(ctx, name); err == nil {
				set("PTR", names)
			}
		})
	}
	wg.Wait()

	return mustJSON(map[string]any{
		"ok":         true,
		"name":       name,
		"records":    records,
		"elapsed_ms": time.Since(start).Milliseconds(),
	})
}

// SearchCertificates queries the public ctlogs.dev Certificate Transparency
// index. The endpoint is deliberately bounded to its first page.
func SearchCertificates(domain string) string {
	start := time.Now()
	domain = normalizeToolHost(domain)
	if domain == "" {
		return mustJSON(map[string]any{"ok": false, "error": "domain is required"})
	}
	client := newHTTPClient()
	data, err := getJSON(client, "https://api.ctlogs.dev/v1/subdomains/"+url.PathEscape(domain), 2<<20)
	if err != nil {
		return mustJSON(map[string]any{
			"ok":         false,
			"domain":     domain,
			"source":     "ctlogs.dev",
			"error":      err.Error(),
			"elapsed_ms": time.Since(start).Milliseconds(),
		})
	}

	var payload struct {
		Rows       []map[string]any `json:"rows"`
		HasNext    bool             `json:"has_next"`
		NextCursor string           `json:"next_cursor"`
	}
	if err := json.Unmarshal(data, &payload); err != nil {
		return mustJSON(map[string]any{
			"ok":     false,
			"domain": domain,
			"source": "ctlogs.dev",
			"error":  err.Error(),
		})
	}
	return mustJSON(map[string]any{
		"ok":          true,
		"domain":      domain,
		"source":      "ctlogs.dev",
		"rows":        payload.Rows,
		"has_next":    payload.HasNext,
		"next_cursor": payload.NextCursor,
		"elapsed_ms":  time.Since(start).Milliseconds(),
	})
}

func normalizeToolHost(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	if u, err := url.Parse(value); err == nil && u.Hostname() != "" {
		value = u.Hostname()
	}
	value = strings.TrimPrefix(value, "//")
	value = strings.TrimSuffix(value, ".")
	if strings.Contains(value, "/") {
		value = strings.SplitN(value, "/", 2)[0]
	}
	return value
}
