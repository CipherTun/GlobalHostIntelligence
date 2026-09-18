# GlobalHostIntelligence — current-main functional UI overlay

BASE COMMIT: 2950831cac2dc4417c23d2a6e2c054173bc5675d
REPOSITORY: https://github.com/CipherTun/GlobalHostIntelligence

This overlay is based on the current GitHub `main` source, not an older project ZIP.

Changes:
- Discover screen: country selector, live results, per-domain Copy, Copy All, selectable domain text.
- Discovery session: default limit 500, supports up to 2000, larger per-source candidate windows, current carrier/country/URLScan/all/custom-ASN fan-out.
- Carrier discovery: checks current RIPEstat ASNs and announced prefixes, expands to 64 ASNs, adds country-scoped URLScan fallback, no TLD filtering.
- Payload generator: HTTP default, `/` default, HTTP/1.1, copy generated request.
- Response/IP tools: real embedded-library operations with copyable output.
- Secondary intelligence screens are functional library-backed operations instead of empty/generic demo screens.
- Bookmarks are persisted locally through the GHI session.
