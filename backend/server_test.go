package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"
)

type tc struct {
	t   *testing.T
	srv *httptest.Server
	s   *server
}

func newTC(t *testing.T, mod ...func(*Config)) *tc {
	t.Helper()
	cfg := loadConfig()
	cfg.DBPath = filepath.Join(t.TempDir(), "test.db")
	cfg.AuthRatePerMin = 10000
	for _, m := range mod {
		m(&cfg)
	}
	s, err := newServer(cfg)
	if err != nil {
		t.Fatal(err)
	}
	ts := httptest.NewServer(s.routes())
	t.Cleanup(func() { ts.Close(); s.db.Close() })
	return &tc{t: t, srv: ts, s: s}
}

type resp map[string]any

func (c *tc) do(method, path, token string, body any) (int, resp) {
	c.t.Helper()
	var buf bytes.Buffer
	if body != nil {
		json.NewEncoder(&buf).Encode(body)
	}
	req, _ := http.NewRequest(method, c.srv.URL+path, &buf)
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		c.t.Fatal(err)
	}
	defer res.Body.Close()
	var out resp
	json.NewDecoder(res.Body).Decode(&out)
	return res.StatusCode, out
}

func (c *tc) register(user, pass string) resp {
	c.t.Helper()
	st, r := c.do("POST", "/v1/auth/register", "", resp{"username": user, "password": pass})
	if st != 201 {
		c.t.Fatalf("register %s: %d %v", user, st, r)
	}
	return r
}

func (c *tc) login(user, pass string) (int, resp) {
	return c.do("POST", "/v1/auth/login", "", resp{"username": user, "password": pass})
}

func item(id string, ts int64, title string) map[string]any {
	return map[string]any{"id": id, "updated_at": ts, "deleted": false, "data": map[string]any{"id": id, "title": title}}
}

func (c *tc) sync(token string, cursor int64, changes ...map[string]any) resp {
	c.t.Helper()
	if changes == nil {
		changes = []map[string]any{}
	}
	st, r := c.do("POST", "/v1/sync", token, resp{"cursor": cursor, "changes": changes})
	if st != 200 {
		c.t.Fatalf("sync: %d %v", st, r)
	}
	return r
}

func ids(r resp, key string) []string {
	var out []string
	for _, v := range r[key].([]any) {
		switch x := v.(type) {
		case string:
			out = append(out, x)
		case map[string]any:
			out = append(out, x["id"].(string))
		}
	}
	return out
}

func TestRegisterLoginAndValidation(t *testing.T) {
	c := newTC(t)
	r := c.register("Ali_01", "secret123")
	if r["recovery_code"] == "" || r["access_token"] == "" || r["username"] != "ali_01" {
		t.Fatalf("unexpected register response: %v", r)
	}
	if st, r := c.do("POST", "/v1/auth/register", "", resp{"username": "ali_01", "password": "another123"}); st != 409 || r["error"] != "username_taken" {
		t.Fatalf("duplicate username: %d %v", st, r)
	}
	if st, r := c.do("POST", "/v1/auth/register", "", resp{"username": "a", "password": "secret123"}); st != 400 || r["error"] != "invalid_username" {
		t.Fatalf("short username: %d %v", st, r)
	}
	if st, r := c.do("POST", "/v1/auth/register", "", resp{"username": "newuser", "password": "short"}); st != 400 || r["error"] != "weak_password" {
		t.Fatalf("weak password: %d %v", st, r)
	}
	if st, _ := c.login("ali_01", "secret123"); st != 200 {
		t.Fatalf("login failed: %d", st)
	}
	if st, r := c.login("ali_01", "wrong-password"); st != 401 || r["error"] != "bad_credentials" {
		t.Fatalf("wrong password: %d %v", st, r)
	}
	if st, r := c.login("nobody", "secret123"); st != 401 || r["error"] != "bad_credentials" {
		t.Fatalf("unknown user must look the same as wrong password: %d %v", st, r)
	}
}

