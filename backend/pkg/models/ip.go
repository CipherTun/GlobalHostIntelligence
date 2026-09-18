package models

import "time"

type IP struct {
	ID             string    `json:"id" db:"id"`
	Address        string    `json:"address" db:"address"`
	ASNID          string    `json:"asn_id,omitempty" db:"asn_id"`
	GeoCountryCode string    `json:"geo_country_code,omitempty" db:"geo_country_code"`
	GeoCity        string    `json:"geo_city,omitempty" db:"geo_city"`
	IsCDNEdge      bool      `json:"is_cdn_edge" db:"is_cdn_edge"`
	CDNID          string    `json:"cdn_id,omitempty" db:"cdn_id"`
	FirstSeenAt    time.Time `json:"first_seen_at" db:"first_seen_at"`
	LastSeenAt     time.Time `json:"last_seen_at" db:"last_seen_at"`
}
