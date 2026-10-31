# =============================================================================
# simple-shop 阶段二前端组合镜像
# 构建上下文：仓库根目录（deploy/docker-compose.yml 的 context: ..）
#   · 买家 SPA 构建到 /（createWebHistory 根路径）
#   · 卖家 SPA 构建到 /seller/（vite --base=/seller/）
#   · nginx 同时托管两个 SPA，并把 /api、/media 反代到 backend
# =============================================================================
ARG REGISTRY=docker.xuanyuan.me/library/

# ---------------------------------------------------------------------------
# 买家端
# ---------------------------------------------------------------------------
FROM ${REGISTRY}node:24-bookworm-slim AS buyer-build

WORKDIR /build/buyer

COPY simple-shop-buyer-frontend/package.json simple-shop-buyer-frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY simple-shop-buyer-frontend/ ./
RUN npm run build

# ---------------------------------------------------------------------------
# 卖家端
# ---------------------------------------------------------------------------
FROM ${REGISTRY}node:24-bookworm-slim AS seller-build

WORKDIR /build/seller

COPY simple-shop-seller-frontend/package.json simple-shop-seller-frontend/package-lock.json simple-shop-seller-frontend/.npmrc ./
RUN npm ci --no-audit --no-fund

COPY simple-shop-seller-frontend/ ./
RUN npm run type-check && ./node_modules/.bin/vite build --base=/seller/

# ---------------------------------------------------------------------------
# 运行阶段：一个 nginx 同时托管 / 与 /seller/
# ---------------------------------------------------------------------------
FROM ${REGISTRY}nginx:1.27-alpine

COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=buyer-build /build/buyer/dist /usr/share/nginx/html
COPY --from=seller-build /build/seller/dist /usr/share/nginx/html/seller

EXPOSE 80
