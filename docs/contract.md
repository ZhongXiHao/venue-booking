# API 契约 v2（合并版）

本文档合并了「字段与响应约定」和「后端骨架里的接口草稿」，**以本文档为准**，取代两份旧文档。
改接口必须先改本文档、再通知组员、最后改代码。

## 1. 通用约定

| 事项 | 约定 |
|---|---|
| API 前缀 | `/api`（后端 `server.servlet.context-path=/api`，Controller 路径不再写 `/api`） |
| 数据格式 | 请求、响应均为 JSON（UTF-8） |
| 时间基准 | 场地业务时间统一为 **Asia/Shanghai**，日期时间都是"当地墙上时间"，不做时区换算 |
| 日期格式 | `YYYY-MM-DD` |
| 时间格式 | `HH:mm:ss`（界面可只显示 `HH:mm`） |
| id 类型 | JSON 中一律为**字符串**（后端用 Long，序列化时转字符串） |
| 跨天预约 | 不允许（同一 `reservationDate` 内，`startTime` < `endTime`） |
| 时间粒度 | 30 分钟：`startTime`、`endTime` 的分钟只能是 `00` 或 `30`，秒必须是 `00` |
| 过去时间 | 不允许创建已经开始的预约（以 Asia/Shanghai 当前时间判断，由后端负责） |
| 开放时间 | 预约必须完整落在场地 `openTime`~`closeTime` 内 |
| 相邻预约 | 允许：10:00–11:00 与 11:00–12:00 可并存 |
| 已取消预约 | 不参与冲突判断 |
| 冲突区间 | 左闭右开 `[startTime, endTime)` |
| 取消方式 | 逻辑取消：状态改为 `CANCELLED`，不删除记录 |
| 鉴权 | 本实验不做用户认证 |

## 2. 统一响应格式

成功与失败使用同一结构：

```json
{ "code": "SUCCESS", "message": "查询成功", "data": { } }
```

- 成功：`code` 固定为 `SUCCESS`，数据放在 `data`
- 失败：`code` 为下表中的错误码，`data` 为 `null`；仅参数校验失败时 `data` 为 `{ "fieldErrors": [...] }`
- **HTTP 状态码必须与语义一致**（不要一律返回 200），前端先看 HTTP 状态，再用 `code` 区分具体原因
- 不返回异常堆栈；未处理异常统一返回 500 + `INTERNAL_ERROR`，详细信息只写服务端日志

### 状态码与错误码

| HTTP | code | 场景 |
|---|---|---|
| 200 | SUCCESS | 查询、取消成功 |
| 201 | SUCCESS | 创建成功 |
| 400 | VALIDATION_ERROR | 必填缺失、格式错误、时间范围非法、不满足 30 分钟粒度、预约时间已过 |
| 400 | VENUE_CLOSED | 场地已停用（status 为 `DISABLED`） |
| 400 | OUT_OF_OPENING_HOURS | 不在场地开放时间内 |
| 404 | VENUE_NOT_FOUND | 场地不存在 |
| 404 | RESERVATION_NOT_FOUND | 预约不存在 |
| 409 | RESERVATION_CONFLICT | 预约时间冲突 |
| 409 | ALREADY_CANCELLED | 重复取消 |
| 409 | VENUE_NAME_DUPLICATE | 场地名称重复 |
| 500 | INTERNAL_ERROR | 未处理的服务端错误 |

