package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

type ctxKey int

const (
	ctxUser ctxKey = iota
	ctxSession
)

type apiErr struct {
	Error   string `json:"error"`   // stable machine code
	Message string `json:"message"` // Persian text safe to show to the user
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func fail(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, apiErr{Error: code, Message: msg})
}

// decode reads a JSON body with a size cap.
func decode(w http.ResponseWriter, r *http.Request, v any, max int64) bool {
	r.Body = http.MaxBytesReader(w, r.Body, max)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		fail(w, http.StatusBadRequest, "bad_request", "درخواست نامعتبر است.")
		return false
	}
	return true
}

// ------------------------------------------------------------------ rate limit

type window struct {
	start time.Time
	n     int
}

type limiter struct {
	mu    sync.Mutex
	m     map[string]*window
	limit int
}

func newLimiter(perMinute int) *limiter { return &limiter{m: map[string]*window{}, limit: perMinute} }

func (l *limiter) allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	w := l.m[key]
	if w == nil || now.Sub(w.start) > time.Minute {
		if len(l.m) > 10000 { // crude memory cap
			l.m = map[string]*window{}
		}
		w = &window{start: now}
		l.m[key] = w
	}
	w.n++
	return w.n <= l.limit
}

func (s *server) clientIP(r *http.Request) string {
	if s.cfg.TrustProxy {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			return strings.TrimSpace(strings.Split(xff, ",")[0])
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func (s *server) rateLimited(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !s.lim.allow(s.clientIP(r)) {
			w.Header().Set("Retry-After", "60")
			fail(w, http.StatusTooManyRequests, "rate_limited", "تعداد درخواست‌ها زیاد است؛ کمی بعد دوباره تلاش کنید.")
			return
		}
		next(w, r)
	}
}

// ------------------------------------------------------------------ auth

func (s *server) authed(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := r.Header.Get("Authorization")
		if !strings.HasPrefix(h, "Bearer ") {
			fail(w, http.StatusUnauthorized, "unauthorized", "ابتدا وارد شوید.")
			return
		}
		tok := strings.TrimPrefix(h, "Bearer ")
		var uid, sid int64
		err := s.db.QueryRow(`SELECT user_id, id FROM sessions WHERE access_hash = ? AND access_exp > ?`,
			hashToken(tok), time.Now().Unix()).Scan(&uid, &sid)
		if err == sql.ErrNoRows {
			// The client reacts to this code by using its refresh token.
			fail(w, http.StatusUnauthorized, "token_expired", "نشست منقضی شده است.")
			return
		}
		if err != nil {
			log.Printf("auth lookup: %v", err)
			fail(w, http.StatusInternalServerError, "server_error", "خطای داخلی سرور.")
			return
		}
		ctx := context.WithValue(r.Context(), ctxUser, uid)
		ctx = context.WithValue(ctx, ctxSession, sid)
		next(w, r.WithContext(ctx))
	}
}

func userID(r *http.Request) int64    { return r.Context().Value(ctxUser).(int64) }
func sessionID(r *http.Request) int64 { return r.Context().Value(ctxSession).(int64) }

// ------------------------------------------------------------------ misc

func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w, status: 200}
		defer func() {
			if rec := recover(); rec != nil {
				log.Printf("panic: %v", rec)
				fail(sw, http.StatusInternalServerError, "server_error", "خطای داخلی سرور.")
			}
			log.Printf("%s %s %d %s", r.Method, r.URL.Path, sw.status, time.Since(start).Round(time.Millisecond))
		}()
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(sw, r)
	})
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}
