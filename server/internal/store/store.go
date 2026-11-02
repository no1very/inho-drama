package store

import (
	"database/sql"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	_ "modernc.org/sqlite"
)

// Store 封装 SQLite 访问。家用 NAS 场景并发量低，
// 固定单连接即可彻底规避 SQLITE_BUSY，配合 WAL 模式读写互不阻塞。
type Store struct {
	db *sql.DB
}

func Open(dataDir string) (*Store, error) {
	if err := os.MkdirAll(dataDir, 0o755); err != nil {
		return nil, err
	}
	abs, err := filepath.Abs(filepath.Join(dataDir, "nasdrama.db"))
	if err != nil {
		return nil, err
	}
	p := filepath.ToSlash(abs)
	if !strings.HasPrefix(p, "/") {
		p = "/" + p
	}
	u := url.URL{
		Scheme:   "file",
		Path:     p,
		RawQuery: "_pragma=busy_timeout(10000)&_pragma=journal_mode(WAL)",
	}
	db, err := sql.Open("sqlite", u.String())
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	if err := db.Ping(); err != nil {
		return nil, err
	}
	return &Store{db: db}, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) Migrate() error {
	_, err := s.db.Exec(schema)
	return err
}

const schema = `
CREATE TABLE IF NOT EXISTS users (
	id            INTEGER PRIMARY KEY AUTOINCREMENT,
	username      TEXT NOT NULL UNIQUE,
	password_hash TEXT NOT NULL,
	created_at    INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS tokens (
	token      TEXT PRIMARY KEY,
	user_id    INTEGER NOT NULL,
	expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sources (
	id           INTEGER PRIMARY KEY AUTOINCREMENT,
	type         TEXT NOT NULL,
	name         TEXT NOT NULL,
	config       TEXT NOT NULL DEFAULT '{}',
	enabled      INTEGER NOT NULL DEFAULT 1,
	last_scan_at INTEGER NOT NULL DEFAULT 0,
	last_error   TEXT NOT NULL DEFAULT '',
	created_at   INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS series (
	id            INTEGER PRIMARY KEY AUTOINCREMENT,
	source_id     INTEGER NOT NULL,
	source_key    TEXT NOT NULL,
	title         TEXT NOT NULL,
	intro         TEXT NOT NULL DEFAULT '',
	category      TEXT NOT NULL DEFAULT '',
	cover_path    TEXT NOT NULL DEFAULT '',
	hot           INTEGER NOT NULL DEFAULT 0,
	episode_count INTEGER NOT NULL DEFAULT 0,
	updated_at    INTEGER NOT NULL DEFAULT 0,
	UNIQUE(source_id, source_key)
);
CREATE TABLE IF NOT EXISTS episodes (
	id         INTEGER PRIMARY KEY AUTOINCREMENT,
	series_id  INTEGER NOT NULL,
	idx        INTEGER NOT NULL,
	title      TEXT NOT NULL,
	source_key TEXT NOT NULL,
	container  TEXT NOT NULL DEFAULT '',
	size       INTEGER NOT NULL DEFAULT -1,
	duration   REAL NOT NULL DEFAULT -1,
	UNIQUE(series_id, idx)
);
-- 观看进度以 (用户, 剧, 集序号) 为主键而不是单集 id，
-- 这样重新扫描重建 episodes 表后进度依然有效。
CREATE TABLE IF NOT EXISTS episode_state (
	user_id    INTEGER NOT NULL,
	series_id  INTEGER NOT NULL,
	idx        INTEGER NOT NULL,
	position   REAL NOT NULL DEFAULT 0,
	duration   REAL NOT NULL DEFAULT -1,
	completed  INTEGER NOT NULL DEFAULT 0,
	updated_at INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (user_id, series_id, idx)
);
CREATE INDEX IF NOT EXISTS idx_series_hot ON series(hot DESC);
CREATE INDEX IF NOT EXISTS idx_episodes_series ON episodes(series_id, idx);
CREATE INDEX IF NOT EXISTS idx_state_series ON episode_state(series_id, idx);
`
