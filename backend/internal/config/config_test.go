package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// clearEnv blanks every variable Load reads so tests do not depend on the
// environment they run in (CI exports GHI_POSTGRES_DSN / GHI_REDIS_ADDR).
func clearEnv(t *testing.T) {
	t.Helper()
	for _, name := range []string{
		"GHI_POSTGRES_DSN", "GHI_REDIS_ADDR", "GHI_REDIS_PASSWORD",
		"GHI_SERVER_HOST", "GHI_SERVER_PORT", "GHI_ENV",
	} {
		t.Setenv(name, "")
	}
}

func writeConfig(t *testing.T, body string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatalf("write config: %v", err)
	}
	return path
}

const minimalYAML = `
postgres:
  dsn: "postgres://u:p@localhost:5432/db?sslmode=disable"
redis:
  addr: "localhost:6379"
`

func TestLoadFillsDefaults(t *testing.T) {
	clearEnv(t)
	cfg, err := Load(writeConfig(t, minimalYAML))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cfg.Server.Port != 8080 || cfg.Server.Env != "development" {
		t.Fatalf("server defaults = %+v", cfg.Server)
	}
	if cfg.Postgres.MaxConns != 10 {
		t.Fatalf("max_conns = %d", cfg.Postgres.MaxConns)
	}
	if cfg.Crawler.DNSWorkerCount != 16 || cfg.Crawler.HTTPWorkerCount != 8 ||
		cfg.Crawler.TLSWorkerCount != 8 || cfg.Crawler.SourceTimeoutSecs != 30 || cfg.Crawler.WorkerPoolSize != 8 {
		t.Fatalf("crawler defaults = %+v", cfg.Crawler)
	}
}

func TestLoadAppliesEnvOverrides(t *testing.T) {
	clearEnv(t)
	t.Setenv("GHI_POSTGRES_DSN", "postgres://env:env@db:5432/env")
	t.Setenv("GHI_REDIS_ADDR", "redis:6379")
	t.Setenv("GHI_REDIS_PASSWORD", "secret")
	t.Setenv("GHI_SERVER_HOST", "127.0.0.1")
	t.Setenv("GHI_SERVER_PORT", " 9090 ")
	t.Setenv("GHI_ENV", "production")

	cfg, err := Load(writeConfig(t, minimalYAML))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cfg.Postgres.DSN != "postgres://env:env@db:5432/env" || cfg.Redis.Addr != "redis:6379" || cfg.Redis.Password != "secret" {
		t.Fatalf("env overrides not applied: %+v %+v", cfg.Postgres, cfg.Redis)
	}
	if cfg.Server.Host != "127.0.0.1" || cfg.Server.Port != 9090 || cfg.Server.Env != "production" {
		t.Fatalf("server env overrides not applied: %+v", cfg.Server)
	}
}

func TestLoadRequiresPostgresDSN(t *testing.T) {
	clearEnv(t)
	_, err := Load(writeConfig(t, "redis:\n  addr: \"localhost:6379\"\n"))
	if err == nil || !strings.Contains(err.Error(), "postgres.dsn") {
		t.Fatalf("expected missing DSN error, got %v", err)
	}
}

func TestLoadRejectsInvalidPortEnv(t *testing.T) {
	clearEnv(t)
	for _, bad := range []string{"abc", "8080abc", "70000", "-1"} {
		t.Setenv("GHI_SERVER_PORT", bad)
		if _, err := Load(writeConfig(t, minimalYAML)); err == nil {
			t.Fatalf("GHI_SERVER_PORT=%q should be rejected", bad)
		}
	}
}

func TestLoadRejectsMinConnsAboveMax(t *testing.T) {
	clearEnv(t)
	body := minimalYAML + "\npostgres_extra: ignored\n"
	body = strings.Replace(body, "postgres:\n", "postgres:\n  max_conns: 2\n  min_conns: 5\n", 1)
	if _, err := Load(writeConfig(t, body)); err == nil || !strings.Contains(err.Error(), "min_conns") {
		t.Fatalf("expected min_conns error, got %v", err)
	}
}

func TestLoadMissingFile(t *testing.T) {
	clearEnv(t)
	if _, err := Load(filepath.Join(t.TempDir(), "nope.yaml")); err == nil {
		t.Fatal("expected error for missing file")
	}
}
