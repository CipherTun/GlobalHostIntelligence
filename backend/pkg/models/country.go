package models

// Country is an ISO 3166-1 alpha-2 country used across classification,
// filtering, and the country explorer.
type Country struct {
	Code      string `json:"code" db:"code"`
	Name      string `json:"name" db:"name"`
	FlagEmoji string `json:"flag_emoji,omitempty" db:"flag_emoji"`
}
