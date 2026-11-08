package api

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"time"
)

type healthResponse struct {
	Status    string            `json:"status"`
	Checks    map[string]string `json:"checks"`
	CheckedAt string            `json:"checked_at"`
}

// handleHealth pings Postgres and Redis so a green response means the
// dependencies the crawler actually needs are reachable, not just that
// the HTTP process is alive.
//
// The endpoint is unauthenticated, so it only reports ok / unreachable /
// not configured. The underlying error (which can contain hostnames and ports
// of internal services) goes to the server log instead of the response body.
func handleHealth(deps Deps) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
		defer cancel()

		checks := map[string]string{}
		overall := "ok"

		switch {
		case deps.DB == nil:
			checks["postgres"] = "not configured"
			overall = "degraded"
		default:
			if err := deps.DB.Ping(ctx); err != nil {
				log.Printf("health: postgres ping failed: %v", err)
				checks["postgres"] = "unreachable"
				overall = "degraded"
			} else {
				checks["postgres"] = "ok"
			}
		}

		switch {
		case deps.Redis == nil:
			checks["redis"] = "not configured"
			overall = "degraded"
		default:
			if err := deps.Redis.Ping(ctx).Err(); err != nil {
				log.Printf("health: redis ping failed: %v", err)
				checks["redis"] = "unreachable"
				overall = "degraded"
			} else {
				checks["redis"] = "ok"
			}
		}

		resp := healthResponse{
			Status:    overall,
			Checks:    checks,
			CheckedAt: time.Now().UTC().Format(time.RFC3339),
		}

		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Cache-Control", "no-store")
		if overall != "ok" {
			w.WriteHeader(http.StatusServiceUnavailable)
		}
		_ = json.NewEncoder(w).Encode(resp)
	}
}

func handleVersion(deps Deps) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{
			"version": deps.Version,
		})
	}
}
