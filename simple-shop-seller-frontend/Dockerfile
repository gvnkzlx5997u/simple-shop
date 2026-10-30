# =============================================================================
# simple-shop 卖家前端**独立预览**镜像（Vite 构建 SPA + nginx 托管与反向代理）
# 正式部署使用 deploy/frontend 的组合镜像（同时托管买家 / 与卖家 /seller/）
#   · 构建期显式 --base=/seller/，只影响构建，不影响本地 npm run dev
#   · 用 Debian 版 node（不用 alpine），避开 rolldown/lightningcss 等原生依赖的 musl 兼容问题
# 镜像源：默认走轩辕镜像免费版（记录日期 2026-10-13），回退官方源用
#   docker build --build-arg REGISTRY= ...
# =============================================================================
ARG REGISTRY=docker.xuanyuan.me/library/

# ---------------------------------------------------------------------------
# 构建阶段：产出 dist/
# ---------------------------------------------------------------------------
FROM ${REGISTRY}node:24-bookworm-slim AS build

WORKDIR /build

# 先装依赖，利用层缓存；.npmrc 指定 npmmirror
COPY package.json package-lock.json .npmrc ./.
RUN npm ci

# 再拷源码构建（node_modules/dist 由 .dockerignore 排除，不会覆盖上面装好的依赖）
COPY . .

# 显式调用本地 vite，不用 npm run build —— run-p 的 {@} 传参会把 --base 弄乱；
# 也不用 npx（避免它在缺包时联网）；需要 Docker 23+ 自带前端支持 cache/多阶段。
RUN npm run type-check && ./node_modules/.bin/vite build --base=/seller/

# ---------------------------------------------------------------------------
# 运行阶段：nginx 托管 SPA + 反向代理后端
# ---------------------------------------------------------------------------
FROM ${REGISTRY}nginx:1.27-alpine AS runtime

# SPA 产物放在 /seller 这一层，与 location /seller/ 对应
COPY --from=build /build/dist /usr/share/nginx/html/seller
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
