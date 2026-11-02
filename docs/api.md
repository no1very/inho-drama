# InhoDrama HTTP API v1

后端与 Web H5 / 安卓 App 共用的接口契约。所有客户端以本文档为准。

## 通用约定

- Base URL：`http(s)://<服务器地址>:<端口>/api/v1`
- 编码：UTF-8，请求/响应体均为 JSON（流式接口除外）
- 时间戳：Unix 秒（int64）
- 鉴权：除标注「无需鉴权」外，所有接口需要登录。
  - 优先 `Authorization: Bearer <token>` 请求头
  - GET 请求（视频流/封面/字幕等无法带请求头的场景）额外支持 `?token=<token>` 查询参数
- 错误响应：`{"error": "原因"}`，HTTP 状态码 400/401/403/404/409/500
- 跨域：后端对所有 `/api/` 返回 `Access-Control-Allow-Origin: *`，支持 OPTIONS 预检

## 对象模型

```jsonc
// 剧集卡片（feed / 列表 / 搜索）
SeriesCard {
  "id": 123,                    // 剧集 ID
  "title": "霸总闪婚娇妻",       // 剧名（目录名）
  "intro": "剧情简介…",          // 可能为 ""
  "category": "都市",            // 可能为 ""（来自 tvshow.nfo 或 Jellyfin 流派）
  "cover": "/api/v1/cover/123", // 封面相对路径，客户端需拼接服务器地址 + ?token=
  "episode_count": 80,
  "source_name": "本地媒体库",
  "updated_at": 1735689600
}

// 单集
Episode {
  "id": 456,                    // 单集 ID（流/字幕/进度均用它）
  "series_id": 123,
  "index": 1,                   // 集数序号，从 1 开始
  "title": "第1集",
  "duration": 123.4,            // 秒；未知为 null
  "size": 52428800,             // 字节；未知为 null
  "container": "mp4"            // 小写扩展名，如 mp4/mkv/avi；未知为 ""
}

// 单集观看状态（当前用户）
EpisodeState {
  "episode_id": 456,
  "index": 1,
  "position": 56.3,             // 已看秒数
  "duration": 123.4,            // 可能为 null
  "completed": false,           // 看完（进度>90% 或客户端标记）
  "updated_at": 1735689600
}
```

## 认证

### POST /auth/login  （无需鉴权）
```jsonc
// 请求
{ "username": "admin", "password": "xxx" }
// 200
{ "token": "hex32", "user": { "id": 1, "username": "admin" } }
// 401  {"error": "用户名或密码错误"}
```

### POST /auth/register  （无需鉴权；服务端可关闭，见 /server-info）
```jsonc
// 请求 { "username": "x", "password": "y" }   密码至少 6 位
// 200 同 login；409 用户名已存在；403 {"error":"注册已关闭"}
```

### GET /auth/me
`200 { "user": { "id": 1, "username": "admin" } }`

### POST /auth/logout
`200 { "ok": true }`（吊销当前 token）

## 服务器信息

### GET /server-info  （无需鉴权）
```jsonc
{ "name": "InhoDrama", "version": "1.0.0", "allow_register": true }
```

### GET /health  （无需鉴权）
`{ "ok": true }`

## 首页滑动流 / 目录

### GET /feed?cursor=0&limit=12
按推荐热度排序的剧集流，`cursor` 为偏移量。
```jsonc
// 200
{ "items": [SeriesCard, ...],
  "next_cursor": 12 | null }   // null 表示没有更多
```

### GET /series?keyword=霸总&page=1&page_size=20
标题模糊搜索；keyword 为空返回全部。
```jsonc
{ "items": [SeriesCard...], "total": 128, "page": 1, "page_size": 20 }
```

### GET /series/{id}
```jsonc
// 200
{
  "series": SeriesCard,
  "episodes": [Episode...],          // 按 index 升序
  "continue_episode_id": 456 | null, // 建议续播的单集（最近未看完的一集；无记录时为第 1 集）
  "episode_states": [EpisodeState...] // 当前用户全部单集状态（做已看角标）
}
```

## 播放进度（多端互通）

### POST /progress
播放中建议每 5 秒上报一次，暂停/切集/退出时再报一次。
```jsonc
// 请求
{ "series_id": 123, "episode_id": 456,
  "position": 56.3, "duration": 123.4, "completed": false }
// 200 { "ok": true }
```
duration 可传 0 表示未知；completed 由客户端在看完时置 true。
**服务端会忽略非 completed 且 position < 5 秒的上报**（不产生观看记录），
避免误点进播放页立刻退出就在「继续观看」里出现垃圾条目。

