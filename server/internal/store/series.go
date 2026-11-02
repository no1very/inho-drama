package store

import (
	"database/sql"
	"errors"
	"fmt"
	"math/rand"
	"strings"
	"time"

	"nasdrama/server/internal/source"
)

type Series struct {
	ID           int64
	SourceID     int64
	SourceKey    string
	Title        string
	Intro        string
	Category     string
	CoverPath    string
	Hot          int64
	EpisodeCount int64
	UpdatedAt    int64
	SourceName   string // 来自 JOIN，非持久化字段
}

type Episode struct {
	ID        int64
	SeriesID  int64
	Index     int64
	Title     string
	SourceKey string
	Container string
	Size      int64   // -1 表示未知
	Duration  float64 // -1 表示未知
}

// ReplaceSourceContent 用一次扫描的完整结果替换该源下的全部剧集数据。
// series 行通过 (source_id, source_key) upsert 保持 id 稳定（进度按 series_id+集序号 存，
// 因此重建 episodes 不影响观看记录）。
func (s *Store) ReplaceSourceContent(sourceID int64, items []source.ScannedSeries) (int64, int64, error) {
	tx, err := s.db.Begin()
	if err != nil {
		return 0, 0, err
	}
	defer tx.Rollback()
	now := time.Now().Unix()
	var seriesN, epN int64
	for _, item := range items {
		var sid int64
		err := tx.QueryRow(`
			INSERT INTO series(source_id, source_key, title, intro, category, cover_path, hot, episode_count, updated_at)
			VALUES(?,?,?,?,?,?,?,?,?)
			ON CONFLICT(source_id, source_key) DO UPDATE SET
				title=excluded.title, intro=excluded.intro, category=excluded.category,
				cover_path=excluded.cover_path, episode_count=excluded.episode_count,
				updated_at=excluded.updated_at
			RETURNING id
		`, sourceID, item.Key, item.Title, item.Intro, item.Category, item.CoverPath,
			rand.Int63n(1_000_000_000), len(item.Episodes), now).Scan(&sid)
		if err != nil {
			return 0, 0, fmt.Errorf("写入剧集 %s: %w", item.Key, err)
		}
		if _, err := tx.Exec(`DELETE FROM episodes WHERE series_id=?`, sid); err != nil {
			return 0, 0, err
		}
		for _, ep := range item.Episodes {
			if _, err := tx.Exec(`INSERT INTO episodes(series_id, idx, title, source_key, container, size, duration)
				VALUES(?,?,?,?,?,?,?)`,
				sid, ep.Index, ep.Title, ep.Key, ep.Container, ep.Size, ep.Duration); err != nil {
				return 0, 0, fmt.Errorf("写入单集 %s: %w", ep.Key, err)
			}
			epN++
		}
		seriesN++
	}
	// 清掉本次扫描中已经消失的剧集
	args := []any{sourceID}
	placeholders := make([]string, 0, len(items))
	for _, item := range items {
		placeholders = append(placeholders, "?")
		args = append(args, item.Key)
	}
	del := `DELETE FROM series WHERE source_id=?`
	if len(items) > 0 {
		del += ` AND source_key NOT IN (` + strings.Join(placeholders, ",") + `)`
	}
	if _, err := tx.Exec(del, args...); err != nil {
		return 0, 0, err
	}
	if err := tx.Commit(); err != nil {
		return 0, 0, err
	}
	return seriesN, epN, nil
}

const seriesCols = `s.id, s.source_id, s.source_key, s.title, s.intro, s.category, s.cover_path, s.hot, s.episode_count, s.updated_at, so.name`

const seriesJoin = `FROM series s JOIN sources so ON so.id = s.source_id`

func scanSeries(row interface{ Scan(...any) error }) (*Series, error) {
	var v Series
	err := row.Scan(&v.ID, &v.SourceID, &v.SourceKey, &v.Title, &v.Intro, &v.Category,
		&v.CoverPath, &v.Hot, &v.EpisodeCount, &v.UpdatedAt, &v.SourceName)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &v, nil
}

