# GlobalHostIntelligence 2.3.0 — focused full-app upgrade

This revision intentionally has exactly five top-level app areas:
1. Discover
2. IP / Domain
3. Response Checker
4. Automatic Payload Generator
5. Settings

Removed from navigation/app packaging: History, Search, Graph, Bookmarks, Exports and the redundant standalone Hosts/Domains/IPs/Countries/ASNs/Certificates menus. Those concepts are not exposed as separate duplicate workspaces.

Discovery uses concurrent source fan-out and concurrent HTTP/HTTPS 2xx–3xx validation, with all ISO countries available in the country selector. Settings control source selection, source parallelism, validation concurrency, timeout, user-agent and interface behavior.