### GET /history?finished=0
继续观看列表（按更新时间倒序，每部剧一条）。
`finished=0`：只返回未看完的记录（首页「继续观看」用，看完的剧自动从首页消失）；
不传参数返回全部（历史页用）。
```jsonc
// 200
{ "items": [ {
    "series": SeriesCard, "episode": Episode,
    "position": 56.3, "duration": 123.4,
    "completed": false, "updated_at": 1735689600 } ... ] }
```

### DELETE /history/{seriesId}
清除某部剧的观看记录 → `{ "ok": true }`

## 流媒体

### GET /stream/{episodeId}?token=xxx[&hls=1]
- 返回视频字节流，**完整支持 Range/206 断点续传**（本地与 WebDAV 由后端代理；Jellyfin 亦代理）。
- `&hls=1`：仅对 Jellyfin 源有效，302 跳转到 Jellyfin 的 `master.m3u8`（网页端播放 mkv/avi 等浏览器不支持的容器时使用，需配 hls.js）。
- 出错返回 404 `{"error":"文件不存在"}`。
- 客户端策略（两端一致）：优先直连；若播放器报解码/容器错误，再用 `hls=1` 重试一次；仍失败提示「建议使用安卓 App 播放」。

### GET /cover/{seriesId}?token=xxx
- 返回封面图。解析顺序：源内海报（poster.jpg/folder.jpg/cover.jpg 或 Jellyfin 海报 API）→ 均无时返回**内置默认封面 PNG**（深色渐变 + 播放键，各端展示一致）。缓存头 `Cache-Control`。
- 客户端用 `服务器地址 + cover + "?token=" + token` 直接 `<img>`/Coil 加载。

### GET /subtitle/{episodeId}?token=xxx[&format=vtt|srt]
- 存在同名 `.srt` 字幕时返回字幕文本（默认转换为 WebVTT，`text/vtt`），不存在返回 404（客户端忽略即可）。
- 安卓端可用 `format=srt` 取原始 SRT（Media3 原生支持）。

## 媒体源管理（设置页用）

### GET /sources
```jsonc
{ "items": [ {
  "id": 1, "type": "local|webdav|jellyfin|emby", "name": "本地媒体库",
  "enabled": true,
  "series_count": 36,
  "last_scan_at": 1735689600,     // 0 表示从未扫描
  "last_error": "",
  "config": { ...见下，密码/ApiKey 字段返回空串 } } ... ] }
```

### POST /sources    / PUT /sources/{id}
```jsonc
// 请求体（按 type 取用字段）
{
  "type": "local|webdav|jellyfin|emby",
  "name": "我的短剧库",
  "enabled": true,
  "config": {
    // local:
    "path": "/media/短剧",
    // webdav:
    "url": "http://192.168.1.10:5244/dav",
    "username": "user", "password": "pass",   // PUT 时密码留空=保持原值
    "base_path": "/短剧",                       // 相对 dav 根目录，默认 /
    "insecure": false,                          // 跳过 HTTPS 证书校验
    // jellyfin / emby（两家接口协议一致）:
    "url": "http://192.168.1.10:8096",
    "username": "user", "password": "pass",    // 推荐：填写后服务端保存时调 AuthenticateByName
                                               // 换取 AccessToken 存为 api_key（用户名密码优先）
    "api_key": "xxxx",                         // 可选；PUT 时留空=保持原值
    "user_id": "",                              // 可选，限定可见库
    "transcode": true                           // 网页端优先 HLS 转码
  }
}
// 200 { "id": 2 }   （POST/PUT 成功后自动触发一次扫描）
```
校验失败 400；名称重复 409。

### DELETE /sources/{id}
`{ "ok": true }`（同时删除该剧库缓存的所有剧集数据，不影响原始文件）

### POST /sources/{id}/scan  与  POST /scan/all
触发扫描（后台队列执行）→ `{ "ok": true }`

### GET /scan/status
```jsonc
{ "running": false, "source_id": 0, "source_name": "",
  "started_at": 0, "finished_at": 1735689600,
  "last_error": "", "series_count": 36, "episode_count": 2880 }
```

---

## 客户端实现要点（两端一致）

1. 服务器地址可配置（H5 默认同源；安卓登录页填写，如 `http://192.168.1.10:8654`）。
2. token 存本地（localStorage / DataStore），401 时清 token 跳登录页。
3. 封面 URL = `server + series.cover + "?token=" + token`。
4. 播放 URL = `server + "/api/v1/stream/" + episode.id + "?token=" + token`。
5. 进度上报：播放中每 5s、暂停/切集/页面隐藏/退出时；开播时先从 `/series/{id}` 的 states 取续播位置（position > 5s 才跳转）。
6. 自动连播：onEnded → completed=true 上报 → 播下一集（index+1）。
