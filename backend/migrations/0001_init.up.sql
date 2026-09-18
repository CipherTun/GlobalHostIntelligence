-- Global Host Intelligence — initial schema (Phase 2 baseline)
-- Country classification is deliberately multi-signal: see
-- domain_country_signals, not a single country column on domains.

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

CREATE TABLE countries (
    code            CHAR(2) PRIMARY KEY,      -- ISO 3166-1 alpha-2
    name            TEXT NOT NULL,
    flag_emoji      TEXT
);

CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    country_code    CHAR(2) REFERENCES countries(code),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE asns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asn_number      INTEGER NOT NULL UNIQUE,
    name            TEXT,
    organization_id UUID REFERENCES organizations(id),
    country_code    CHAR(2) REFERENCES countries(code),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ips (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address         INET NOT NULL UNIQUE,
    asn_id          UUID REFERENCES asns(id),
    geo_country_code CHAR(2) REFERENCES countries(code),
    geo_city        TEXT,
    is_cdn_edge     BOOLEAN NOT NULL DEFAULT false,
    cdn_id          UUID, -- FK added after cdns table exists
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cdns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE, -- e.g. "Cloudflare", "Fastly"
    detection_method TEXT
);

ALTER TABLE ips
    ADD CONSTRAINT ips_cdn_id_fkey FOREIGN KEY (cdn_id) REFERENCES cdns(id);

CREATE TABLE certificates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sha256_fingerprint  TEXT NOT NULL UNIQUE,
    subject_cn          TEXT,
    subject_org         TEXT,
    subject_org_country CHAR(2) REFERENCES countries(code),
    issuer               TEXT,
    not_before          TIMESTAMPTZ,
    not_after           TIMESTAMPTZ,
    san_domains         TEXT[] NOT NULL DEFAULT '{}',
    source_ct_log       TEXT,
    first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE domains (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fqdn                TEXT NOT NULL UNIQUE,
    tld                 TEXT NOT NULL,
    registrable_domain  TEXT NOT NULL,
    parent_domain_id    UUID REFERENCES domains(id), -- subdomain relationship
    primary_ip_id       UUID REFERENCES ips(id),
    current_certificate_id UUID REFERENCES certificates(id),
    http_status         INTEGER,
    http_server_header   TEXT,
    tls_version          TEXT,
    discovered_via       TEXT,           -- source name at first discovery
    discovered_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_domains_registrable_domain ON domains(registrable_domain);
CREATE INDEX idx_domains_tld ON domains(tld);
CREATE INDEX idx_domains_primary_ip_id ON domains(primary_ip_id);

-- Multi-signal country classification: one row per signal per domain,
-- so the UI can render "South Africa — 94%, United Kingdom — 4%" instead
-- of a single unquestionable country field.
CREATE TABLE domain_country_signals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain_id       UUID NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    signal_type     TEXT NOT NULL CHECK (signal_type IN (
                        'tld', 'ip_geo', 'asn', 'organization',
                        'rdap', 'certificate_org', 'nameserver')),
    country_code    CHAR(2) REFERENCES countries(code),
    confidence      NUMERIC(5,2) NOT NULL CHECK (confidence >= 0 AND confidence <= 100),
    evidence        TEXT,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (domain_id, signal_type)
);

CREATE TABLE dns_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain_id       UUID NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    record_type     TEXT NOT NULL, -- A, AAAA, CNAME, MX, NS, TXT
    value           TEXT NOT NULL,
    ttl_seconds     INTEGER,
    observed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dns_records_domain_id ON dns_records(domain_id);

CREATE TABLE relationships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain_id           UUID NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    related_domain_id   UUID NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    relationship_type   TEXT NOT NULL CHECK (relationship_type IN (
                            'shared_ip', 'shared_asn', 'shared_certificate_san',
                            'shared_nameserver', 'subdomain_of')),
    evidence            TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (domain_id, related_domain_id, relationship_type)
);
CREATE INDEX idx_relationships_domain_id ON relationships(domain_id);

CREATE TABLE sources (
    name                TEXT PRIMARY KEY,
    status              TEXT NOT NULL DEFAULT 'ONLINE'
                            CHECK (status IN ('ONLINE', 'DEGRADED', 'OFFLINE')),
    last_success_at     TIMESTAMPTZ,
    last_failure_at     TIMESTAMPTZ,
    last_failure_reason TEXT,
    request_count       BIGINT NOT NULL DEFAULT 0,
    error_count         BIGINT NOT NULL DEFAULT 0,
    latency_millis      BIGINT
);

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type      TEXT NOT NULL CHECK (scope_type IN ('GLOBAL', 'COUNTRY')),
    scope_value     TEXT, -- country code when scope_type = COUNTRY
    status          TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'RUNNING', 'PAUSED', 'STOPPED', 'COMPLETED', 'FAILED')),
    domains_found   INTEGER NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only observation log: drives "date discovered" / "last seen"
-- for any entity without mutating the entity row itself.
CREATE TABLE observations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     TEXT NOT NULL CHECK (entity_type IN ('domain', 'ip', 'asn', 'certificate')),
    entity_id       UUID NOT NULL,
    source_name     TEXT REFERENCES sources(name),
    job_id          UUID REFERENCES jobs(id),
    observed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_observations_entity ON observations(entity_type, entity_id);

CREATE TABLE bookmarks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     TEXT NOT NULL CHECK (entity_type IN ('domain', 'ip', 'asn', 'certificate', 'country')),
    entity_id       TEXT NOT NULL, -- UUID or country code, hence TEXT
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
