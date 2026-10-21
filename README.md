# 场地预约系统（云原生软件技术 Lab 1）

前端 + 后端 API + 关系数据库，通过 Docker Compose 一键运行。

## 架构

```
浏览器 → frontend（nginx，:8088 对外）
            │  /api/ 反向代理
            ▼
         api（Spring Boot，容器内 :8080，不对外）
            │  Compose 网络，服务名 database
            ▼
         database（MySQL 8.0，不对外，数据在命名 Volume db_data）
```

- `frontend` 与 `database` 位于不同网络，前端无法直连数据库
- 只发布前端一个端口

## 目录结构

```
venue-booking/
├── compose.yaml
├── .env.example          # 复制为 .env 使用（.env 不提交）
├── .gitignore
├── api/                  # 后端（Spring Boot + Dockerfile）
├── frontend/             # 前端（静态页面 + nginx.conf + Dockerfile）
├── db/init/              # 建表与种子数据（首次初始化时自动执行）
└── docs/contract.md      # API 契约
```

## 快速开始

```bash
cp .env.example .env      # 按需修改密码
docker compose up --build -d
docker compose ps         # 三个服务应为 running，database 为 healthy
```

浏览器打开 http://localhost:8088

## 接口列表

详见 [docs/contract.md](docs/contract.md)。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/venues | 场地列表 |
| GET | /api/venues/{id} | 场地详情 |
| POST | /api/venues | 创建场地 |
| POST | /api/reservations | 创建预约 |
| GET | /api/reservations | 预约列表（分页、可筛选） |
| GET | /api/reservations/{id} | 预约详情 |
| POST | /api/reservations/{id}/cancel | 取消预约（逻辑取消） |

## 前端对接指南

> 接口的完整定义以 [docs/contract.md](docs/contract.md) 为准，这里只讲怎么快速接上。

### 1. 先把后端跑起来（不需要装 Java）

```bash
cp .env.example .env
docker compose up --build -d
```

- 页面：http://localhost:8088　　接口：http://localhost:8088/api/...
- 启动后等几秒再访问，后端就绪前 nginx 会返回 502
- 种子数据：5 个场地（id 1~5），其中 5 号「旧礼堂」是 `DISABLED`，预约它会得到 `VENUE_CLOSED`

### 2. 代码里怎么写请求

- **只写相对路径** `/api/...`，不要写 `localhost:8080`（前端容器里 nginx 会反代到后端）
- 用 Vite（5173）本地开发时，把 `/api` 代理到上面的 Docker 环境，**保留 `/api` 前缀，不要重写**：

  ```js
  // vite.config.js
  export default { server: { proxy: { '/api': 'http://localhost:8088' } } }
  ```
- 改了 `frontend/` 下的文件后，需要重建前端镜像才会生效：`docker compose up --build -d frontend`

### 3. 响应怎么处理

所有接口（成功和失败）都是同一个结构：`{ "code": "...", "message": "...", "data": ... }`。
`frontend/public/index.html` 里的 `request()` 已经封装好了：成功返回 `data`，失败抛出带 `message` 的 Error，直接复用即可。

```js
// 创建预约
const reservation = await request('/reservations', {
  method: 'POST',
  body: JSON.stringify({
    venueId: '1', bookerName: '软件工程学习小组', reservationDate: '2030-01-15',
    startTime: '14:00:00', endTime: '16:00:00', purpose: '课程项目讨论'
  })
});

// 预约列表（分页，参数都可选）
const page = await request('/reservations?venueId=1&status=ACTIVE&page=1&pageSize=10');
// page = { items: [...], total, page, pageSize }

// 取消预约（无请求体）
await request('/reservations/' + reservation.id + '/cancel', { method: 'POST' });
```

需要区分失败原因时：**先看 HTTP 状态，再看 `code`**。表单校验失败（400 `VALIDATION_ERROR`）时，
`data.fieldErrors` 是 `[{ field, message }]`，`field` 就是请求体里的字段名，可以直接标到对应输入框上。

### 4. 格式速查

