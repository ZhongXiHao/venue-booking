# ---------- 构建阶段：Node 编译 Vue ----------
FROM node:22-alpine AS build
WORKDIR /build

# 先只复制依赖清单，源码变动时不用重新下载依赖
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# public/index.html 是旧静态页，与 Vite 产物同名，构建时会覆盖 Vue 入口
RUN rm -f public/index.html \
    && npm run build-only

# ---------- 运行阶段：nginx 托管静态文件，并反代 /api ----------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/ /usr/share/nginx/html/

EXPOSE 80
# 官方镜像默认以前台运行（daemon off），无需额外 CMD
