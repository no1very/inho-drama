# Jellyfin 源对接教程

已在用 Jellyfin 管理媒体库？InhoDrama 可以直接挂上 Jellyfin，复用它已有的剧集元数据（海报、简介、流派、时长），无需重新刮削。播放默认走 Jellyfin 原文件直连；网页端遇到 mkv 等浏览器不支持的容器时自动 302 到 **Jellyfin HLS 转码**，全格式通吃。

## 一、前置条件

1. Jellyfin 10.8+（更早版本未验证），与 InhoDrama 网络互通。
2. Jellyfin 里已有一个内容类型为「Mixed content / Shows」的媒体库，且目录结构符合短剧布局（一部剧一个目录、集数文件名规范可参考 [naming.md](naming.md)，Jellyfin 自己能正确分集即可）。

## 二、获取 API Key

1. 打开 Jellyfin 控制台：`http://JELLYFIN_IP:8096/web/#/dashboard`
2. 左侧菜单 **控制台 → API 密钥**（Dashboard → API Keys）。
3. 点 **「+」** 添加，应用名随意（如 `inho-drama`），生成后复制那串密钥。

## 三、在 InhoDrama 中添加源

| 字段 | 说明 | 示例 |
|---|---|---|
| 类型 | Jellyfin | — |
| 名称 | 随意 | `客厅 Jellyfin` |
| url | Jellyfin 地址 | `http://192.168.1.10:8096` |
| api_key | 上一步生成的密钥（编辑时留空=保持原值） | `xxxxxxxxxxxxxxxx` |
| user_id | 一般留空即可（见下） | |
| transcode | 勾选后网页端优先走 HLS 转码 | ☑ |
| insecure | Jellyfin 用自签名 HTTPS 时勾选 | ☐ |

保存后自动扫描，剧名/封面/简介/流派/时长全部来自 Jellyfin。

### user_id 什么时候需要填？

API Key 以管理员身份可见**全部**媒体库。如果你只希望 InhoDrama 看到某个用户可见的库（利用 Jellyfin 的库权限隔离），才需要填 user_id：

```bash
# 列出用户拿 id
curl "http://192.168.1.10:8096/Users" -H "X-Emby-Token: 你的KEY"
```

把返回的 `Id` 填进 user_id 即可。

## 四、播放链路说明

| 场景 | 行为 |
|---|---|
| mp4/m4v 等网页友好格式 | 代理 Jellyfin `/Videos/{id}/stream?static=true` 原文件直出，支持 Range |
| mkv/avi 等网页不友好格式 | 网页端自动重试 `/Videos/{id}/master.m3u8`（Jellyfin 实时转码 HLS），安卓端仍走原文件直连（Media3 原生解 mkv） |
| 字幕 | 由 Jellyfin 转码链路处理，InhoDrama 不重复提供 |

> 转码吃 CPU。Jellyfin 控制台 → 播放 → 转码里建议：开启硬件加速（QSV/VAAPI/NVDEC 按你机器选），「允许的视频转码格式」保持默认即可。ARM NAS 性能有限时，尽量提前把库转成 mp4（见 naming.md 第六节）。

## 五、与本地/WebDAV 源共用

三种源可以同时启用，剧库合并展示与推荐；同名剧互不影响（按源隔离）。播放进度按**用户 + 剧 + 集序号**存储，与源类型无关——今天在 Jellyfin 源看到第 8 集，明天把剧挪到本地源重新扫描，进度照常续上。

## 六、常见问题

| 现象 | 处理 |
|---|---|
| 扫描报 401 | API Key 无效：重新生成；注意复制时别带空格 |
| 扫描成功但剧数为 0 | Jellyfin 里该剧库未扫描完成；先在 Jellyfin 侧扫完，再点 InhoDrama「扫描」 |
| 网页放 mkv 报错 | 勾选 `transcode`；确认 Jellyfin 转码可用（CPU/硬件加速）；查看 Jellyfin 日志 |
| 安卓端正常网页端不行 | 预期行为：安卓 Media3 支持几乎所有容器，网页端受浏览器限制走转码 |
| 拖动转码视频卡 | Jellyfin 转码 segment 参数问题；在 Jellyfin 转码设置里勾选「允许视频回删」(Enable segment deletion) |
| InhoDrama 上搜不到新加的剧 | Jellyfin 侧刮削完成后，到 InhoDrama 媒体源管理点一次「扫描」 |
