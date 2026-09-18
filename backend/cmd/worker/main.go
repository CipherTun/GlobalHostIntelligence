// Command worker runs the crawler: it pulls jobs from the queue and
// drives them through discovery -> normalization -> deduplication ->
// DNS -> IP/ASN enrichment -> country classification -> HTTP/TLS ->
// certificates -> CDN -> relationships -> database -> WebSocket event.
//
// This is the Phase 1 skeleton: it connects to Postgres and Redis and
// confirms it can start, but the pipeline stages themselves (each an
// internal/* package) are implemented in Phase 6 per docs/ARCHITECTURE.md.
package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/cache"
	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/config"
	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/database"
)

func main() {
	configPath := flag.String("config", "configs/config.yaml", "path to config YAML")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	db, err := database.Connect(ctx, cfg.Postgres)
	if err != nil {
		log.Fatalf("postgres: %v", err)
	}
	defer db.Close()

	rdb, err := cache.Connect(ctx, cfg.Redis)
	if err != nil {
		log.Fatalf("redis: %v", err)
	}
	defer rdb.Close()

	log.Printf("worker: connected, pool size=%d (pipeline stages land in Phase 6)", cfg.Crawler.WorkerPoolSize)

	<-ctx.Done()
	log.Println("worker: shutting down")
}
