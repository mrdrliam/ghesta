package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

// version is set at build time: -ldflags "-X main.version=1.2.3"
var version = "dev"

func newServer(cfg Config) (*server, error) {
	db, err := openDB(cfg.DBPath)
	if err != nil {
		return nil, err
	}
	dummy, err := hashPassword("dummy-password-for-timing")
	if err != nil {
		return nil, err
	}
	return &server{cfg: cfg, db: db, lim: newLimiter(cfg.AuthRatePerMin), dummyHash: dummy}, nil
}

func (s *server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok", "version": version})
	})

	mux.HandleFunc("POST /v1/auth/register", s.rateLimited(s.register))
	mux.HandleFunc("POST /v1/auth/login", s.rateLimited(s.login))
	mux.HandleFunc("POST /v1/auth/refresh", s.rateLimited(s.refresh))
	mux.HandleFunc("POST /v1/auth/recover", s.rateLimited(s.recoverAccount))
	mux.HandleFunc("POST /v1/auth/logout", s.authed(s.logout))
	mux.HandleFunc("POST /v1/auth/change-password", s.rateLimited(s.authed(s.changePassword)))
	mux.HandleFunc("GET /v1/me", s.authed(s.me))
	// POST (not DELETE) so every mobile HTTP stack can send the password in the body
	mux.HandleFunc("POST /v1/account/delete", s.rateLimited(s.authed(s.deleteAccount)))

	mux.HandleFunc("POST /v1/sync", s.authed(s.sync))
	return logging(mux)
}

// cleanupLoop removes expired sessions once an hour.
func (s *server) cleanupLoop(ctx context.Context) {
	t := time.NewTicker(time.Hour)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			s.db.Exec(`DELETE FROM sessions WHERE refresh_exp < ?`, time.Now().Unix())
		}
	}
}

func main() {
	cfg := loadConfig()
	s, err := newServer(cfg)
	if err != nil {
		log.Fatalf("startup: %v", err)
	}
	defer s.db.Close()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go s.cleanupLoop(ctx)
	go s.backupLoop(ctx)

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           s.routes(),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		srv.Shutdown(shutdown)
	}()

	log.Printf("ghesta server %s listening on %s (db: %s, registration open: %v, backups: %q)", version, cfg.Addr, cfg.DBPath, cfg.AllowRegister, cfg.BackupDir)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
