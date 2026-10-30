# 可观测性

- **Tracing**：Spring Boot 3.5 使用 Micrometer Tracing + Brave bridge，发送到 Zipkin `http://127.0.0.1:9411/api/v2/spans`。
- **Metrics**：所有服务开放 `/actuator/prometheus`，由 Prometheus 抓取。
- **Dashboard**：Grafana 预置 Prometheus 数据源和「晴川二手市场 · 微服务总览」看板。

当前本机没有 Docker，因此文件按本机端口配置；后续容器化阶段会在 Compose 中改为容器服务名。

本地启动：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-observability.ps1
```

| 组件 | 地址 |
|---|---|
| Zipkin | http://127.0.0.1:9411 |
| Sentinel Dashboard | http://127.0.0.1:8858 |
| Prometheus | http://127.0.0.1:9090 |
| Grafana | http://127.0.0.1:3000 |
| Grafana 看板 | http://127.0.0.1:3000/d/mall-overview |

Grafana 本地演示账号：`admin / admin`，并启用了 Viewer 匿名访问。Prometheus 当前抓取 7/7 个服务，Grafana 看板包含请求速率、P95、5xx 错误率和 JVM 堆内存。Sentinel Dashboard 用于查看网关流控和订单服务熔断规则。
