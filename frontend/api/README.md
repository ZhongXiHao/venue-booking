# api（后端，负责人：鍾錫豪）

此目录放 Spring Boot 项目：`pom.xml` 与 `src/` 直接放在本目录下（`Dockerfile` 已按此结构编写）。

## 约定

- 包结构：`controller / service / mapper / entity / dto / vo / common / exception`
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

## 本地运行后端（不走 Docker）

1. 只启动数据库：在仓库根目录 `docker compose up -d database`。数据库默认不对外发布端口，本地调试需要在 `compose.yaml` 的 database 下**临时**加 `ports: ["3307:3306"]`（用完删除，不要提交）。
2. 设置环境变量（IDEA 的 Run Configuration，或终端里 export）：

   | 变量 | 值（对应 `.env.example`）       |
   |---|----------------------------|
   | `DB_HOST` | `localhost`                |
   | `DB_PORT` | `3307`（与上面发布的端口一致）         |
   | `DB_NAME` | `venue_booking`            |
   | `DB_USER` | `venue_user`               |
   | `DB_PASSWORD` | `.env` 里的 `MYSQL_PASSWORD` |

3. 运行 `ApiApplication`，或在本目录执行 `./mvnw spring-boot:run`，访问 `http://localhost:8080/api/venues`。
4. 测试：`./mvnw test`。`VenueTests`、`ReservationTests`、`ApiApplicationTests` 是 `@SpringBootTest`，**需要上面的数据库和环境变量**，没有数据库会失败；其中 `ReservationTests` 用 `@Transactional` 自动回滚，不会留下数据。

## 代码约定

- Controller 路径里不写 `/api`（已由 `context-path` 提供）；响应统一用 `Result`，业务错误抛 `BusinessException(ErrorCode)`，由 `GlobalExceptionHandler` 转成 `{code,message,data}`
- 新增错误码先在 `docs/contract.md` 的错误码表里登记，再加到 `ErrorCode`
- 请求 DTO 里的日期、时间字段用 `@JsonFormat(..., lenient = OptBoolean.FALSE)`，否则 `2030-02-30`、`24:00:00` 会被悄悄改成别的值
- 创建预约在**事务内先锁场地行**（`lockVenueByIdForUpdate`）再查冲突，加锁必须是事务里的第一次查询