func TestLockoutAfterRepeatedFailures(t *testing.T) {
	c := newTC(t, func(cfg *Config) { cfg.MaxLoginFailures = 3 })
	c.register("sara", "secret123")
	for i := 0; i < 3; i++ {
		if st, _ := c.login("sara", "bad-password"); st != 401 {
			t.Fatalf("attempt %d: expected 401, got %d", i, st)
		}
	}
	// even the right password is refused while locked
	if st, r := c.login("sara", "secret123"); st != 429 || r["error"] != "locked" {
		t.Fatalf("expected locked, got %d %v", st, r)
	}
}

func TestRefreshRotation(t *testing.T) {
	c := newTC(t)
	r := c.register("reza", "secret123")
	oldRefresh := r["refresh_token"].(string)
	st, r2 := c.do("POST", "/v1/auth/refresh", "", resp{"refresh_token": oldRefresh})
	if st != 200 || r2["access_token"] == r["access_token"] {
		t.Fatalf("refresh: %d %v", st, r2)
	}
	if st, r3 := c.do("POST", "/v1/auth/refresh", "", resp{"refresh_token": oldRefresh}); st != 401 || r3["error"] != "invalid_refresh" {
		t.Fatalf("old refresh token must be dead: %d %v", st, r3)
	}
	// the old access token is replaced as well
	if st, r4 := c.do("GET", "/v1/me", r["access_token"].(string), nil); st != 401 || r4["error"] != "token_expired" {
		t.Fatalf("old access token must be dead: %d %v", st, r4)
	}
	if st, _ := c.do("GET", "/v1/me", r2["access_token"].(string), nil); st != 200 {
		t.Fatalf("new access token should work: %d", st)
	}
}

func TestExpiredAccessTokenSaysSo(t *testing.T) {
	c := newTC(t, func(cfg *Config) { cfg.AccessTTL = time.Second })
	r := c.register("expiring", "secret123")
	time.Sleep(2100 * time.Millisecond)
	if st, e := c.do("GET", "/v1/me", r["access_token"].(string), nil); st != 401 || e["error"] != "token_expired" {
		t.Fatalf("expected token_expired, got %d %v", st, e)
	}
	if st, _ := c.do("GET", "/v1/me", "", nil); st != 401 {
		t.Fatalf("missing token must be 401, got %d", st)
	}
}

func TestSyncBetweenTwoDevices(t *testing.T) {
	c := newTC(t)
	c.register("maryam", "secret123")
	_, a := c.login("maryam", "secret123")
	_, b := c.login("maryam", "secret123")
	ta, tb := a["access_token"].(string), b["access_token"].(string)

	// device A pushes two items
	r := c.sync(ta, 0, item("loan1", 1000, "وام مسکن"), item("debt1", 1000, "قرض از علی"))
	if len(ids(r, "accepted")) != 2 || len(ids(r, "changes")) != 0 {
		t.Fatalf("A push: %v", r)
	}
	cursorA := int64(r["cursor"].(float64))

	// device B pulls both
	r = c.sync(tb, 0)
	if len(ids(r, "changes")) != 2 {
		t.Fatalf("B pull: %v", r)
	}
	cursorB := int64(r["cursor"].(float64))
	if cursorB != cursorA {
		t.Fatalf("cursors differ: %d vs %d", cursorA, cursorB)
	}

	// B edits loan1 (newer) and deletes debt1; A receives both
	del := map[string]any{"id": "debt1", "updated_at": 3000, "deleted": true}
	r = c.sync(tb, cursorB, item("loan1", 2000, "وام مسکن (ویرایش‌شده)"), del)
	cursorB = int64(r["cursor"].(float64))
	r = c.sync(ta, cursorA)
	got := map[string]map[string]any{}
	for _, v := range r["changes"].([]any) {
		m := v.(map[string]any)
		got[m["id"].(string)] = m
	}
	if len(got) != 2 || got["debt1"]["deleted"] != true {
		t.Fatalf("A should see the deletion: %v", r)
	}
	if d := got["loan1"]["data"].(map[string]any); d["title"] != "وام مسکن (ویرایش‌شده)" {
		t.Fatalf("A should see the edit: %v", got["loan1"])
	}
	cursorA = int64(r["cursor"].(float64))

	// A (stale) tries to overwrite loan1 with an OLDER timestamp: rejected, and A gets the winner back
	r = c.sync(ta, cursorA, item("loan1", 1500, "قدیمی"))
	if len(ids(r, "rejected")) != 1 || len(ids(r, "accepted")) != 0 {
		t.Fatalf("stale write must be rejected: %v", r)
	}
	ch := ids(r, "changes")
	if len(ch) != 1 || ch[0] != "loan1" {
		t.Fatalf("rejected write must return the winning version: %v", r)
	}

	// re-sending the same change is idempotent
	r = c.sync(tb, cursorB, item("loan1", 2000, "وام مسکن (ویرایش‌شده)"))
	if len(ids(r, "accepted")) != 1 {
		t.Fatalf("equal timestamp should be accepted: %v", r)
	}
}

