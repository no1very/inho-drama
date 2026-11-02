package api

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"nasdrama/server/internal/source"
	"nasdrama/server/internal/store"
)

// handleSubtitle 提供同名 .srt 字幕：
// format=vtt（默认）返回转换为 WebVTT 的内容（网页端 <track> 用），
// format=srt 返回原始 SRT（安卓 Media3 原生支持）。
func (s *Server) handleSubtitle(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的单集 ID")
		return
	}
	ep, err := s.opts.Store.GetEpisode(id)
	if err != nil {
		writeErr(w, http.StatusNotFound, "单集不存在")
		return
	}
	series, err := s.opts.Store.GetSeries(ep.SeriesID)
	if err != nil {
		writeErr(w, http.StatusNotFound, "剧集不存在")
		return
	}
	src, err := s.opts.Store.GetSource(series.SourceID)
	if err != nil {
		writeErr(w, http.StatusNotFound, "媒体源不存在")
		return
	}
	var cfg source.Config
	if err := json.Unmarshal([]byte(src.Config), &cfg); err != nil {
		writeErr(w, http.StatusInternalServerError, "媒体源配置异常")
		return
	}

	var data []byte
	switch src.Type {
	case "local":
		data = localSubtitle(cfg.Path, ep.SourceKey)
	case "webdav":
		data = webdavSubtitle(r.Context(), cfg, ep.SourceKey)
	default:
		writeErr(w, http.StatusNotFound, "该媒体源不支持外挂字幕")
		return
	}
	if len(data) == 0 {
		writeErr(w, http.StatusNotFound, "未找到字幕文件")
		return
	}
	if r.URL.Query().Get("format") == "srt" {
		w.Header().Set("Content-Type", "application/x-subrip; charset=utf-8")
		_, _ = w.Write(data)
		return
	}
	w.Header().Set("Content-Type", "text/vtt; charset=utf-8")
	_, _ = w.Write(srtToVTT(data))
}

func localSubtitle(rootCfg, key string) []byte {
	root, err := filepath.Abs(rootCfg)
	if err != nil {
		return nil
	}
	full := filepath.Join(root, filepath.FromSlash(key))
	base := strings.TrimSuffix(full, filepath.Ext(full))
	for _, cand := range []string{base + ".srt", base + ".SRT", full + ".srt"} {
		if b, err := os.ReadFile(cand); err == nil && len(b) > 0 {
			return b
		}
	}
	return nil
}

func webdavSubtitle(ctx context.Context, cfg source.Config, key string) []byte {
	base := strings.TrimSuffix(key, path.Ext(key))
	target := webdavFileURL(cfg, base+".srt")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return nil
	}
	if cfg.Username != "" || cfg.Password != "" {
		req.SetBasicAuth(cfg.Username, cfg.Password)
	}
	client := &http.Client{Timeout: 15 * time.Second,
		Transport: mediaHTTPClient(cfg.Insecure).Transport}
	resp, err := client.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, 5<<20))
	if err != nil || len(b) == 0 {
		return nil
	}
	return b
}

var reSRTTime = regexp.MustCompile(`^\d{1,2}:\d{2}:\d{2},\d{1,3}\s*-->`)

// srtToVTT 把 SRT 转成 WebVTT（本身就是 WebVTT 的原样返回）。
func srtToVTT(data []byte) []byte {
	s := strings.TrimPrefix(string(data), "\ufeff")
	s = strings.ReplaceAll(s, "\r\n", "\n")
	if strings.HasPrefix(s, "WEBVTT") {
		return []byte(s)
	}
	lines := strings.Split(s, "\n")
	for i, ln := range lines {
		if reSRTTime.MatchString(ln) {
			lines[i] = strings.ReplaceAll(ln, ",", ".")
		}
	}
	out := "WEBVTT\n\n" + strings.Join(lines, "\n")
	return []byte(out)
}
