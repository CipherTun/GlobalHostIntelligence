package models

type CDN struct {
	ID               string `json:"id" db:"id"`
	Name             string `json:"name" db:"name"`
	DetectionMethod  string `json:"detection_method,omitempty" db:"detection_method"`
}
