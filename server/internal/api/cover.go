package api

import (
	"context"
	_ "embed"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"nasdrama/server/internal/source"
	"nasdrama/server/internal/store"
)

const coverCacheDir = "covers"

// 无海报剧集的内置默认封面（深色渐变 + 播放键），由 /cover/{id} 统一下发，各端展示一致
//
//go:embed assets/default_cover.png
var defaultCoverPNG []byte

func (s *Server) coverLock(id int64) *sync.Mutex {
	s.coverLk.Lock()
	defer s.coverLk.Unlock()
	mu, ok := s.coverMu[id]
	if !ok {
		mu = &sync.Mutex{}
		s.coverMu[id] = mu
	}
	return mu
}

func fileExists(p string) bool {
	info, err := os.Stat(p)
	return err == nil && !info.IsDir()
}

// handleCover 封面解析顺序：
//  1. 本地/远端目录内的 poster.jpg|folder.jpg|cover.jpg（或 Jellyfin 海报 API）
//  2. 均无 → 返回内置默认封面（assets/default_cover.png，不落盘）
func (s *Server) handleCover(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的剧集 ID")
		return
	}
	series, err := s.opts.Store.GetSeries(id)
	if err != nil {
		writeErr(w, http.StatusNotFound, "剧集不存在")
		return
	}
	cachePath := filepath.Join(s.opts.DataDir, coverCacheDir, fmt.Sprintf("cover_%d.jpg", id))
	if fileExists(cachePath) {
		s.serveCoverFile(w, r, cachePath)
		return
	}

	mu := s.coverLock(id)
	mu.Lock()
	defer mu.Unlock()
	if fileExists(cachePath) {
		s.serveCoverFile(w, r, cachePath)
		return
	}

	var cfg source.Config
	if src, err := s.opts.Store.GetSource(series.SourceID); err == nil {
		_ = json.Unmarshal([]byte(src.Config), &cfg)
		s.saveSourceCover(r.Context(), src.Type, cfg, series, cachePath)
	}
	if fileExists(cachePath) {
		s.serveCoverFile(w, r, cachePath)
		return
	}
	// 无海报：返回内置默认封面（不再截视频随机帧）
	serveDefaultCover(w)
}

// serveDefaultCover 返回内置默认封面 PNG
func serveDefaultCover(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	_, _ = w.Write(defaultCoverPNG)
}

func (s *Server) serveCoverFile(w http.ResponseWriter, r *http.Request, p string) {
	w.Header().Set("Cache-Control", "public, max-age=86400")
	http.ServeFile(w, r, p)
}

func (s *Server) saveSourceCover(ctx context.Context, typ string, cfg source.Config, series *store.Series, cachePath string) {
	dir := filepath.Dir(cachePath)
	_ = os.MkdirAll(dir, 0o755)
	tmp := cachePath + ".tmp"
	switch typ {
	case "local":
		if series.CoverPath == "" || cfg.Path == "" {
			return
		}
		root, err := filepath.Abs(cfg.Path)
		if err != nil {
			return
		}
		p := filepath.Join(root, filepath.FromSlash(series.CoverPath))
		if !fileExists(p) {
			return
		}
		if b, err := os.ReadFile(p); err == nil && looksLikeImage(b) {
			_ = os.WriteFile(tmp, b, 0o644)
		}
	case "webdav":
		for _, name := range []string{"poster.jpg", "folder.jpg", "cover.jpg", "poster.png", "cover.png"} {
			target := webdavFileURL(cfg, series.SourceKey+"/"+name)
			req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
			if err != nil {
				continue
			}
			if cfg.Username != "" || cfg.Password != "" {
				req.SetBasicAuth(cfg.Username, cfg.Password)
			}
			client := &http.Client{Timeout: 30 * time.Second,
				Transport: mediaHTTPClient(cfg.Insecure).Transport}
			resp, err := client.Do(req)
			if err != nil {
				continue
			}
			b, readErr := io.ReadAll(io.LimitReader(resp.Body, 12<<20))
			resp.Body.Close()
			if readErr == nil && resp.StatusCode == http.StatusOK && looksLikeImage(b) {
				_ = os.WriteFile(tmp, b, 0o644)
				break
			}
		}
	case "jellyfin", "emby":
		target := strings.TrimRight(cfg.URL, "/") + "/Items/" + url.PathEscape(series.SourceKey) +
			"/Images/Primary?api_key=" + url.QueryEscape(cfg.APIKey) + "&maxWidth=640"
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
		if err != nil {
			return
		}
		client := &http.Client{Timeout: 30 * time.Second,
			Transport: mediaHTTPClient(cfg.Insecure).Transport}
		resp, err := client.Do(req)
		if err != nil {
			return
		}
		b, readErr := io.ReadAll(io.LimitReader(resp.Body, 12<<20))
		resp.Body.Close()
		if readErr == nil && resp.StatusCode == http.StatusOK && looksLikeImage(b) {
			_ = os.WriteFile(tmp, b, 0o644)
		}
	}
	if fileExists(tmp) {
		_ = os.Rename(tmp, cachePath)
	} else {
		_ = os.Remove(tmp)
	}
}

func looksLikeImage(b []byte) bool {
	if len(b) < 12 {
		return false
	}
	if b[0] == 0xFF && b[1] == 0xD8 { // JPEG
		return true
	}
	if b[0] == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' { // PNG
		return true
	}
	if string(b[:4]) == "RIFF" && string(b[8:12]) == "WEBP" { // WebP
		return true
	}
	if b[0] == '<' && b[1] == 's' { // SVG
		return true
	}
	return false
}