func TestUsersAreIsolated(t *testing.T) {
	c := newTC(t)
	a := c.register("alice", "secret123")["access_token"].(string)
	b := c.register("bobby", "secret123")["access_token"].(string)
	c.sync(a, 0, item("shared-id", 1000, "alice only"))
	r := c.sync(b, 0)
	if len(ids(r, "changes")) != 0 {
		t.Fatalf("bob must not see alice's data: %v", r)
	}
	// same id for another user is a separate record
	c.sync(b, 0, item("shared-id", 500, "bob's"))
	r = c.sync(a, 0)
	if d := r["changes"].([]any)[0].(map[string]any)["data"].(map[string]any); d["title"] != "alice only" {
		t.Fatalf("alice's record was touched: %v", d)
	}
}

func TestSyncValidation(t *testing.T) {
	c := newTC(t)
	tok := c.register("validator", "secret123")["access_token"].(string)
	if st, _ := c.do("POST", "/v1/sync", tok, resp{"cursor": 0, "changes": []any{map[string]any{"id": "../etc", "updated_at": 1, "data": map[string]any{}}}}); st != 400 {
		t.Fatalf("bad id should be 400, got %d", st)
	}
	if st, _ := c.do("POST", "/v1/sync", tok, resp{"cursor": 0, "changes": []any{map[string]any{"id": "ok", "updated_at": 1, "data": "not-an-object"}}}); st != 400 {
		t.Fatalf("non-object data should be 400, got %d", st)
	}
	// a clock far in the future is clamped so it can never win forever
	far := time.Now().Add(10 * 365 * 24 * time.Hour).UnixMilli()
	c.sync(tok, 0, item("x", far, "future"))
	r := c.sync(tok, 0, item("x", time.Now().UnixMilli()+2*24*3600*1000, "also future but later"))
	if len(ids(r, "accepted")) != 1 {
		t.Fatalf("clamped timestamps should compare equal and be accepted: %v", r)
	}
}

