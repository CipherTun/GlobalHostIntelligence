package models

import "time"

type ASN struct {
	ID             string    `json:"id" db:"id"`
	ASNNumber      int       `json:"asn_number" db:"asn_number"`
	Name           string    `json:"name,omitempty" db:"name"`
	OrganizationID string    `json:"organization_id,omitempty" db:"organization_id"`
	CountryCode    string    `json:"country_code,omitempty" db:"country_code"`
	CreatedAt      time.Time `json:"created_at" db:"created_at"`
}
