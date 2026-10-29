# ---------- 构建阶段：Node + Vite，产出 dist/ ----------
FROM node:24-alpine AS build
WORKDIR /app

# 只复制依赖清单，利用 Docker 层缓存：依赖不变时不用重新下载
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# build 脚本 = vue-tsc 类型检查 + vite build，类型错误会让镜像构建失败
RUN npm run build

# ---------- 运行阶段：Nginx 提供静态资源与反向代理 ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 80
# nginx 官方镜像默认以前台方式运行（daemon off），无需额外 CMD
