package models

import "time"

type Organization struct {
	ID          string    `json:"id" db:"id"`
	Name        string    `json:"name" db:"name"`
	CountryCode string    `json:"country_code,omitempty" db:"country_code"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
}
