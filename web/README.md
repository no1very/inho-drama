# InhoDrama Web H5（Inho短剧）

Vue 3 + Vite 实现的移动端 H5 前端，对标红果短剧的全屏竖向滑动刷剧交互。
接口契约见 `../docs/api.md`，所有请求路径与字段名严格按契约实现。

## 技术栈

- Vue 3.4+（Composition API，`<script setup>`）
- Vite 5 / vue-router 4（hash 模式）/ pinia 2 / hls.js
- 纯 CSS 深色主题（主色 #ff4d4f → #ff7a45，背景 #0b0b0f），无 UI 组件库、无外部 CDN 资源

## 开发

```bash
cd web
npm install
npm run dev
```

- 默认地址 http://localhost:5173。
- `vite.config.js` 已把 `/api` 代理到 `http://127.0.0.1:8080`（本地后端），开发时无需处理跨域。
- 服务器地址默认取 `window.location.origin`，可在登录页或「我的」页修改。

## 构建

```bash
npm run build
```

- 产物输出到 `web/dist/`，由后端通过 Go embed 嵌入（`vite.config.js` 的 `build.outDir` 固定为 `dist`，勿改）。
- 本地预览构建产物：`npm run preview`。

## 页面一览

| 路由 | 说明 |
| --- | --- |
| `#/` | 首页：继续观看横滑区 + scroll-snap 全屏竖滑刷剧流（next_cursor 分页） |
| `#/search` | 搜索：300ms 防抖，两列网格结果 |
| `#/detail/:id` | 详情：大封面、立即播放 / 继续播放、选集网格（已看✓ / 看到一半高亮） |
| `#/play/:seriesId?ep=N` | 播放页：上下滑切集、控制层、选集抽屉、进度上报、续播、HLS 兜底、字幕 |
| `#/history` | 观看历史：继续播放、删除记录 |
| `#/mine` | 我的：用户名 / 服务器地址 / 版本号、改服务器地址、媒体源管理入口、退出登录 |
| `#/login` | 登录 / 注册（allow_register=true 时显示注册入口） |
| `#/admin/sources` | 媒体源管理：增删改查、单源扫描、扫描全部、2s 轮询扫描状态 |

## 实现要点

- token 存 `localStorage`（`inho-drama.token`），所有请求带 `Authorization: Bearer`，GET 额外附 `?token=`；401 自动清登录态跳 `#/login`。
- 播放地址：`/api/v1/stream/{episodeId}?token=...`；浏览器直连报「容器不支持」（MediaError code=4）且容器非 mp4/m4v/webm/mov 时，自动改用 `&hls=1` + hls.js 兜底一次，仍失败提示使用安卓 App。
- 进度上报（多端互通）：播放中每 5s、暂停、切集前、页面隐藏、退出（`fetch keepalive`），鉴权走 `?token=`。
- 续播：进入播放页从 `episode_states` 取该集 position（>5s 且未接近结尾时在 loadedmetadata 后定位）。
- 字幕：loadedmetadata 后探测 `GET /subtitle/{episodeId}`，200 时挂 `<track kind="subtitles" srclang="zh" default>`。
- 视频默认 muted 自动播放，首次触摸解除静音。
