package store

import (
	"database/sql"
	"errors"
	"time"
)

type Source struct {
	ID         int64
	Type       string
	Name       string
	Config     string // JSON 序列化后的 source.Config
	Enabled    bool
	LastScanAt int64
	LastError  string
}

const sourceCols = `id, type, name, config, enabled, last_scan_at, last_error`

func scanSource(row interface{ Scan(...any) error }) (*Source, error) {
	var src Source
	var enabled int
	err := row.Scan(&src.ID, &src.Type, &src.Name, &src.Config, &enabled, &src.LastScanAt, &src.LastError)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	src.Enabled = enabled != 0
	return &src, nil
}

func (s *Store) ListSources() ([]Source, error) {
	rows, err := s.db.Query(`SELECT ` + sourceCols + ` FROM sources ORDER BY id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Source
	for rows.Next() {
		src, err := scanSource(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *src)
	}
	return out, rows.Err()
}

func (s *Store) GetSource(id int64) (*Source, error) {
	return scanSource(s.db.QueryRow(`SELECT `+sourceCols+` FROM sources WHERE id = ?`, id))
}

func (s *Store) CreateSource(typ, name, config string, enabled bool) (int64, error) {
	res, err := s.db.Exec(`INSERT INTO sources(type, name, config, enabled, created_at) VALUES(?,?,?,?,?)`,
		typ, name, config, boolInt(enabled), time.Now().Unix())
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (s *Store) UpdateSource(id int64, typ, name, config string, enabled bool) error {
	_, err := s.db.Exec(`UPDATE sources SET type=?, name=?, config=?, enabled=? WHERE id=?`,
		typ, name, config, boolInt(enabled), id)
	return err
}

func (s *Store) DeleteSource(id int64) error {
	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.Exec(`DELETE FROM episodes WHERE series_id IN (SELECT id FROM series WHERE source_id=?)`, id); err != nil {
		return err
	}
	if _, err := tx.Exec(`DELETE FROM episode_state WHERE series_id IN (SELECT id FROM series WHERE source_id=?)`, id); err != nil {
		return err
	}
	if _, err := tx.Exec(`DELETE FROM series WHERE source_id=?`, id); err != nil {
		return err
	}
	if _, err := tx.Exec(`DELETE FROM sources WHERE id=?`, id); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) SetSourceScanResult(id int64, lastErr string) error {
	_, err := s.db.Exec(`UPDATE sources SET last_scan_at=?, last_error=? WHERE id=?`, time.Now().Unix(), lastErr, id)
	return err
}

func (s *Store) EnabledSourceIDs() ([]int64, error) {
	rows, err := s.db.Query(`SELECT id FROM sources WHERE enabled = 1 ORDER BY id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		out = append(out, id)
	}
	return out, rows.Err()
}

func (s *Store) SourceSeriesCount(id int64) (int64, error) {
	var n int64
	err := s.db.QueryRow(`SELECT COUNT(*) FROM series WHERE source_id=?`, id).Scan(&n)
	return n, err
}

func boolInt(b bool) int {
	if b {
		return 1
	}
	return 0
}
