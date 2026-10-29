# 验收日志：场地预约系统（云原生软件技术 Lab 1）

- 验收日期：2026-10-29
- 项目：[ZhongXiHao/venue-booking](https://github.com/ZhongXiHao/venue-booking)
- 验收版本：基于 `main`（03978c4）的 P0 修复版，变更明细见 `CHANGELOG.md`
- 执行方式：验收人操作 + AI 助手协同；所有接口测试经 8088 入口（nginx → api → MySQL）发起
- 环境：Windows + Docker Desktop（已配 registry-mirrors 加速）+ MySQL 8.0 + Node 24；api 构建与运行均在容器内（Java 21）

## 一、结论先行

系统主链路验收**通过**：部署、页面冒烟、端到端业务、接口契约、并发安全、缺陷修复回归均达到预期。另有 4 个补充项未执行（见 §7）。**数据持久化（README「8.3」评分点）已补测通过**，见 §7。

## 二、部署与启动（第 1 项）✅

- `docker compose build --no-cache frontend`：多阶段构建成功（npm ci 394 包；vue-tsc 类型检查 + vite build 52 模块；dist 产物 0.44 kB）。
- `docker compose up --build -d`：三服务 Up，database `(healthy)`；api 日志 `Started ApiApplication in 2.514 seconds`。
- 容器内产物断言：`/usr/share/nginx/html/index.html` 为 Vue 入口（`<title>场地预约系统</title>` + 带 hash 的 `/assets/index-*.js`），与本地独立验证产物 hash 一致。
- 说明：api 镜像首次构建时 Maven 直连下载依赖耗时较长（未配置 Maven 镜像），最终成功；Docker 镜像拉取通过 registry-mirrors 完成。

## 三、8088 部署冒烟（第 2 项）✅

- 首页为 Vue 应用（`id="app"`、hash 资源引用）。
- 深链直达 `/venues/1`、`/reservations/1`、`/reservations/new` 均 200（SPA 回退生效）。
- `/api/venues` 经 nginx 反代返回正常 JSON（`/api` 前缀保留正确）；静态资源 200。
- 冷启动实测：本次 0 次 502（api 启动快于首次请求；502 窗口说明保留在 README）。
- 重建 api 实测：`docker compose up --build -d api` 后前端出现持续 502（nginx 缓存旧容器地址）→ `docker compose restart frontend` 恢复 200。该处理方式已写入 README 常见问题。

## 四、端到端业务流程（第 3 项）✅

浏览器 8088 完整 8 步通过：场地列表（5 种子）→ 场地详情 → 创建场地 → 未来日期/半小时粒度创建预约 → 预约列表与详情 → 按场地/日期/状态筛选 → 二次确认取消 → 原时段重新预约成功。深链刷新无 404。全程截图留档。

## 五、接口契约回归（第 4 项）✅（含 1 处缺陷的发现与修复）

已验证用例（响应与期望一致）：

| 类别 | 已验证场景 |
|---|---|
| 基础 | 场地列表分页（total/page/pageSize）；404 `VENUE_NOT_FOUND` / `RESERVATION_NOT_FOUND` |
| 创建预约 | 201；同段冲突 409 `RESERVATION_CONFLICT`；相邻时段 201；非半小时粒度 400（field=startTime）；停用场地 400 `VENUE_CLOSED`；开放时间外 400 `OUT_OF_OPENING_HOURS` |
| 校验 | 空名称（field=name）、容量 0（field=capacity）、昨天时段已开始（field=reservationDate）、不存在日期 2030-02-30、时间缺秒（field=startTime）、page=0、pageSize=101、超范围页码返回空列表 |
| 协议 | 未知路径 404 `NOT_FOUND`；DELETE 405 `METHOD_NOT_ALLOWED`；非 JSON Content-Type 415 `UNSUPPORTED_MEDIA_TYPE` |
| 业务 | 场地重名 409 `VENUE_NAME_DUPLICATE`；省略 status 默认 OPEN；错误响应不含异常堆栈 |
| 其他 | README 12 条验收清单（Git Bash 执行，结论通过） |

**发现并修复的缺陷**：`POST /api/venues` 传 `capacity: 1.5` 返回 201 且容量被截断为 1，违反契约（正整数）。修复：`application.yaml` 增加 `spring.jackson.deserialization.accept-float-as-int: false`。修复后实测 `venueId: 1.5` → 400（field=venueId），同类字段截断问题消除；`capacity: 1.5` 已在 `acceptance/p0-fixes`（413fb39）重建镜像后经 8088 入口复测通过：`capacity: 1.5` 与 `capacity: 20.0` 均 → 400（field=capacity，message=字段格式错误），`capacity: 10` → 201。

## 六、并发安全（第 5 项）✅

- 同场地同时段并发创建 ×2：一个 201、一个 409，库中最终 1 条 ACTIVE。
- 相邻时段并发创建 ×2：双双 201，最终 2 条。
- 同一预约并发取消 ×2：一个 200、一个 409 `ALREADY_CANCELLED`，最终单条 CANCELLED。

## 七、未执行项与建议（按优先级）

| 项 | 内容 | 建议 |
|---|---|---|
| 6.1 数据持久化（README 8.3 评分点） | `docker compose down`（不带 -v）→ `up -d` → 原预约仍在 | ✅ 已完成：在 `acceptance/p0-fixes`（413fb39）上创建预约后 `docker compose down`（不带 -v）→ `up -d`，重启后 `GET /api/reservations/3` 返回 200（ACTIVE），2030-01-15 当天仍为 3 条（含 1 条已取消） |
| capacity=1.5 直接复测 | `PostJ "$BASE/venues" "{""name"":""capfix-e"",""location"":""t"",""capacity"":1.5,""openTime"":""08:00:00"",""closeTime"":""22:00:00""}"` | ✅ 已完成：400（field=capacity），见 §5 |
| 6.2–6.6 初始化/隔离/凭据/时区 | 全新卷初始化、端口网络隔离、凭据 grep、UTC 审计 | 时间允许则补 |
| 7 后端 mvn test | Java 21 + 临时 3307 端口跑 `mvnw test` | 可选（HTTP 层已回归） |
| 8 Playwright | `E2E_BASE_URL=http://localhost:8088 npx playwright test` | 可选（升级后的用例已写好） |
| 9 全量回归 | 清库后重跑 2–5 项 | 可选 |

## 八、已知事项（如实记录）

- nginx 启动时解析并缓存 api 容器地址：重建 api 后需 `docker compose restart frontend`（已写入 README）。
- api 冷启动就绪前有短暂 502 窗口（README 已说明，等待日志出现 `Started ApiApplication`）。
- `npm ci` 报告 2 个 moderate 漏洞（依赖树，仅记录，不阻塞验收）。
- 镜像构建依赖外网（Docker registry-mirrors 已配置；Maven 直连较慢但可用）。
- 验收期间产生的测试场地/预约均为临时数据。

## 九、演示/交付前建议

1. `docker compose down -v && docker compose up -d` 重置到种子数据（5 个场地、无预约）。
2. 按第 3 项 8 步过一遍演示脚本（截图已备）。
3. 数据持久化与 capacity 复测均已补测完成（见 §7、§5）。
4. 备好本日志与 `CHANGELOG.md` 作为验收材料附件。
