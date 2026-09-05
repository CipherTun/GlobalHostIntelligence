package models

import "time"

// Domain is the central entity. Country is deliberately NOT a single
// field here — see CountrySignal — because a domain's org, hosting
// infrastructure, and CDN edge can each sit in a different country.
type Domain struct {
	ID                     string    `json:"id" db:"id"`
	FQDN                   string    `json:"fqdn" db:"fqdn"`
	TLD                    string    `json:"tld" db:"tld"`
	RegistrableDomain      string    `json:"registrable_domain" db:"registrable_domain"`
	ParentDomainID         string    `json:"parent_domain_id,omitempty" db:"parent_domain_id"`
	PrimaryIPID            string    `json:"primary_ip_id,omitempty" db:"primary_ip_id"`
	CurrentCertificateID   string    `json:"current_certificate_id,omitempty" db:"current_certificate_id"`
	HTTPStatus             *int      `json:"http_status,omitempty" db:"http_status"`
	HTTPServerHeader       string    `json:"http_server_header,omitempty" db:"http_server_header"`
	TLSVersion             string    `json:"tls_version,omitempty" db:"tls_version"`
	DiscoveredVia          string    `json:"discovered_via,omitempty" db:"discovered_via"`
	DiscoveredAt           time.Time `json:"discovered_at" db:"discovered_at"`
	LastSeenAt             time.Time `json:"last_seen_at" db:"last_seen_at"`
}

// CountrySignal is one row of evidence toward a domain's country
// classification (tld / ip_geo / asn / organization / rdap /
// certificate_org / nameserver), each with its own confidence.
type CountrySignal struct {
	DomainID    string  `json:"domain_id" db:"domain_id"`
	SignalType  string  `json:"signal_type" db:"signal_type"`
	CountryCode string  `json:"country_code" db:"country_code"`
	Confidence  float64 `json:"confidence" db:"confidence"`
	Evidence    string  `json:"evidence,omitempty" db:"evidence"`
}

type DNSRecord struct {
	ID         string    `json:"id" db:"id"`
	DomainID   string    `json:"domain_id" db:"domain_id"`
	RecordType string    `json:"record_type" db:"record_type"`
	Value      string    `json:"value" db:"value"`
	TTLSeconds *int      `json:"ttl_seconds,omitempty" db:"ttl_seconds"`
	ObservedAt time.Time `json:"observed_at" db:"observed_at"`
}

type Relationship struct {
	ID                string    `json:"id" db:"id"`
	DomainID          string    `json:"domain_id" db:"domain_id"`
	RelatedDomainID   string    `json:"related_domain_id" db:"related_domain_id"`
	RelationshipType  string    `json:"relationship_type" db:"relationship_type"`
	Evidence          string    `json:"evidence,omitempty" db:"evidence"`
	CreatedAt         time.Time `json:"created_at" db:"created_at"`
}
