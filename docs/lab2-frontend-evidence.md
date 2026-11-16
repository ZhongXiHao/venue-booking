# Lab2 前端实验记录与截图清单

下面所有“实际结果”均待在真实 Kubernetes 环境填写，不以模拟接口截图代替集群验收。

环境：集群类型 ______；Kubernetes 版本 ______；节点数 ______；命名空间 venue-booking；前端镜像/摘要 ______；代码版本 ______；验收时间（Asia/Shanghai）______。

访问入口：运行 `kubectl -n venue-booking port-forward service/frontend 8088:80`，浏览器访问 `http://localhost:8088`。

| 编号 | 操作 | 预期结果 | 实际结果 | 建议截图名 |
| --- | --- | --- | --- | --- |
| F01 | 打开场地列表、点进详情 | 列表与开放时间等详情正确 | 待验收 | 01-venues.png |
| F02 | 创建唯一名称的测试场地 | 创建成功，可在列表及详情查询 | 待验收 | 02-create-venue.png |
| F03 | 创建未来日期预约 | 成功后可查询详情 | 待验收 | 03-create-reservation.png |
| F04 | 筛选预约并取消一条 | 筛选正确，取消后状态为已取消 | 待验收 | 04-cancel-reservation.png |
| F05 | 触发报告、观察进度 | 等待与执行均显示处理中 | 待验收 | 05-report-processing.png |
| F06 | 查看成功报告 | 专用场地 2 次、150 分钟，零预约场地 0/0 | 待验收 | 06-report-success.png |
| F07 | 查看后端可控失败任务 | 显示失败及真实 errorMessage | 待验收 | 07-report-failed.png |
| F08 | Network 观察轮询 | 约每 2 秒查询，成功或失败停止 | 待验收 | 08-report-polling.png |
| F09 | 直接打开并刷新深链 | 场地、预约、报告路由正常 | 待验收 | 09-deep-link.png |
| K01 | 查看 Deployment、Service | 1 副本 Ready，ClusterIP 服务可用 | 待验收 | 10-frontend-resources.png |
| K02 | 删除前端 Pod 前后对比 | 新 Pod 自动创建并恢复服务 | 待验收 | 11-pod-recreated.png |
| K03 | 查看挂载的 ConfigMap | Nginx 配置外部化，代理 api:8080 | 待验收 | 12-nginx-config.png |
| D01 | 对比节点与检查 Pod 分布 | 每个符合条件的节点一个检查 Pod | 待验收 | 13-daemonset-distribution.png |
| D02 | 查看各检查 Pod 日志 | 包含节点名、时间、结果，周期持续输出 | 待验收 | 14-daemonset-logs.png |

补充记录：报告 ID ______；专用场地 ID ______；预约日期 ______；两条有效预约 ID ______；取消预约 ID ______；报告查询 JSON 与页面对照 ______。

失败原因与修复过程：______。

截图应包含必要上下文（页面地址、命名空间、Pod 或节点名），避免截入数据库密码、令牌、kubeconfig 等信息。
