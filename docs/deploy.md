# InhoDrama 部署文档

InhoDrama 是一套部署在自己 NAS 上的短剧流媒体系统：后端 Go + SQLite 单二进制（Docker 多阶段构建，同时支持 **x86_64 与 ARM64**），前端 H5 内嵌进二进制，另有原生安卓 App。**不依赖任何外网刮削接口**，全部元数据来自本地文件名 / nfo / 你已有的 Jellyfin 库。

## 一、环境要求

| 项目 | 要求 |
|---|---|
| 系统 | 群晖 / 威联通 / Unraid / OpenMediaVault / Debian / Ubuntu / PVE 等，x86_64 或 ARM64 均可 |
| Docker | 20.10+（含 docker compose 插件） |
| 磁盘 | 应用本体约 200MB；数据库与封面缓存随剧库增长（1000 部剧约 1GB 内） |
| 内存 | 256MB 起步即可（流式转发不占内存） |
| 网络 | 局域网即可；仅首次 `docker build` 需要联网拉取依赖 |

## 二、快速开始（Docker Compose，推荐）

```bash
# 1. 上传项目到 NAS（如 /volume1/docker/inho-drama），进入目录
cd /volume1/docker/inho-drama

# 2. （可选）编辑 docker-compose.yml：端口、管理员密码、媒体目录挂载

# 3. 构建并启动（首次构建约 3-8 分钟，视网络而定）
docker compose up -d --build

# 4. 查看日志确认启动
docker compose logs -f
# 看到 "InhoDrama v1.0.0 已启动" 即成功

# 5. 浏览器打开
# http://<NAS的IP>:8080
```

默认管理员：`admin / admin123456`（compose 环境变量 `NASDRAMA_ADMIN_PASSWORD` 可改，**请尽快在 Web 端修改密码**）。

登录后进入「我的 → 媒体源管理」添加媒体源，然后等待自动扫描完成即可刷剧。三种源的接入教程：

- 本地目录：见下文第三节
- WebDAV：见 [webdav.md](webdav.md)
- Jellyfin：见 [jellyfin.md](jellyfin.md)

## 三、添加本地目录源

1. 把短剧目录挂载进容器。`docker-compose.yml` 默认已带 `./media:/media:ro`，把你的剧放进 `inho-drama/media/` 即可；也可以自行追加一行挂载 NAS 上已有的目录（**只读挂载即可，服务端不会改你的文件**）：

```yaml
    volumes:
      - ./data:/data
      - /volume1/video/短剧:/media:ro
```

2. Web 端「媒体源管理 → 添加媒体源」：类型选 **本地目录**，`path` 填 `/media`（容器内路径）。
3. 保存后自动开始扫描。目录组织方式见 [naming.md](naming.md)。

## 四、环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `NASDRAMA_PORT` | `8080` | HTTP 监听端口 |
| `NASDRAMA_DATA_DIR` | `/data` | 数据库与封面缓存目录 |
| `NASDRAMA_ADMIN_USER` | `admin` | 首次启动创建的管理员用户名 |
| `NASDRAMA_ADMIN_PASSWORD` | `admin123456` | 首次启动创建的管理员密码 |
| `NASDRAMA_ALLOW_REGISTER` | `true` | 是否允许自助注册 |
| `NASDRAMA_SCAN_ON_START` | `true` | 启动时自动扫描所有启用的媒体源 |
| `NASDRAMA_TOKEN_TTL_DAYS` | `30` | 登录 token 有效天数 |
| `TZ` | — | 时区，建议 `Asia/Shanghai` |

> 管理员账号只在首次启动（users 表为空）时创建；之后修改这些变量不会改动已有账号密码。

## 五、多架构构建（x86_64 + ARM64）

Dockerfile 采用 `$BUILDPLATFORM` + `GOOS/GOARCH` 交叉编译方案，**在任意架构的机器上构建都会产出目标架构镜像，且无需 QEMU 模拟**（Go 静态编译 + 纯 Go SQLite 驱动，天然跨架构）。

在本机一次性构建双架构镜像并推送到仓库：

```bash
# 初始化 buildx（多数新版 Docker 已自带）
docker buildx create --use --name inho-drama-builder

# 构建并推送（替换 yourname/你的仓库）
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t docker.io/yourname/inho-drama:latest \
  -t ghcr.io/yourname/inho-drama:latest \
  --push .
```

推好后，任何 NAS 上都能直接拉取运行，把 `docker-compose.yml` 中 `build: .` 换成 `image: yourname/inho-drama:latest` 即可。

项目还内置 GitHub Actions（`.github/workflows/docker.yml`）：推送到 `main` 或打 `v*` 标签时自动构建双架构镜像发布到 GHCR。

### 在 ARM NAS 上直接本地构建

群晖 / 树莓派等 ARM 设备直接 `docker compose up -d --build` 即可——buildx 未启用时传统构建器也能本地构建本机架构镜像，Dockerfile 完全兼容。

## 六、反向代理（可选）

需要公网 HTTPS 访问时，在反向代理后挂 `8080` 即可，注意三点：

1. **关闭代理缓冲**（nginx: `proxy_buffering off; proxy_request_buffering off;`），否则视频拖动会卡。
2. 透传 `Range` 头（nginx 默认透传，勿改写）。
3. 客户端多、并发高时把 `proxy_read_timeout` 调大到 3600s。

nginx 示例：

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_buffering off;
    proxy_request_buffering off;
    proxy_read_timeout 3600s;
    proxy_set_header Host $host;
}
```

Caddy 更简单（自动 HTTPS）：

```
drama.example.com {
    reverse_proxy 127.0.0.1:8080 {
        flush_interval -1
    }
}
```

> 公网暴露请务必：改掉默认密码、关闭注册（`NASDRAMA_ALLOW_REGISTER=false`）、最好再套一层 VPN（WireGuard/Tailscale）。

## 七、数据备份与升级

- **备份**：停服后打包 `data/` 目录即可（SQLite 单文件 `inho-drama.db` + `covers/` 封面缓存）。媒体文件在源目录，不在此列。
- **升级**：`git pull`（或替换新代码）→ `docker compose up -d --build`。数据库自动迁移，进度、账号、媒体源配置全部保留。
- **完全重置**：删除 `data/` 目录后重启。

## 八、常见问题

| 现象 | 排查 |
|---|---|
| 扫描后列表为空 | 检查目录结构是否符合 [naming.md](naming.md)：源目录下必须是一级子目录（每部剧一个文件夹），文件夹里才有视频 |
| 本地源扫不到 | 确认挂载路径是**容器内**路径（如 `/media`），`docker compose exec inho-drama ls /media` 能看到剧名目录 |
| 网页能放 mp4 但 mkv 不能 | 正常现象：浏览器不支持 mkv 容器。Jellyfin 源会自动走 HLS 转码兜底；本地/WebDAV 源建议转 mp4，或用安卓 App 看（Media3 原生支持 mkv） |
| WebDAV 401/403 | 检查账号密码、WebDAV 服务是否对该账号开放对应目录权限 |
| Jellyfin 401 | API Key 无效或被删除；到 Jellyfin 控制台重新生成 |
| 拖进度条卡顿 | 反向代理没关缓冲（见第六节），或磁盘/NAS CPU 过载 |
| 忘记管理员密码 | 删除 `data/inho-drama.db` 会重置全部数据；或临时用 SQLite 工具删掉 users 表中该用户后重启容器（会用环境变量密码重建 admin） |
