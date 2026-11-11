package main

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"strings"
	"time"
)

type server struct {
	cfg       Config
	db        *sql.DB
	lim       *limiter
	dummyHash string // compared against when the username does not exist, so timing does not leak it
}

type execer interface {
	Exec(query string, args ...any) (sql.Result, error)
}

type tokenResp struct {
	Username     string `json:"username"`
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int64  `json:"expires_in"`
	RecoveryCode string `json:"recovery_code,omitempty"`
}

func (s *server) issueSession(db execer, uid int64, username string) (tokenResp, error) {
	access, refresh := newToken(), newToken()
	now := time.Now()
	_, err := db.Exec(`INSERT INTO sessions(user_id, access_hash, refresh_hash, access_exp, refresh_exp, created_at) VALUES(?,?,?,?,?,?)`,
		uid, hashToken(access), hashToken(refresh), now.Add(s.cfg.AccessTTL).Unix(), now.Add(s.cfg.RefreshTTL).Unix(), now.Unix())
	return tokenResp{
		Username:     username,
		AccessToken:  access,
		RefreshToken: refresh,
		ExpiresIn:    int64(s.cfg.AccessTTL.Seconds()),
	}, err
}

func serverError(w http.ResponseWriter, what string, err error) {
	log.Printf("%s: %v", what, err)
	fail(w, http.StatusInternalServerError, "server_error", "خطای داخلی سرور.")
}

const (
	msgBadUser = "نام کاربری باید ۳ تا ۳۲ نویسه و فقط شامل حروف کوچک انگلیسی، عدد، «_» و «.» باشد."
	msgBadPass = "رمز عبور باید حداقل ۸ نویسه باشد."
	msgBadCred = "نام کاربری یا رمز عبور درست نیست."
)

// ------------------------------------------------------------------ register / login

func (s *server) register(w http.ResponseWriter, r *http.Request) {
	if !s.cfg.AllowRegister {
		fail(w, http.StatusForbidden, "registration_closed", "ثبت‌نام در این سرور بسته است.")
		return
	}
	var in struct{ Username, Password string }
	if !decode(w, r, &in, 4<<10) {
		return
	}
	u := normalizeUsername(in.Username)
	if !validUsername(u) {
		fail(w, http.StatusBadRequest, "invalid_username", msgBadUser)
		return
	}
	if !validPassword(in.Password) {
		fail(w, http.StatusBadRequest, "weak_password", msgBadPass)
		return
	}
	ph, err := hashPassword(in.Password)
	if err != nil {
		serverError(w, "hash", err)
		return
	}
	code := newRecoveryCode()

	tx, err := s.db.Begin()
	if err != nil {
		serverError(w, "begin", err)
		return
	}
	defer tx.Rollback()
	res, err := tx.Exec(`INSERT INTO users(username, pass_hash, recovery_hash, created_at) VALUES(?,?,?,?)`,
		u, ph, hashToken(normalizeRecovery(code)), time.Now().Unix())
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			fail(w, http.StatusConflict, "username_taken", "این نام کاربری قبلاً گرفته شده است.")
			return
		}
		serverError(w, "insert user", err)
		return
	}
	uid, _ := res.LastInsertId()
	tok, err := s.issueSession(tx, uid, u)
	if err != nil {
		serverError(w, "session", err)
		return
	}
	if err := tx.Commit(); err != nil {
		serverError(w, "commit", err)
		return
	}
	tok.RecoveryCode = code
	writeJSON(w, http.StatusCreated, tok)
}

// recordFailure bumps the wrong-password counter and locks the account after too many.
func (s *server) recordFailure(uid int64, failed int) {
	failed++
	locked := int64(0)
	if failed >= s.cfg.MaxLoginFailures {
		locked = time.Now().Add(s.cfg.LockDuration).Unix()
		failed = 0
	}
	if _, err := s.db.Exec(`UPDATE users SET failed_logins=?, locked_until=? WHERE id=?`, failed, locked, uid); err != nil {
		log.Printf("recordFailure: %v", err)
	}
}

