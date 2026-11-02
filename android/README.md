# InhoDrama 安卓 App

NAS 短剧系统的原生安卓客户端：**Kotlin + Jetpack Compose**（UI 全部原生绘制，无任何 WebView / JS 桥），播放器为 Media3 / ExoPlayer。

## 功能

- 首页全屏**上下滑动刷剧流**（VerticalPager），继续观看横滑区
- 点击剧集 → 详情选集 / 直接播放；播放中**上下滑切集**、播完**自动连播**
- **多端播放进度互通**：每 5 秒 / 暂停 / 切集 / 退出时上报服务端，与 Web H5 共用同一账号体系
- 外挂同名 `.srt` 字幕自动加载（Media3 SubtitleConfiguration）
- mkv/avi 等容器播放失败时自动切换 HLS 转码地址重试（需 Jellyfin 源）
- 媒体源管理（本地目录 / WebDAV / Jellyfin 增删改 + 扫描 + 状态轮询）

## 编译

详细步骤（Android Studio / 命令行 / 签名）见 [`../docs/android.md`](../docs/android.md)。速览：

```bash
# Android Studio Ladybug+ 打开本目录，Sync 后 Run

# 或命令行（JDK 17）：
gradlew.bat assembleDebug        # Windows
./gradlew assembleDebug          # Linux/macOS
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

首次使用：登录页填写 NAS 地址（如 `http://192.168.1.10:8080`）+ 账号密码（与 Web 端通用）。

## 结构

```
app/src/main/java/com/inho-drama/app/
├── MainActivity.kt          # 单 Activity 入口（enableEdgeToEdge）
├── AppNavHost.kt            # 路由：login/home/history/mine/detail/player/adminSources
├── data/
│   ├── Api.kt               # Retrofit + 动态 BaseUrl/鉴权拦截器（ApiSession/AppGraph）
│   ├── Models.kt            # DTO（字段与 docs/api.md 一致）
│   ├── Repo.kt              # 仓库层（Result 包装 / 401 登出事件 / URL 拼接）
│   └── SettingsStore.kt     # DataStore：serverUrl/token/username
├── ui/
│   ├── theme/               # 深色主题
│   ├── login/               # 登录 / 注册（server-info 探活）
│   ├── home/                # 竖滑剧流 + 继续观看 + 防抖搜索
│   ├── player/              # ExoPlayer 播放器（进度互通核心）
│   ├── detail/              # 详情 + 选集
│   ├── history/             # 观看历史
│   ├── mine/                # 账号 / 服务器设置
│   └── admin/               # 媒体源管理
└── util/Formatters.kt
```

## 技术版本

Gradle 8.9 · AGP 8.5.2 · Kotlin 2.0.21 · compileSdk 34 · minSdk 24 · Compose BOM 2024.09.03 · Media3 1.4.1 · Retrofit 2.11 · Coil 2.7 · DataStore 1.1.1（版本集中在 `gradle/libs.versions.toml`）
