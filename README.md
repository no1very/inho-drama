# InhoDrama · NAS 短剧系统

**[简体中文](README.md)** · [English](README_EN.md) · [繁體中文](README_繁体.md)

部署在自己 NAS 上的短剧流媒体服务，交互对标红果短剧：**首页上下滑动刷剧流 → 点击分集播放 → 播完自动连播，多端播放进度互通**。完全跑在局域网内，**不依赖任何外网刮削接口**。

## 功能特性

- **三种媒体源**：本地目录 / WebDAV（Alist、群晖等）/ 对接已有 **Jellyfin** 库（复用海报、简介、时长）
- **Web H5 前端**：首页「短视频流 ↔ 海报墙」双模式一键切换、全屏竖滑刷剧、选集、搜索、继续观看、网页端播放器（上一集/下一集控件；mp4 直连，Jellyfin 源 mkv 自动 HLS 转码兜底），构建后**内嵌进后端二进制**，单容器交付
- **原生安卓 App**：Kotlin + Jetpack Compose（**无 WebView**）+ Media3/ExoPlayer，双模式首页、竖滑切集、上一集/下一集按钮、自动连播、外挂 SRT
- **多端进度互通**：进度按「账号 + 剧 + 集序号」存服务端 SQLite，网页看一半，App 打开接着播；重新扫描/换源进度也不丢；**误触产生的垃圾记录服务端自动忽略，看完的剧自动从首页消失**（历史页仍可见）
- **零刮削依赖**：剧名/集数来自文件名规范，封面用目录内 `poster.jpg`，无海报时 ffmpeg 自动截取视频随机帧或生成占位图；可选读取 `tvshow.nfo` 简介
- **多架构 Docker**：x86_64 + ARM64（buildx 交叉编译，ARM NAS 上构建无需 QEMU），单镜像含可选 ffmpeg
- **内置管理台**：Web 端即可添加/编辑/扫描三种媒体源、查看扫描状态、注册用户

## 目录结构

```
inho-drama/
├── docker-compose.yml        # 一键部署
├── Dockerfile                # 多阶段多架构构建（前端+后端 → 单镜像）
├── docs/
│   ├── api.md                # HTTP API 契约（三端共同依据）
│   ├── deploy.md             # 部署文档（compose / 环境变量 / 反代 / 备份升级）
│   ├── naming.md             # 文件命名规范（集数/封面/字幕/编码建议）
│   ├── webdav.md             # WebDAV 对接教程（Alist/群晖/威联通/OMV）
│   ├── jellyfin.md           # Jellyfin 对接教程（API Key/HLS 转码/权限）
│   └── android.md            # 安卓 App 编译打包指南
├── server/                   # Go 后端（SQLite 纯 Go 驱动，无 CGO）
│   ├── main.go
│   ├── webdist/              # 构建时嵌入的 H5 产物
│   └── internal/
│       ├── api/              # 路由 / 鉴权 / 流媒体代理 / 封面 / 字幕 / 源管理
│       ├── store/            # SQLite 存储层
│       ├── source/           # 三种媒体源扫描器 + 集数解析
│       ├── scanner/          # 扫描队列与状态
│       └── ffmpeg/           # 可选截帧封面
├── web/                      # Vue 3 + Vite H5 前端
└── android/                  # Kotlin Jetpack Compose 安卓 App
```

## 快速开始

```bash
cd inho-drama
docker compose up -d --build
# 浏览器打开 http://<NAS_IP>:8654
# 默认账号 admin / admin（务必尽快修改）
```

然后「我的 → 媒体源管理」添加源（本地目录 `/media` / WebDAV / Jellyfin），自动扫描完成后即可开刷。

安卓 App 源码在 `android/`，编译步骤见 [docs/android.md](docs/android.md)。

## 文档导航

| 文档 | 内容 |
|---|---|
| [部署文档](docs/deploy.md) | Compose 部署、环境变量、多架构构建与推送、反代、备份升级、FAQ |
| [文件命名规范](docs/naming.md) | 目录结构、集数命名、封面/简介/字幕、网页端友好的编码参数 |
| [WebDAV 对接](docs/webdav.md) | Alist / 群晖 / 威联通 / OMV 的地址与账号填法、排错 |
| [Jellyfin 对接](docs/jellyfin.md) | API Key 获取、用户隔离、HLS 转码说明、排错 |
| [安卓编译](docs/android.md) | Android Studio / 命令行构建、签名、常见问题 |
| [API 契约](docs/api.md) | 全部接口与字段定义（想自己写客户端看这个） |

## 界面截图

<div align="center">

**安卓 App**

<img src="https://github.com/no1very/inho-drama/releases/download/v1.0.0/app-1.png" width="30%" alt="App 截图 1"/>
<img src="https://github.com/no1very/inho-drama/releases/download/v1.0.0/app-2.png" width="30%" alt="App 截图 2"/>
<img src="https://github.com/no1very/inho-drama/releases/download/v1.0.0/app-3.png" width="30%" alt="App 截图 3"/>
<img src="https://github.com/no1very/inho-drama/releases/download/v1.0.0/app-4.png" width="30%" alt="App 截图 4"/>

**Docker 部署**

<img src="https://github.com/no1very/inho-drama/releases/download/v1.0.0/docker-1.png" width="45%" alt="Docker 运行 1"/>
<img src="https://github.com/no1very/inho-drama/releases/download/v1.0.0/docker-2.png" width="45%" alt="Docker 运行 2"/>

</div>

## 技术选型

- **后端**：Go 1.23 + `modernc.org/sqlite`（纯 Go SQLite，无 CGO，天然跨架构）+ 标准库 `net/http`；WebDAV 客户端 `studio-b12/gowebdav`；密码 bcrypt；token 鉴权
- **H5**：Vue 3 + Vite + vue-router(hash) + pinia + hls.js（兜底），构建产物 `embed` 进二进制
- **安卓**：Kotlin 2.0 + Compose BOM + Navigation + Media3 1.4 + Retrofit + kotlinx-serialization + Coil + DataStore
- **流媒体**：本地 `http.ServeFile`；WebDAV/Jellyfin 服务端代理并透传 `Range/206`；Jellyfin 网页端不友好容器 302 至 `master.m3u8` 转码

## AI 开发声明

本项目由 **AI 全流程开发**：从需求分析、架构设计、后端（Go）、H5 前端（Vue3）、安卓客户端（Kotlin/Jetpack Compose）、测试、文档，到 **GitHub 开源与版本发布（含推送到 GitHub）**，均由 AI 完成。

## 声明

本项目仅提供私有影音管理能力，请确保接入的内容为**你拥有合法权利**的影视资源；请勿用于传播受版权保护的内容。