func lockedMsg(until int64) string {
	mins := (until-time.Now().Unix())/60 + 1
	return fmt.Sprintf("به‌خاطر تلاش‌های ناموفق زیاد، ورود تا حدود %d دقیقه‌ی دیگر قفل است.", mins)
}

func (s *server) login(w http.ResponseWriter, r *http.Request) {
	var in struct{ Username, Password string }
	if !decode(w, r, &in, 4<<10) {
		return
	}
	u := normalizeUsername(in.Username)
	var id int64
	var hash string
	var failed int
	var locked int64
	err := s.db.QueryRow(`SELECT id, pass_hash, failed_logins, locked_until FROM users WHERE username=?`, u).
		Scan(&id, &hash, &failed, &locked)
	if err == sql.ErrNoRows {
		verifyPassword(in.Password, s.dummyHash)
		fail(w, http.StatusUnauthorized, "bad_credentials", msgBadCred)
		return
	}
	if err != nil {
		serverError(w, "login lookup", err)
		return
	}
	if locked > time.Now().Unix() {
		fail(w, http.StatusTooManyRequests, "locked", lockedMsg(locked))
		return
	}
	if !verifyPassword(in.Password, hash) {
		s.recordFailure(id, failed)
		fail(w, http.StatusUnauthorized, "bad_credentials", msgBadCred)
		return
	}
	if failed != 0 || locked != 0 {
		s.db.Exec(`UPDATE users SET failed_logins=0, locked_until=0 WHERE id=?`, id)
	}
	tok, err := s.issueSession(s.db, id, u)
	if err != nil {
		serverError(w, "session", err)
		return
	}
	writeJSON(w, http.StatusOK, tok)
}

// refresh rotates both tokens; the old refresh token stops working immediately.
func (s *server) refresh(w http.ResponseWriter, r *http.Request) {
	var in struct {
		RefreshToken string `json:"refresh_token"`
	}
	if !decode(w, r, &in, 4<<10) {
		return
	}
	var sid, uid int64
	var username string
	err := s.db.QueryRow(`SELECT s.id, s.user_id, u.username FROM sessions s JOIN users u ON u.id = s.user_id
		WHERE s.refresh_hash=? AND s.refresh_exp>?`, hashToken(in.RefreshToken), time.Now().Unix()).Scan(&sid, &uid, &username)
	if err == sql.ErrNoRows {
		fail(w, http.StatusUnauthorized, "invalid_refresh", "نشست شما تمام شده؛ دوباره وارد شوید.")
		return
	}
	if err != nil {
		serverError(w, "refresh lookup", err)
		return
	}
	access, refresh := newToken(), newToken()
	now := time.Now()
	if _, err := s.db.Exec(`UPDATE sessions SET access_hash=?, refresh_hash=?, access_exp=?, refresh_exp=? WHERE id=?`,
		hashToken(access), hashToken(refresh), now.Add(s.cfg.AccessTTL).Unix(), now.Add(s.cfg.RefreshTTL).Unix(), sid); err != nil {
		serverError(w, "refresh update", err)
		return
	}
	writeJSON(w, http.StatusOK, tokenResp{Username: username, AccessToken: access, RefreshToken: refresh, ExpiresIn: int64(s.cfg.AccessTTL.Seconds())})
}

func (s *server) logout(w http.ResponseWriter, r *http.Request) {
	s.db.Exec(`DELETE FROM sessions WHERE id=?`, sessionID(r))
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *server) me(w http.ResponseWriter, r *http.Request) {
	var u string
	if err := s.db.QueryRow(`SELECT username FROM users WHERE id=?`, userID(r)).Scan(&u); err != nil {
		serverError(w, "me", err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"username": u})
}

// ------------------------------------------------------------------ password management

