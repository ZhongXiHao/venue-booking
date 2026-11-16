# Lab2 前端：实施、部署与验收

本次实现基于 main 的 `ea66616`，本地分支 `feat/lab2-frontend`；不包含向 GitHub 推送。

## 0. 当前完成范围与依赖

| 项目 | 实现位置 | 验收状态 |
| --- | --- | --- |
| Lab1 功能保留 | 原场地及预约页面 | 原单元测试通过；真实集群全流程待验收 |
| 报告列表、生成与详情 | `frontend/src/views/Report*View.vue` | 构建、类型检查及报告单元测试通过 |
| 容器与代理 | 现有 `frontend/Dockerfile`、更新的 `frontend/nginx.conf` | Vue 生产构建通过；Docker 镜像实构建待执行 |
| Deployment、Service、ConfigMap | `k8s/frontend/` | 已通过 Kustomize 渲染及 Kubernetes 1.34 schema 检查；集群运行待验收 |
| DaemonSet | `k8s/node-check/` | 清单 schema、脚本语法和模拟 wget 的 PASS/FAIL 分支验证通过；节点分布与真实日志待验收 |
| 操作流程与截图 | 本文及 `lab2-frontend-evidence.md` | 已提供步骤；截图必须来自实际实验环境 |

当前后端只有 `NoopReportJobLauncher`：默认创建报告后会停在 `PENDING`。要验收真实报告成功/失败，需要后端同学完成 Kubernetes Job launcher、报告执行程序和相应权限。前端不会把等待超时伪装为报告成功或失败。

当前环境没有 Docker daemon、Kubernetes 集群或你的 kubeconfig。下文的部署、Pod 重建及截图步骤尚未在真实集群执行。接口模拟测试只验证前端，不代表后端生成任务已完成。

## 1. 应用改动并验证前端

如果从交付包拿到补丁，在你自己的仓库根目录运行（先将 `lab2-frontend.patch` 放到仓库外的上级目录）：

```bash
git status --short
git switch -c feat/lab2-frontend
git apply --check ../lab2-frontend.patch
git apply ../lab2-frontend.patch
```

若 `--check` 提示冲突，说明本地代码与基线不同，先对比相关文件，不要覆盖队友改动。交付包的 `changed-files/` 保存了修改后的完整文件，便于逐项比较。

```bash
cd frontend
node --version
npm ci
npm run build
npm run test:unit -- --run
npm run dev
```

Node 版本应符合 `package.json`：`^22.18.0 || >=24.12.0`。浏览器打开 `http://localhost:5173/reports`。开发代理默认指向 `http://localhost:8080`；可在本地环境文件配置 `API_PROXY_TARGET`。这个变量用于开发代理，不写 API Pod IP，也不写数据库密码。

报告实现：最近七天默认为上海日期；范围含首尾最多 366 天，与现有后端一致；创建成功跳转详情；详情立即查询，此后在上次请求结束 2 秒后继续查询，避免重叠请求；成功或失败停止；离开页面取消请求和定时器。网络错误只显示查询错误，不修改任务状态；404 停止轮询。终态可用“刷新状态”再次查询，以核实后端是否重试。

## 2. 构建镜像并让集群能取得镜像

以下除特别注明外均在仓库根目录执行。现有 Dockerfile 使用 Node 多阶段构建、`npm ci` 锁文件安装，再用 Nginx 提供 `dist`。

```bash
docker build -t venue-frontend:lab2 ./frontend
```

下面按你的集群类型选择一个方式，不要全部运行：

| 环境 | 镜像准备 |
| --- | --- |
| kind | `kind load docker-image venue-frontend:lab2 --name <你的集群名>` |
| minikube | `minikube image load venue-frontend:lab2` |
| Docker Desktop Kubernetes | 先验证集群能否读取本地镜像；不同镜像存储配置可能需要导入或仓库推送 |
| 多机/远程集群 | 使用所有节点可访问的镜像仓库，推送镜像并修改 Deployment 中的 `image`；私有仓库另配 imagePullSecrets |

仓库推送示例（替换域名与账号后执行）：

```bash
docker tag venue-frontend:lab2 REGISTRY/USER/venue-frontend:lab2-v1
docker push REGISTRY/USER/venue-frontend:lab2-v1
```

使用仓库时，把 `k8s/frontend/deployment.yaml` 的镜像改成上述完整地址。日后更新建议换版本标签或 digest；保留同一个标签配合 `IfNotPresent` 可能继续使用节点上的旧镜像。若要求基础镜像字节级可重复，另将 Dockerfile 中基础镜像固定到核实过的 digest；当前标签不保证永久不变。

