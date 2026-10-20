# api、接口对接

## 业务规则

| 问题             | 建议约定                                  |
| ---------------- | ----------------------------------------- |
| 时间基准         | 场地业务时间统一为 Asia/Shanghai          |
| 日期格式         | YYYY-MM-DD                                |
| 时间格式         | HH:mm:ss；界面可以只显示小时和分钟        |
| 是否允许跨天     | 不允许进行跨天预约                        |
| 时间范围         | 开始时间必须早于结束时间                  |
| 是否允许过去时间 | 建议禁止创建已经开始的预约，由后端判断    |
| 开放时间限制     | 预约必须完整落在开放时间内                |
| 时间粒度         | 时间颗粒度为30min，不接受小于颗粒度的预约 |
| 相邻预约         | 允许，如 10:00–11:00 与 11:00–12:00       |
| 已取消预约       | 不参与冲突判断                            |
| 预约冲突规则     | 左闭右开区间 [startTime, endTime ）   |

## api 对齐

| 对齐事项             | 最终约定                                 | 负责人         |
| -------------------- | ---------------------------------------- | -------------- |
| API 前缀             | /api                                     | 前后端共同确认 |
| 前端请求方式         | 使用相对路径 /api/...                    | 前端           |
| 本地前端端口         | 5173                                     | 前端           |
| 本地后端端口         | 8080                                     | 后端           |
| 本地代理目标         | 默认 http://localhost:8080，允许配置修改 | 前端           |
| Docker  前端访问端口 | 宿主机 8088 → 容器 80                    | 部署           |
| Docker  后端服务地址 | http://api:8080                          | 后端、部署     |
| 代理路径处理         | 保留 /api，不重写                        | 前端、部署     |
| 后端容器监听地址     | 0.0.0.0:8080                             | 后端           |
| 数据库连接           | 仅后端连接，主机名使用 database          | 后端、数据库   |
| 配置与密码           | 数据库凭据不进入前端代码或前端配置       | 全体           |

## 字段对齐

### Venue

| API 字段  | JSON 类型 | 含义与建议规则              | 数据库存储方向 |
| --------- | --------- | --------------------------- | -------------- |
| id        | string    | 后端生成，前端只读取        | 主键           |
| name      | string    | 场地名称，必填，最长 100 字 | VARCHAR        |
| location  | string    | 位置，必填，最长 200 字     | VARCHAR        |
| capacity  | number    | 容纳人数，正整数            | INTEGER        |
| openTime  | string    | 每日开放时间，如 08:00:00   | TIME           |
| closeTime | string    | 每日关闭时间，如 22:00:00   | TIME           |
| status    | string    | OPEN / DISABLED             | VARCHAR 加约束 |

### reservation

| reservation     |           |                                 |                            |
| --------------- | --------- | ------------------------------- | -------------------------- |
| API 字段        | JSON 类型 | 含义与建议规则                  | 数据库存储方向             |
| id              | string    | 后端生成                        | 主键                       |
| venueId         | string    | 场地 ID，必填                   | 外键，引用场地主键         |
| venueName       | string    | 查询时返回，方便列表展示        | 后端关联查询，不必重复存储 |
| bookerName      | string    | 预约人或组织，必填，最长 100 字 | VARCHAR                    |
| reservationDate | string    | 日期，如 2026-10-25             | DATE                       |
| startTime       | string    | 开始时间，如 14:00:00           | TIME                       |
| endTime         | string    | 结束时间，如 16:00:00           | TIME                       |
| purpose         | string    | 使用目的，必填，最长 500 字     | VARCHAR                    |
| status          | string    | ACTIVE / CANCELLED              | VARCHAR 加约束             |

## 响应规则

| 状态码 | 含义                             | 返回规则（json 格式）                                        |
| ------ | -------------------------------- | ------------------------------------------------------------ |
| 200    | 查询单个场地成功                 | {      "code":  "SUCCESS",      "message":  "查询成功",      "data": {        "id": "1",        "name":  "第一会议室",        "location": "教学楼A栋201",        "capacity": 30,        "openTime":  "08:00:00",        "closeTime":  "22:00:00",        "status":  "OPEN"      }    } |
| 200    | 查询预约列表成功                 | {      "code":  "SUCCESS",      "message":  "查询成功",      "data": {        "items": [          {            "id":  "101",            "venueId":  "1",            "venueName":  "第一会议室",            "bookerName":  "软件工程学习小组",            "reservationDate":  "2026-10-25",            "startTime":  "14:00:00",            "endTime":  "16:00:00",            "purpose":  "课程项目讨论",            "status":  "ACTIVE"          }        ],        "total": 1,        "page": 1,        "pageSize": 10      }    } |
| 200    | 取消成功                         | {      "code":  "SUCCESS",      "message":  "预约已取消",      "data": {        "id":  "101",        "venueId":  "1",        "venueName": "第一会议室",        "bookerName":  "软件工程学习小组",        "reservationDate":  "2026-10-25",        "startTime":  "14:00:00",        "endTime":  "16:00:00",        "purpose":  "课程项目讨论",        "status":  "CANCELLED"      }    } |
| 201    | 创建成功                         | {      "code":  "SUCCESS",      "message":  "预约创建成功",      "data": {        "id":  "101",        "venueId":  "1",        "venueName": "第一会议室",        "bookerName":  "软件工程学习小组",        "reservationDate":  "2026-10-25",        "startTime":  "14:00:00",        "endTime":  "16:00:00",        "purpose":  "课程项目讨论",        "status":  "ACTIVE"      }    } |
| 400    | 必填缺失、格式错误、时间范围非法 | {      "code":  "VALIDATION_ERROR",      "message":  "请求参数校验失败",      "data": {        "fieldErrors": [          {            "field":  "bookerName",            "message":  "预约人或组织名称不能为空"          },          {            "field":  "reservationDate",            "message":  "预约日期格式必须为YYYY-MM-DD"          },          {            "field":  "endTime",            "message":  "结束时间必须晚于开始时间"          }        ]      }    } |
| 400    | 场地已经停用                     | {      "code":  "VENUE_CLOSED",      "message":  "该场地已停用，无法创建预约",      "data": null    } |
| 400    | 不在开放时间内                   | {      "code":  "OUT_OF_OPENING_HOURS",      "message":  "预约时间必须在场地开放时间内（如08:00至22:00，按场地实际开放时间生成）",      "data": null    } |
| 404    | 场地不存在                       | {      "code":  "VENUE_NOT_FOUND",      "message":  "指定的场地不存在",      "data": null    } |
| 404    | 预约不存在                       | {      "code":  "RESERVATION_NOT_FOUND",      "message":  "指定的预约不存在",      "data": null    } |
| 409    | 预约时间冲突                     | {      "code":  "RESERVATION_CONFLICT",      "message":  "该场地在所选时间段已有有效预约，请选择其他时间",      "data": null    } |
| 409    | 重复取消预约                     | {      "code":  "ALREADY_CANCELLED",      "message":  "该预约已取消，请勿重复操作",      "data": null    } |
| 500    | 未处理的服务端错误               | {      "code":  "INTERNAL_ERROR",      "message":  "服务器内部错误，请稍后重试",      "data": null    } |

## 补充约定

以下内容 v1 中已有，v2 未重复说明。响应统一使用上面的 `{code, message, data}` 包装。

| 事项 | 约定 |
| ---- | ---- |
| 接口列表 | `GET /api/venues`、`GET /api/venues/{id}`、`POST /api/venues`、`POST /api/reservations`、`GET /api/reservations`、`GET /api/reservations/{id}`、`DELETE /api/reservations/{id}`（逻辑取消） |
| 预约列表参数 | 可选 `venueId`、`reservationDate`、`status`、`page`、`pageSize`；按 `reservationDate`、`startTime` 升序 |
| 校验顺序 | 先格式（400 `VALIDATION_ERROR`），再场地存在（404 `VENUE_NOT_FOUND`），再场地启用（400 `VENUE_CLOSED`），再开放时间（400 `OUT_OF_OPENING_HOURS`），最后冲突（409 `RESERVATION_CONFLICT`） |
| 时间粒度不合法 | 不是 30 分钟整数倍时返回 400 `VALIDATION_ERROR` |
| 场地名称重复 | 409 `VENUE_NAME_DUPLICATE` |
| 场地状态与错误码 | 状态值为 `DISABLED`，对应的错误码仍为 `VENUE_CLOSED` |
| 并发安全 | 创建预约在事务内先 `SELECT ... FOR UPDATE` 锁住场地行，再做冲突检查并插入 |

