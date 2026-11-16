# 场地预约系统（云原生软件技术 Lab 1 / Lab 2）

前端 + 后端 API + 关系数据库的场地预约系统。

- **Lab 1**：用 Docker Compose 一键运行。
- **Lab 2**：迁移到 Kubernetes，API 多副本运行，并新增由 Job / CronJob 生成的"场地利用率报告"。

两种运行方式都保留，互不影响。

## 当前进度（Lab 2）

更新于 2026-11-16。实验报告截止 2026-11-23。

| 部分 | 内容 | 状态 |
|---|---|---|
| 数据库 | StatefulSet、Headless + ClusterIP Service、PVC、初始化脚本、ConfigMap / Secret | ✅ 已合并 |
| 后端 | 健康检查、实例标识、报告接口、报告生成、由 API 创建 Job、CronJob | ✅ 已合并 |
| 后端清单 | API Deployment（2 副本）、Service、RBAC、CronJob | ✅ 已合并 |
| 前端 | 报告列表与详情页、前端 Deployment / Service、Nginx 配置外部化 | ✅ 已合并 |
| 节点检查 | `node-check` DaemonSet | ✅ 已合并 |
| 验证：并发预约、手动 Job、CronJob | [docs/evidence](docs/evidence/README.md) 第 02–04 项 | ✅ 通过 |
| 验证：API 多副本与自愈、数据库持久化 | [docs/evidence](docs/evidence/README.md) 第 01、05 项 | ⬜ 待执行 |
| 验证：前端页面与 DaemonSet | [docs/lab2-frontend-evidence.md](docs/lab2-frontend-evidence.md) | ⬜ 待验收 |
| 实验报告 | — | ⬜ 未开始 |

整套系统已在 3 节点 minikube 上完整部署并联通过一次（数据库、API、前端、DaemonSet、CronJob）。

## 架构

### Kubernetes（Lab 2）

所有资源都在命名空间 `venue-booking`。

```
浏览器 ──(kubectl port-forward 8088)──▶ Service frontend:80
                                          │
                                 Deployment frontend（1 副本，Nginx）
                                          │  /api/ 反向代理
                                          ▼
                                    Service api:8080
                                          │
                          Deployment api（2 副本，Spring Boot）
                           │                         │
                           │ database:3306           │ 调用 Kubernetes API 创建 Job
                           ▼                         ▼
              StatefulSet database-0（MySQL）   Job report-<id>（手动触发的报告）
                    │                                 │
              PVC data-database-0                     │ 同一个镜像，以 report 模式运行，
                    ▲                                 │ 跑完一次就退出
                    └─────────────────────────────────┘
                    ▲
        CronJob report-cron ──▶ Job report-cron-<时间戳>（定时报告）

DaemonSet node-check：每个节点一个 Pod，定期检查 frontend Service 是否可达
```

- 前端、API、数据库都只有 ClusterIP Service，不对集群外暴露。
- API 用 ServiceAccount `api`，只被授予"在本命名空间创建 Job"这一项权限。
- 数据库密码只存在于 Secret `database-credentials`，不在镜像、ConfigMap 和仓库里。

### Docker Compose（Lab 1）

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
├── compose.yaml              # Lab 1：Docker Compose 部署
├── compose.dev.yaml          # 本地开发：只给数据库加宿主机端口 3307
├── .env.example              # 复制为 .env 使用（.env 不提交）
├── api/                      # 后端（Spring Boot + Dockerfile）
├── frontend/                 # 前端（Vue 3 + Vite，nginx 提供静态文件与反向代理）
├── db/init/                  # 建表与种子数据（数据卷首次初始化时执行）
├── k8s/
│   ├── database/             # 命名空间、MySQL StatefulSet、Service、ConfigMap、Secret 示例
│   ├── rbac/                 # API 创建 Job 所需的 ServiceAccount、Role、RoleBinding
│   ├── api/                  # api-config、API Deployment、Service
│   ├── frontend/             # 前端 Deployment、Service（Nginx 配置生成为 ConfigMap）
│   ├── node-check/           # 节点检查 DaemonSet
│   └── jobs/
│       ├── report-cron/      # 定时报告 CronJob（每天 02:00）
│       └── report-cron-demo/ # 演示用：每分钟一次
└── docs/
    ├── contract.md               # Lab 1 接口契约
    ├── contract-lab2.md          # Lab 2 新增：报告接口与 Job / CronJob 约定
    ├── evidence/                 # Lab 2 验证记录与原始证据
    ├── lab2-frontend-guide.md    # 前端实施与部署说明
    ├── lab2-frontend-evidence.md # 前端验收清单
    └── acceptance-log.md         # Lab 1 验收日志
