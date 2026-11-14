# 以下 API 契约内容为 lab2 新增

内容衔接自 [lab1](contract.md)，lab2 主要新增了报告功能，前端可在页面上触发报告生成，并查看报告状态和结果。

## 9. 报告

场地利用率报告：统计指定日期范围内各场地的预约次数和累计预约时长。报告由 Kubernetes Job（手动触发）或 CronJob（定时触发）生成，结果保存在数据库中，前端通过本节接口查询状态和结果。

沿用第 2 节的统一响应格式 `{code, message, data}` 与第 1 节的通用约定。

### 9.1 业务规则

| 事项     | 约定                                                                                                                    |
|--------|-----------------------------------------------------------------------------------------------------------------------|
| 统计对象   | 预约；**只统计 `ACTIVE`**，已取消（`CANCELLED`）的不计入                                                                              |
| 统计范围   | `rangeStart`、`rangeEnd` 为日期（`YYYY-MM-DD`），**含首尾**；预约的 `reservationDate` 落在范围内即计入（包括未来日期的预约）                           |
| 范围校验   | `rangeStart` ≤ `rangeEnd`，且跨度不超过 366 天；不合法 → 400 `VALIDATION_ERROR`                                                   |
| 统计指标   | `reservationCount`：预约次数；`totalMinutes`：累计预约时长（分钟，整数）。前端展示时可自行换算为小时                                                    |
| 场地覆盖   | **所有场地都出现在结果中**，没有预约的场地两项均为 0（包括状态为 `DISABLED` 的场地）                                                                   |
| 报告状态   | `PENDING`（已创建，Job 尚未开始）、`RUNNING`（执行中）、`SUCCESS`、`FAILED`                                                             |
| 前端状态展示 | `PENDING` 和 `RUNNING` 统一显示为"处理中"；`SUCCESS` 显示"成功"；`FAILED` 显示"失败"并展示 `errorMessage`                                   |
| 触发方式   | `MANUAL`：用户在页面触发；`SCHEDULED`：由 CronJob 定时触发                                                                           |
| 前端轮询   | 触发后每 2 秒请求一次 `GET /api/reports/{id}`，状态变为 `SUCCESS` 或 `FAILED` 时停止轮询                                                  |
| 时间表示   | `createdAt`、`startedAt`、`finishedAt` 为 UTC，ISO-8601 带 `Z`（如 `2026-11-15T08:30:00Z`），由前端转换为 Asia/Shanghai 显示；日期范围不涉及时区 |
| 鉴权     | 与其他接口一致，本实验不做用户认证                                                                                                     |

### 9.2 定时报告（CronJob）约定

| 事项                | 约定                                                                                                                                             |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| 统计窗口              | **最近 7 天（含当天）**，按 Asia/Shanghai：`rangeStart` = 今天 − 6 天，`rangeEnd` = 今天                                                                        |
| 记录创建              | 定时任务由程序自己插入记录：`triggerType = SCHEDULED`，`jobName` 为运行该任务的 Job 名                                                                                |
| 防重复               | 记录带 `schedule_key`（见下），库中该列唯一；同一计划时刻重复执行时，后到的一方撞唯一键，不会写出第二份                                                                                    |
| `schedule_key` 格式 | `SCHEDULED:` 加上启动时刻（Asia/Shanghai）按调度周期向下取整后的时间戳。生产环境每日执行取到天（`SCHEDULED:20261115`），演示用的分钟级周期取到分钟（`SCHEDULED:202611150830`）；格式由 CronJob 的环境变量指定 |
| 并发策略              | CronJob 使用 `concurrencyPolicy: Forbid`，上一次未结束时跳过本次                                                                                             |
| 生产建议周期            | 每日一次（例如每天 02:00，`timeZone: Asia/Shanghai`）                                                                                                     |

### 9.3 字段定义

#### Report（报告运行记录）

| 字段           | 类型             | 说明                                           |
|--------------|----------------|----------------------------------------------|
| id           | string         | 后端生成的运行 ID                                   |
| triggerType  | string         | `MANUAL` / `SCHEDULED`                       |
| rangeStart   | string         | 统计起始日期，`YYYY-MM-DD`                          |
| rangeEnd     | string         | 统计结束日期，`YYYY-MM-DD`                          |
| status       | string         | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| jobName      | string \| null | 对应的 Kubernetes Job 名；Job 尚未创建时为 `null`       |
| attempt      | number         | 已执行次数（含重试）                                   |
| errorMessage | string \| null | 失败原因；非失败时为 `null`                            |
| createdAt    | string         | 创建时间（UTC）                                    |
| startedAt    | string \| null | 开始执行时间（UTC）                                  |
| finishedAt   | string \| null | 结束时间（UTC）                                    |
| results      | array          | 仅在报告详情中返回；每个元素见下表。状态不是 `SUCCESS` 时为空数组       |

#### results 元素

| 字段               | 类型     | 说明                           |
|------------------|--------|------------------------------|
| venueId          | string | 场地 ID                        |
| venueName        | string | 生成报告时的场地名称（快照，后续场地改名不影响历史报告） |
| reservationCount | number | 预约次数                         |
| totalMinutes     | number | 累计预约时长（分钟）                   |

### 9.4 接口

