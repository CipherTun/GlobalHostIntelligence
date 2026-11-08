// Package config loads server configuration from a YAML file with
// environment-variable overrides. Kept dependency-light and explicit:
// no magic global singleton, config is constructed once in main and
// passed down.
package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"

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
	DSN      string `yaml:"dsn"`
	MaxConns int32  `yaml:"max_conns"`
	MinConns int32  `yaml:"min_conns"`
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
// deployed environments (DSNs, addresses, secrets), fills defaults and
// validates the result so a bad deployment fails at startup with a clear
// message instead of misbehaving later.
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("config: read %s: %w", path, err)
	}

	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("config: parse %s: %w", path, err)
	}

	if err := applyEnvOverrides(&cfg); err != nil {
		return nil, err
	}
	applyDefaults(&cfg)
	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return &cfg, nil
}

func applyDefaults(cfg *Config) {
	if cfg.Server.Port == 0 {
		cfg.Server.Port = 8080
	}
	if cfg.Server.Env == "" {
		cfg.Server.Env = "development"
	}
	if cfg.Postgres.MaxConns == 0 {
		cfg.Postgres.MaxConns = 10
	}
	if cfg.Crawler.WorkerPoolSize == 0 {
		cfg.Crawler.WorkerPoolSize = 8
	}
	if cfg.Crawler.DNSWorkerCount == 0 {
		cfg.Crawler.DNSWorkerCount = 16
	}
	if cfg.Crawler.HTTPWorkerCount == 0 {
		cfg.Crawler.HTTPWorkerCount = 8
	}
	if cfg.Crawler.TLSWorkerCount == 0 {
		cfg.Crawler.TLSWorkerCount = 8
	}
	if cfg.Crawler.SourceTimeoutSecs == 0 {
		cfg.Crawler.SourceTimeoutSecs = 30
	}
}

func (c *Config) validate() error {
	if strings.TrimSpace(c.Postgres.DSN) == "" {
		return errors.New("config: postgres.dsn (or GHI_POSTGRES_DSN) is required")
	}
	if strings.TrimSpace(c.Redis.Addr) == "" {
		return errors.New("config: redis.addr (or GHI_REDIS_ADDR) is required")
	}
	if c.Server.Port < 1 || c.Server.Port > 65535 {
		return fmt.Errorf("config: server.port %d is out of range 1-65535", c.Server.Port)
	}
	if c.Postgres.MaxConns < 1 {
		return fmt.Errorf("config: postgres.max_conns must be at least 1, got %d", c.Postgres.MaxConns)
	}
	if c.Postgres.MinConns < 0 || c.Postgres.MinConns > c.Postgres.MaxConns {
		return fmt.Errorf("config: postgres.min_conns %d must be between 0 and max_conns (%d)", c.Postgres.MinConns, c.Postgres.MaxConns)
	}
	if c.Redis.DB < 0 {
		return fmt.Errorf("config: redis.db must not be negative, got %d", c.Redis.DB)
	}
	for name, v := range map[string]int{
		"crawler.worker_pool_size":   c.Crawler.WorkerPoolSize,
		"crawler.dns_worker_count":   c.Crawler.DNSWorkerCount,
		"crawler.http_worker_count":  c.Crawler.HTTPWorkerCount,
		"crawler.tls_worker_count":   c.Crawler.TLSWorkerCount,
		"crawler.source_timeout_secs": c.Crawler.SourceTimeoutSecs,
	} {
		if v < 1 {
			return fmt.Errorf("config: %s must be at least 1, got %d", name, v)
		}
	}
	return nil
}

func applyEnvOverrides(cfg *Config) error {
	if v := os.Getenv("GHI_POSTGRES_DSN"); v != "" {
		cfg.Postgres.DSN = v
	}
	if v := os.Getenv("GHI_REDIS_ADDR"); v != "" {
		cfg.Redis.Addr = v
	}
	if v := os.Getenv("GHI_REDIS_PASSWORD"); v != "" {
		cfg.Redis.Password = v
	}
	if v := os.Getenv("GHI_SERVER_HOST"); v != "" {
		cfg.Server.Host = v
	}
	if v := os.Getenv("GHI_ENV"); v != "" {
		cfg.Server.Env = v
	}
	if v := os.Getenv("GHI_SERVER_PORT"); v != "" {
		// strconv.Atoi rejects trailing garbage ("8080abc"), which fmt.Sscanf
		// used to accept as 8080.
		port, err := strconv.Atoi(strings.TrimSpace(v))
		if err != nil {
			return fmt.Errorf("config: GHI_SERVER_PORT %q is not a valid port", v)
		}
		cfg.Server.Port = port
	}
	return nil
}
