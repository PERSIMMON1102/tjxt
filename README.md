# 天机学堂 · 在线教育微服务平台

面向 C 端学员的一站式在线学习平台，按业务域拆分为 **12 个业务微服务**，连同网关、鉴权与公共模块共 **16 个 Maven 模块**，打通「选课 → 下单 → 支付 → 学习 → 考试 → 互动 → 积分」全链路。

- 技术底座：Spring Boot 2.7 + Spring Cloud Alibaba（Nacos / Gateway / Sentinel）
- 中间件：MySQL、Redis + Redisson、RabbitMQ、Elasticsearch、MongoDB、XXL-Job、Seata
- 工程规模：约 800 个 Java 类

## 功能概览

| 域 | 能力 |
| --- | --- |
| 用户与认证 | 学员 / 员工 / 教师三类账号、JWT 登录与续期、RBAC 权限、网关统一鉴权 |
| 课程 | 课程与目录、教师、分类、课程草稿、媒资管理 |
| 交易与支付 | 购物车、下单、优惠券抵扣、支付宝 / 微信支付、退款回调 |
| 营销 | 优惠券模板与发放计划、领取、兑换码、最优券组合试算 |
| 学习 | 我的课表、学习计划、学习记录、问答互动、笔记、积分与赛季榜单、签到 |
| 考试 | 章节测验 / 考试、限时交卷、判分（题库存于 MongoDB） |
| 搜索 | ES 课程搜索、兴趣分类推荐 |
| 消息 | 短信、站内信 |
| 数据 | 今日数据、排行榜、运营看板 |

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 基础框架 | Spring Boot 2.7.2、Spring Cloud 2021.0.3、Spring Cloud Alibaba 2021.0.1.0 |
| 注册与配置 | Nacos 2.x |
| 网关 / 熔断 | Spring Cloud Gateway、Sentinel |
| 持久层 | MyBatis-Plus 3.4.3、MySQL 8 |
| 缓存 | Redis、Redisson 3.13.6（分布式锁 / Lua 脚本） |
| 消息 | RabbitMQ（自定义 `RabbitMqHelper`） |
| 搜索 | Elasticsearch 7.12 |
| 文档型存储 | MongoDB（考试题库与答卷） |
| 任务调度 | XXL-Job 2.3.1 |
| 分布式事务 | Seata 1.5.1 |
| 接口文档 | Knife4j 3.0.3 |
| 部署 | Docker |

## 模块与端口

| 模块 | 端口 | 说明 |
| --- | --- | --- |
| `tj-gateway` | 10010 | 网关：路由转发、JWT 校验、用户上下文透传、跨域 |
| `tj-auth` | 8081 | 登录签发 Token、刷新、RBAC 权限（含 gateway/resource 两个 SDK 子模块） |
| `tj-user` | 8082 | 学员 / 员工 / 教师、用户详情 |
| `tj-search` | 8083 | ES 课程搜索与推荐 |
| `tj-media` | 8084 | 图片 / 视频上传（腾讯云 COS / VOD） |
| `tj-message` | 8085 | 短信、站内信 |
| `tj-course` | 8086 | 课程、目录、教师、分类、课程草稿 |
| `tj-pay` | 8087 | 支付、退款、回调 |
| `tj-trade` | 8088 | 购物车、订单 |
| `tj-exam` | 8089 | 考试与题库 |
| `tj-learning` | 8090 | 课表、学习记录、互动问答、笔记、积分、签到 |
| `tj-remark` | 8091 | 点赞 / 收藏 |
| `tj-promotion` | 8092 | 优惠券、发放、领取、兑换码 |
| `tj-data` | 8093 | 数据看板与排行榜 |
| `tj-common` | — | 公共依赖：统一异常、分页、MQ、Redisson 锁、用户上下文、XXL-Job |
| `tj-api` | — | 跨服务 Feign 客户端与 DTO |

请求统一由网关进入，路径前缀映射到对应服务：

```
/ms → media      /as → auth       /us → user       /cs → course
/os → trade      /ss → search     /ls → learning   /ps → pay
/ts → trade      /es → exam       /rs → remark     /prs → promotion
/ds → data
```

## 架构示意

```
                         ┌────────────┐
  浏览器 / App  ────────▶ │  Gateway   │  10010  路由 + JWT 鉴权
                         └─────┬──────┘
        ┌──────────┬───────────┼───────────┬──────────┬──────────┐
     course     trade        pay       learning   promotion    exam ...
        │          │           │           │          │          │
        └──────────┴───────────┴─────┬─────┴──────────┴──────────┘
                                      │
        Nacos（注册/配置）  MySQL  Redis  RabbitMQ  Elasticsearch  MongoDB  XXL-Job
```

## 快速开始

### 1. 环境依赖

| 组件 | 版本 |
| --- | --- |
| JDK | 11 |
| Maven | 3.8+ |
| MySQL | 8.0 |
| Redis | 5.0+ |
| RabbitMQ | 3.9+（vhost `/tjxt`） |
| Nacos | 2.x |
| Elasticsearch | 7.12 |
| MongoDB | 4.4+（可选，考试模块） |
| XXL-Job | 2.3.1（可选，定时任务） |