```

## 快速开始：Kubernetes（Lab 2）

下面的命令在**仓库根目录**、bash 环境里执行（Windows 用 WSL 或 Git Bash）。以 minikube 为例，其他集群只有"载入镜像"一步不同。

### 0. 准备

- Docker、kubectl，以及一个可用的集群。想看到 DaemonSet 在多个节点上的效果，建议多节点，例如：

  ```bash
  minikube start --nodes 3
  ```

- 准备 `.env`（数据库密码从这里读取，不要提交）：

  ```bash
  cp .env.example .env
  ```

  `.env` 里的 `MYSQL_DATABASE`、`MYSQL_USER` 保持示例值（`venue_booking`、`venue_user`），清单里的 ConfigMap 用的是这两个值。

### 1. 构建镜像并载入集群

```bash
docker build -t venue-api:lab2 ./api
```

```bash
docker build -t venue-frontend:lab2 ./frontend
```

```bash
minikube image load venue-api:lab2
```

```bash
minikube image load venue-frontend:lab2
```

- minikube 用了自定义 profile 时，每条 `minikube` 命令都要加 `-p <profile 名>`。
- kind 用 `kind load docker-image <镜像> --name <集群名>`。
- `node-check` 用的是 `busybox:1.37.0`。节点拉不到 Docker Hub 时，先在本机 `docker pull busybox:1.37.0`，再同样 `minikube image load`。

### 2. 命名空间与数据库密码

```bash
kubectl apply -f k8s/database/namespace.yaml
```

从 `.env` 创建 Secret（只取两个密码，值不会出现在命令行历史里）：

```bash
kubectl create secret generic database-credentials -n venue-booking \
  --from-literal=MYSQL_ROOT_PASSWORD="$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2- | tr -d '\r')" \
  --from-literal=MYSQL_PASSWORD="$(grep '^MYSQL_PASSWORD=' .env | cut -d= -f2- | tr -d '\r')"
