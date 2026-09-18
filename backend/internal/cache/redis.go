// Package cache owns the Redis client used for queues, response caching,
// rate limiting, and short-lived distributed state (source health
// counters, in-flight job locks).
package cache

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/config"
)

// Connect creates a Redis client and verifies connectivity with PING.
func Connect(ctx context.Context, cfg config.RedisConfig) (*redis.Client, error) {
	client := redis.NewClient(&redis.Options{
		Addr:     cfg.Addr,
		Password: cfg.Password,
		DB:       cfg.DB,
	})

	pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := client.Ping(pingCtx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("cache: ping: %w", err)
	}

	return client, nil
}
