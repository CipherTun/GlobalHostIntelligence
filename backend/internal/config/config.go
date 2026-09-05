// Package config loads server configuration from a YAML file with
// environment-variable overrides. Kept dependency-light and explicit:
// no magic global singleton, config is constructed once in main and
// passed down.
package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// Config is the full application configuration.
type Config struct {
	Server   ServerConfig   `yaml:"server"`
	Postgres PostgresConfig `yaml:"postgres"`
	Redis    RedisConfig    `yaml:"redis"`
	Crawler  CrawlerConfig  `yaml:"crawler"`
}

type ServerConfig struct {
	Host string `yaml:"host"`
	Port int    `yaml:"port"`
	// Env is "development", "staging", or "production". Controls log
	// verbosity and whether debug endpoints are mounted.
	Env string `yaml:"env"`
}

type PostgresConfig struct {
	DSN         string `yaml:"dsn"`
	MaxConns    int32  `yaml:"max_conns"`
	MinConns    int32  `yaml:"min_conns"`
}

type RedisConfig struct {
	Addr     string `yaml:"addr"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
}

type CrawlerConfig struct {
	WorkerPoolSize    int `yaml:"worker_pool_size"`
	DNSWorkerCount    int `yaml:"dns_worker_count"`
	HTTPWorkerCount   int `yaml:"http_worker_count"`
	TLSWorkerCount    int `yaml:"tls_worker_count"`
	SourceTimeoutSecs int `yaml:"source_timeout_secs"`
}

// Load reads the YAML config at path, then applies environment-variable
// overrides for the values most likely to differ between local, CI, and
// deployed environments (DSNs, addresses, secrets).
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("config: read %s: %w", path, err)
	}

	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("config: parse %s: %w", path, err)
	}

	applyEnvOverrides(&cfg)

	if cfg.Server.Port == 0 {
		cfg.Server.Port = 8080
	}
	if cfg.Postgres.MaxConns == 0 {
		cfg.Postgres.MaxConns = 10
	}
	if cfg.Crawler.WorkerPoolSize == 0 {
		cfg.Crawler.WorkerPoolSize = 8
	}

	return &cfg, nil
}

func applyEnvOverrides(cfg *Config) {
	if v := os.Getenv("GHI_POSTGRES_DSN"); v != "" {
		cfg.Postgres.DSN = v
	}
	if v := os.Getenv("GHI_REDIS_ADDR"); v != "" {
		cfg.Redis.Addr = v
	}
	if v := os.Getenv("GHI_REDIS_PASSWORD"); v != "" {
		cfg.Redis.Password = v
	}
	if v := os.Getenv("GHI_SERVER_PORT"); v != "" {
		var port int
		if _, err := fmt.Sscanf(v, "%d", &port); err == nil {
			cfg.Server.Port = port
		}
	}
}
