package source

import (
	"context"
	"fmt"
)

// Config 三种媒体源的配置，JSON 存于 sources 表。
type Config struct {
	// local
	Path string `json:"path,omitempty"`
	// webdav / jellyfin
	URL      string `json:"url,omitempty"`
	Username string `json:"username,omitempty"`
	Password string `json:"password,omitempty"`
	// webdav：相对 dav 根目录的起始路径，默认 "/"
	BasePath string `json:"base_path,omitempty"`
	// jellyfin
	APIKey string `json:"api_key,omitempty"`
	UserID string `json:"user_id,omitempty"`
	// jellyfin：网页端优先用 HLS 转码播放
	Transcode bool `json:"transcode,omitempty"`
	// 跳过 HTTPS 证书校验（自签名场景）
	Insecure bool `json:"insecure,omitempty"`
}

// ScannedEpisode 扫描得到的单集。Key 的含义随源类型不同：
// local/webdav 为相对路径（/ 分隔），jellyfin 为 ItemId。
type ScannedEpisode struct {
	Index     int64
	Title     string
	Key       string
	Container string
	Size      int64   // -1 未知
	Duration  float64 // -1 未知
}

type ScannedSeries struct {
	Key       string
	Title     string
	Intro     string
	Category  string
	CoverPath string // local/webdav：相对路径；jellyfin 不使用（图片走 Jellyfin API）
	Episodes  []ScannedEpisode
}

type Scanner interface {
	Scan(ctx context.Context) ([]ScannedSeries, error)
}

func Build(typ string, cfg Config) (Scanner, error) {
	switch typ {
	case "local":
		if cfg.Path == "" {
			return nil, fmt.Errorf("本地目录源必须填写 path")
		}
		return &Local{root: cfg.Path}, nil
	case "webdav":
		if cfg.URL == "" {
			return nil, fmt.Errorf("WebDAV 源必须填写 url")
		}
		return &WebDAV{cfg: cfg}, nil
	case "jellyfin", "emby":
		// Emby 与 Jellyfin 接口协议一致，同一扫描器通吃
		if cfg.URL == "" {
			return nil, fmt.Errorf("Jellyfin/Emby 源必须填写 url")
		}
		if cfg.APIKey == "" && cfg.Username == "" {
			return nil, fmt.Errorf("Jellyfin/Emby 源需填写 API Key 或用户名+密码")
		}
		return &Jellyfin{cfg: cfg}, nil
	default:
		return nil, fmt.Errorf("未知的媒体源类型: %s", typ)
	}
}
