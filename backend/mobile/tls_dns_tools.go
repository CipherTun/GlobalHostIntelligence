package mobile

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"net"
	"net/url"
	"strings"
	"time"
)

// AnalyzeTLS performs a bounded TLS handshake and extracts the peer
// certificate without sending application data.
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
		&tls.Config{ServerName: serverName, MinVersion: tls.VersionTLS12},
	)
	if err != nil {
		return mustJSON(map[string]any{
			"ok":        false,
			"host":      host,
			"port":      port,
			"error":     err.Error(),
			"elapsed_ms": time.Since(start).Milliseconds(),
		})
	}
	defer conn.Close()

	state := conn.ConnectionState()
	result := map[string]any{
		"ok":                 true,
		"host":               host,
		"port":               port,
		"tls_version":        tlsVersionName(state.Version),
		"cipher_suite":       tls.CipherSuiteName(state.CipherSuite),
		"alpn":               state.NegotiatedProtocol,
		"handshake_complete": state.HandshakeComplete,
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
		result["certificate_chain_pem"] = chainPEM.String()
		result["certificate"] = map[string]any{
			"subject":               leaf.Subject.String(),
			"issuer":                leaf.Issuer.String(),
			"common_name":           leaf.Subject.CommonName,
			"dns_names":             leaf.DNSNames,
			"ip_addresses":          certificateIPs(leaf.IPAddresses),
			"serial":                leaf.SerialNumber.Text(16),
			"not_before":            leaf.NotBefore.UTC().Format(time.RFC3339),
			"not_after":             leaf.NotAfter.UTC().Format(time.RFC3339),
			"signature_algorithm":   leaf.SignatureAlgorithm.String(),
			"public_key_algorithm":  leaf.PublicKeyAlgorithm.String(),
			"is_ca":                 leaf.IsCA,
			"sha256":                strings.ToUpper(hex.EncodeToString(sum[:])),
			"valid_now":             !time.Now().Before(leaf.NotBefore) && !time.Now().After(leaf.NotAfter),
		}
		result["certificate_pem"] = string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: leaf.Raw}))
	}
	return mustJSON(result)
}

func certificateIPs(ips []net.IP) []string {
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		out = append(out, ip.String())
	}
	return out
}

// InspectDNS queries common record types using the platform resolver.
func InspectDNS(name string) string {
	start := time.Now()
	name = normalizeToolHost(name)
	if name == "" {
		return mustJSON(map[string]any{"ok": false, "error": "hostname is required"})
	}
	resolver := net.DefaultResolver
	records := map[string]any{}

	if ips, err := resolver.LookupIPAddr(context.Background(), name); err == nil {
		a, aaaa := []string{}, []string{}
		for _, ip := range ips {
			if ip.IP.To4() != nil {
				a = append(a, ip.IP.String())
			} else {
				aaaa = append(aaaa, ip.IP.String())
			}
		}
		records["A"] = a
		records["AAAA"] = aaaa
	} else {
		records["A_error"] = err.Error()
	}
	if cname, err := resolver.LookupCNAME(context.Background(), name); err == nil {
		records["CNAME"] = strings.TrimSuffix(cname, ".")
	}
	if mx, err := resolver.LookupMX(context.Background(), name); err == nil {
		values := make([]map[string]any, 0, len(mx))
		for _, item := range mx {
			values = append(values, map[string]any{
				"host":     strings.TrimSuffix(item.Host, "."),
				"priority": item.Pref,
			})
		}
		records["MX"] = values
	}
	if ns, err := resolver.LookupNS(context.Background(), name); err == nil {
		values := make([]string, 0, len(ns))
		for _, item := range ns {
			values = append(values, strings.TrimSuffix(item.Host, "."))
		}
		records["NS"] = values
	}
	if txt, err := resolver.LookupTXT(context.Background(), name); err == nil {
		records["TXT"] = txt
	}
	if _, srv, err := resolver.LookupSRV(context.Background(), "sip", "tcp", name); err == nil {
		values := make([]map[string]any, 0, len(srv))
		for _, item := range srv {
			values = append(values, map[string]any{
				"host":     strings.TrimSuffix(item.Target, "."),
				"port":     item.Port,
				"priority": item.Priority,
				"weight":   item.Weight,
			})
		}
		records["SRV"] = values
	}
	if ip := net.ParseIP(name); ip != nil {
		if names, err := resolver.LookupAddr(context.Background(), name); err == nil {
			records["PTR"] = names
		}
	}

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

