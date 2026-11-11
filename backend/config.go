package main

import (
	"os"
	"strconv"
	"time"
)

// Config is read from environment variables so the same binary runs locally and in Docker.
type Config struct {
	Addr             string        // listen address
	DBPath           string        // SQLite file
	TrustProxy       bool          // trust X-Forwarded-For (only behind your own reverse proxy)
	AllowRegister    bool          // false = closed server, only existing accounts can log in
	AccessTTL        time.Duration // lifetime of an access token
	RefreshTTL       time.Duration // lifetime of a refresh token
	AuthRatePerMin   int           // auth requests per minute per IP
	MaxLoginFailures int           // wrong passwords before the account is locked
	LockDuration     time.Duration // how long it stays locked
	MaxRecords       int           // max items per user
	BackupDir        string        // where nightly snapshots go; empty = disabled
	BackupInterval   time.Duration // time between snapshots
	BackupKeep       int           // how many snapshots to keep
}

func loadConfig() Config {
	return Config{
		Addr:             env("ADDR", ":8080"),
		DBPath:           env("DB_PATH", "./data/ghesta.db"),
		TrustProxy:       envBool("TRUST_PROXY", false),
		AllowRegister:    envBool("ALLOW_REGISTER", true),
		AccessTTL:        envDuration("ACCESS_TTL", 30*time.Minute),
		RefreshTTL:       envDuration("REFRESH_TTL", 90*24*time.Hour),
		AuthRatePerMin:   envInt("AUTH_RATE_PER_MIN", 30),
		MaxLoginFailures: envInt("MAX_LOGIN_FAILURES", 5),
		LockDuration:     envDuration("LOCK_DURATION", 15*time.Minute),
		MaxRecords:       envInt("MAX_RECORDS", 5000),
		BackupDir:        env("BACKUP_DIR", ""),
		BackupInterval:   envDuration("BACKUP_INTERVAL", 24*time.Hour),
		BackupKeep:       envInt("BACKUP_KEEP", 7),
	}
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envBool(key string, def bool) bool {
	if v := os.Getenv(key); v != "" {
		b, err := strconv.ParseBool(v)
		if err == nil {
			return b
		}
	}
	return def
}

func envInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func envDuration(key string, def time.Duration) time.Duration {
	if v := os.Getenv(key); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return def
}
