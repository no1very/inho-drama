package api

import (
	"encoding/json"
	"io/fs"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"

	"nasdrama/server/internal/scanner"
	"nasdrama/server/internal/store"
)

type Options struct {
	Store         *store.Store
	Scanner       *scanner.Scanner
	DataDir       string
	Version       string
	AllowRegister bool
	TokenTTL      time.Duration
}

type Server struct {
	opts    Options
	coverMu map[int64]*sync.Mutex
	coverLk sync.Mutex
}

func New(o Options) *Server {
	return &Server{opts: o, coverMu: map[int64]*sync.Mutex{}}
}

func (s *Server) Handler(dist fs.FS) http.Handler {
	mux := http.NewServeMux()

	// 无需鉴权
	mux.HandleFunc("GET /api/v1/health", s.handleHealth)
	mux.HandleFunc("GET /api/v1/server-info", s.handleServerInfo)
	mux.HandleFunc("POST /api/v1/auth/login", s.handleLogin)
	mux.HandleFunc("POST /api/v1/auth/register", s.handleRegister)

	// 需要鉴权
	mux.HandleFunc("GET /api/v1/auth/me", s.auth(s.handleMe))
	mux.HandleFunc("POST /api/v1/auth/logout", s.auth(s.handleLogout))
	mux.HandleFunc("GET /api/v1/feed", s.auth(s.handleFeed))
	mux.HandleFunc("GET /api/v1/series", s.auth(s.handleSeriesList))
	mux.HandleFunc("GET /api/v1/series/{id}", s.auth(s.handleSeriesDetail))
	mux.HandleFunc("POST /api/v1/progress", s.auth(s.handleProgress))
	mux.HandleFunc("GET /api/v1/history", s.auth(s.handleHistory))
	mux.HandleFunc("DELETE /api/v1/history/{id}", s.auth(s.handleDeleteHistory))
	mux.HandleFunc("GET /api/v1/stream/{id}", s.auth(s.handleStream))
	mux.HandleFunc("GET /api/v1/cover/{id}", s.auth(s.handleCover))
	mux.HandleFunc("GET /api/v1/subtitle/{id}", s.auth(s.handleSubtitle))
	mux.HandleFunc("GET /api/v1/sources", s.auth(s.handleSourcesList))
	mux.HandleFunc("POST /api/v1/sources", s.auth(s.handleSourceCreate))
	mux.HandleFunc("PUT /api/v1/sources/{id}", s.auth(s.handleSourceUpdate))
	mux.HandleFunc("DELETE /api/v1/sources/{id}", s.auth(s.handleSourceDelete))
	mux.HandleFunc("POST /api/v1/sources/{id}/scan", s.auth(s.handleSourceScan))
	mux.HandleFunc("POST /api/v1/scan/all", s.auth(s.handleScanAll))
	mux.HandleFunc("GET /api/v1/scan/status", s.auth(s.handleScanStatus))

	// 内嵌 H5 前端（SPA fallback 到 index.html）
	fileServer := http.FileServer(http.FS(dist))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/") {
			writeErr(w, http.StatusNotFound, "接口不存在")
			return
		}
		p := strings.TrimPrefix(r.URL.Path, "/")
		if p == "" {
			p = "index.html"
		}
		if f, err := dist.Open(p); err == nil {
			f.Close()
			fileServer.ServeHTTP(w, r)
			return
		}
		b, err := fs.ReadFile(dist, "index.html")
		if err != nil {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache")
		_, _ = w.Write(b)
	})

	return s.withCORS(mux)
}

func (s *Server) withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

type loggingWriter struct {
	http.ResponseWriter
	code int
}

func (l *loggingWriter) WriteHeader(code int) {
	l.code = code
	l.ResponseWriter.WriteHeader(code)
}

func WithLogging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rw := &loggingWriter{ResponseWriter: w, code: 200}
		next.ServeHTTP(rw, r)
		if strings.HasPrefix(r.URL.Path, "/api/") {
			log.Printf("%s %s -> %d (%dms)", r.Method, r.URL.Path, rw.code, time.Since(start).Milliseconds())
		}
	})
}

// ---------- 通用小工具 ----------

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]string{"error": msg})
}

func writeOK(w http.ResponseWriter) {
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func readJSON(w http.ResponseWriter, r *http.Request, dst any) error {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	if err := json.NewDecoder(r.Body).Decode(dst); err != nil {
		return err
	}
	return nil
}

func bearerToken(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if strings.HasPrefix(h, "Bearer ") {
		return strings.TrimSpace(h[len("Bearer "):])
	}
	return ""
}

// auth 鉴权中间件；请求头优先，同时接受 ?token= 查询参数
// （视频/封面/字幕元素与 keepalive 上报等场景无法附带请求头）。
func (s *Server) auth(next func(http.ResponseWriter, *http.Request, *store.User)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tok := bearerToken(r)
		if tok == "" {
			tok = r.URL.Query().Get("token")
		}
		u, err := s.opts.Store.UserByToken(tok)
		if err != nil {
			writeErr(w, http.StatusUnauthorized, "未登录或登录已过期")
			return
		}
		next(w, r, u)
	}
}
