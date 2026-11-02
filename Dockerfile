# syntax=docker/dockerfile:1
# 多架构构建：docker buildx build --platform linux/amd64,linux/arm64 -t inho-drama:latest .
# 阶段一/二跑在构建机原生架构上（$BUILDPLATFORM），通过 GOOS/GOARCH 交叉编译，
# 因此在 ARM NAS（群晖/树莓派等）上构建也不需要 QEMU 模拟，速度快且稳定。

# ---------- 阶段一：构建 H5 前端 ----------
FROM --platform=$BUILDPLATFORM node:20-alpine AS webbuild
WORKDIR /src/web
COPY web/package.json ./
RUN npm install --no-audit --no-fund
COPY web/ ./
RUN npm run build

# ---------- 阶段二：交叉编译 Go 后端（纯静态，无 CGO） ----------
# 用 Go 1.26：最新版 golang.org/x/crypto 等依赖要求 go >= 1.26
FROM --platform=$BUILDPLATFORM golang:1.26-alpine AS gobuild
ARG TARGETOS
ARG TARGETARCH
WORKDIR /src
COPY server/go.mod ./
COPY server/ ./
# go mod tidy 生成 go.sum 并解析依赖（首次构建需要联网）
# 使用国内 Go 代理，避免 proxy.golang.org 被墙导致构建失败
ENV GOPROXY=https://goproxy.cn,direct
RUN go mod tidy
# 把构建好的前端嵌入二进制
COPY --from=webbuild /src/web/dist ./webdist
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags="-s -w" -o /out/inho-drama .

# ---------- 阶段三：运行镜像 ----------
FROM alpine:3.20
# 默认封面为内置图片，不再截视频帧，无需 ffmpeg
RUN apk add --no-cache ca-certificates tzdata
COPY --from=gobuild /out/inho-drama /usr/local/bin/inho-drama
ENV NASDRAMA_PORT=8654 \
    NASDRAMA_DATA_DIR=/data
VOLUME ["/data"]
EXPOSE 8654
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8654/api/v1/health >/dev/null 2>&1 || exit 1
ENTRYPOINT ["/usr/local/bin/inho-drama"]
