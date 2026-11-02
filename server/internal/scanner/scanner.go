package scanner

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	"nasdrama/server/internal/source"
	"nasdrama/server/internal/store"
)

// Scanner 维护一个单工作协程的扫描队列：NAS 上同时扫描多个源
// 容易打满磁盘/带宽，串行执行更稳妥。
type Scanner struct {
	st      *store.Store
	dataDir string
	queue   chan int64

	mu     sync.Mutex
	status Status
}

type Status struct {
	Running      bool   `json:"running"`
	SourceID     int64  `json:"source_id"`
	SourceName   string `json:"source_name"`
	StartedAt    int64  `json:"started_at"`
	FinishedAt   int64  `json:"finished_at"`
	LastError    string `json:"last_error"`
	SeriesCount  int64  `json:"series_count"`
	EpisodeCount int64  `json:"episode_count"`
}

func New(st *store.Store, dataDir string) *Scanner {
	return &Scanner{st: st, dataDir: dataDir, queue: make(chan int64, 128)}
}

func (sc *Scanner) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case id := <-sc.queue:
			sc.scanOne(ctx, id)
		}
	}
}

func (sc *Scanner) Enqueue(id int64) {
	select {
	case sc.queue <- id:
	default:
		log.Printf("扫描队列已满，忽略媒体源 %d", id)
	}
}

func (sc *Scanner) EnqueueAll() {
	ids, err := sc.st.EnabledSourceIDs()
	if err != nil {
		log.Printf("获取媒体源列表失败: %v", err)
		return
	}
	for _, id := range ids {
		sc.Enqueue(id)
	}
}

func (sc *Scanner) Status() Status {
	sc.mu.Lock()
	defer sc.mu.Unlock()
	return sc.status
}

func (sc *Scanner) scanOne(ctx context.Context, id int64) {
	src, err := sc.st.GetSource(id)
	if err != nil {
		log.Printf("扫描媒体源 %d 失败: %v", id, err)
		return
	}
	if !src.Enabled {
		return
	}
	sc.mu.Lock()
	sc.status = Status{Running: true, SourceID: id, SourceName: src.Name, StartedAt: time.Now().Unix()}
	sc.mu.Unlock()
	log.Printf("开始扫描媒体源 [%s] %s", src.Type, src.Name)

	items, scanErr := sc.scanSource(ctx, src)

	sc.mu.Lock()
	sc.status.Running = false
	sc.status.FinishedAt = time.Now().Unix()
	sc.mu.Unlock()

	if scanErr != nil {
		sc.st.SetSourceScanResult(id, scanErr.Error())
		sc.mu.Lock()
		sc.status.LastError = scanErr.Error()
		sc.mu.Unlock()
		log.Printf("扫描媒体源 [%s] 失败: %v", src.Name, scanErr)
		return
	}
	n, m, err := sc.st.ReplaceSourceContent(id, items)
	if err != nil {
		sc.st.SetSourceScanResult(id, err.Error())
		sc.mu.Lock()
		sc.status.LastError = err.Error()
		sc.mu.Unlock()
		log.Printf("写入扫描结果失败: %v", err)
		return
	}
	sc.st.SetSourceScanResult(id, "")
	sc.mu.Lock()
	sc.status.SeriesCount = n
	sc.status.EpisodeCount = m
	sc.status.LastError = ""
	sc.mu.Unlock()
	log.Printf("媒体源 [%s] 扫描完成: %d 部剧 / %d 集", src.Name, n, m)
}

func (sc *Scanner) scanSource(ctx context.Context, src *store.Source) ([]source.ScannedSeries, error) {
	var cfg source.Config
	if err := json.Unmarshal([]byte(src.Config), &cfg); err != nil {
		return nil, fmt.Errorf("解析源配置失败: %w", err)
	}
	s, err := source.Build(src.Type, cfg)
	if err != nil {
		return nil, err
	}
	cctx, cancel := context.WithTimeout(ctx, 30*time.Minute)
	defer cancel()
	return s.Scan(cctx)
}