| 项 | 约定 |
|---|---|
| id（场地、预约、`venueId`） | JSON 里都是**字符串**，不要当数字做运算 |
| 日期 | `YYYY-MM-DD`，必须是真实存在的日期 |
| 时间 | `HH:mm:ss`（如 `14:00:00`），不接受 `14:00`；界面显示时可自行截成 `HH:mm` |
| 预约时间 | 分钟只能是 `00` 或 `30`；`endTime` 晚于 `startTime`；不能是已经开始的时间；要落在场地开放时间内 |
| 场地状态 | `OPEN` / `DISABLED` |
| 预约状态 | `ACTIVE` / `CANCELLED` |
| 列表分页 | `page` 从 1 开始；`pageSize` 默认 10，最大 100；按日期、开始时间升序 |

### 5. 常见错误码

| HTTP | code | 界面上建议 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 按 `data.fieldErrors` 标出具体字段 |
| 400 | `VENUE_CLOSED` | 提示场地已停用（也可以在选择场地时禁用 `DISABLED` 的项） |
| 400 | `OUT_OF_OPENING_HOURS` | 直接显示 `message`，里面带有场地的开放时间 |
| 404 | `VENUE_NOT_FOUND` / `RESERVATION_NOT_FOUND` | 提示资源不存在，刷新列表 |
| 409 | `RESERVATION_CONFLICT` | 提示该时段已被预约，请换时间 |
| 409 | `ALREADY_CANCELLED` | 提示已取消，刷新列表 |
| 409 | `VENUE_NAME_DUPLICATE` | 提示场地名称已存在 |
| 500 | `INTERNAL_ERROR` | 提示稍后重试 |

此外，请求路径不存在、方法不对、`Content-Type` 不是 JSON 时分别返回 404 `NOT_FOUND`、405 `METHOD_NOT_ALLOWED`、415 `UNSUPPORTED_MEDIA_TYPE`，正常使用不会遇到。

## 联调指南

### 1. 环境准备

1. 安装并启动 Docker Desktop
2. `cp .env.example .env`
3. **检查系统里有没有同名环境变量**：Compose 里系统环境变量的优先级高于 `.env`。如果系统里设置过 `MYSQL_PASSWORD` 等变量，数据库实际密码会和 `.env` 里的不一致，出现 `Access denied`。
   - PowerShell 检查：`Get-ChildItem Env:MYSQL*`；有就删掉（用户级变量用 `[Environment]::SetEnvironmentVariable('MYSQL_PASSWORD', $null, 'User')`），然后重开终端
   - 数据库用户和密码只在数据卷**第一次初始化**时生效，改了之后要 `docker compose down -v` 重建
4. 启动并确认：

   ```bash
   docker compose up --build -d
   docker compose ps          # 三个服务 Up，database 为 healthy
   docker compose logs api    # 看到 "Started ApiApplication" 即就绪
   ```

### 2. 接口验收清单

用 Git Bash 执行（PowerShell 里的 `curl` 是别名，引号规则也不同，不建议）。日期必须是未来日期。

```bash
BASE=http://localhost:8088/api
H='Content-Type: application/json'
body() { echo "{\"venueId\":\"$1\",\"bookerName\":\"联调\",\"reservationDate\":\"2030-01-15\",\"startTime\":\"$2\",\"endTime\":\"$3\",\"purpose\":\"联调\"}"; }

curl -s -w ' [%{http_code}]\n' $BASE/venues                                                  # 1
curl -s -w ' [%{http_code}]\n' $BASE/venues/999                                              # 2
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 1 10:00:00 11:00:00)"   # 3
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 1 10:00:00 11:00:00)"   # 4
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 1 11:00:00 12:00:00)"   # 5
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 1 14:15:00 15:00:00)"   # 6
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 5 10:00:00 11:00:00)"   # 7
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 1 06:00:00 07:00:00)"   # 8
curl -s -w ' [%{http_code}]\n' "$BASE/reservations?venueId=1&reservationDate=2030-01-15"             # 9
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations/1/cancel                                   # 10
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations/1/cancel                                   # 11
curl -s -w ' [%{http_code}]\n' -X POST $BASE/reservations -H "$H" -d "$(body 1 10:00:00 11:00:00)"   # 12
```

