# api（后端，负责人：A）

此目录放 Spring Boot 项目：`pom.xml` 与 `src/` 直接放在本目录下（`Dockerfile` 已按此结构编写）。

## 约定

- 包结构沿用 blog 项目：`controller / service / mapper / pojo / dto / vo / exception / config`
- 容器内监听 `8080`，路径前缀 `/api`
- 数据库配置只从环境变量读取，**不要写 fallback 默认值**（缺失即启动失败）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC
    username: ${DB_USER}
    password: ${DB_PASSWORD}
server:
  port: 8080
```

- 本地开发（不走 Docker）时，在 IDEA Run Configuration 里设置这几个环境变量即可
- 接口、错误格式与冲突规则见 `../docs/contract.md`