校验失败示例（400）：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数校验失败",
  "data": {
    "fieldErrors": [
      { "field": "bookerName", "message": "预约人或组织名称不能为空" },
      { "field": "endTime", "message": "结束时间必须晚于开始时间" }
    ]
  }
}
```

## 3. 字段定义

### Venue（场地）

| 字段 | 类型 | 说明 | 数据库 |
|---|---|---|---|
| id | string | 后端生成，前端只读 | BIGINT 主键 |
| name | string | 必填，≤100 字，不可重复 | VARCHAR(100) |
| location | string | 必填，≤200 字 | VARCHAR(200) |
| capacity | number | 正整数 | INT |
| openTime | string | 每日开放时间，如 `08:00:00` | TIME |
| closeTime | string | 每日关闭时间，如 `22:00:00` | TIME |
| status | string | `OPEN` / `DISABLED` | ENUM |

### Reservation（预约）

| 字段 | 类型 | 说明 | 数据库 |
|---|---|---|---|
| id | string | 后端生成 | BIGINT 主键 |
| venueId | string | 必填，外键引用场地 | BIGINT 外键 |
| venueName | string | 仅响应返回，关联查询得到，不存库 | - |
| bookerName | string | 预约人或组织，必填，≤100 字 | VARCHAR(100) |
| reservationDate | string | 如 `2026-10-25` | DATE |
| startTime | string | 如 `14:00:00` | TIME |
| endTime | string | 如 `16:00:00` | TIME |
| purpose | string | 使用目的，必填，≤500 字 | VARCHAR(500) |
| status | string | `ACTIVE` / `CANCELLED` | ENUM |

## 4. 接口总览

| 方法 | 路径 | 说明 | 成功状态 |
|---|---|---|---|
| GET | `/api/venues` | 场地列表 | 200 |
| GET | `/api/venues/{id}` | 场地详情 | 200 |
| POST | `/api/venues` | 创建场地 | 201 |
| POST | `/api/reservations` | 创建预约 | 201 |
| GET | `/api/reservations` | 预约列表（分页、可筛选） | 200 |
| GET | `/api/reservations/{id}` | 预约详情 | 200 |
| POST | `/api/reservations/{id}/cancel` | 取消预约（无请求体） | 200 |

> 取消采用题目允许的显式取消接口 `POST .../cancel`，因为是"改状态"而不是物理删除，语义比 `DELETE` 更准确。

## 5. 接口详情

### GET /api/venues
无参数。成功 200，`data` 为场地数组（场地数量少，不分页）：

```json
{ "code": "SUCCESS", "message": "查询成功", "data": [
  { "id": "1", "name": "第一会议室", "location": "行政楼 3 楼 301", "capacity": 20,
    "openTime": "08:00:00", "closeTime": "22:00:00", "status": "OPEN" }
] }
```

### GET /api/venues/{id}
成功 200，`data` 为单个场地对象；不存在 → 404 `VENUE_NOT_FOUND`。

### POST /api/venues
请求体：

```json
{ "name": "新场地", "location": "某楼 1 楼", "capacity": 50,
  "openTime": "09:00:00", "closeTime": "18:00:00", "status": "OPEN" }
```

- `status` 可省略，默认 `OPEN`
- 校验：`name`、`location` 非空；`capacity` > 0；`openTime` < `closeTime` 且均为 30 分钟整点；`status` ∈ {`OPEN`, `DISABLED`}
- 成功 201，`data` 为创建后的场地对象；名称重复 → 409 `VENUE_NAME_DUPLICATE`

### POST /api/reservations
请求体：

```json
{ "venueId": "1", "bookerName": "软件工程学习小组", "reservationDate": "2026-10-25",
  "startTime": "14:00:00", "endTime": "16:00:00", "purpose": "课程项目讨论" }
