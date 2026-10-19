# 场地预约系统（云原生软件技术 Lab 1）

前端 + 后端 API + 关系数据库，通过 Docker Compose 一键运行。

## 架构

```
浏览器 → frontend（nginx，:8080 对外）
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
└── docs/api-contract.md  # API 契约
```

## 快速开始

```bash
cp .env.example .env      # 按需修改密码
docker compose up --build -d
docker compose ps         # 三个服务应为 running，database 为 healthy
```

浏览器打开 http://localhost:8080

## 接口列表

详见 [docs/api-contract.md](docs/api-contract.md)。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/venues | 场地列表 |
| GET | /api/venues/{id} | 场地详情 |
| POST | /api/venues | 创建场地 |
| POST | /api/reservations | 创建预约 |
| GET | /api/reservations | 预约列表 |
| GET | /api/reservations/{id} | 预约详情 |
| DELETE | /api/reservations/{id} | 取消预约（逻辑取消） |

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
