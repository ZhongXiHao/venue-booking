FROM nginx:1.27-alpine

# 静态页面 + 反向代理配置
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY public/ /usr/share/nginx/html/

EXPOSE 80
# nginx 官方镜像默认以前台方式运行（daemon off），无需额外 CMD
