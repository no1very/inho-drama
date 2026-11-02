# InhoDrama · NAS 短劇系統

[简体中文](README.md) · [English](README_EN.md) · **[繁體中文](README_繁体.md)**

部署在自己 NAS 上的短劇串流服務，交互對標紅果短劇：**首頁上下滑動刷劇流 → 點擊分集播放 → 播完自動連播，多端播放進度互通**。完全跑在區域網路內，**不依賴任何外網刮削介面**。

## 功能特性

- **三種媒體來源**：本機目錄 / WebDAV（Alist、群暉等）/ 對接既有 **Jellyfin** 庫（複用海報、簡介、時長）
- **Web H5 前端**：首頁「短影音流 ↔ 海報牆」雙模式一鍵切換、全螢幕豎滑刷劇、選集、搜尋、繼續觀看、網頁端播放器（上一集/下一集控制；mp4 直連，Jellyfin 來源 mkv 自動 HLS 轉碼兜底），建構後**內嵌進後端二進位檔**，單容器交付
- **原生安卓 App**：Kotlin + Jetpack Compose（**無 WebView**）+ Media3/ExoPlayer，雙模式首頁、豎滑切集、上一集/下一集按鈕、自動連播、外掛 SRT
- **多端進度互通**：進度按「帳號 + 劇 + 集序號」存服務端 SQLite，網頁看一半，App 打開接著播；重新掃描/換源進度也不丟；**誤觸產生的垃圾紀錄服務端自動忽略，看完的劇自動從首頁消失**（歷史頁仍可見）
- **零刮削依賴**：劇名/集數來自檔案名稱規範，封面用目錄內 `poster.jpg`，無海報時 ffmpeg 自動截取影片隨機幀或產生佔位圖；可選讀取 `tvshow.nfo` 簡介
- **多架構 Docker**：x86_64 + ARM64（buildx 交叉編譯，ARM NAS 上建構無需 QEMU），單映像含可選 ffmpeg
- **內建管理台**：Web 端即可新增/編輯/掃描三種媒體來源、檢視掃描狀態、註冊使用者

## 目錄結構

```
inho-drama/
├── docker-compose.yml        # 一鍵部署
├── Dockerfile                # 多階段多架構建構（前端+後端 → 單映像）
├── docs/
│   ├── api.md                # HTTP API 契約（三端共同依據）
│   ├── deploy.md             # 部署文件（compose / 環境變數 / 反代 / 備份升級）
│   ├── naming.md             # 檔案命名規範（集數/封面/字幕/編碼建議）
│   ├── webdav.md             # WebDAV 對接教學（Alist/群暉/威聯通/OMV）
│   ├── jellyfin.md           # Jellyfin 對接教學（API Key/HLS 轉碼/權限）
│   └── android.md            # 安卓 App 編譯打包指南
├── server/                   # Go 後端（SQLite 純 Go 驅動，無 CGO）
│   ├── main.go
│   ├── webdist/              # 建構時嵌入的 H5 產物
│   └── internal/
│       ├── api/              # 路由 / 鑑權 / 串流代理 / 封面 / 字幕 / 來源管理
│       ├── store/            # SQLite 儲存層
│       ├── source/           # 三種媒體來源掃描器 + 集數解析
│       ├── scanner/          # 掃描佇列與狀態
│       └── ffmpeg/           # 可選截幀封面
├── web/                      # Vue 3 + Vite H5 前端
└── android/                  # Kotlin Jetpack Compose 安卓 App
```

## 快速開始

```bash
cd inho-drama
docker compose up -d --build
# 瀏覽器開啟 http://<NAS_IP>:8654
# 預設帳號 admin / admin（務必盡快修改）
```

然後「我的 → 媒體來源管理」新增來源（本機目錄 `/media` / WebDAV / Jellyfin），自動掃描完成後即可開刷。

安卓 App 原始碼在 `android/`，編譯步驟見 [docs/android.md](docs/android.md)。

## 文件導覽

| 文件 | 內容 |
|---|---|
| [部署文件](docs/deploy.md) | Compose 部署、環境變數、多架構建構與推送、反代、備份升級、FAQ |
| [檔案命名規範](docs/naming.md) | 目錄結構、集數命名、封面/簡介/字幕、網頁端友好的編碼參數 |
| [WebDAV 對接](docs/webdav.md) | Alist / 群暉 / 威聯通 / OMV 的位址與帳號填法、排錯 |
| [Jellyfin 對接](docs/jellyfin.md) | API Key 取得、使用者隔離、HLS 轉碼說明、排錯 |
| [安卓編譯](docs/android.md) | Android Studio / 命令列建構、簽名、常見問題 |
| [API 契約](docs/api.md) | 全部介面與欄位定義（想自己寫用戶端看這個） |

## 技術選型

- **後端**：Go 1.23 + `modernc.org/sqlite`（純 Go SQLite，無 CGO，天然跨架構）+ 標準庫 `net/http`；WebDAV 用戶端 `studio-b12/gowebdav`；密碼 bcrypt；token 鑑權
- **H5**：Vue 3 + Vite + vue-router(hash) + pinia + hls.js（兜底），建構產物 `embed` 進二進位檔
- **安卓**：Kotlin 2.0 + Compose BOM + Navigation + Media3 1.4 + Retrofit + kotlinx-serialization + Coil + DataStore
- **串流**：本機 `http.ServeFile`；WebDAV/Jellyfin 服務端代理並透傳 `Range/206`；Jellyfin 網頁端不友好容器 302 至 `master.m3u8` 轉碼

## AI 開發聲明

本專案由 **AI 全流程開發**：從需求分析、架構設計、後端（Go）、H5 前端（Vue3）、安卓用戶端（Kotlin/Jetpack Compose）、測試、文件，到 **GitHub 開源與版本發布（含推送到 GitHub）**，均由 AI 完成。

## 聲明

本專案僅提供私有影音管理能力，請確保接入的內容為**你擁有合法權利**的影視資源；請勿用於傳播受版權保護的內容。
