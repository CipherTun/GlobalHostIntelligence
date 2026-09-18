package models

import "time"

type Certificate struct {
	ID                 string     `json:"id" db:"id"`
	SHA256Fingerprint  string     `json:"sha256_fingerprint" db:"sha256_fingerprint"`
	SubjectCN          string     `json:"subject_cn,omitempty" db:"subject_cn"`
	SubjectOrg         string     `json:"subject_org,omitempty" db:"subject_org"`
	SubjectOrgCountry  string     `json:"subject_org_country,omitempty" db:"subject_org_country"`
	Issuer             string     `json:"issuer,omitempty" db:"issuer"`
	NotBefore          *time.Time `json:"not_before,omitempty" db:"not_before"`
	NotAfter           *time.Time `json:"not_after,omitempty" db:"not_after"`
	SANDomains         []string   `json:"san_domains" db:"san_domains"`
	SourceCTLog        string     `json:"source_ct_log,omitempty" db:"source_ct_log"`
	FirstSeenAt        time.Time  `json:"first_seen_at" db:"first_seen_at"`
}