func TestRecoveryCodeFlow(t *testing.T) {
	c := newTC(t)
	reg := c.register("forgetful", "secret123")
	code := reg["recovery_code"].(string)
	tok := reg["access_token"].(string)

	if st, _ := c.do("POST", "/v1/auth/recover", "", resp{"username": "forgetful", "recovery_code": "AAAAA-BBBBB-CCCCC-DDDDD", "new_password": "newsecret99"}); st != 401 {
		t.Fatalf("wrong code must fail, got %d", st)
	}
	// lowercase and without dashes still works
	st, r := c.do("POST", "/v1/auth/recover", "", resp{"username": "forgetful", "recovery_code": code, "new_password": "newsecret99"})
	if st != 200 || r["recovery_code"] == code || r["recovery_code"] == "" {
		t.Fatalf("recover: %d %v", st, r)
	}
	if st, _ := c.login("forgetful", "secret123"); st != 401 {
		t.Fatalf("old password must stop working")
	}
	if st, _ := c.login("forgetful", "newsecret99"); st != 200 {
		t.Fatalf("new password must work")
	}
	if st, _ := c.do("GET", "/v1/me", tok, nil); st != 401 {
		t.Fatalf("old sessions must be revoked, got %d", st)
	}
	if st, _ := c.do("POST", "/v1/auth/recover", "", resp{"username": "forgetful", "recovery_code": code, "new_password": "third-pass-1"}); st != 401 {
		t.Fatalf("a used recovery code must not work again")
	}
}

func TestChangePasswordAndDeleteAccount(t *testing.T) {
	c := newTC(t)
	reg := c.register("changer", "secret123")
	tok := reg["access_token"].(string)
	_, other := c.login("changer", "secret123")
	otherTok := other["access_token"].(string)

	if st, _ := c.do("POST", "/v1/auth/change-password", tok, resp{"old_password": "nope-nope", "new_password": "newsecret99"}); st != 403 {
		t.Fatalf("wrong old password must be 403, got %d", st)
	}
	if st, _ := c.do("POST", "/v1/auth/change-password", tok, resp{"old_password": "secret123", "new_password": "newsecret99"}); st != 200 {
		t.Fatalf("change password failed: %d", st)
	}
	if st, _ := c.do("GET", "/v1/me", otherTok, nil); st != 401 {
		t.Fatalf("other devices must be signed out, got %d", st)
	}
	if st, _ := c.do("GET", "/v1/me", tok, nil); st != 200 {
		t.Fatalf("current device must stay signed in, got %d", st)
	}

	c.sync(tok, 0, item("a", 1, "x"))
	if st, _ := c.do("POST", "/v1/account/delete", tok, resp{"password": "wrong-password"}); st != 403 {
		t.Fatalf("delete with wrong password must be 403, got %d", st)
	}
	if st, _ := c.do("POST", "/v1/account/delete", tok, resp{"password": "newsecret99"}); st != 200 {
		t.Fatalf("delete failed: %d", st)
	}
	if st, _ := c.login("changer", "newsecret99"); st != 401 {
		t.Fatalf("deleted account must not log in")
	}
	var n int
	c.s.db.QueryRow(`SELECT COUNT(*) FROM records`).Scan(&n)
	if n != 0 {
		t.Fatalf("records must be deleted with the account, %d left", n)
	}
}

func TestRegistrationCanBeClosed(t *testing.T) {
	c := newTC(t, func(cfg *Config) { cfg.AllowRegister = false })
	if st, r := c.do("POST", "/v1/auth/register", "", resp{"username": "intruder", "password": "secret123"}); st != 403 || r["error"] != "registration_closed" {
		t.Fatalf("expected closed, got %d %v", st, r)
	}
}

func TestRateLimit(t *testing.T) {
	c := newTC(t, func(cfg *Config) { cfg.AuthRatePerMin = 3 })
	var last int
	for i := 0; i < 5; i++ {
		last, _ = c.login(fmt.Sprintf("u%d", i), "whatever1")
	}
	if last != 429 {
		t.Fatalf("expected 429 after the limit, got %d", last)
	}
}

func TestRecordLimit(t *testing.T) {
	c := newTC(t, func(cfg *Config) { cfg.MaxRecords = 2 })
	tok := c.register("limited", "secret123")["access_token"].(string)
	c.sync(tok, 0, item("a", 1, "1"), item("b", 1, "2"))
	st, r := c.do("POST", "/v1/sync", tok, resp{"cursor": 0, "changes": []any{item("c", 1, "3")}})
	if st != 413 || r["error"] != "too_many_records" {
		t.Fatalf("expected record limit, got %d %v", st, r)
	}
}
