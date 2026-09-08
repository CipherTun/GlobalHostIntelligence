# GlobalHostIntelligence #71 — researched runtime/tool upgrade overlay

Baseline audited: GitHub commit 1e73f4aa456aa52dcd8a1d66b58db93936b79ba9 (Android Actions run #71).

This overlay adds real, bounded network-intelligence tools to the existing Android/Go architecture:
- TLS / SSL Analyzer: live TLS handshake, TLS version, cipher, ALPN, leaf certificate metadata, SHA-256 fingerprint, PEM leaf certificate and PEM peer chain.
- DNS Inspector: A, AAAA, CNAME, MX, NS, TXT, SRV and PTR where applicable.
- Certificate Search: public Certificate Transparency search through ctlogs.dev.
- The UI exposes these as actual tools, not storage/history/non-tool menus.
- Existing configuration routes remain available under Configuration so no existing settings/source functionality is removed.
- New Go functions are package-level string-returning functions so gomobile can bind them using the same bridge architecture already used by the project.

Research basis:
- ProjectDiscovery httpx documents HTTP probing including IP, CNAME, TLS certificate, server, technology, CDN and response-time probes.
- ProjectDiscovery dnsx documents A/AAAA/CNAME/PTR/NS/MX/TXT/SRV/SOA/CAA queries and wildcard handling.
- ctlogs.dev documents public CT domain/subdomain search and certificate detail/PEM extraction.
- RDAP was researched as a next backend tool candidate; it is not silently added in this overlay because the current IP/Domain UI needs a structured RDAP result model first.

Apply the overlay with apply.sh from the repository root. Then run the verification commands in the accompanying command block.