| 方法   | 路径                  | 说明        | 成功状态 |
|------|---------------------|-----------|------|
| POST | `/api/reports`      | 创建并触发一次报告 | 202  |
| GET  | `/api/reports`      | 报告列表（分页）  | 200  |
| GET  | `/api/reports/{id}` | 报告详情（含结果） | 200  |

#### POST /api/reports

请求体：

```json
{
  "rangeStart": "2026-11-01",
  "rangeEnd": "2026-11-14"
}
```

后端处理：校验参数 → 插入一条 `PENDING` 记录 → 创建 Kubernetes Job（只把运行 ID 作为环境变量传给 Job）→ 返回。

成功 202（任务已受理，尚未完成），`data` 为刚创建的 Report（不含 `results`）：

```json
{
  "code": "SUCCESS",
  "message": "报告任务已创建",
  "data": {
    "id": "12",
    "triggerType": "MANUAL",
    "rangeStart": "2026-11-01",
    "rangeEnd": "2026-11-14",
    "status": "PENDING",
    "jobName": "report-12",
    "attempt": 0,
    "errorMessage": null,
    "createdAt": "2026-11-15T08:30:00Z",
    "startedAt": null,
    "finishedAt": null
  }
}
```

失败：

- 参数不合法 → 400 `VALIDATION_ERROR`（`data.fieldErrors` 指出具体字段）
- 记录已写入但创建 Job 失败 → 该记录被改为 `FAILED` 并写入 `errorMessage`，接口返回 503 `REPORT_JOB_CREATE_FAILED`，`data` 为 `null`（这条失败记录仍可在列表中查到）

#### GET /api/reports

查询参数（均可选）：

| 参数          | 默认 | 说明          |
|-------------|----|-------------|
| page        | 1  | 页码，从 1 开始   |
| pageSize    | 10 | 每页条数，最大 100 |
| status      | -  | 按状态筛选       |
| triggerType | -  | 按触发方式筛选     |

按 `createdAt` 倒序。列表项不含 `results`。成功 200：

```json
{
  "code": "SUCCESS",
  "message": "查询成功",
  "data": {
    "items": [
      {
        "id": "12",
        "triggerType": "MANUAL",
        "rangeStart": "2026-11-01",
        "rangeEnd": "2026-11-14",
        "status": "SUCCESS",
        "jobName": "report-12",
        "attempt": 1,
        "errorMessage": null,
        "createdAt": "2026-11-15T08:30:00Z",
        "startedAt": "2026-11-15T08:30:04Z",
        "finishedAt": "2026-11-15T08:30:06Z"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 10
  }
}
```

#### GET /api/reports/{id}

成功 200，`data` 为完整 Report，`SUCCESS` 时包含 `results`：

```json
{
  "code": "SUCCESS",
  "message": "查询成功",
  "data": {
    "id": "12",
    "triggerType": "MANUAL",
    "rangeStart": "2026-11-01",
    "rangeEnd": "2026-11-14",
    "status": "SUCCESS",
    "jobName": "report-12",
    "attempt": 1,
    "errorMessage": null,
    "createdAt": "2026-11-15T08:30:00Z",
    "startedAt": "2026-11-15T08:30:04Z",
    "finishedAt": "2026-11-15T08:30:06Z",
    "results": [
      {
        "venueId": "1",
        "venueName": "第一会议室",
        "reservationCount": 6,
        "totalMinutes": 540
      },
      {
        "venueId": "5",
        "venueName": "旧礼堂",
        "reservationCount": 0,
        "totalMinutes": 0
      }
    ]
  }
}
```

不存在 → 404 `REPORT_NOT_FOUND`。

### 9.5 新增错误码

| HTTP | code                     | 场景                                  |
|------|--------------------------|-------------------------------------|
| 404  | REPORT_NOT_FOUND         | 报告运行记录不存在                           |
| 503  | REPORT_JOB_CREATE_FAILED | 无法创建 Kubernetes Job（记录已置为 `FAILED`） |

### 9.6 后端与数据库实现约定

- 表：`report_runs`（运行记录）、`report_results`（各场地统计结果），建表脚本为 `db/init/03-reports.sql`，由数据库同学提交，**表结构以该脚本为准**
- 报告程序与 API 使用同一个镜像，用不同启动参数区分；程序运行结束后退出，不启动 Web 服务
- **手动触发**：Job 只接收环境变量 `REPORT_RUN_ID`，启动后读取该记录的统计范围，状态依次为 `RUNNING` → `SUCCESS` / `FAILED`
- **定时触发**：没有 `REPORT_RUN_ID`，程序自己计算窗口和 `schedule_key` 并插入记录
- **重试不新增记录**：Job 失败重试时更新同一条记录（`attempt` 加 1），结果行先清除再写入
- **结果与状态同一事务提交**：写完全部结果行与把状态改为 `SUCCESS` 在同一个事务里，避免"状态成功但结果不完整"
- 失败时程序先把 `FAILED` 和 `errorMessage` 写入记录，再以非 0 退出码结束，由 Kubernetes 按 `backoffLimit` 决定是否重试
- API 访问 Kubernetes 的权限：只授予在本命名空间创建和查询 Job 的最小权限（ServiceAccount + Role + RoleBinding）

