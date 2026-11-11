package main

import (
	"encoding/json"
	"net/http"
	"regexp"
	"time"
)

// Sync model: every user owns a set of opaque JSON "records" (one per loan/debt) identified by a client id.
// Each accepted write gets a per-user increasing `seq`; clients remember the highest seq they have seen
// (the cursor) and ask only for newer ones. Conflicts are resolved last-writer-wins on `updated_at`.

type changeIn struct {
	ID        string          `json:"id"`
	UpdatedAt int64           `json:"updated_at"`
	Deleted   bool            `json:"deleted"`
	Data      json.RawMessage `json:"data"`
}

type changeOut struct {
	ID        string          `json:"id"`
	UpdatedAt int64           `json:"updated_at"`
	Deleted   bool            `json:"deleted"`
	Data      json.RawMessage `json:"data,omitempty"`
}

type syncReq struct {
	Cursor  int64      `json:"cursor"`
	Changes []changeIn `json:"changes"`
}

type syncResp struct {
	Cursor   int64       `json:"cursor"`
	Changes  []changeOut `json:"changes"`  // what the client is missing (or must overwrite with)
	Accepted []string    `json:"accepted"` // ids from the request that were stored
	Rejected []string    `json:"rejected"` // ids where the server already had a newer version
}

const (
	maxChanges    = 2000
	maxRecordSize = 256 << 10
)

var idRe = regexp.MustCompile(`^[A-Za-z0-9_-]{1,64}$`)

func (s *server) sync(w http.ResponseWriter, r *http.Request) {
	var req syncReq
	if !decode(w, r, &req, 16<<20) {
		return
	}
	if len(req.Changes) > maxChanges {
		fail(w, http.StatusRequestEntityTooLarge, "too_many_changes", "تعداد تغییرات در یک درخواست زیاد است.")
		return
	}
	for _, ch := range req.Changes {
		if !idRe.MatchString(ch.ID) {
			fail(w, http.StatusBadRequest, "bad_id", "شناسه‌ی نامعتبر.")
			return
		}
		if !ch.Deleted {
			if len(ch.Data) == 0 || len(ch.Data) > maxRecordSize || ch.Data[0] != '{' || !json.Valid(ch.Data) {
				fail(w, http.StatusBadRequest, "bad_data", "داده‌ی نامعتبر.")
				return
			}
		}
	}

	uid := userID(r)
	tx, err := s.db.Begin()
	if err != nil {
		serverError(w, "sync begin", err)
		return
	}
	defer tx.Rollback()

	var seq int64
	if err := tx.QueryRow(`SELECT seq FROM users WHERE id=?`, uid).Scan(&seq); err != nil {
		serverError(w, "sync seq", err)
		return
	}
	var count int
	tx.QueryRow(`SELECT COUNT(*) FROM records WHERE user_id=?`, uid).Scan(&count)

	maxTs := time.Now().Add(24 * time.Hour).UnixMilli() // a wrong phone clock must not pin a record forever
	accepted := map[string]bool{}
	resp := syncResp{Changes: []changeOut{}, Accepted: []string{}, Rejected: []string{}}

	for _, ch := range req.Changes {
		ts := ch.UpdatedAt
		if ts > maxTs {
			ts = maxTs
		}
		var cur int64
		err := tx.QueryRow(`SELECT updated_at FROM records WHERE user_id=? AND id=?`, uid, ch.ID).Scan(&cur)
		exists := err == nil
		if exists && cur > ts {
			resp.Rejected = append(resp.Rejected, ch.ID)
			continue
		}
		if !exists {
			if count >= s.cfg.MaxRecords {
				fail(w, http.StatusRequestEntityTooLarge, "too_many_records", "تعداد موارد ذخیره‌شده به سقف مجاز رسیده است.")
				return
			}
			count++
		}
		seq++
		data := string(ch.Data)
		if ch.Deleted {
			data = ""
		}
		del := 0
		if ch.Deleted {
			del = 1
		}
		if _, err := tx.Exec(`INSERT INTO records(user_id, id, data, updated_at, deleted, seq) VALUES(?,?,?,?,?,?)
			ON CONFLICT(user_id, id) DO UPDATE SET data=excluded.data, updated_at=excluded.updated_at, deleted=excluded.deleted, seq=excluded.seq`,
			uid, ch.ID, data, ts, del, seq); err != nil {
			serverError(w, "sync upsert", err)
			return
		}
		accepted[ch.ID] = true
		resp.Accepted = append(resp.Accepted, ch.ID)
	}
	if len(accepted) > 0 {
		if _, err := tx.Exec(`UPDATE users SET seq=? WHERE id=?`, seq, uid); err != nil {
			serverError(w, "sync seq update", err)
			return
		}
	}

	// Everything newer than the client's cursor, except what it just sent us.
	sent := map[string]bool{}
	rows, err := tx.Query(`SELECT id, data, updated_at, deleted FROM records WHERE user_id=? AND seq>? ORDER BY seq`, uid, req.Cursor)
	if err != nil {
		serverError(w, "sync pull", err)
		return
	}
	for rows.Next() {
		var c changeOut
		var data string
		var del int
		if err := rows.Scan(&c.ID, &data, &c.UpdatedAt, &del); err != nil {
			rows.Close()
			serverError(w, "sync scan", err)
			return
		}
		if accepted[c.ID] {
			continue
		}
		c.Deleted = del == 1
		if !c.Deleted {
			c.Data = json.RawMessage(data)
		}
		resp.Changes = append(resp.Changes, c)
		sent[c.ID] = true
	}
	rows.Close()

	// A rejected write means the client holds an older copy: make sure it receives the winner.
	for _, id := range resp.Rejected {
		if sent[id] {
			continue
		}
		var c changeOut
		var data string
		var del int
		if err := tx.QueryRow(`SELECT id, data, updated_at, deleted FROM records WHERE user_id=? AND id=?`, uid, id).
			Scan(&c.ID, &data, &c.UpdatedAt, &del); err == nil {
			c.Deleted = del == 1
			if !c.Deleted {
				c.Data = json.RawMessage(data)
			}
			resp.Changes = append(resp.Changes, c)
		}
	}

	if err := tx.Commit(); err != nil {
		serverError(w, "sync commit", err)
		return
	}
	resp.Cursor = seq
	writeJSON(w, http.StatusOK, resp)
}
