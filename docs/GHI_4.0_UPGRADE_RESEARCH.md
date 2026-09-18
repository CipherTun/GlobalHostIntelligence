# GHI 4.0 Upgrade Research

This upgrade keeps the existing GHI architecture and adds real capabilities to the
existing Go mobile core and Compose application. It does not embed or copy source
code from third-party security tools.

## Research basis

- OWASP MASVS-NETWORK requires secure network communication and appropriate TLS
  configuration. GHI's TLS analyzer therefore reports the negotiated protocol,
  cipher, ALPN and certificate metadata rather than treating a static setting as
  proof of runtime behavior.
- OWASP MASTG's current network guidance distinguishes live negotiated TLS from
  static configuration and covers certificate validation and cleartext traffic.
- OWASP describes Amass as attack-surface management and external asset discovery
  that combines multiple information sources. GHI's investigation engine follows
  the same high-level workflow while retaining GHI's independent implementations.
- ProjectDiscovery's current platform exposes domain-associated discovery and
  asset information through authenticated APIs. GHI optionally integrates its
  current Chaos associated-domain endpoint using the user's own API key.
- SNI Host currently presents TLS/CDN/domain analysis and payload tooling as
  browser-based infrastructure intelligence. GHI uses a similar visual language
  (dark glass surfaces and cyan/blue signals) without copying assets or branding.

## Real features added

### Investigation

A single bounded investigation runs:

- host/HTTP analysis
- DNS analysis
- TLS/certificate analysis
- HTTP security-header analysis
- redirect-chain mapping
- DNS/TCP/TLS/request timing
- technology/CDN fingerprinting
- certificate search

Each section is backed by a real network operation and returns the observed data.

### GHI Agent

The Agent is deliberately described as a local deterministic agent, not as a
fake LLM. It has no API key and no fabricated responses. It routes supported
natural-language requests to actual GHI engines:

- investigate
- discovery
- DNS
- TLS/certificates
- security headers
- redirects
- technology fingerprinting
- public web search

Unsupported requests fall back to a real public search and return source URLs and
snippets rather than inventing an answer.

### Discovery

The existing passive source fan-out is preserved. An optional ProjectDiscovery
Chaos source is added and requires the user's own API key; the key is stored in
local app preferences and is never embedded in the APK.

### UI

The new screens use the existing GHI glass-card component system and the updated
navy/black + cyan/blue palette. Animations are limited to visual feedback and
do not pretend that work is occurring when no work is running.

## Build integrity

- ARM32 (`armeabi-v7a`) and ARM64 (`arm64-v8a`) AAR handling is preserved.
- Existing Go build caching is not removed.
- Release R8/minification remains disabled in `android/app/build.gradle.kts`.
- Production ad implementation is not removed or replaced.
