package mobile

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// RDAPLookup returns registration intelligence for a domain or an IP address
// using the public RDAP bootstrap service (rdap.org), which redirects to the
// authoritative registry. The raw RDAP document is reduced to a stable,
// UI-friendly summary: registrar, status flags, lifecycle events, name
// servers, abuse contact and the flattened entity list.
func RDAPLookup(target string) string {
	start := time.Now()
	target = normalizeToolHost(target)
	if target == "" {
		return mustJSON(map[string]any{"ok": false, "error": "domain or IP address is required"})
	}
	kind, path := "domain", "domain/"+url.PathEscape(strings.ToLower(target))
	if ip := net.ParseIP(target); ip != nil {
		kind, path = "ip", "ip/"+url.PathEscape(ip.String())
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://rdap.org/"+path, nil)
	if err != nil {
		return rdapFailure(target, kind, err.Error(), start)
	}
	req.Header.Set("Accept", "application/rdap+json, application/json")

	resp, err := (&http.Client{Timeout: 15 * time.Second}).Do(req)
	if err != nil {
		return rdapFailure(target, kind, err.Error(), start)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return rdapFailure(target, kind, fmt.Sprintf("RDAP server answered HTTP %d", resp.StatusCode), start)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return rdapFailure(target, kind, err.Error(), start)
	}
	var raw map[string]any
	if err := json.Unmarshal(body, &raw); err != nil {
		return rdapFailure(target, kind, err.Error(), start)
	}

	out := summarizeRDAP(kind, raw)
	out["ok"] = true
	out["target"] = target
	out["kind"] = kind
	out["source"] = "rdap.org"
	out["elapsed_ms"] = time.Since(start).Milliseconds()
	return mustJSON(out)
}

func rdapFailure(target, kind, message string, start time.Time) string {
	return mustJSON(map[string]any{
		"ok":         false,
		"target":     target,
		"kind":       kind,
		"source":     "rdap.org",
		"error":      message,
		"elapsed_ms": time.Since(start).Milliseconds(),
	})
}

// summarizeRDAP reduces a raw RDAP document. It is pure (no I/O) so it can be
// unit-tested without network access.
func summarizeRDAP(kind string, raw map[string]any) map[string]any {
	out := map[string]any{}
	for _, key := range []string{"handle", "ldhName", "name", "type", "country", "startAddress", "endAddress", "ipVersion", "parentHandle", "port43"} {
		if v, ok := raw[key]; ok {
			out[key] = v
		}
	}
	if status, ok := raw["status"].([]any); ok {
		out["status"] = status
	}

	events := []map[string]any{}
	if list, ok := raw["events"].([]any); ok {
		for _, item := range list {
			if e, ok := item.(map[string]any); ok {
				events = append(events, map[string]any{"action": e["eventAction"], "date": e["eventDate"]})
			}
		}
	}
	out["events"] = events

	if kind == "domain" {
		servers := []string{}
		if list, ok := raw["nameservers"].([]any); ok {
			for _, item := range list {
				if n, ok := item.(map[string]any); ok {
					if name, ok := n["ldhName"].(string); ok {
						servers = append(servers, strings.ToLower(name))
					}
				}
			}
		}
		out["nameservers"] = servers
	}

	entities := rdapEntities(raw["entities"])
	out["entities"] = entities
	for _, e := range entities {
		roles, _ := e["roles"].([]string)
		for _, role := range roles {
			switch role {
			case "registrar":
				if _, exists := out["registrar"]; !exists {
					out["registrar"] = e["name"]
				}
			case "abuse":
				if email, _ := e["email"].(string); email != "" {
					out["abuse_email"] = email
				}
			}
		}
	}
	return out
}

// rdapEntities flattens the (possibly nested) RDAP entity tree.
func rdapEntities(v any) []map[string]any {
	out := []map[string]any{}
	list, ok := v.([]any)
	if !ok {
		return out
	}
	for _, item := range list {
		e, ok := item.(map[string]any)
		if !ok {
			continue
		}
		roles := []string{}
		if rs, ok := e["roles"].([]any); ok {
			for _, r := range rs {
				if s, ok := r.(string); ok {
					roles = append(roles, s)
				}
			}
		}
		entry := map[string]any{"roles": roles}
		handle, _ := e["handle"].(string)
		if handle != "" {
			entry["handle"] = handle
		}
		name := rdapVCard(e, "fn")
		if name == "" {
			name = handle
		}
		entry["name"] = name
		if email := rdapVCard(e, "email"); email != "" {
			entry["email"] = email
		}
		out = append(out, entry)
		out = append(out, rdapEntities(e["entities"])...)
	}
	return out
}

// rdapVCard reads one text property from an entity's jCard (vcardArray).
func rdapVCard(entity map[string]any, field string) string {
	card, ok := entity["vcardArray"].([]any)
	if !ok || len(card) < 2 {
		return ""
	}
	props, ok := card[1].([]any)
	if !ok {
		return ""
	}
	for _, p := range props {
		row, ok := p.([]any)
		if !ok || len(row) < 4 {
			continue
		}
		if key, _ := row[0].(string); key == field {
			if val, ok := row[3].(string); ok {
				return val
			}
		}
	}
	return ""
}
