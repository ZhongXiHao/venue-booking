# API 契约

前端（B）与后端（A）以本文档为准。有改动先改文档、再通知组员。

- 基础路径：`/api`，请求与响应均为 JSON（UTF-8）
- 时间约定：`date` 用 `YYYY-MM-DD`，`time` 用 `HH:mm`（场地所在地的本地"墙上时间"，不做时区换算）；`createdAt` / `cancelledAt` 为 UTC，ISO-8601 带 `Z`（如 `2026-10-19T04:10:00Z`）
- HTTP 状态码必须与错误语义一致（Controller 返回 `ResponseEntity`，不要一律 200）

## 1. 通用错误格式

所有非 2xx 响应统一为：

```json
{
  "code": "RESERVATION_CONFLICT",
  "message": "该场地在 2026-11-01 10:00-11:00 已有预约",
  "details": []
}
```

`details` 仅参数校验失败时有内容：`[{"field": "endTime", "message": "结束时间必须晚于开始时间"}]`。**不返回异常堆栈**；未处理异常统一返回 500 + `INTERNAL_ERROR`，详细信息只写服务端日志。

| 场景 | HTTP | code |
|---|---|---|
| 查询 / 取消成功 | 200 | - |
| 创建成功 | 201 | - |
| 必填缺失、格式错误、时间范围非法 | 400 | `VALIDATION_ERROR` |
| 场地已停用 | 400 | `VENUE_CLOSED` |
| 不在场地开放时间内 | 400 | `OUT_OF_OPENING_HOURS` |
| 场地不存在 | 404 | `VENUE_NOT_FOUND` |
| 预约不存在 | 404 | `RESERVATION_NOT_FOUND` |
| 预约时间冲突 | 409 | `RESERVATION_CONFLICT` |
| 重复取消已取消的预约 | 409 | `ALREADY_CANCELLED` |
| 未处理的服务端错误 | 500 | `INTERNAL_ERROR` |

## 2. 场地

### GET /api/venues
响应 200：

```json
[
  {"id": 1, "name": "第一会议室", "location": "行政楼 3 楼 301", "capacity": 20,
   "openTime": "08:00", "closeTime": "22:00", "status": "OPEN"}
]
```

### GET /api/venues/{id}
响应 200：单个场地对象（同上）；不存在 → 404 `VENUE_NOT_FOUND`

### POST /api/venues
请求：

```json
{"name": "新场地", "location": "某楼 1 楼", "capacity": 50,
 "openTime": "09:00", "closeTime": "18:00", "status": "OPEN"}
```

校验：`name`、`location` 非空；`capacity` > 0；`openTime` < `closeTime`；`status` ∈ {`OPEN`, `CLOSED`}（缺省 `OPEN`）。
响应 201：创建后的场地对象；名称重复 → 409 `VENUE_NAME_DUPLICATE`

## 3. 预约

预约对象：

```json
{"id": 12, "venueId": 1, "venueName": "第一会议室", "reserver": "张三",
 "date": "2026-11-01", "startTime": "10:00", "endTime": "11:00",
 "purpose": "小组讨论", "status": "ACTIVE",
 "createdAt": "2026-10-19T04:10:00Z", "cancelledAt": null}
```

### POST /api/reservations
请求：

```json
{"venueId": 1, "reserver": "张三", "date": "2026-11-01",
 "startTime": "10:00", "endTime": "11:00", "purpose": "小组讨论"}
```

校验顺序（先格式、再存在性、再业务规则）：
1. 必填字段非空、日期时间格式合法、`startTime` < `endTime`、`date` 不早于当天（不允许预约过去的日期） → 400 `VALIDATION_ERROR`
2. 场地存在 → 否则 404 `VENUE_NOT_FOUND`
3. 场地状态为 `OPEN` → 否则 400 `VENUE_CLOSED`
4. 预约时段落在场地 `openTime`~`closeTime` 内 → 否则 400 `OUT_OF_OPENING_HOURS`
5. 无冲突 → 否则 409 `RESERVATION_CONFLICT`

响应 201：预约对象

### GET /api/reservations
可选查询参数：`venueId`、`date`、`status`（后端必须全部支持；前端"按日期筛选"为选做）。按 `date`、`startTime` 升序。响应 200：预约对象数组。

### GET /api/reservations/{id}
响应 200：预约对象；不存在 → 404 `RESERVATION_NOT_FOUND`

### DELETE /api/reservations/{id}
**逻辑取消**：`status` 改为 `CANCELLED` 并记录 `cancelledAt`，不物理删除。
响应 200：取消后的预约对象；不存在 → 404；已是 `CANCELLED` → 409 `ALREADY_CANCELLED`

## 4. 冲突判定规则（核心业务逻辑）

同一 `venueId`、同一 `date`、状态为 `ACTIVE` 的既有预约，若满足下式则冲突：

```
新.startTime < 既有.endTime  AND  新.endTime > 既有.startTime
```

- 首尾相接**不算冲突**：10:00-11:00 与 11:00-12:00 可以并存（边界用严格不等号）
- `CANCELLED` 的预约不参与判定，所以取消后同一时段可以重新预约（对应验收 8.1 第 7 条）
- 冲突只在后端判断，前端不做

参考查询：

```sql
SELECT COUNT(*) FROM reservations
WHERE venue_id = ? AND reservation_date = ? AND status = 'ACTIVE'
  AND start_time < ? /* 新.endTime */ AND end_time > ? /* 新.startTime */;
```

### 并发安全

"先查冲突、再插入"在两个请求同时到达时可能都通过检查。建议在一个事务内先锁住场地行，让同一场地的预约请求串行化：

```sql
START TRANSACTION;
SELECT id FROM venues WHERE id = ? FOR UPDATE;   -- 锁场地行
-- 然后执行上面的冲突查询；无冲突才 INSERT
COMMIT;
```

Spring 里对应 `@Transactional` + Mapper 里的 `SELECT ... FOR UPDATE`。实验报告中写明这一点。