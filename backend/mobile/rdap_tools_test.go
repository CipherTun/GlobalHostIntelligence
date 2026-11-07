package mobile

import "testing"

func TestSummarizeRDAPDomain(t *testing.T) {
	raw := map[string]any{
		"ldhName": "example.com",
		"status":  []any{"client transfer prohibited"},
		"events": []any{
			map[string]any{"eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z"},
		},
		"nameservers": []any{
			map[string]any{"ldhName": "A.IANA-SERVERS.NET"},
		},
		"entities": []any{
			map[string]any{
				"handle": "376",
				"roles":  []any{"registrar"},
				"vcardArray": []any{"vcard", []any{
					[]any{"fn", map[string]any{}, "text", "Example Registrar Inc."},
				}},
				"entities": []any{
					map[string]any{
						"roles": []any{"abuse"},
						"vcardArray": []any{"vcard", []any{
							[]any{"email", map[string]any{}, "text", "abuse@example.test"},
						}},
					},
				},
			},
		},
	}
	out := summarizeRDAP("domain", raw)
	if got := out["registrar"]; got != "Example Registrar Inc." {
		t.Fatalf("registrar = %v", got)
	}
	if got := out["abuse_email"]; got != "abuse@example.test" {
		t.Fatalf("abuse_email = %v", got)
	}
	servers, _ := out["nameservers"].([]string)
	if len(servers) != 1 || servers[0] != "a.iana-servers.net" {
		t.Fatalf("nameservers = %v", out["nameservers"])
	}
	if events, _ := out["events"].([]map[string]any); len(events) != 1 {
		t.Fatalf("events = %v", out["events"])
	}
}

func TestSummarizeRDAPTolerantOfGarbage(t *testing.T) {
	out := summarizeRDAP("ip", map[string]any{"entities": "nope", "events": 3})
	if _, ok := out["entities"]; !ok {
		t.Fatal("entities key must always be present")
	}
}
