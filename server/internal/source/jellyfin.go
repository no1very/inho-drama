package source

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"
)

// Jellyfin 源：直接读取已有媒体库的 Series/Episode 元数据，
// 不做任何刮削，播放走 Jellyfin 的流媒体接口。
type Jellyfin struct{ cfg Config }

type jfItem struct {
	Id           string  `json:"Id"`
	Name         string  `json:"Name"`
	Overview     string  `json:"Overview"`
	Genres       []string `json:"Genres"`
	IndexNumber  *int64  `json:"IndexNumber"`
	RunTimeTicks int64   `json:"RunTimeTicks"`
	Container    string  `json:"Container"`
	MediaSources []struct {
		Size      int64  `json:"Size"`
		Container string `json:"Container"`
	} `json:"MediaSources"`
}

func (j *Jellyfin) httpClient() *http.Client {
	if j.cfg.Insecure {
		return &http.Client{Timeout: 60 * time.Second,
			Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}}
	}
	return &http.Client{Timeout: 60 * time.Second}
}

func (j *Jellyfin) get(ctx context.Context, p string, out any) error {
	u := strings.TrimRight(j.cfg.URL, "/") + p
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return err
	}
	req.Header.Set("X-Emby-Token", j.cfg.APIKey)
	resp, err := j.httpClient().Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return fmt.Errorf("Jellyfin 返回 %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

func (j *Jellyfin) Scan(ctx context.Context) ([]ScannedSeries, error) {
	q := url.Values{}
	q.Set("IncludeItemTypes", "Series")
	q.Set("Recursive", "true")
	q.Set("Fields", "Overview,Genres")
	q.Set("Limit", "20000")
	if j.cfg.UserID != "" {
		q.Set("UserId", j.cfg.UserID)
	}
	var resp struct {
		Items []jfItem `json:"Items"`
	}
	if err := j.get(ctx, "/Items?"+q.Encode(), &resp); err != nil {
		return nil, fmt.Errorf("拉取 Jellyfin 剧集列表失败: %w", err)
	}
	var out []ScannedSeries
	for _, it := range resp.Items {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		eps := j.scanEpisodes(ctx, it.Id)
		if len(eps) == 0 {
			continue
		}
		sort.Slice(eps, func(a, b int) bool { return eps[a].Index < eps[b].Index })
		out = append(out, ScannedSeries{
			Key:      it.Id,
			Title:    it.Name,
			Intro:    it.Overview,
			Category: strings.Join(it.Genres, " / "),
			Episodes: eps,
		})
	}
	return out, nil
}

func (j *Jellyfin) scanEpisodes(ctx context.Context, seriesID string) []ScannedEpisode {
	q := url.Values{}
	q.Set("Fields", "MediaSources")
	q.Set("Limit", "2000")
	if j.cfg.UserID != "" {
		q.Set("UserId", j.cfg.UserID)
	}
	var resp struct {
		Items []jfItem `json:"Items"`
	}
	if err := j.get(ctx, "/Shows/"+url.PathEscape(seriesID)+"/Episodes?"+q.Encode(), &resp); err != nil {
		return nil
	}
	var out []ScannedEpisode
	seen := map[int64]bool{}
	next := int64(1)
	for _, e := range resp.Items {
		idx := next
		if e.IndexNumber != nil && *e.IndexNumber > 0 {
			idx = *e.IndexNumber
		}
		for seen[idx] && idx < 9999 {
			idx++
		}
		seen[idx] = true
		next = idx + 1
		container := strings.ToLower(e.Container)
		size := int64(-1)
		if len(e.MediaSources) > 0 {
			if e.MediaSources[0].Size > 0 {
				size = e.MediaSources[0].Size
			}
			if container == "" {
				container = strings.ToLower(e.MediaSources[0].Container)
			}
		}
		dur := float64(-1)
		if e.RunTimeTicks > 0 {
			dur = float64(e.RunTimeTicks) / 1e7
		}
		out = append(out, ScannedEpisode{
			Index:     idx,
			Title:     e.Name,
			Key:       e.Id,
			Container: container,
			Size:      size,
			Duration:  dur,
		})
	}
	return out
}
