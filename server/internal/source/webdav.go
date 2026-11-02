package source

import (
	"context"
	"crypto/tls"
	"fmt"
	"net/http"
	"path"
	"sort"
	"strings"

	"github.com/studio-b12/gowebdav"
)

// WebDAV 源：起始路径（base_path）下每个一级子目录为一部剧，
// 递归（最多 4 层）收集其中的视频文件。
type WebDAV struct{ cfg Config }

func (w *WebDAV) client() *gowebdav.Client {
	// gowebdav 新版本 API：NewClient(server, user, password)；用户名密码为空时按匿名访问
	c := gowebdav.NewClient(strings.TrimRight(w.cfg.URL, "/"), w.cfg.Username, w.cfg.Password)
	if w.cfg.Insecure {
		c.SetTransport(&http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}})
	}
	return c
}

func (w *WebDAV) Scan(ctx context.Context) ([]ScannedSeries, error) {
	c := w.client()
	base := normalizeBase(w.cfg.BasePath)
	entries, err := c.ReadDir(base)
	if err != nil {
		return nil, fmt.Errorf("WebDAV 目录读取失败（请检查地址/账号/权限）: %w", err)
	}
	var out []ScannedSeries
	for _, e := range entries {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		if !e.IsDir() || strings.HasPrefix(e.Name(), ".") {
			continue
		}
		seriesPath := path.Join(base, e.Name())
		files, cover := w.collectSeries(c, seriesPath)
		if len(files) == 0 {
			continue
		}
		out = append(out, ScannedSeries{
			Key:       e.Name(),
			Title:     e.Name(),
			CoverPath: cover,
			Episodes:  BuildEpisodes(files),
		})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Key < out[j].Key })
	return out, nil
}

func (w *WebDAV) collectSeries(c *gowebdav.Client, seriesPath string) ([]IndexedFile, string) {
	var files []IndexedFile
	cover := ""
	var walk func(p string, depth int)
	walk = func(p string, depth int) {
		if depth > 4 {
			return
		}
		infos, err := c.ReadDir(p)
		if err != nil {
			return
		}
		for _, info := range infos {
			name := info.Name()
			if strings.HasPrefix(name, ".") {
				continue
			}
			child := path.Join(p, name)
			if info.IsDir() {
				walk(child, depth+1)
				continue
			}
			lower := strings.ToLower(name)
			if cover == "" && isCoverName(lower) {
				cover = strings.TrimPrefix(child, seriesPath+"/")
			}
			if IsVideoFile(name) {
				size := int64(-1)
				if info.Size() > 0 {
					size = info.Size()
				}
				files = append(files, IndexedFile{
					Rel:  strings.TrimPrefix(child, seriesPath+"/"),
					Size: size,
				})
			}
		}
	}
	walk(seriesPath, 0)
	return files, cover
}
