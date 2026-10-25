# 前端（Vue 3 + Vite + TypeScript）

场地预约系统的前端。接口约定见仓库根目录 [`docs/contract.md`](../docs/contract.md)，整体运行方式见根目录 [`README.md`](../README.md)。

## 本地开发

需要 Node `^22.18 || >=24.12`。

```sh
npm install
npm run dev
```

开发服务器监听 `http://localhost:5173`，把 `/api` 代理到后端。后端地址默认是 `http://localhost:8080`，需要改时复制 `.env.example` 为 `.env.local` 后修改 `API_PROXY_TARGET`。

## 常用命令

```sh
npm run build        # 类型检查并打包到 dist/
npm run test:unit    # Vitest 单元测试
npm run test:e2e     # Playwright 端到端测试（首次需 npx playwright install）
npm run lint         # ESLint / oxlint
```

## 目录

- `src/api/`：请求封装（`http.ts` 统一处理 `{code,message,data}` 与错误）和各资源接口
- `src/types/`：与契约对应的类型
- `src/views/`、`src/components/`：页面与组件
- `src/router/`：路由（history 模式，nginx 已配置回退到 `index.html`）