func (s *server) changePassword(w http.ResponseWriter, r *http.Request) {
	var in struct {
		OldPassword string `json:"old_password"`
		NewPassword string `json:"new_password"`
	}
	if !decode(w, r, &in, 4<<10) {
		return
	}
	if !validPassword(in.NewPassword) {
		fail(w, http.StatusBadRequest, "weak_password", msgBadPass)
		return
	}
	var hash string
	if err := s.db.QueryRow(`SELECT pass_hash FROM users WHERE id=?`, userID(r)).Scan(&hash); err != nil {
		serverError(w, "change pw lookup", err)
		return
	}
	if !verifyPassword(in.OldPassword, hash) {
		fail(w, http.StatusForbidden, "bad_credentials", "رمز فعلی درست نیست.")
		return
	}
	nh, err := hashPassword(in.NewPassword)
	if err != nil {
		serverError(w, "hash", err)
		return
	}
	tx, err := s.db.Begin()
	if err != nil {
		serverError(w, "begin", err)
		return
	}
	defer tx.Rollback()
	tx.Exec(`UPDATE users SET pass_hash=? WHERE id=?`, nh, userID(r))
	tx.Exec(`DELETE FROM sessions WHERE user_id=? AND id<>?`, userID(r), sessionID(r)) // sign out other devices
	if err := tx.Commit(); err != nil {
		serverError(w, "commit", err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

// recoverAccount sets a new password using the recovery code given at sign-up, then issues a fresh code.
func (s *server) recoverAccount(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Username     string `json:"username"`
		RecoveryCode string `json:"recovery_code"`
		NewPassword  string `json:"new_password"`
	}
	if !decode(w, r, &in, 4<<10) {
		return
	}
	if !validPassword(in.NewPassword) {
		fail(w, http.StatusBadRequest, "weak_password", msgBadPass)
		return
	}
	u := normalizeUsername(in.Username)
	var id int64
	var rh string
	var failed int
	var locked int64
	err := s.db.QueryRow(`SELECT id, recovery_hash, failed_logins, locked_until FROM users WHERE username=?`, u).Scan(&id, &rh, &failed, &locked)
	badCode := func() {
		fail(w, http.StatusUnauthorized, "bad_recovery", "نام کاربری یا کد بازیابی درست نیست.")
	}
	if err == sql.ErrNoRows {
		badCode()
		return
	}
	if err != nil {
		serverError(w, "recover lookup", err)
		return
	}
	if locked > time.Now().Unix() {
		fail(w, http.StatusTooManyRequests, "locked", lockedMsg(locked))
		return
	}
	if hashToken(normalizeRecovery(in.RecoveryCode)) != rh {
		s.recordFailure(id, failed)
		badCode()
		return
	}
	nh, err := hashPassword(in.NewPassword)
	if err != nil {
		serverError(w, "hash", err)
		return
	}
	code := newRecoveryCode()
	tx, err := s.db.Begin()
	if err != nil {
		serverError(w, "begin", err)
		return
	}
	defer tx.Rollback()
	tx.Exec(`UPDATE users SET pass_hash=?, recovery_hash=?, failed_logins=0, locked_until=0 WHERE id=?`, nh, hashToken(normalizeRecovery(code)), id)
	tx.Exec(`DELETE FROM sessions WHERE user_id=?`, id) // everyone signs in again
	tok, err := s.issueSession(tx, id, u)
	if err != nil {
		serverError(w, "session", err)
		return
	}
	if err := tx.Commit(); err != nil {
		serverError(w, "commit", err)
		return
	}
	tok.RecoveryCode = code
	writeJSON(w, http.StatusOK, tok)
}

func (s *server) deleteAccount(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Password string `json:"password"`
	}
	if !decode(w, r, &in, 4<<10) {
		return
	}
	var hash string
	if err := s.db.QueryRow(`SELECT pass_hash FROM users WHERE id=?`, userID(r)).Scan(&hash); err != nil {
		serverError(w, "delete lookup", err)
		return
	}
	if !verifyPassword(in.Password, hash) {
		fail(w, http.StatusForbidden, "bad_credentials", "رمز عبور درست نیست.")
		return
	}
	if _, err := s.db.Exec(`DELETE FROM users WHERE id=?`, userID(r)); err != nil { // cascades to sessions and records
		serverError(w, "delete", err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}
