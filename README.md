# 晴川二手市场 · Campus Flea Market

基于 **Spring Cloud 微服务 + Vue 3** 的校园二手交易平台（期末项目）。

- 设计文档：[`文档归类/01-总体开发文档.md`](文档归类/01-总体开发文档.md) ｜ [02 数据库](文档归类/02-数据库设计.md) ｜ [03 接口](文档归类/03-接口设计.md) ｜ [04 前端](文档归类/04-前端设计文档.md) ｜ [05 工程改动记录](文档归类/05-工程改动记录.md)
- 当前进度：**后端 9 个 Maven 模块 + 前端 Vue 工程全部实现，本机全链路联调通过**。

---

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 运行环境 | JDK 21、Node 22、MySQL 8、Redis |
| 后端 | Spring Boot 3.5.16、Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0 |
| 注册/配置 | Nacos 3.0.3（8848，控制台 8090） |
| 分布式事务 | Seata 2.5.0（8091，注册到 Nacos，`store.mode=db`） |
| 持久层 | MyBatis-Plus 3.5.9（需额外引入 `mybatis-plus-jsqlparser`） |
| 鉴权 | JJWT 0.12.6，网关统一校验并注入 `X-User-Id` / `X-User-Role` |
| 熔断/链路 | Sentinel 1.8.10（8858）、Zipkin 3.6.1（9411，可选） |
| 前端 | Vue 3.5 + TypeScript + Vite 5 + Element Plus + Pinia |

## 2. 模块与端口

| 模块 | 端口 | 职责 |
|---|---|---|
| `mall-gateway` | 4399 | 路由、JWT/token_version/封禁校验、限流、CORS、traceId |
| `mall-auth` | 4400 | 验证码、注册、登录、刷新、登出、改密、找回密码（**不建库**） |
| `mall-user` | 4401 | 资料、个性签名、收货地址、信用分、卖家主页、后台用户治理 |
| `mall-product` | 4402 | 分类、商品、图片、收藏、商品评论区、库存锁定、上传、静态资源 |
| `mall-order` | 4403 | 订单状态机、运费、双向评价、退款仲裁 |
| `mall-payment` | 4404 | 支付单、一步模拟支付、退款执行、回调 |
| `mall-message` | **4405** | 会话、聊天消息、订单卡片、系统通知、WebSocket |
| `mall-common` | — | 统一响应、错误码、全局异常、鉴权上下文、文件存储等公共能力 |
| `frontend` | 5173 | Vue 前端（Vite 代理 `/api` → 4399、`/ws` → 4399） |

> 端口统一从 **4399** 起顺排：网关 4399 / auth 4400 / user 4401 / product 4402 / order 4403 / payment 4404 / message 4405（避开常见的 8080 段，也顺带解决了本机 8086 被 `influxd.exe` 占用的问题）。详见 `文档归类/05-工程改动记录.md` 第 1 节。

## 3. 快速启动

### 3.1 中间件（本机已配置好）

```powershell
# Nacos（控制台已改 8090；网关现为 4399，二者错开）
Start-Process cmd.exe "/c D:\nacos\bin\startup.cmd -m standalone"

# Seata（注意：必须先放 mysql-connector-j-8.4.0.jar 到 lib\jdbc\）
$base = 'D:\seata-server-2.5.0（UI）\apache-seata-2.5.0-incubating-bin\seata-server'
Start-Process "$env:JAVA_HOME\bin\java.exe" @(
  "-Dloader.path=$base\lib", "-classpath", "$base\conf;$base\lib\*",
  "-Dspring.config.location=$base/conf/application.yml",
  "-jar", "$base\target\seata-server.jar"
)
```

MySQL(3306) 与 Redis(6379) 作为系统服务常驻。详细的外部配置与验证证据见 `文档归类/05-工程改动记录.md` 第 8 节。

### 3.2 数据库

