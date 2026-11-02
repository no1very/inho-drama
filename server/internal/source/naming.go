package source

import (
	"fmt"
	"path"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

var videoExtSet = map[string]bool{
	".mp4": true, ".m4v": true, ".mkv": true, ".mov": true, ".webm": true,
	".avi": true, ".flv": true, ".ts": true, ".wmv": true, ".mpg": true,
	".mpeg": true, ".rmvb": true, ".3gp": true, ".vob": true,
}

func IsVideoFile(name string) bool {
	return videoExtSet[strings.ToLower(path.Ext(name))]
}

func ContainerOf(name string) string {
	return strings.TrimPrefix(strings.ToLower(path.Ext(name)), ".")
}

// FileStem 返回不含扩展名的文件名。
func FileStem(rel string) string {
	base := path.Base(rel)
	return strings.TrimSuffix(base, path.Ext(base))
}

var (
	reZhEp = regexp.MustCompile(`第\s*(\d{1,4})\s*[集话回期]`)
	reSE   = regexp.MustCompile(`(?i)S\d{1,3}\s*E(\d{1,4})`)
	reSep  = regexp.MustCompile(`(?i)(?:^|[\s_\-.·,，()（）\[\]【】])e?p?(\d{1,4})(?:$|[\s_\-.·,，()（）\[\]【】])`)
	reLead = regexp.MustCompile(`^(\d{1,4})[\s_\-.·]`)
	reTail = regexp.MustCompile(`[\s_\-.·](\d{1,4})$`)
	rePure = regexp.MustCompile(`^(\d{1,4})$`)
)

// ParseEpisodeIndex 从文件名（不含扩展名）解析集数。
// 依次尝试：第01集 / 第01话、S01E02、EP01/E01/分隔回数字、行首数字、行尾数字、纯数字，
// 最后兜底取文件名末尾一段不超过 4 位的连续数字。
func ParseEpisodeIndex(stem string) (int64, bool) {
	s := strings.TrimSpace(stem)
	if s == "" {
		return 0, false
	}
	for _, re := range []*regexp.Regexp{reZhEp, reSE, reSep, reLead, reTail, rePure} {
		if m := re.FindStringSubmatch(s); m != nil {
			n, err := strconv.ParseInt(m[1], 10, 64)
			if err == nil && n >= 1 && n <= 9999 {
				return n, true
			}
		}
	}
	runes := []rune(s)
	end := -1
	begin := 0
	for i := len(runes) - 1; i >= 0; i-- {
		c := runes[i]
		if c >= '0' && c <= '9' {
			if end == -1 {
				end = i
			}
			begin = i
		} else if end != -1 {
			break
		}
	}
	if end != -1 && end-begin+1 <= 4 {
		n, err := strconv.ParseInt(string(runes[begin:end+1]), 10, 64)
		if err == nil && n >= 1 && n <= 9999 {
			return n, true
		}
	}
	return 0, false
}

func episodeTitle(idx int64, stem string) string {
	if strings.TrimSpace(stem) == strconv.FormatInt(idx, 10) {
		return fmt.Sprintf("第%d集", idx)
	}
	return stem
}

// IndexedFile 供 BuildEpisodes 使用的扁平文件信息。Rel 使用 / 分隔。
type IndexedFile struct {
	Rel  string
	Size int64 // -1 未知
}

// BuildEpisodes 把一批视频文件解析成有序单集列表：
// 先按文件名排序，能解析出集数的按集数编号；解析失败的文件排在已解析最大集数之后，
// 集数冲突时自动顺延，保证 idx 唯一。
func BuildEpisodes(files []IndexedFile) []ScannedEpisode {
	sort.Slice(files, func(i, j int) bool { return files[i].Rel < files[j].Rel })
	type parsed struct {
		idx  int64
		file IndexedFile
	}
	var known []parsed
	var unknown []IndexedFile
	maxIdx := int64(0)
	for _, f := range files {
		if idx, ok := ParseEpisodeIndex(FileStem(f.Rel)); ok {
			known = append(known, parsed{idx: idx, file: f})
			if idx > maxIdx {
				maxIdx = idx
			}
		} else {
			unknown = append(unknown, f)
		}
	}
	seen := map[int64]bool{}
	var out []ScannedEpisode
	for _, p := range known {
		idx := p.idx
		for seen[idx] && idx < 9999 {
			idx++
		}
		seen[idx] = true
		out = append(out, ScannedEpisode{
			Index:     idx,
			Title:     episodeTitle(idx, FileStem(p.file.Rel)),
			Key:       p.file.Rel,
			Container: ContainerOf(p.file.Rel),
			Size:      p.file.Size,
			Duration:  -1,
		})
	}
	next := maxIdx + 1
	for _, f := range unknown {
		for seen[next] && next < 9999 {
			next++
		}
		seen[next] = true
		out = append(out, ScannedEpisode{
			Index:     next,
			Title:     episodeTitle(next, FileStem(f.Rel)),
			Key:       f.Rel,
			Container: ContainerOf(f.Rel),
			Size:      f.Size,
			Duration:  -1,
		})
		next++
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Index < out[j].Index })
	return out
}

// normalizeBase 把用户输入的 WebDAV 起始路径规整成 /a/b 或 /。
func normalizeBase(b string) string {
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

var coverNames = []string{"poster.jpg", "folder.jpg", "cover.jpg", "poster.png", "cover.png"}

func isCoverName(lower string) bool {
	for _, n := range coverNames {
		if lower == n {
			return true
		}
	}
	return false
}
