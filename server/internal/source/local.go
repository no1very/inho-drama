package source

import (
	"context"
	"encoding/xml"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// Local 本地目录源：根目录下每个一级子目录为一部剧，
// 子目录内（含更深层级）的视频文件为单集。
type Local struct{ root string }

type nfoTVShow struct {
	Title string `xml:"title"`
	Plot  string `xml:"plot"`
}

func (l *Local) Scan(ctx context.Context) ([]ScannedSeries, error) {
	root := filepath.Clean(l.root)
	entries, err := os.ReadDir(root)
	if err != nil {
		return nil, fmt.Errorf("读取目录失败: %w", err)
	}
	var out []ScannedSeries
	for _, e := range entries {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		if !e.IsDir() || strings.HasPrefix(e.Name(), ".") {
			continue
		}
		dir := filepath.Join(root, e.Name())
		files, cover := l.collectDir(ctx, root, dir)
		if len(files) == 0 {
			continue
		}
		item := ScannedSeries{
			Key:       e.Name(),
			Title:     e.Name(),
			CoverPath: cover,
			Episodes:  BuildEpisodes(files),
		}
		if nfo := readNFO(filepath.Join(dir, "tvshow.nfo")); nfo != nil {
			if nfo.Plot != "" {
				item.Intro = nfo.Plot
			}
			if nfo.Title != "" {
				item.Title = nfo.Title
			}
		}
		out = append(out, item)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Key < out[j].Key })
	return out, nil
}

func (l *Local) collectDir(ctx context.Context, root, dir string) ([]IndexedFile, string) {
	var files []IndexedFile
	cover := ""
	_ = filepath.WalkDir(dir, func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil // 跳过无权限等问题
		}
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if d.IsDir() {
			if p != dir && strings.HasPrefix(d.Name(), ".") {
				return fs.SkipDir
			}
			return nil
		}
		name := d.Name()
		lower := strings.ToLower(name)
		if cover == "" && isCoverName(lower) {
			if rel, rerr := filepath.Rel(root, p); rerr == nil {
				cover = filepath.ToSlash(rel)
			}
		}
		if IsVideoFile(name) {
			rel, rerr := filepath.Rel(root, p)
			if rerr != nil {
				return nil
			}
			size := int64(-1)
			if info, ierr := d.Info(); ierr == nil {
				size = info.Size()
			}
			files = append(files, IndexedFile{Rel: filepath.ToSlash(rel), Size: size})
		}
		return nil
	})
	return files, cover
}

func readNFO(p string) *nfoTVShow {
	b, err := os.ReadFile(p)
	if err != nil {
		return nil
	}
	var nfo nfoTVShow
	if err := xml.Unmarshal(b, &nfo); err != nil {
		return nil
	}
	return &nfo
}