```powershell
$mysql = 'D:\MySQL\bin\mysql.exe'
& $mysql --host=127.0.0.1 --port=3306 --user=root --password=123456 -e "source sql/01-schema.sql"
& $mysql --host=127.0.0.1 --port=3306 --user=root --password=123456 -e "source sql/02-init-data.sql"
```

- 5 个业务库 + `mall_seata`；`undo_log` 只建在 `mall_product`、`mall_order`（退款不走全局事务）。
- 应用账号：`mall_app` / `Mall@2026`。

### 3.3 后端

```powershell
# 先编译（Maven 用项目自带 wrapper）
.\mvnw.cmd -B -DskipTests install

# 按依赖顺序启动：先被调方，再网关
$services = 'mall-user','mall-auth','mall-product','mall-order','mall-payment','mall-message','mall-gateway'
foreach ($s in $services) {
  Start-Process java -ArgumentList '-jar', "$s\target\$s-1.0.0-SNAPSHOT.jar" -WindowStyle Hidden
}
```

### 3.4 前端

```powershell
cd frontend
npm install
npm run dev          # http://127.0.0.1:5173
# 或只启动/复用前端（不触碰后端）：
scripts\start-frontend.cmd
```

生产构建：`npm run build`（会先跑 `vue-tsc --noEmit` 做类型检查）。

> Windows 下若 `vite build` 报 esbuild 删除临时文件失败，把 `TEMP`/`TMP` 指到工作区内即可：
> `$env:TEMP='<项目>\.tmp'; $env:TMP=$env:TEMP`。

### 3.5 端口被占 / 僵尸进程（常见，一条命令解决）

改完代码在 IDEA 里点 Run 时若报 `Port 4399 was already in use`，说明上一轮 `java -jar` 或 IDEA 的旧实例还活着。
**双击 `scripts\free-ports.cmd`** 即可：它会结束占着 4399~4405 与 5173 的进程树（`taskkill /T /F`，连 JVM 子进程一起），并循环确认端口真的释放。

```bat
scripts\free-ports.cmd            :: 清掉 4399~4405 + 5173，之后直接在 IDEA 里 Run
scripts\free-ports.cmd -KeepFrontend
scripts\status.cmd                :: 看每个端口是谁占着、健康状态
scripts\stop-all.cmd              :: 停掉本项目全部进程（不动 Nacos/Seata/MySQL/Redis）
scripts\restart-all.cmd           :: 停 → 构建 → 起（改完代码推荐用这个，不会撞 jar 占用）
```

设计要点：
- `stop-all` / `restart-all` / `start-all` 都会**先清端口再启动**，所以不会出现“上次没停干净，这次起不来”。
- 杀进程用的是 `taskkill /PID x /T /F`（连同子进程），不是 `Stop-Process`——后者会留下 4~8MB 的空壳 JVM 继续占端口。
- 只动本项目的端口，不碰 Nacos(8848/8090)、Sentinel(8858)、Seata(8091)、MySQL(3306)、Redis(6379)。
## 4. 演示账号

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| `superadmin` | `Qingchuan@2026` | 超级管理员 | 唯一，只能由初始化 SQL 插入；可设置其他用户角色 |
| `admin` | `Qingchuan@2026` | 普通管理员 | 可建分类、强制下架、封禁/解封、限制上架权限 |
| `alice` | `Qingchuan@2026` | 普通用户 | 种子商品卖家 |
| `bob` | `Qingchuan@2026` | 普通用户 | 演示买家 |

登录支持**用户名或邮箱**（`alice@qingchuan.local`）。验证码在开发环境可用 `--mall.captcha.enabled=false` 关闭，关闭时接口仍要求传占位值。

## 5. 关键业务约定（改代码前先看）

