# 修改日志：P0 验收修复

- 日期：2026-10-29
- 范围：仅 P0（让 Docker 部署真正的 Vue 前端）
- 说明：nginx.conf、compose.yaml、后端代码、数据库脚本、contract.md 均未改动

## 1. frontend/Dockerfile —— 多阶段构建（核心修复）

- 改动前：只 `COPY public/` 到 nginx 镜像；8088 展示的是旧静态页（只有场地列表，创建/取消为 TODO）。
- 改动后：`node:24-alpine` 构建阶段（`npm ci` → `npm run build`），`nginx:1.27-alpine` 运行阶段复制 `dist/`。
- 验证：等价本地构建通过（类型检查 + vite build 52 模块，见第 6 节）。

## 2. frontend/public/index.html → docs/legacy/public-index.html（迁出）

- 原因：Vite 构建会把 `public/` 原样复制进 `dist/`，旧 `index.html` 与构建产物同名，可能覆盖 Vue 入口。
- 改动：文件迁至 `docs/legacy/public-index.html` 存档，不参与任何构建；`public/` 现只剩 `favicon.ico`（Vite 正常复制到 dist）。
- 验证：`dist/index.html` 含 `<div id="app">`、不含旧页特征（无 `loadVenues`），见第 6 节。

## 3. README.md —— 5 处过时描述

1. 目录结构：frontend 由「静态页面」改为「Vue 3 + Vite 构建为 dist，nginx 提供静态与反代」。
2. 「复用 `frontend/public/index.html` 的 request()」→ 指向 `frontend/src/api/http.ts`。
3. 「当前页面只能查看，创建和取消需要手动发」→ 页面已支持创建场地、创建预约、查询详情和取消。
4. 「重新部署后端代码 `docker compose up --build -d api`」处补充：重建 api 后若持续 502，执行 `docker compose restart frontend`（nginx 启动时解析并缓存 api 的容器地址）。
5. 常见问题新增：8088 仍是旧页面时的重建命令（`docker compose build --no-cache frontend && docker compose up -d frontend`）。

## 4. frontend/e2e/vue.spec.ts + playwright.config.ts —— 让 e2e 能识别部署错版本

- 改动前：只断言 h1 标题，而新旧页面标题相同，测试无法发现部署错版本。
- 改动后：新增「Vue 应用外壳」断言（`#app` + 4 个导航链接）与「深链直达」断言（`/reservations/new` 直接打开不 404）。
- `playwright.config.ts` 支持 `E2E_BASE_URL` 指向部署环境（如 `http://localhost:8088`），设置后不再自动启动 dev/preview server。
- 用法：`$env:E2E_BASE_URL='http://localhost:8088'; npx playwright test`

## 5. 未改动的文件（有意保留）

- `frontend/nginx.conf`：SPA 回退与 `/api/` 前缀保留本就正确；加固项（缓存、安全头、healthz）属 P1。
- `compose.yaml`：未加 healthcheck（P1）。
- 后端代码、数据库脚本、`docs/contract.md`：未动。

## 6. 验证记录（2026-10-29 执行）

环境：Node v24.22.0、npm 11.19.1；在临时副本中执行，不污染项目目录。

| 检查 | 结果 |
|---|---|
| npm ci | 394 个包，成功 |
| vue-tsc --build（类型检查） | 通过 |
| vite build（v8.3.4） | 52 模块，成功；dist/index.html 0.44 kB |
| vitest | 5 个测试文件、15 个测试全部通过 |
| dist/index.html 含 `id="app"` | ✓ |
| dist/index.html 不含旧页 `loadVenues` | ✓ |
| dist/index.html 引用 `/assets/` 带 hash 资源 | ✓ |

已执行（2026-10-29，验收人执行）：Docker 镜像构建与 8088 冒烟通过（三服务健康、容器内 dist 产物断言通过、深链 200）；验收记录见 accept-logs。

## 7. 后续建议（未执行，按需）

- 从 8088 验收完整业务流程：创建场地 → 创建预约 → 冲突拒绝 → 取消 → 原时段重新预约。
- 用 `E2E_BASE_URL=http://localhost:8088` 跑升级后的 Playwright 用例。
- P1 项（api healthcheck、nginx 加固、契约/并发测试）见验收清单。

## 8. 补充修复：场地容量小数被静默截断（验收测试发现）

- 现象：`POST /api/venues` 传 `capacity: 1.5` 返回 201，容量被截断为 1；契约要求正整数、非整数应 400。
- 根因：Jackson 反序列化默认允许浮点字面量赋给整型字段（`ACCEPT_FLOAT_AS_INT`）。
- 修复：`api/src/main/resources/application.yaml` 增加 `spring.jackson.deserialization.accept-float-as-int: false`。
- 影响：`capacity`（以及 `venueId` 等整型字段）收到小数时改为 400 `VALIDATION_ERROR`，字段定位到具体字段，符合契约。
- 验证：重建 api 镜像后复测 `capacity=1.5`（期望 400）与 `capacity=10`（期望 201）。

## 9. 验收阶段与日志

- 2026-10-29 完成第 1–5 项验收：部署启动、8088 冒烟、端到端业务 8 步、接口契约回归（含容量缺陷发现与修复）、并发安全三组，全部通过。
- 修复回归证据：`venueId: 1.5` → 400（field=venueId）；`capacity=1.5` 直接复测因 api 重启窗口未完成，见验收日志建议补测项。
- 未执行项：第 6 项（持久化/初始化/隔离/凭据/时区）、第 7 项（后端 mvn test）、第 8 项（Playwright）、第 9 项（全量回归）；其中数据持久化为 README 8.3 评分点，建议验收前补跑。
- 完整结果与交付建议见 [docs/acceptance-log.md](docs/acceptance-log.md)。
