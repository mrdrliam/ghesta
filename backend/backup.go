package main

import (
	"context"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// backupOnce writes a consistent snapshot of the database into dir (SQLite "VACUUM INTO") and
// deletes the oldest snapshots so that at most keep remain. Snapshots contain password hashes,
// so they are created owner-only.
func (s *server) backupOnce(dir string, keep int) (string, error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	name := filepath.Join(dir, "ghesta-"+time.Now().UTC().Format("20060102-150405.000000000")+".db")
	if _, err := s.db.Exec(`VACUUM INTO ?`, name); err != nil {
		return "", err
	}
	if err := os.Chmod(name, 0o600); err != nil {
		return name, err
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		return name, err
	}
	var snaps []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasPrefix(e.Name(), "ghesta-") && strings.HasSuffix(e.Name(), ".db") {
			snaps = append(snaps, e.Name())
		}
	}
	sort.Strings(snaps) // timestamps sort chronologically
	for len(snaps) > keep {
		os.Remove(filepath.Join(dir, snaps[0]))
		snaps = snaps[1:]
	}
	return name, nil
}

// backupLoop takes a snapshot shortly after start and then every BackupInterval. Disabled when BACKUP_DIR is empty.
func (s *server) backupLoop(ctx context.Context) {
	if s.cfg.BackupDir == "" {
		return
	}
	timer := time.NewTimer(time.Minute)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
			if name, err := s.backupOnce(s.cfg.BackupDir, s.cfg.BackupKeep); err != nil {
				log.Printf("backup failed: %v", err)
			} else {
				log.Printf("backup written: %s", name)
			}
			timer.Reset(s.cfg.BackupInterval)
		}
	}
}
