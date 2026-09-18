package api

import (
	"context"
	"encoding/json"
	"net/http"
	"time"
)

type healthResponse struct {
	Status   string            `json:"status"`
	Checks   map[string]string `json:"checks"`
	CheckedAt string           `json:"checked_at"`
}

// handleHealth pings Postgres and Redis so a green response means the
// dependencies the crawler actually needs are reachable, not just that
// the HTTP process is alive.
func handleHealth(deps Deps) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
		defer cancel()

		checks := map[string]string{}
		overall := "ok"

		if err := deps.DB.Ping(ctx); err != nil {
			checks["postgres"] = "unreachable: " + err.Error()
			overall = "degraded"
		} else {
			checks["postgres"] = "ok"
		}

		if err := deps.Redis.Ping(ctx).Err(); err != nil {
			checks["redis"] = "unreachable: " + err.Error()
			overall = "degraded"
		} else {
			checks["redis"] = "ok"
		}

		resp := healthResponse{
			Status:    overall,
			Checks:    checks,
			CheckedAt: time.Now().UTC().Format(time.RFC3339),
		}

		w.Header().Set("Content-Type", "application/json")
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
