# Nacos Config 配置

本目录是 Nacos 配置中心的可复现来源。启动项目前执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/init-nacos-config.ps1
```

| Data ID | 用途 |
|---|---|
| `mall-common.yaml` | 公共运行信息、Actuator 暴露配置 |
| `mall-gateway.yaml` | 网关服务专属标记 |
| `mall-auth.yaml` | 认证服务专属标记 |
| `mall-user.yaml` | 用户服务专属标记 |
| `mall-product.yaml` | 商品服务专属标记 |
| `mall-order.yaml` | 订单服务专属标记 |
| `mall-payment.yaml` | 支付服务专属标记 |
| `mall-message.yaml` | 消息服务专属标记 |

所有服务通过 `spring.config.import` 加载公共配置和自身配置；本地 `application.yml` 保留关键兜底配置，Nacos 临时不可用时仍可启动。需要热更新的非敏感配置应放在 Nacos，并通过 `@RefreshScope` 使用。
