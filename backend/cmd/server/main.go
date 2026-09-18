// Command server runs the Global Host Intelligence API server: REST API,
// WebSocket live events, and job orchestration. The crawler itself runs
// in the separate `worker` binary so the two scale independently.
package main

import (
	"context"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/api"
	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/cache"
	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/config"
	"github.com/CipherTun/GlobalHostIntelligence/backend/internal/database"
)

// version is set at build time via -ldflags "-X main.version=...".
var version = "dev"

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

	router := api.NewRouter(api.Deps{
		DB:      db,
		Redis:   rdb,
		Version: version,
	})

	port := cfg.Server.Port
	if port == 0 {
		port = 8080
	}

	srv := &http.Server{
		Addr:         cfg.Server.Host + ":" + strconv.Itoa(port),
		Handler:      router,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("server: listening on %s (env=%s, version=%s)", srv.Addr, cfg.Server.Env, version)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("server: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("server: shutting down")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("server: forced shutdown: %v", err)
	}
}
