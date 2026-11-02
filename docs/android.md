# 安卓 App 编译与打包指南

原生客户端位于 `android/` 目录：**Kotlin + Jetpack Compose**（全原生 UI，无任何 WebView），播放器为 Media3/ExoPlayer，支持竖滑切集、自动连播、多端进度互通、外挂 SRT。

## 一、环境要求

| 工具 | 版本 |
|---|---|
| JDK | 17（Android Studio 自带 JBR 17 即可） |
| Android Studio | Ladybug (2024.2) 及以上 |
| Android SDK | compileSdk 34（Studio 会自动装） |
| Gradle | 8.9（项目已带 wrapper 配置） |

## 二、用 Android Studio 打包（推荐）

1. **打开项目**：Android Studio → Open → 选择 `nasdrama/android` 目录，等待 Sync 完成（首次会自动下载 Gradle 8.9 与依赖，需要能访问谷歌 Maven；国内可给 Studio 配置代理或镜像）。
2. **连真机调试**：手机开「开发者选项 → USB 调试」，插线后点 Run ▶。
3. **出 Debug 包**：菜单 Build → Build Bundle(s)/APK(s) → Build APK(s)，产物在
   `android/app/build/outputs/apk/debug/app-debug.apk`。
4. **出 Release 包（未签名→签名）**：
   - Build → Generate Signed Bundle / APK → APK → Create new… 新建 keystore（或选已有）；填好密码后选 release，产物在
     `android/app/build/outputs/apk/release/app-release.apk`。

## 三、命令行打包

```bash
cd nasdrama/android

# 没有 gradlew wrapper jar 时（首次克隆），任选其一：
#   a) 用本机 gradle 生成： gradle wrapper --gradle-version 8.9
#   b) 直接用 Android Studio 打开一次（会自动补齐）

# Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release APK（需先配置签名，见下）
./gradlew assembleRelease

# 直接安装到连接的设备
./gradlew installDebug
```

Windows 用 `gradlew.bat`，Linux/macOS 用 `./gradlew`。

### Release 签名配置（命令行）

在 `android/keystore.properties`（不要提交到 git）写入：

```properties
storeFile=../release.keystore
storePassword=你的keystore密码
keyAlias=nasdrama
keyPassword=你的key密码
```

生成 keystore：

```bash
keytool -genkeypair -v -keystore release.keystore -alias nasdrama \
  -keyalg RSA -keysize 2048 -validity 10000
```

然后取消 `app/build.gradle.kts` 中 signingConfigs 注释块并指向该文件即可。

## 四、首次使用

1. 安装打开后进入登录页，**服务器地址**填你的 NAS 地址，如 `http://192.168.1.10:8080`（App 允许明文 http，局域网无需证书）。
2. 输入账号密码登录（账号与 Web 端通用，同一个账号的进度在 H5 / 安卓 / 任何设备间实时互通）。
3. 首页上下滑动刷剧 → 点「立即播放」直接续播上次进度；播放中上下滑切集、播完自动连播；右上角菜单可搜索、看历史、管理媒体源。

## 五、项目结构速览

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   # Gradle 8.9
└── app/src/main/
    ├── AndroidManifest.xml                    # INTERNET + 明文 http
    └── java/com/nasdrama/app/
        ├── MainActivity.kt                    # 单 Activity + NavHost
        ├── data/                              # Retrofit API、DTO、DataStore 设置、仓库层
        ├── ui/theme/                          # Compose 深色主题
        ├── ui/login/ ui/home/ ui/player/      # 登录 / 竖滑刷剧流 / 播放器
        ├── ui/detail/ ui/history/ ui/mine/    # 详情 / 历史 / 我的
        └── ui/admin/                          # 媒体源管理
```

## 六、常见问题

| 问题 | 处理 |
|---|---|
| Sync 卡在下载 Gradle | 给 Studio 设置代理，或在 `gradle/wrapper/gradle-wrapper.properties` 换国内镜像发行版地址 |
| `SDK location not found` | 在 `android/local.properties` 写 `sdk.dir=C\\:\\\\Users\\\\你\\\\AppData\\\\Local\\\\Android\\\\Sdk` |
| 无法连接服务器 | 确认手机与 NAS 同一局域网；浏览器先访问 `http://NAS:8080/api/v1/health` 验证 |
| 明文 http 被拦 | 项目已配 `usesCleartextTraffic="true"`，无需处理；如自行改动 manifest 请保留 |
| 播放转圈不出画面 | 确认该集在 Jellyfin 源里可转码，或换 mp4（见 naming.md 编码建议） |
