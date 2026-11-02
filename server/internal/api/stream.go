package api

import (
	"crypto/tls"
	"encoding/json"
	"io"
	"mime"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strings"
	"sync"

	"nasdrama/server/internal/source"
	"nasdrama/server/internal/store"
)

// 三种源的统一出口：本地直接 ServeFile，WebDAV / Jellyfin 由后端代理，
// 全程透传 Range 头以支持拖动进度条与断点续传。
func (s *Server) handleStream(w http.ResponseWriter, r *http.Request, u *store.User) {
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
	switch src.Type {
	case "local":
		s.streamLocal(w, r, cfg, ep)
	case "webdav":
		s.proxyMedia(w, r, webdavFileURL(cfg, ep.SourceKey), cfg, ep)
	case "jellyfin", "emby":
		if r.URL.Query().Get("hls") == "1" {
			// HLS 转码清单无法在代理侧改写内部地址，直接 302 到媒体服务器
			target := strings.TrimRight(cfg.URL, "/") + "/Videos/" + url.PathEscape(ep.SourceKey) +
				"/master.m3u8?api_key=" + url.QueryEscape(cfg.APIKey)
			http.Redirect(w, r, target, http.StatusFound)
			return
		}
		target := strings.TrimRight(cfg.URL, "/") + "/Videos/" + url.PathEscape(ep.SourceKey) +
			"/stream?api_key=" + url.QueryEscape(cfg.APIKey) + "&static=true"
		s.proxyMedia(w, r, target, cfg, ep)
	default:
		writeErr(w, http.StatusInternalServerError, "未知媒体源类型")
	}
}

func (s *Server) streamLocal(w http.ResponseWriter, r *http.Request, cfg source.Config, ep *store.Episode) {
	if cfg.Path == "" {
		writeErr(w, http.StatusInternalServerError, "本地源未配置目录")
		return
	}
	root, err := filepath.Abs(cfg.Path)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "本地目录无效")
		return
	}
	full := filepath.Join(root, filepath.FromSlash(ep.SourceKey))
	if full != root && !strings.HasPrefix(full, root+string(filepath.Separator)) {
		writeErr(w, http.StatusNotFound, "非法路径")
		return
	}
	if _, err := os.Stat(full); err != nil {
		writeErr(w, http.StatusNotFound, "文件不存在")
		return
	}
	http.ServeFile(w, r, full)
}

// webdavFileURL 拼出 WebDAV 上文件/目录的完整 URL（逐段转义）。
func webdavFileURL(cfg source.Config, key string) string {
	base := strings.TrimRight(cfg.URL, "/")
	p := path.Join(normalizePath(cfg.BasePath), key)
	return base + escapeURLPath(p)
}

func normalizePath(b string) string {
	b = strings.TrimSpace(b)
	if b == "" {
		return "/"
	}
	if !strings.HasPrefix(b, "/") {
		b = "/" + b
	}
	b = strings.TrimRight(b, "/")
	if b == "" {
		return "/"
	}
	return b
}

func escapeURLPath(p string) string {
	segs := strings.Split(p, "/")
	for i := range segs {
		segs[i] = url.PathEscape(segs[i])
	}
	return strings.Join(segs, "/")
}

var (
	mediaClientOnce sync.Once
	mediaNormal     *http.Client
	mediaInsecure   *http.Client
)

func mediaHTTPClient(insecure bool) *http.Client {
	mediaClientOnce.Do(func() {
		mediaNormal = &http.Client{}
		mediaInsecure = &http.Client{
			Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}},
		}
	})
	if insecure {
		return mediaInsecure
	}
	return mediaNormal
}

// proxyMedia 把上游视频响应按原状态码与关键响应头转发给客户端。
func (s *Server) proxyMedia(w http.ResponseWriter, r *http.Request, target string, cfg source.Config, ep *store.Episode) {
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, target, nil)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "构建上游请求失败")
		return
	}
	if cfg.Username != "" || cfg.Password != "" {
		req.SetBasicAuth(cfg.Username, cfg.Password)
	}
	if rng := r.Header.Get("Range"); rng != "" {
		req.Header.Set("Range", rng)
	}
	resp, err := mediaHTTPClient(cfg.Insecure).Do(req)
	if err != nil {
		writeErr(w, http.StatusBadGateway, "上游请求失败: "+err.Error())
		return
	}
	defer resp.Body.Close()
	for _, h := range []string{"Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Content-Disposition", "Last-Modified"} {
		if v := resp.Header.Get(h); v != "" {
			w.Header().Set(h, v)
		}
	}
	ct := resp.Header.Get("Content-Type")
	if ct == "" || ct == "application/octet-stream" {
		if ep != nil && ep.Container != "" {
			if t := mime.TypeByExtension("." + ep.Container); t != "" {
				w.Header().Set("Content-Type", t)
			}
		}
	}
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}