## 3. 确认集群依赖

```bash
kubectl config current-context
kubectl get nodes -o wide
kubectl apply -f k8s/database/namespace.yaml
kubectl -n venue-booking get service api
kubectl -n venue-booking get endpointslices -l kubernetes.io/service-name=api
```

要求后端同学在 **venue-booking** 命名空间提供名为 **api** 的 Service，服务端口 **8080**，并保留 `/api` 前缀。数据库部署由数据库同学维护，前端无需数据库 Secret。

若 `api` Service 不存在，先完成后端服务配置，再启动前端。Nginx 在启动时解析 `api`，此时服务名不存在会导致启动失败。前端健康探针仅检查 Nginx，后端是否可用必须另外通过业务请求确认。

## 4. 部署前端与访问入口

```bash
kubectl kustomize k8s/frontend --load-restrictor LoadRestrictionsNone | kubectl apply -f -
kubectl -n venue-booking rollout status deployment/frontend --timeout=120s
kubectl -n venue-booking get deployment,pods,service -l app=frontend
kubectl -n venue-booking get configmap
kubectl -n venue-booking port-forward service/frontend 8088:80
```

也可直接用下列命令查看控制器和 Service：

```bash
kubectl -n venue-booking get deployment/frontend service/frontend
```

保持 port-forward 终端运行，在电脑浏览器打开：

- `http://localhost:8088/venues`
- `http://localhost:8088/reservations`
- `http://localhost:8088/reports`

访问方式选定为 **ClusterIP + port-forward**。端口转发在运行 kubectl 的电脑上监听 localhost；若 kubectl 在远程服务器执行，需使用 SSH 本地转发或在电脑上配置可访问集群的 kubectl。Pod 重建后，port-forward 可能中断，需要重新运行。

前端初始 1 副本。requests 为 CPU `50m`、内存 `32Mi`；limits 为 CPU `250m`、内存 `128Mi`。启动、就绪、存活探针均使用本地 `/healthz`。

Kustomize 从 `frontend/nginx.conf` 生成 ConfigMap 并挂载到 `/etc/nginx/conf.d`。使用 `--load-restrictor LoadRestrictionsNone` 是因为源配置在 kustomization 目录之外，应只对本项目这类已审阅的本地文件使用。保留 ConfigMap 名称哈希，修改配置后再次执行同一部署命令，会触发 Deployment 使用新配置滚动更新，无需手动进入 Pod 编辑。

代理为 `proxy_pass http://api:8080;`，完整保留 `/api/` 和查询参数。浏览器只访问前端同源 `/api/...`。SPA 使用 `try_files` 回退到 `index.html`；刷新 `/reports/12` 等深链应能打开应用。静态资源不存在时返回 404，不回退为 HTML。

## 5. 验证 Lab1 与报告页面

按 `lab2-frontend-evidence.md` 操作。先验证 Lab1，再检查报告：

1. 创建一个名称唯一、开放时间明确的测试场地。
2. 选未来日期 D，创建 09:00–10:00、10:00–11:30 两条有效预约，再创建 13:00–13:30 一条并取消。
3. 用 D 至 D 生成报告。该场地预期 **2 次、150 分钟**；取消的预约不计入。为避免重复验收影响结果，使用专门新建的场地。
4. 确认零预约场地显示 **0 次、0 分钟**；停用场地也应包含，名称来自报告快照。
5. 验证“处理中 → 成功”及结果；失败场景由后端提供可控的测试失败任务，在前端检查 `errorMessage`。
6. 在浏览器 Network 中观察详情 GET 的轮询，终态后停止。测试 400、404、503 的提示。503 创建 Job 失败时，失败记录仍应出现在列表。
7. 刷新列表页、详情页和 Lab1 深链，确认页面可以直达。

当前契约规定任务 `FAILED` 就停止轮询，但后端可能随后重试；本实现遵循契约，用户可点“刷新状态”核实。最终失败语义需要团队进一步统一，见原契约分析。

## 6. 验证前端 Pod 自动恢复

先关闭 port-forward 或准备重开。在只包含本实验前端的命名空间里操作：

```bash
kubectl -n venue-booking get pods -l app=frontend -o wide
kubectl -n venue-booking delete pod -l app=frontend
kubectl -n venue-booking rollout status deployment/frontend --timeout=120s
kubectl -n venue-booking get pods -l app=frontend -o wide
kubectl -n venue-booking port-forward service/frontend 8088:80
```