func (s *Store) ListFeed(offset, limit int) ([]Series, bool, error) {
	rows, err := s.db.Query(`SELECT `+seriesCols+` `+seriesJoin+`
		WHERE s.episode_count > 0 ORDER BY s.hot DESC LIMIT ? OFFSET ?`, limit+1, offset)
	if err != nil {
		return nil, false, err
	}
	defer rows.Close()
	var out []Series
	for rows.Next() {
		v, err := scanSeries(rows)
		if err != nil {
			return nil, false, err
		}
		out = append(out, *v)
	}
	if err := rows.Err(); err != nil {
		return nil, false, err
	}
	more := len(out) > limit
	if more {
		out = out[:limit]
	}
	return out, more, nil
}

func (s *Store) SearchSeries(keyword string, offset, limit int) ([]Series, int64, error) {
	where := `WHERE s.episode_count > 0`
	var args []any
	if kw := strings.TrimSpace(keyword); kw != "" {
		where += ` AND s.title LIKE ?`
		args = append(args, "%"+kw+"%")
	}
	var total int64
	if err := s.db.QueryRow(`SELECT COUNT(*) `+seriesJoin+` `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	q := `SELECT ` + seriesCols + ` ` + seriesJoin + ` ` + where + ` ORDER BY s.updated_at DESC, s.hot DESC LIMIT ? OFFSET ?`
	args = append(args, limit, offset)
	rows, err := s.db.Query(q, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []Series
	for rows.Next() {
		v, err := scanSeries(rows)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, *v)
	}
	return out, total, rows.Err()
}

func (s *Store) GetSeries(id int64) (*Series, error) {
	return scanSeries(s.db.QueryRow(`SELECT `+seriesCols+` `+seriesJoin+` WHERE s.id = ?`, id))
}

const episodeCols = `id, series_id, idx, title, source_key, container, size, duration`

func scanEpisode(row interface{ Scan(...any) error }) (*Episode, error) {
	var v Episode
	err := row.Scan(&v.ID, &v.SeriesID, &v.Index, &v.Title, &v.SourceKey, &v.Container, &v.Size, &v.Duration)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &v, nil
}

func (s *Store) ListEpisodes(seriesID int64) ([]Episode, error) {
	rows, err := s.db.Query(`SELECT `+episodeCols+` FROM episodes WHERE series_id = ? ORDER BY idx`, seriesID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Episode
	for rows.Next() {
		v, err := scanEpisode(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *v)
	}
	return out, rows.Err()
}

func (s *Store) GetEpisode(id int64) (*Episode, error) {
	return scanEpisode(s.db.QueryRow(`SELECT `+episodeCols+` FROM episodes WHERE id = ?`, id))
}

func (s *Store) GetFirstEpisode(seriesID int64) (*Episode, error) {
	return scanEpisode(s.db.QueryRow(`SELECT `+episodeCols+` FROM episodes WHERE series_id = ? ORDER BY idx LIMIT 1`, seriesID))
}

// EpisodeIndex 返回单集所属剧 id 与集序号，用于把进度写到稳定键上。
func (s *Store) EpisodeIndex(episodeID int64) (int64, int64, error) {
	var seriesID, idx int64
	err := s.db.QueryRow(`SELECT series_id, idx FROM episodes WHERE id = ?`, episodeID).Scan(&seriesID, &idx)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, 0, ErrNotFound
	}
	return seriesID, idx, err
}

func (s *Store) SaveState(userID, seriesID, idx int64, position, duration float64, completed bool) error {
	if position < 0 {
		position = 0
	}
	if duration < 0 {
		duration = -1
	}
	_, err := s.db.Exec(`INSERT INTO episode_state(user_id, series_id, idx, position, duration, completed, updated_at)
		VALUES(?,?,?,?,?,?,?)
		ON CONFLICT(user_id, series_id, idx) DO UPDATE SET
			position=excluded.position, duration=excluded.duration,
			completed=excluded.completed, updated_at=excluded.updated_at`,
		userID, seriesID, idx, position, duration, boolInt(completed), time.Now().Unix())
	return err
}

type EpisodeState struct {
	EpisodeID int64
	Index     int64
	Position  float64
	Duration  float64
	Completed bool
	UpdatedAt int64
}

func (s *Store) SeriesStates(userID, seriesID int64) ([]EpisodeState, error) {
	rows, err := s.db.Query(`SELECT e.id, es.idx, es.position, es.duration, es.completed, es.updated_at
		FROM episode_state es JOIN episodes e ON e.series_id = es.series_id AND e.idx = es.idx
		WHERE es.user_id = ? AND es.series_id = ? ORDER BY es.idx`, userID, seriesID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []EpisodeState
	for rows.Next() {
		var st EpisodeState
		var completed int
		if err := rows.Scan(&st.EpisodeID, &st.Index, &st.Position, &st.Duration, &completed, &st.UpdatedAt); err != nil {
			return nil, err
		}
		st.Completed = completed != 0
		out = append(out, st)
	}
	return out, rows.Err()
}

// ContinueEpisode 返回建议续播的一集：最近一个未看完的单集；没有任何记录时返回第 1 集。
func (s *Store) ContinueEpisode(userID, seriesID int64) (*Episode, error) {
	ep, err := scanEpisode(s.db.QueryRow(`SELECT `+episodeCols+`
		FROM episode_state es JOIN episodes e ON e.series_id = es.series_id AND e.idx = es.idx
		WHERE es.user_id = ? AND es.series_id = ? AND es.completed = 0 AND es.position > 5
		ORDER BY es.updated_at DESC LIMIT 1`, userID, seriesID))
	if err == nil {
		return ep, nil
	}
	if !errors.Is(err, ErrNotFound) {
		return nil, err
	}
	return s.GetFirstEpisode(seriesID)
}

type HistoryItem struct {
	Series    Series
	Episode   Episode
	Position  float64
	Duration  float64
	Completed bool
	UpdatedAt int64
}

// History 返回每部剧最新一条观看记录。
// unfinishedOnly=true 时只取未看完的记录（首页「继续观看」用，
// 看完的剧不再出现在首页，但历史页仍可见）。
func (s *Store) History(userID int64, limit int, unfinishedOnly bool) ([]HistoryItem, error) {
	finishedFilter := ""
	if unfinishedOnly {
		finishedFilter = " AND es.completed = 0"
	}
	q := `SELECT ` + seriesCols + `,
		e.id, e.series_id, e.idx, e.title, e.source_key, e.container, e.size, e.duration,
		es.position, es.duration, es.completed, es.updated_at
		FROM episode_state es
		JOIN (SELECT series_id, MAX(updated_at) AS m FROM episode_state
			WHERE user_id = ?` + finishedFilter + ` GROUP BY series_id) t
			ON t.series_id = es.series_id AND t.m = es.updated_at
		JOIN series s ON s.id = es.series_id
		JOIN sources so ON so.id = s.source_id
		JOIN episodes e ON e.series_id = es.series_id AND e.idx = es.idx
		WHERE es.user_id = ?` + finishedFilter + `
		GROUP BY es.series_id
		ORDER BY es.updated_at DESC
		LIMIT ?`
	rows, err := s.db.Query(q, userID, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []HistoryItem
	for rows.Next() {
		var it HistoryItem
		var completed int
		err := rows.Scan(&it.Series.ID, &it.Series.SourceID, &it.Series.SourceKey, &it.Series.Title,
			&it.Series.Intro, &it.Series.Category, &it.Series.CoverPath, &it.Series.Hot,
			&it.Series.EpisodeCount, &it.Series.UpdatedAt, &it.Series.SourceName,
			&it.Episode.ID, &it.Episode.SeriesID, &it.Episode.Index, &it.Episode.Title,
			&it.Episode.SourceKey, &it.Episode.Container, &it.Episode.Size, &it.Episode.Duration,
			&it.Position, &it.Duration, &completed, &it.UpdatedAt)
		if err != nil {
			return nil, err
		}
		it.Completed = completed != 0
		out = append(out, it)
	}
	return out, rows.Err()
}

func (s *Store) ClearHistory(userID, seriesID int64) error {
	_, err := s.db.Exec(`DELETE FROM episode_state WHERE user_id = ? AND series_id = ?`, userID, seriesID)
	return err
}

func (s *Store) ClearAllHistory(userID int64) error {
	_, err := s.db.Exec(`DELETE FROM episode_state WHERE user_id = ?`, userID)
	return err
}
