package main

import (
	"context"
	"embed"
	"io/fs"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"nasdrama/server/internal/api"
	"nasdrama/server/internal/scanner"
	"nasdrama/server/internal/store"
)

//go:embed all:webdist
var webDist embed.FS

var version = "1.0.0"

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func main() {
	port := envOr("NASDRAMA_PORT", "8654")
	dataDir := envOr("NASDRAMA_DATA_DIR", "./data")
	adminUser := envOr("NASDRAMA_ADMIN_USER", "admin")
	adminPass := envOr("NASDRAMA_ADMIN_PASSWORD", "admin")
	allowRegister := envOr("NASDRAMA_ALLOW_REGISTER", "true") == "true"
	scanOnStart := envOr("NASDRAMA_SCAN_ON_START", "true") == "true"
	ttlDays, err := strconv.Atoi(envOr("NASDRAMA_TOKEN_TTL_DAYS", "30"))
	if err != nil || ttlDays <= 0 {
		ttlDays = 30
	}

	st, err := store.Open(dataDir)
	if err != nil {
		log.Fatalf("打开数据库失败: %v", err)
	}
	if err := st.Migrate(); err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}
	if err := st.EnsureAdmin(adminUser, adminPass); err != nil {
		log.Fatalf("初始化管理员失败: %v", err)
	}
	if err := st.CleanExpiredTokens(); err != nil {
		log.Printf("清理过期 token 失败: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sc := scanner.New(st, dataDir)
	go sc.Run(ctx)

	svc := api.New(api.Options{
		Store:         st,
		Scanner:       sc,
		DataDir:       dataDir,
		Version:       version,
		AllowRegister: allowRegister,
		TokenTTL:      time.Duration(ttlDays) * 24 * time.Hour,
	})

	dist, err := fs.Sub(webDist, "webdist")
	if err != nil {
		log.Fatalf("内嵌前端资源异常: %v", err)
	}

	if scanOnStart {
		go sc.EnqueueAll()
	}

	srv := &http.Server{
		Addr:              ":" + port,
		Handler:           api.WithLogging(svc.Handler(dist)),
		ReadHeaderTimeout: 15 * time.Second,
	}

	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
		<-sig
		log.Println("收到退出信号，正在关闭…")
		shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer shutdownCancel()
		_ = srv.Shutdown(shutdownCtx)
		cancel()
	}()

	log.Printf("InhoDrama v%s 已启动: http://0.0.0.0:%s (数据目录: %s)", version, port, dataDir)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("HTTP 服务异常退出: %v", err)
	}
}