保留删除前后 Pod 名及年龄截图，确认出现新 Pod，Deployment 仍是 1 个期望副本。再次访问页面并验证 API。Pod 删除会短暂中断这一实验实例。

## 7. 部署和验证 DaemonSet

```bash
kubectl apply -k k8s/node-check
kubectl -n venue-booking rollout status daemonset/node-check --timeout=120s
kubectl -n venue-booking get daemonset node-check
kubectl -n venue-booking get pods -l app=node-check -o wide
kubectl -n venue-booking logs -l app=node-check --prefix=true --tail=20 --max-log-requests=20
```

程序通过 Downward API 读取 `spec.nodeName`，每 30 秒检查 `http://frontend/healthz`，输出节点名、UTC ISO 时间和 PASS/FAIL。它验证的是**该节点上 Pod 到前端 Service 的 DNS/HTTP 连通性**，不是完整硬件健康检查；不读取宿主机文件，不需要节点管理权限。

调度条件是 Linux 节点，并容忍 control-plane/master 的 NoSchedule 污点以兼容单节点实验集群。其他自定义污点仍可能排除节点。逐一核对 Pod 的 NODE 列与符合条件的节点，每个应有一个 Pod；DaemonSet 的 DESIRED、CURRENT、READY 应与预期一致。单节点集群只有一个检查 Pod 属正常情况。

探针检查程序是否持续产生心跳，依赖服务访问失败只输出 FAIL，不故意触发检查程序重启。检查周期改长时，需要同步调整探针的 90 秒心跳阈值。

## 8. 常见故障

| 现象 | 优先检查 |
| --- | --- |
| ImagePullBackOff | 镜像是否导入每个节点、镜像名与标签是否一致、仓库权限 |
| 前端 CrashLoopBackOff | `kubectl -n venue-booking logs deployment/frontend`；api Service 是否存在；Nginx 配置语法 |
| 页面能开、接口 502 | api Service 端口和 EndpointSlice、后端就绪状态；浏览器请求必须是 `/api/...` |
| 刷新深链 404 | ConfigMap 是否挂载；Nginx 是否保留 SPA 回退；是否使用新镜像 |
| 报告一直处理中 | 后端是否还是 noop launcher；真正的 Job 是否创建及执行 |
| 报告成功但结果错误 | 核对日期、ACTIVE 过滤、全场地覆盖；前端不重新计算次数和分钟 |
| DaemonSet 数量偏少 | 节点 OS、污点、节点可调度条件、资源和镜像拉取状态 |
| 节点检查 FAIL | 从对应 Pod 的节点检查集群 DNS、Service 和网络策略；不能直接归因为硬件损坏 |
| 修改 Nginx 配置没有生效 | 重新运行 Kustomize 部署命令，并等待 rollout 完成 |

## 9. 浏览器自动化与交付前检查

在 `frontend` 目录中：

```bash
npx playwright install chromium
```

已开启 `8088` port-forward 后，PowerShell：

```powershell
$env:E2E_BASE_URL = 'http://localhost:8088'
$env:CI = '1'
npx playwright test --project=chromium
```

Bash：

```bash
CI=1 E2E_BASE_URL=http://localhost:8088 npx playwright test --project=chromium
```

这些部署冒烟用例验证导航和深链。`reports.spec.ts` 明确使用模拟 API，验证报告交互，不作为真实后端或集群验收证据。Lab1 增删查操作及真实报告结果仍按第 5 节手工核对。

本次本地验证：24 项单元测试通过，生产构建与 TypeScript 类型检查通过。浏览器自动化启动时遇到系统 `socket() failed: Operation not permitted`，因此未执行浏览器用例，也未生成可验收的页面截图；请在自己的电脑按以上命令运行。

部署配置验证：使用 kubectl v1.34.1 渲染两套 Kustomization，使用 kubeconform v0.7.0 严格校验 Kubernetes v1.34.0 schema，5 个资源全部有效。脚本用模拟 wget 分别验证 PASS、FAIL 和非法周期拒绝行为。这些检查不能替代实际镜像构建、Nginx 运行和 Kubernetes 调度验证。

提交前检查 `git diff --check`，确认没有凭据、node_modules、dist 或测试运行缓存进入提交。向组员说明 `api:8080` 的依赖和当前报告执行程序的缺口。

参考：[ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/)、[DaemonSet](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/)、[Nginx proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)。
