# WebDAV 源对接教程

剧库放在另一台机器 / 网盘 / Alist 上时，用 WebDAV 源：InhoDrama 只扫描目录结构建立索引，播放时实时流式转发（支持 Range 拖动），**不会把文件复制到本地**。

## 一、填什么

在 Web 端「媒体源管理 → 添加媒体源」中：

| 字段 | 说明 | 示例 |
|---|---|---|
| 类型 | WebDAV | — |
| 名称 | 随意，展示用 | `Alist 剧库` |
| url | WebDAV 服务根地址 | `http://192.168.1.10:5244/dav` |
| 用户名 | 服务账号 | `admin` |
| 密码 | 服务密码（编辑时留空=保持原值） | `******` |
| base_path | 相对 dav 根目录的起始路径，即「短剧库所在的目录」 | `/媒体/短剧` |
| insecure | HTTPS 自签名证书时勾选 | ☐ |

规则：

- `url` 填到 WebDAV 服务根（含 ContextPath），不要带上剧库名；剧库名写进 `base_path`。
- `base_path` 以 `/` 开头；留空表示 dav 根目录。
- 扫描逻辑与本地源一致：`base_path` 下每个一级子目录是一部剧，命名规范见 [naming.md](naming.md)。
- 扫描深度最多 4 层，隐藏目录（`.` 开头）跳过。

## 二、各服务获取 WebDAV 地址与密码

### Alist（推荐，支持聚合各家网盘）

1. 安装：`docker run -d --name alist -p 5244:5244 -v /volume1/docker/alist:/opt/alist xhofe/alist:latest`
2. 浏览器打开 `http://NAS_IP:5244`，进管理后台 → 存储 → 添加存储（本地磁盘或任意网盘）。
3. WebDAV 地址就是 `http://NAS_IP:5244/dav`；账号密码 = Alist 登录账号密码（后台 → 用户 里可单独设置）。
4. 若剧库挂在 Alist 的 `/媒体/短剧` 路径，`base_path` 填 `/媒体/短剧`。

### 群晖 DSM

1. 控制面板 → 文件服务 → **WebDAV** → 启用 WebDAV（HTTP 端口 5005 / HTTPS 5006）。
2. 地址：`http://NAS_IP:5005`（https 则 5006；DSM 自签名证书需勾选 `insecure`）。
3. 账号密码 = DSM 用户账号（需有对应共享文件夹的读写权限；只给读取权限也能扫描播放）。
4. 例：剧库在共享文件夹 `video/短剧`，`base_path` 填 `/video/短剧`。

### 威联通 QTS

Hybrid Backup Sync / File Station 均可启用 WebDAV（控制台 → 网络与文件服务 → Win/Mac/NFS → WebDAV），端口默认 8081/8082，填法同上。

### OpenMediaVault / Linux

安装 `apache2` 的 `dav` 模块或轻量的 `dufs`/`webdav-server`，例如 dufs 一行起服务：

```bash
dufs -A /srv/media --auth admin:pass@/:rw -p 5007
```

地址 `http://NAS_IP:5007`，`base_path` 填 `/短剧`。

## 三、验证连通性

保存源后系统自动扫描。若失败，可先用 curl 手测（路径含中文时 curl 会自动转义）：

```bash
# 能列出剧名目录即通
curl -u admin:pass -X PROPFIND -H "Depth: 1" http://192.168.1.10:5244/dav/%E7%9F%AD%E5%89%A7/
```

## 四、常见问题

| 现象 | 处理 |
|---|---|
| 扫描报 401 | 用户名/密码错误；Alist 注意是**登录密码**而非 token |
| 扫描报 404 | `base_path` 写错，或 url 少了 `/dav` 这类 ContextPath |
| HTTPS 报证书错误 | 勾选 `insecure`（自签名场景） |
| 能扫描但播放卡顿 | 大多为网盘中转限速（如天翼云盘），换本地盘或开启 Alist 的代理；InhoDrama 侧是纯转发，无缓冲设置 |
| 播放 502 | WebDAV 服务端断开/限流，重试或降低并发 |
| 中文目录乱码 | WebDAV 服务端编码非 UTF-8 所致，优先用 Alist/dufs 这类 UTF-8 实现 |
