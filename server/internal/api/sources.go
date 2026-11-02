package api

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"

	"nasdrama/server/internal/source"
	"nasdrama/server/internal/store"
)

type sourceItemDTO struct {
	ID          int64         `json:"id"`
	Type        string        `json:"type"`
	Name        string        `json:"name"`
	Enabled     bool          `json:"enabled"`
	SeriesCount int64         `json:"series_count"`
	LastScanAt  int64         `json:"last_scan_at"`
	LastError   string        `json:"last_error"`
	Config      source.Config `json:"config"`
}

type sourcePayload struct {
	Type    string        `json:"type"`
	Name    string        `json:"name"`
	Enabled *bool         `json:"enabled"`
	Config  source.Config `json:"config"`
}

// handleSourcesList 列出媒体源；密码与 ApiKey 一律掩码返回空串。
func (s *Server) handleSourcesList(w http.ResponseWriter, r *http.Request, u *store.User) {
	list, err := s.opts.Store.ListSources()
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "查询媒体源失败")
		return
	}
	items := make([]sourceItemDTO, 0, len(list))
	for _, src := range list {
		var cfg source.Config
		_ = json.Unmarshal([]byte(src.Config), &cfg)
		cfg.Password = ""
		cfg.APIKey = ""
		count, _ := s.opts.Store.SourceSeriesCount(src.ID)
		items = append(items, sourceItemDTO{
			ID:          src.ID,
			Type:        src.Type,
			Name:        src.Name,
			Enabled:     src.Enabled,
			SeriesCount: count,
			LastScanAt:  src.LastScanAt,
			LastError:   src.LastError,
			Config:      cfg,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func validateSourcePayload(p *sourcePayload) string {
	switch p.Type {
	case "local":
		if p.Config.Path == "" {
			return "本地目录源必须填写 path"
		}
	case "webdav":
		if p.Config.URL == "" {
			return "WebDAV 源必须填写 url"
		}
	case "jellyfin", "emby":
		if p.Config.URL == "" {
			return "Jellyfin/Emby 源必须填写 url"
		}
	default:
		return "type 必须是 local / webdav / jellyfin / emby"
	}
	if p.Name == "" {
		return "名称不能为空"
	}
	return ""
}

// isMediaServerType：Jellyfin 与 Emby 接口协议一致，扫描/播放/封面共用一套逻辑
func isMediaServerType(t string) bool {
	return t == "jellyfin" || t == "emby"
}

// exchangeMediaServerToken：Jellyfin/Emby 用户名密码优先——
// 未填 API Key 且提供用户名+密码时，调用 AuthenticateByName 换取 AccessToken
// 写回 cfg.APIKey（后续扫描/播放/封面统一使用），UserID 留空时一并回填。
// 返回错误消息；空串表示无需处理或成功。
func exchangeMediaServerToken(cfg *source.Config) string {
	if cfg.APIKey != "" || cfg.Username == "" || cfg.Password == "" {
		return ""
	}
	token, uid, err := authMediaServerByName(cfg)
	if err != nil {
		return "登录媒体服务器失败（检查地址/用户名/密码）：" + err.Error()
	}
	cfg.APIKey = token
	if cfg.UserID == "" {
		cfg.UserID = uid
	}
	return ""
}

// authMediaServerByName：Jellyfin/Emby 的 POST /Users/AuthenticateByName
func authMediaServerByName(cfg *source.Config) (string, string, error) {
	body, _ := json.Marshal(map[string]string{"Username": cfg.Username, "Pw": cfg.Password})
	req, err := http.NewRequest(http.MethodPost,
		strings.TrimRight(cfg.URL, "/")+"/Users/AuthenticateByName", bytes.NewReader(body))
	if err != nil {
		return "", "", err
	}
	req.Header.Set("Content-Type", "application/json")
	// 两家服务端都要求携带客户端标识头，否则 401
	req.Header.Set("X-Emby-Authorization",
		`MediaBrowser Client="inho-drama", Device="Server", DeviceId="inho-drama-server", Version="1.0"`)
	resp, err := mediaHTTPClient(cfg.Insecure).Do(req)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		return "", "", fmt.Errorf("HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	var out struct {
		AccessToken string `json:"AccessToken"`
		User        struct {
			Id string `json:"Id"`
		} `json:"User"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return "", "", err
	}
	if out.AccessToken == "" {
		return "", "", errors.New("响应中没有 AccessToken")
	}
	return out.AccessToken, out.User.Id, nil
}

func (s *Server) handleSourceCreate(w http.ResponseWriter, r *http.Request, u *store.User) {
	var p sourcePayload
	if err := readJSON(w, r, &p); err != nil {
		writeErr(w, http.StatusBadRequest, "请求体格式错误")
		return
	}
	if msg := validateSourcePayload(&p); msg != "" {
		writeErr(w, http.StatusBadRequest, msg)
		return
	}
	// Jellyfin/Emby：用户名密码优先——未填 API Key 时用 AuthenticateByName 换 token 存库
	if isMediaServerType(p.Type) {
		if msg := exchangeMediaServerToken(&p.Config); msg != "" {
			writeErr(w, http.StatusBadRequest, msg)
			return
		}
		if p.Config.APIKey == "" {
			writeErr(w, http.StatusBadRequest, "请填写 API Key，或用户名+密码")
			return
		}
	}
	b, _ := json.Marshal(p.Config)
	id, err := s.opts.Store.CreateSource(p.Type, p.Name, string(b), p.Enabled == nil || *p.Enabled)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "创建媒体源失败")
		return
	}
	s.opts.Scanner.Enqueue(id)
	writeJSON(w, http.StatusOK, map[string]int64{"id": id})
}

func (s *Server) handleSourceUpdate(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的媒体源 ID")
		return
	}
	old, err := s.opts.Store.GetSource(id)
	if err != nil {
		writeErr(w, http.StatusNotFound, "媒体源不存在")
		return
	}
	var p sourcePayload
	if err := readJSON(w, r, &p); err != nil {
		writeErr(w, http.StatusBadRequest, "请求体格式错误")
		return
	}
	if msg := validateSourcePayload(&p); msg != "" {
		writeErr(w, http.StatusBadRequest, msg)
		return
	}
	var oldCfg source.Config
	_ = json.Unmarshal([]byte(old.Config), &oldCfg)
	incomingAPIKey := p.Config.APIKey
	incomingUser := p.Config.Username
	incomingPass := p.Config.Password
	// 密码 / ApiKey 留空表示保持原值
	if p.Config.Password == "" {
		p.Config.Password = oldCfg.Password
	}
	if p.Config.APIKey == "" {
		p.Config.APIKey = oldCfg.APIKey
	}
	if p.Config.Username == "" && oldCfg.Username != "" && p.Type == old.Type {
		p.Config.Username = oldCfg.Username
	}
	// Jellyfin/Emby：本次请求填了用户名+密码且没填 API Key → 重新换取 token
	//（支持从 API Key 登录切换为密码登录；密码留空则沿用已存 token/密码）
	if isMediaServerType(p.Type) && incomingAPIKey == "" && incomingUser != "" && incomingPass != "" {
		if msg := exchangeMediaServerToken(&p.Config); msg != "" {
			writeErr(w, http.StatusBadRequest, msg)
			return
		}
	}
	if isMediaServerType(p.Type) && p.Config.APIKey == "" {
		writeErr(w, http.StatusBadRequest, "请填写 API Key，或用户名+密码")
		return
	}
	b, _ := json.Marshal(p.Config)
	enabled := p.Enabled == nil || *p.Enabled
	if err := s.opts.Store.UpdateSource(id, p.Type, p.Name, string(b), enabled); err != nil {
		writeErr(w, http.StatusInternalServerError, "更新媒体源失败")
		return
	}
	s.opts.Scanner.Enqueue(id)
	writeOK(w)
}

func (s *Server) handleSourceDelete(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的媒体源 ID")
		return
	}
	if _, err := s.opts.Store.GetSource(id); errors.Is(err, store.ErrNotFound) {
		writeErr(w, http.StatusNotFound, "媒体源不存在")
		return
	}
	if err := s.opts.Store.DeleteSource(id); err != nil {
		writeErr(w, http.StatusInternalServerError, "删除媒体源失败")
		return
	}
	writeOK(w)
}

func (s *Server) handleSourceScan(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的媒体源 ID")
		return
	}
	if _, err := s.opts.Store.GetSource(id); err != nil {
		writeErr(w, http.StatusNotFound, "媒体源不存在")
		return
	}
	s.opts.Scanner.Enqueue(id)
	writeOK(w)
}

func (s *Server) handleScanAll(w http.ResponseWriter, r *http.Request, u *store.User) {
	s.opts.Scanner.EnqueueAll()
	writeOK(w)
}

func (s *Server) handleScanStatus(w http.ResponseWriter, r *http.Request, u *store.User) {
	writeJSON(w, http.StatusOK, s.opts.Scanner.Status())
}
