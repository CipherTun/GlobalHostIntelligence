// Package api assembles the HTTP surface of the Go API server. Route
// registration lives here; handler logic for each resource lives in its
// own file (health.go, and one file per resource added in Phase 3:
// countries.go, domains.go, ips.go, asns.go, certificates.go, jobs.go,
// sources.go, search.go, bookmarks.go, exports.go).
package api

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

// Deps holds everything a handler might need. Passed in explicitly at
// router construction time rather than via package globals, so handlers
// stay testable with fakes.
type Deps struct {
	DB      *pgxpool.Pool
	Redis   *redis.Client
	Version string
}

// NewRouter builds the full chi router for the API server.
func NewRouter(deps Deps) http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(30 * time.Second))

	r.Route("/v1", func(r chi.Router) {
		r.Get("/health", handleHealth(deps))
		r.Get("/version", handleVersion(deps))

		// Resource routes are added here phase by phase (Phase 3):
		// r.Route("/countries", ...)
		// r.Route("/domains", ...)
		// r.Route("/ips", ...)
		// r.Route("/asns", ...)
		// r.Route("/certificates", ...)
		// r.Route("/jobs", ...)
		// r.Route("/sources", ...)
		// r.Get("/search", ...)
		// r.Route("/bookmarks", ...)
		// r.Route("/exports", ...)
		// r.Get("/ws/jobs/{id}", ...)
	})

	return r
}
