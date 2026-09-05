// Package discovery defines the pluggable source-adapter contract. Every
// discovery source (Certificate Transparency, RDAP, a user-supplied
// domain list, future additions) implements Source. The crawler never
// depends on a concrete source — only on this interface — so a source
// going down degrades gracefully instead of breaking the pipeline.
package discovery

import (
	"context"
	"time"
)

// Status is the health of a source, tracked per the spec's SOURCE HEALTH
// requirement (name, status, last success/failure, request/error counts,
// rate limit, latency).
type Status string

const (
	StatusOnline   Status = "ONLINE"
	StatusDegraded Status = "DEGRADED"
	StatusOffline  Status = "OFFLINE"
)

// Scope describes what a discovery job is looking for: either GLOBAL or
// a specific ISO country.
type Scope struct {
	Global      bool
	CountryCode string // ISO 3166-1 alpha-2, empty when Global is true
}

// RawRecord is what a source adapter emits before normalization —
// intentionally loose (a raw domain/host string plus provenance) because
// normalization is a separate, source-agnostic pipeline stage.
type RawRecord struct {
	Value      string // raw domain or host string as the source returned it
	SourceName string
	ObservedAt time.Time
}

// Source is the contract every discovery adapter implements.
type Source interface {
	// Name is a stable identifier used in the sources table and UI
	// (e.g. "certificate_transparency", "rdap", "user_domain_list").
	Name() string

	// Discover starts producing records for the given scope. The
	// returned channel is closed when discovery completes or ctx is
	// canceled. Implementations must respect RateLimit between requests.
	Discover(ctx context.Context, scope Scope) (<-chan RawRecord, error)

	// HealthCheck performs a cheap connectivity/availability check used
	// to populate source status without running a full discovery pass.
	HealthCheck(ctx context.Context) error

	// RateLimit returns the minimum delay to wait between outbound
	// requests to this source.
	RateLimit() time.Duration

	// Close releases any resources (connections, file handles) held by
	// the adapter.
	Close() error
}

// HealthRecord is what gets persisted to the sources table after each
// HealthCheck or Discover run.
type HealthRecord struct {
	Name              string
	Status            Status
	LastSuccessAt     *time.Time
	LastFailureAt     *time.Time
	LastFailureReason string
	RequestCount      int64
	ErrorCount        int64
	LatencyMillis     int64
}