### 2. 初始化数据库

每个服务独立库，需要先创建：

```
tj_auth  tj_user  tj_course  tj_trade  tj_pay  tj_promotion
tj_learning  tj_exam  tj_remark  tj_search  tj_media  tj_message  tj_data
```

### 3. 配置 Nacos

仓库不包含 Nacos 上的配置，需要在 Nacos 控制台按下列 dataId 建好（`DEFAULT_GROUP`，`yaml` 格式）：

| dataId | 用途 |
| --- | --- |
| `shared-spring.yaml` | Jackson、MVC 路径匹配等公共配置 |
| `shared-mybatis.yaml` | 数据源、MyBatis-Plus（库名由各服务 `${tj.jdbc.database}` 指定） |
| `shared-redis.yaml` | Redis 连接池 |
| `shared-mq.yaml` | RabbitMQ 连接与消费重试 |
| `shared-feign.yaml` | Feign + Sentinel |
| `shared-logs.yaml` | 日志格式与目录 |
| `shared-xxljob.yaml` | 调度中心地址与执行器 |
| `shared-seata.yaml` | Seata 事务组 |
| `shared-mongo.yaml` | MongoDB（考试模块） |
| `gateway-service.yaml` | 网关免登录白名单 |
| `course-service.yaml` / `trade-service.yaml` / `pay-service.yaml` / `remark-service.yaml` / `search-service.yaml` | 各服务业务配置 |

### 4. 启动

```bash
# 编译
mvn -DskipTests clean package

# 启动顺序建议：网关与注册中心之后，业务服务无强依赖
java -jar tj-gateway/target/*.jar
java -jar tj-auth/tj-auth-service/target/*.jar
java -jar tj-user/target/*.jar
# ... 其余服务同理
```

服务起来后：业务接口统一走 `http://localhost:10010`，接口文档见各服务的 `http://localhost:<port>/doc.html`。

## 配置与安全

仓库中的云厂商与支付凭据已全部脱敏为环境变量占位符，未设置时相关功能不可用（其余功能不受影响）：

| 环境变量 | 用途 | 影响服务 |
| --- | --- | --- |
| `TENCENT_SECRET_ID` / `TENCENT_SECRET_KEY` | 腾讯云 API 密钥 | `tj-media` |
| `TENCENT_VOD_URL_KEY` | 点播防盗链 Key | `tj-media` |
| `ALIYUN_ACCESS_KEY_ID` / `ALIYUN_ACCESS_KEY_SECRET` | 阿里云短信 | `tj-message` |
| `ALIPAY_MERCHANT_PRIVATE_KEY` | 支付宝商户私钥 | `tj-pay` |
| `PAY_PRIVATE_KEY` / `WX_API_V3_KEY` | 微信支付商户私钥 / APIv3 密钥 | `tj-pay` |

本地调试时可以临时写回配置文件，但请不要提交真实凭据。

## 性能压测

使用 JMeter 对领券接口（`POST /prs/user-coupons/{couponId}/receive`）做了三档并发压测，脚本与报告在 [`loadtest/`](loadtest/)：

| 并发 | 请求数 | TPS | P50 | P95 | P99 | 错误 |
| --- | --- | --- | --- | --- | --- | --- |
| 50 | 2000 | 318 | 45 ms | 103 ms | 148 ms | 0 |
| 100 | 3000 | **399** | 103 ms | 206 ms | **273 ms** | 0 |
| 200 | 3000 | 350 | 248 ms | 662 ms | 1024 ms | 0 |

- 100 并发为吞吐拐点，继续加压 TPS 反降、P99 明显抬升（线程池排队）
- 8000 次成功请求与 Redis 库存扣减、`user_coupon` 落库行数逐条对齐，**无超发**
- 复现：`jmeter -n -t loadtest/receive.jmx -l result.jtl -Jthreads=100 -Jramp=5 -Jloops=30`（需先生成 `loadtest/users.csv` 中的登录 token）

## 目录结构

```
tjxt
├── tj-common/            公共依赖（异常、分页、MQ、锁、上下文、XXL-Job）
├── tj-api/               Feign 客户端与 DTO
├── tj-gateway/           网关
├── tj-auth/              认证与权限
│   ├── tj-auth-common/
│   ├── tj-auth-gateway-sdk/
│   ├── tj-auth-resource-sdk/
│   └── tj-auth-service/
├── tj-user/  tj-course/  tj-search/  tj-media/  tj-message/
├── tj-trade/ tj-pay/     tj-promotion/ tj-learning/ tj-exam/
├── tj-remark/ tj-data/
├── loadtest/             JMeter 脚本、压测报告与分析脚本
├── resume/               项目简历素材（docx 由脚本生成）
├── docs/                 建表脚本
├── Dockerfile
└── startup.sh
```

## 说明

- 本项目在《天机学堂》课程资料基础上二次开发；学习域（课表、学习记录、互动问答、积分与签到）与营销域（优惠券、兑换码、最优券组合）、考试模块由本人实现，并补充了压测脚本与性能数据。
- 仅供学习交流使用。