```

后端按以下顺序校验，**先失败先返回**：

1. 必填非空、格式合法、`startTime` < `endTime`、满足 30 分钟粒度、预约时间尚未开始 → 400 `VALIDATION_ERROR`
2. 场地存在 → 否则 404 `VENUE_NOT_FOUND`
3. 场地状态为 `OPEN` → 否则 400 `VENUE_CLOSED`
4. 时段完整落在开放时间内 → 否则 400 `OUT_OF_OPENING_HOURS`
5. 无冲突 → 否则 409 `RESERVATION_CONFLICT`

成功 201，`data` 为预约对象（含 `venueName`、`status: "ACTIVE"`）。

### GET /api/reservations
查询参数（均可选）：

| 参数 | 默认 | 说明 |
|---|---|---|
| page | 1 | 页码，从 1 开始 |
| pageSize | 10 | 每页条数，最大 100 |
| venueId | - | 按场地筛选 |
| reservationDate | - | 按日期筛选，`YYYY-MM-DD` |
| status | - | `ACTIVE` / `CANCELLED` |

排序：`reservationDate`、`startTime` 升序。成功 200：

```json
{ "code": "SUCCESS", "message": "查询成功", "data": {
  "items": [
    { "id": "101", "venueId": "1", "venueName": "第一会议室", "bookerName": "软件工程学习小组",
      "reservationDate": "2026-10-25", "startTime": "14:00:00", "endTime": "16:00:00",
      "purpose": "课程项目讨论", "status": "ACTIVE" }
  ],
  "total": 1, "page": 1, "pageSize": 10
} }
```

参数非法（如 `page=0`、日期格式错误）→ 400 `VALIDATION_ERROR`。

### GET /api/reservations/{id}
成功 200，`data` 为预约对象；不存在 → 404 `RESERVATION_NOT_FOUND`。

### POST /api/reservations/{id}/cancel
无请求体。成功 200，`message` 为"预约已取消"，`data` 为取消后的预约对象（`status: "CANCELLED"`）；
不存在 → 404 `RESERVATION_NOT_FOUND`；已取消 → 409 `ALREADY_CANCELLED`。

## 6. 冲突判定与并发安全（后端实现）

同一 `venueId`、同一 `reservationDate`、状态为 `ACTIVE` 的既有预约，满足下式即冲突：

```
新.startTime < 既有.endTime  AND  新.endTime > 既有.startTime
```

并发安全：在一个事务内先锁场地行，再做冲突查询和插入，让同一场地的预约请求串行化：

```sql
SELECT id FROM venues WHERE id = ? FOR UPDATE;
SELECT COUNT(*) FROM reservations
 WHERE venue_id = ? AND reservation_date = ? AND status = 'ACTIVE'
   AND start_time < ? AND end_time > ?;
-- 无冲突才 INSERT
```

## 7. 环境与联调约定

| 事项 | 约定 | 负责人 |
|---|---|---|
| 前端请求方式 | 使用相对路径 `/api/...` | 前端 |
| 本地前端端口 | 5173（Vite 开发服务器） | 前端 |
| 本地后端端口 | 8080 | 后端 |
| 本地代理目标 | 默认 `http://localhost:8080`，可配置 | 前端 |
| Docker 前端访问 | 宿主机 `8088` → 容器 `80`（`FRONTEND_PORT` 默认 8088） | 部署 |
| Docker 后端地址 | `http://api:8080` | 后端、部署 |
| 代理路径处理 | **保留 `/api`，不重写** | 前端、部署 |
| 后端监听 | `0.0.0.0:8080` | 后端 |
| 数据库连接 | 仅后端连接，主机名 `database` | 后端、数据库 |
| 凭据 | 数据库凭据不进入前端代码或配置 | 全体 |

## 8. 本版相对旧文档的变更

| 项 | 旧（骨架契约） | 现（采用队友约定） |
|---|---|---|
| 响应结构 | 成功直接返回对象；失败 `{code,message,details}` | 统一 `{code,message,data}` |
| id 类型 | number | string |
| 时间格式 | `HH:mm` | `HH:mm:ss` |
| 预约人字段 | `reserver` | `bookerName` |
| 日期字段 | `date` | `reservationDate` |
| 场地停用状态 | `CLOSED` | `DISABLED`（错误码仍沿用 `VENUE_CLOSED`） |
| 取消接口 | `DELETE /api/reservations/{id}` | `POST /api/reservations/{id}/cancel` |
| 预约列表 | 数组 | 分页 `items/total/page/pageSize` |
| 新增规则 | - | 30 分钟粒度、禁止已开始的预约、不跨天、业务时区 Asia/Shanghai |
| 取消 createdAt | 响应含 createdAt/cancelledAt | 响应不再返回（库里仍可保留） |