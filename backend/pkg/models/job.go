package models

import "time"

type JobStatus string

const (
	JobPending   JobStatus = "PENDING"
	JobRunning   JobStatus = "RUNNING"
	JobPaused    JobStatus = "PAUSED"
	JobStopped   JobStatus = "STOPPED"
	JobCompleted JobStatus = "COMPLETED"
	JobFailed    JobStatus = "FAILED"
)

type Job struct {
	ID            string     `json:"id" db:"id"`
	ScopeType     string     `json:"scope_type" db:"scope_type"` // GLOBAL | COUNTRY
	ScopeValue    string     `json:"scope_value,omitempty" db:"scope_value"`
	Status        JobStatus  `json:"status" db:"status"`
	DomainsFound  int        `json:"domains_found" db:"domains_found"`
	StartedAt     *time.Time `json:"started_at,omitempty" db:"started_at"`
	FinishedAt    *time.Time `json:"finished_at,omitempty" db:"finished_at"`
	CreatedAt     time.Time  `json:"created_at" db:"created_at"`
}
