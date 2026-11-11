package main

import (
	"database/sql"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite" // pure-Go SQLite driver, no cgo needed
)

const schema = `
CREATE TABLE IF NOT EXISTS users (
	id            INTEGER PRIMARY KEY AUTOINCREMENT,
	username      TEXT    NOT NULL UNIQUE,
	pass_hash     TEXT    NOT NULL,
	recovery_hash TEXT    NOT NULL,
	created_at    INTEGER NOT NULL,
	failed_logins INTEGER NOT NULL DEFAULT 0,
	locked_until  INTEGER NOT NULL DEFAULT 0,
	seq           INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sessions (
	id          INTEGER PRIMARY KEY AUTOINCREMENT,
	user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	access_hash TEXT    NOT NULL UNIQUE,
	refresh_hash TEXT   NOT NULL UNIQUE,
	access_exp  INTEGER NOT NULL,
	refresh_exp INTEGER NOT NULL,
	created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions(user_id);

CREATE TABLE IF NOT EXISTS records (
	user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	id         TEXT    NOT NULL,
	data       TEXT    NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted    INTEGER NOT NULL DEFAULT 0,
	seq        INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);
CREATE INDEX IF NOT EXISTS records_seq ON records(user_id, seq);
`

func openDB(path string) (*sql.DB, error) {
	if dir := filepath.Dir(path); dir != "." && dir != "" {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return nil, err
		}
	}
	dsn := "file:" + path + "?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	// One writer at a time keeps SQLite simple and avoids "database is locked".
	db.SetMaxOpenConns(1)
	if _, err := db.Exec(schema); err != nil {
		db.Close()
		return nil, err
	}
	return db, nil
}