```

PowerShell 的写法见 [k8s/database/secret.example.yaml](k8s/database/secret.example.yaml) 文件头。

### 3. 按顺序部署

顺序不能换：API 依赖数据库、Secret 和 ServiceAccount；前端的 Nginx 启动时要解析 `api` 这个 Service 名，所以必须在 API 之后。

数据库（初始化 SQL 在目录之外，不能直接 `apply -k`）：

```bash
kubectl kustomize k8s/database --load-restrictor LoadRestrictionsNone | kubectl apply -f -
```

```bash
kubectl -n venue-booking rollout status statefulset/database --timeout=300s
```

API 的权限和 API 本身：

```bash
kubectl apply -k k8s/rbac
```

```bash
kubectl apply -k k8s/api
```

```bash
kubectl -n venue-booking rollout status deployment/api --timeout=240s
```

前端（同样要用 `--load-restrictor`）：

```bash
kubectl kustomize k8s/frontend --load-restrictor LoadRestrictionsNone | kubectl apply -f -
```

```bash
kubectl -n venue-booking rollout status deployment/frontend --timeout=120s
```

节点检查和定时报告：

```bash
kubectl apply -k k8s/node-check
```

```bash
kubectl apply -k k8s/jobs/report-cron
```

### 4. 确认并访问

```bash
kubectl -n venue-booking get pods -o wide
```

应当看到：`database-0`、两个 `api-…`、一个 `frontend-…`、每个节点一个 `node-check-…`，全部 `1/1 Running`。

```bash
kubectl -n venue-booking port-forward service/frontend 8088:80
```

保持这个命令运行，浏览器打开 http://localhost:8088 。报告页在 http://localhost:8088/reports 。

### 5. 常用操作

| 想做什么 | 怎么做 |
|---|---|
| 手动生成一份报告 | 在报告页选日期范围后点生成；几秒后状态变为成功 |
| 看报告 Job | `kubectl -n venue-booking get jobs`，日志用 `kubectl -n venue-booking logs job/report-<id>` |
| 看定时报告（不想等到 02:00） | `kubectl apply -k k8s/jobs/report-cron-demo` 换成每分钟一次；看完用 `kubectl apply -k k8s/jobs/report-cron` 换回来 |
| 制造一次可控的失败 | `kubectl -n venue-booking delete rolebinding api-report-job-creator`，再生成报告，会得到 503 和一条失败记录。**验证完必须恢复**：`kubectl apply -k k8s/rbac` |
| 确认两个 API 副本都在处理请求 | 多刷新几次页面，看 `/api/...` 响应头里的 `X-Instance-Id` |
| 看节点检查结果 | `kubectl -n venue-booking logs -l app=node-check --prefix=true --tail=5` |
| 进数据库查询 | `kubectl -n venue-booking exec -it database-0 -- sh -c 'mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'` |
| 改了后端代码后更新 | 重新 `docker build`、`minikube image load venue-api:lab2`，再 `kubectl -n venue-booking rollout restart deployment/api` |
| 全部清掉重来 | `kubectl delete namespace venue-booking`（数据库数据也会删除） |

更细的验证步骤和已有的结果见 [docs/evidence/README.md](docs/evidence/README.md)；前端部分见 [docs/lab2-frontend-guide.md](docs/lab2-frontend-guide.md)。

### 6. Kubernetes 下的常见问题

- **Pod 是 `ErrImageNeverPull` / `ImagePullBackOff`**：镜像没有载入集群。检查 `minikube image ls` 里有没有 `venue-api:lab2`、`venue-frontend:lab2`；多 profile 时确认 `-p` 指向了正确的集群。
- **`database-0` 一直是 `CreateContainerConfigError`**：Secret `database-credentials` 还没创建，按第 2 步创建后会自动恢复。
- **前端 `CrashLoopBackOff`**：通常是 `api` Service 还不存在，Nginx 解析不了上游。先部署 `k8s/api`，再重启前端。
- **API 一直不就绪**：就绪探针包含数据库检查。先看 `database-0` 是否就绪，再看 `kubectl -n venue-booking logs deployment/api`。
- **生成报告总是失败，原因是"没有创建 Job 的权限（403）"**：RoleBinding 不在。执行 `kubectl apply -k k8s/rbac`。
- **生成报告失败，原因是"同名 Job 已存在（409）"**：重建过数据库后，报告 id 从 1 重新开始，而之前的 `report-<id>` Job 还没被自动清理。执行 `kubectl -n venue-booking delete jobs -l app=report-job`。
- **改了 `db/init/*.sql` 不生效**：初始化脚本只在数据卷为空时执行。需要 `kubectl delete namespace venue-booking` 后重新部署，或者删掉 PVC `data-database-0` 再重建数据库。
- **容器里用 `mysql` 查询，中文显示成 `?`**：加 `--default-character-set=utf8mb4`。
- **看失败的详细原因**：页面和接口里的 `errorMessage` 只给出一句分类后的原因；完整信息在 API 日志里，用 `kubectl -n venue-booking logs -l app=api --prefix=true --tail=500 | grep -A5 "创建报告 Job 失败"`（`-l app=api` 会同时看两个副本）。

## 快速开始：Docker Compose（Lab 1）

```bash
cp .env.example .env      # 按需修改密码
docker compose up --build -d
docker compose ps         # 三个服务应为 running，database 为 healthy
```

浏览器打开 http://localhost:8088

Compose 环境里没有 Kubernetes，报告接口仍然可用，但创建的报告会一直停在"处理中"：此时 API 用的是不创建 Job 的空实现（`REPORT_LAUNCHER` 默认为 `noop`）。

## 接口列表

Lab 1 的接口详见 [docs/contract.md](docs/contract.md)，报告接口详见 [docs/contract-lab2.md](docs/contract-lab2.md) 第 9 节。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/venues | 场地列表（分页） |
| GET | /api/venues/{id} | 场地详情 |
| POST | /api/venues | 创建场地 |
| POST | /api/reservations | 创建预约 |
| GET | /api/reservations | 预约列表（分页、可筛选） |
| GET | /api/reservations/{id} | 预约详情 |
| POST | /api/reservations/{id}/cancel | 取消预约（逻辑取消） |
| POST | /api/reports | 创建一次报告任务，返回 202（Lab 2） |
| GET | /api/reports | 报告列表（分页，可按状态和触发方式筛选）（Lab 2） |
| GET | /api/reports/{id} | 报告详情，成功后带各场地的统计结果（Lab 2） |
| GET | /api/actuator/health/liveness、/readiness | 健康检查，供探针使用（Lab 2） |

所有 `/api/` 响应都带 `X-Instance-Id` 响应头，值是处理该请求的 Pod 名。

### 后端的环境变量

| 变量 | 说明 |
|---|---|
| `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD` | 数据库连接。Kubernetes 下前四项来自 ConfigMap `api-config`，密码来自 Secret |
| `POD_NAME` | 实例标识，由 Downward API 注入 |
| `REPORT_LAUNCHER` | `noop`（默认，不创建 Job）或 `k8s`（调用 Kubernetes API 创建 Job） |
| `REPORT_JOB_IMAGE`、`REPORT_JOB_NAMESPACE`、`REPORT_JOB_CONFIG_MAP`、`REPORT_JOB_SECRET`、`REPORT_JOB_SECRET_PASSWORD_KEY` | 创建报告 Job 时使用的镜像和资源名 |
| `SPRING_PROFILES_ACTIVE=report` | 以报告模式运行：不启动 Web，跑完一次就退出 |
| `REPORT_RUN_ID` | 报告模式下要执行的记录 id；不设置时按定时任务处理 |
| `REPORT_JOB_NAME`、`REPORT_SCHEDULE_DEMO` | 定时任务用：写进记录的 Job 名；是否按分钟生成防重复的 key |

> 以下"前端对接指南""联调指南""数据持久化验证""常见问题"四节写于 Lab 1，针对的是 Docker Compose 环境。其中的接口约定和格式在 Kubernetes 下同样适用。

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
Vue 应用中的请求封装在 `frontend/src/api/http.ts`：`request()` 成功返回 `data`，失败抛出带 `message` 的 `ApiError`；`getFieldErrors()` / `parseApiError()` 可把校验失败信息映射到表单字段。

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
| 1 | 场地列表 | 200，`data.total` 为 5，`items` 含 5 个场地（默认每页 10 条），中文正常 |
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

页面已支持创建场地、创建预约、查询详情和取消。也可以在浏览器打开 http://localhost:8088，按 F12 → Console 里用相对路径直接发请求：

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
- 重新部署后端代码：`docker compose up --build -d api`。重建 api 后若前端持续 502，执行 `docker compose restart frontend`（nginx 启动时解析并缓存 api 的容器地址）。

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

## 常见问题（Docker Compose）

- **打开 8088 仍是旧页面（只有场地列表，没有创建预约等导航）**：前端镜像还是旧静态页。执行 `docker compose build --no-cache frontend && docker compose up -d frontend` 后强制刷新浏览器（Ctrl+F5）。
- **改了 `db/init/*.sql` 不生效**：初始化脚本只在 Volume 首次创建时执行。需要重来：`docker compose down -v` 后再 `up`
- **刚启动时访问返回 502**：后端比 nginx 慢几秒才就绪，等 `docker compose logs api` 出现 `Started ApiApplication` 再试
- **api 启动报连不上数据库**：先看 `docker compose ps` 中 database 是否 healthy；再检查 `.env` 中账号密码与 `docker compose logs api`
- **中文乱码**：脚本顶部已有 `SET NAMES utf8mb4`；在 Windows 上建议用 Git Bash 执行含中文的 SQL 导入导出，避免 PowerShell 的 UTF-16 重定向

## 小组分工

| 成员 | Lab 1 | Lab 2 |
|---|---|---|
| 鍾錫豪 | 后端 API（实体、校验、冲突逻辑、错误处理）+ `api/Dockerfile` | 后端：健康检查与实例标识、报告接口与生成逻辑、由 API 创建 Job、CronJob；API Deployment / Service、RBAC 清单；并发、Job、CronJob 的验证 |
| 费天睿 | 前端页面 + `frontend/Dockerfile` + `nginx.conf` | 前端：报告列表与详情页；前端 Deployment / Service、Nginx 配置外部化；`node-check` DaemonSet |
| 關喬賢 | 数据库脚本、`compose.yaml`、`.env.example`、联调验收、实验报告整理 | 数据库与集群：MySQL StatefulSet、Service、PVC、初始化、ConfigMap / Secret；多副本与持久化的验证 |

## 设计说明

- **取消预约**：逻辑取消（`status = CANCELLED`），保留历史；冲突检查只比对 `ACTIVE`
- **配置外部化**：连接信息和资源名都通过环境变量注入。Compose 下来自 `.env`；Kubernetes 下非敏感项来自 ConfigMap，密码来自 Secret。镜像和仓库中都没有密码
- **并发冲突**：创建预约在事务内锁定场地行后再检查冲突，所以多个 API 副本同时处理重叠预约时也只有一个成功
- **一个镜像，两种运行方式**：API 和报告任务用同一个镜像。默认启动为常驻的 Web 服务；加上 `SPRING_PROFILES_ACTIVE=report` 则不启动 Web，执行一次报告后以退出码 0 或 1 结束，Kubernetes 据此判断 Job 成败
- **创建 Job 的方式可替换**：服务层只依赖一个接口，由 `REPORT_LAUNCHER` 决定用空实现还是真正调用 Kubernetes 的实现，本地开发和测试不需要集群
- **报告的重试与防重复**：重试更新同一条记录，结果先清除再写入，并与"置为成功"在同一个事务里；已成功的记录不会重算。定时报告靠数据库唯一键防止同一计划时刻生成两份，CronJob 另设 `concurrencyPolicy: Forbid`
- **最小权限**：API 只能在自己的命名空间里创建 Job，不能查看或删除 Job，也不能读 Secret；报告任务的 Pod 不挂载 ServiceAccount 令牌
- **探针分工**：就绪探针包含数据库，连不上数据库的副本不接收流量；存活探针不包含，数据库故障不会导致 API 被反复重启
- **失败可追踪**：报告记录里只保存一句分类后的失败原因（会显示给用户），完整的异常信息写在 API 日志里