| # | 场景 | 期望 |
|---|---|---|
| 1 | 场地列表 | 200，5 个场地，中文正常 |
| 2 | 不存在的场地 | 404 `VENUE_NOT_FOUND` |
| 3 | 正常创建预约 | 201，`status` 为 `ACTIVE`，带 `venueName` |
| 4 | 同一时段再创建 | 409 `RESERVATION_CONFLICT` |
| 5 | 相邻时段（11:00–12:00） | 201 |
| 6 | 开始时间不是 30 分钟整点 | 400 `VALIDATION_ERROR`，`fieldErrors` 指向 `startTime` |
| 7 | 已停用的场地（5 号） | 400 `VENUE_CLOSED` |
| 8 | 超出开放时间 | 400 `OUT_OF_OPENING_HOURS`，消息里有开放时间 |
| 9 | 按场地和日期查询 | 200，`{items,total,page,pageSize}`，共 2 条 |
| 10 | 取消预约（用 #3 返回的 id 替换 `1`） | 200，`status` 为 `CANCELLED` |
| 11 | 再取消一次 | 409 `ALREADY_CANCELLED` |
| 12 | 取消后在同一时段重新预约 | 201（已取消的不占用时段） |

也可以直接在浏览器打开 http://localhost:8088，按 F12 → Console 里用相对路径发请求（当前页面只能查看，创建和取消需要手动发）：

```js
fetch('/api/reservations', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ venueId: '1', bookerName: '联调', reservationDate: '2030-01-15',
                         startTime: '15:00:00', endTime: '16:00:00', purpose: '联调' })
}).then(r => r.json()).then(console.log)
```

### 3. 持久化与重置

- 持久化验证见下一节。
- 联调结束后清掉测试数据：`docker compose down -v && docker compose up -d`（`-v` 会删除数据库数据，回到种子数据）。
- 重新部署后端代码：`docker compose up --build -d api`。

### 4. 查看数据库

数据库不对外发布端口，进容器查询即可（`--default-character-set=utf8mb4` 能避免中文显示成 `?`）：

```bash
docker exec -it venue-database mysql -uvenue_user -p --default-character-set=utf8mb4 venue_booking
# 提示输入密码时输入 .env 里的 MYSQL_PASSWORD，然后：select * from reservations;
```

需要用 DataGrip 等图形工具时，可以在 `compose.yaml` 的 database 下**临时**加 `ports: ["3307:3306"]`，用完删除，不要提交。

## 数据持久化验证（8.3）

```bash
# 1. 创建一条预约并记下编号，然后：
docker compose down          # 注意：不要加 -v，加了会删掉 Volume
docker compose up -d
# 2. 再次查询该预约，确认仍然存在
docker volume ls             # 可见 venue-booking_db_data
```

## 常见问题

- **改了 `db/init/*.sql` 不生效**：初始化脚本只在 Volume 首次创建时执行。需要重来：`docker compose down -v` 后再 `up`
- **刚启动时访问返回 502**：后端比 nginx 慢几秒才就绪，等 `docker compose logs api` 出现 `Started ApiApplication` 再试
- **api 启动报连不上数据库**：先看 `docker compose ps` 中 database 是否 healthy；再检查 `.env` 中账号密码与 `docker compose logs api`
- **中文乱码**：脚本顶部已有 `SET NAMES utf8mb4`；在 Windows 上建议用 Git Bash 执行含中文的 SQL 导入导出，避免 PowerShell 的 UTF-16 重定向

## 小组分工

| 成员  | 负责 |
|-----|---|
| 鍾錫豪 | 后端 API（实体、校验、冲突逻辑、错误处理）+ `api/Dockerfile` |
| 费天睿 | 前端页面 + `frontend/Dockerfile` + `nginx.conf` |
| 關喬賢 | 数据库脚本、`compose.yaml`、`.env.example`、联调验收、实验报告整理 |

## 设计说明

- **取消预约**：逻辑取消（`status = CANCELLED`），保留历史；冲突检查只比对 `ACTIVE`
- **配置外部化**：数据库连接信息由 `.env` → Compose `environment` → 环境变量注入后端；镜像中无密码
- **并发冲突**：创建预约在事务内锁定场地行后再检查冲突