- **游客**只能看到首页前 20 条商品，且不能进详情页（服务端强制 `pageNum=1&pageSize=20`，返回 `guestLimited=true`）。
- **注册**必填头像/用户名/邮箱/密码/确认密码；角色恒为 `USER`，管理员只能在后台由超管提升。
- **封禁**：`t_user.status=BANNED` + `ban_until`（永久写 `9999-12-31 23:59:59`）；在线用户通过 `token_version+1` + Redis 标记 + WS `KICKED` 立即踢下线。
- **上架权限**：`can_publish=0` 时发布接口返回 `30009`，前端弹「您因违反用户规则暂时无法上架商品，解封时间…」。
- **运费**：包邮 0 / 同城 5 / 跨城 10 / 仅自提 0，跨城自提直接 `40004`。
- **下单**：一件商品一单，先锁库存（`ON_SALE→LOCKED` + `lock_order_no`）再建单，`@GlobalTransactional` 覆盖两个库。
- **支付**：一步模拟，`t_payment.uk_order_no` 保证一单一张支付单，重复支付幂等返回。
- **评价**：双向互评，`uk_order_direction(order_id, direction)` 保证每个方向一次，重复 `40005`。
- **聊天卡片**：只由服务端产生（客户端伪造 `contentType=ORDER` 返回 `60004`），`clientMsgId = CARD:{orderNo}:{cardType}` 保证不重复。

## 6. 当前实现与设计文档的差异

| 差异 | 原因 | 影响面 |
|---|---|---|
| 全部服务端口 → **4399~4405** | 与常见 8080 段冲突（本机 8086 还被 influxd 占用） | 已同步 01/03/04/05 |
| 图片存储用**本地磁盘**（`LocalFileStorage` + `/files/**`） | 本机未安装 MinIO | 接口契约不变，换 MinIO 只改一个类 |
| 上传目录默认 `.tmp/uploads`（相对服务工作目录） | Java 的 `user.home` 在本机可能指向不可写目录 | auth/product 从项目根启动时共用同一目录 |
| 订单卡片用**同步 Feign** 调 `/inner/message/card` | 未安装 RocketMQ | 幂等键与"失败不影响主流程"的约束已实现 |
| 支付只有 `MockPayChannel` | 课程项目无需真实支付 | `Y3` 回调已按验签实现，可直接接沙箱 |

## 7. 已知待办

1. 接入 RocketMQ（订单超时延迟消息、订单状态卡片异步化）。
2. 图片改 MinIO（或对象存储）。
3. 前端补单元测试与 E2E；主 chunk 拆包。
4. 一键启停脚本（`scripts/start-all.ps1` / `stop-all.ps1`）。
5. 接入 Sentinel 控制台规则与 Zipkin 链路（jar 已在 `D:\sentinel-dashboard`、`D:\zipkin-server`）。

## 8. 目录结构

```
spring_Cloud_QiMo/
├─ pom.xml                  # 聚合父 pom（mall-parent）
├─ mall-common/             # 公共库
├─ mall-gateway/            # 网关 4399
├─ mall-auth/               # 认证 4400
├─ mall-user/               # 用户 4401
├─ mall-product/            # 商品 4402
├─ mall-order/              # 订单 4403
├─ mall-payment/            # 支付 4404
├─ mall-message/            # 消息 4405
├─ frontend/                # Vue 3 前端 5173
├─ sql/                     # 01-schema.sql、02-init-data.sql
└─ 文档归类/                 # 01~05 设计文档与施工日志
```
<img width="2492" height="1274" alt="image-20261029234318291" src="https://github.com/user-attachments/assets/0cd10f17-82f9-49cf-8493-0d9f9d6ee5dc" />
主页

![image-20261029234335217](C:\Users\Lz\AppData\Roaming\Typora\typora-user-images\image-20261029234335217.png)

消息页面

![image-20261029234404757](C:\Users\Lz\AppData\Roaming\Typora\typora-user-images\image-20261029234404757.png)

个人页面

![image-20261029234421593](C:\Users\Lz\AppData\Roaming\Typora\typora-user-images\image-20261029234421593.png)

商品详情页

![image-20261029234503892](C:\Users\Lz\AppData\Roaming\Typora\typora-user-images\image-20261029234503892.png)

管理员后台

![image-20261029234522893](C:\Users\Lz\AppData\Roaming\Typora\typora-user-images\image-20261029234522893.png)
