# 架构说明

## 1. 定位
基于 Spring Boot 的**模块化单体（modular monolith）**企业后台基础框架，作为后续业务系统的脚手架。覆盖 RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

技术栈迁移已完成：持久层 MyBatis-Plus → **jOOQ**，任务调度 XXL-JOB → **JobRunr**，工作流 **Flowable 7.1.0**；全流程**不使用 Docker**（构建与部署以原生进程方式运行）。

## 2. 分层
```
基础层      common · realtime · kafka · codegen        ← 无业务、可独立复用
框架装配层  framework                                  ← 全局 Spring 配置、指标链路、N+1/慢查询监听
业务域层    security · system · monitor · job · workbench · workflow
启动装配层  admin                                      ← 唯一可部署模块（@SpringBootApplication 扫描 com.acme.scaffold）
```
- `security` 在 V1.0.6 重构中从 `system` 抽出为独立模块，承载认证授权与令牌域。
- `common` 内含 `jooq`（DSL 层）子包，`framework` 内含 `config`/`observability`/`monitor.detection` 子包——保留为子包而非独立模块（见 §5 重构说明）。

## 3. 关键技术决策
| 关注点 | 方案 | 说明 |
|---|---|---|
| 持久层 | jOOQ（手写动态 DSL，`JooqWriters`/`JooqTables`） | 类型安全、租户隔离与逻辑删除内建于写入层 |
| 调度 | JobRunr | 周期性任务与 DAG 依赖 |
| 工作流 | Flowable 7.1.0 | 流程定义/实例/任务，认领/转办/完成 + 管理员治理 |
| 认证 | JWT（HS256+RS256 混合 `CompositeJwtDecoder`）+ 刷新令牌 + Token 版本失效 | `sub` 数值声明按 `Number→intValue()` 解析 |
| 授权服务器 | OAuth2 授权码/令牌/同意/内省（资源服务器复用同一解码器） | 权限码由 `ApiResourceAutoSyncRunner` 扫描 `@PreAuthorize` 自动落库并授予 SUPER_ADMIN |
| 实时 | WebSocket（握手鉴权经 `realtime.port.HandshakeTokenAuthenticator`） | 在线会话、实时通知 |
| 多租户 | 租户列 + 旁路上下文（`TenantContext`） | 数据层强制隔离 |
| 数据权限 | `DataScope` 切面 + Port 防腐层 | system 提供适配器，security 定义规则 |
| 脱敏 | `SensitiveMasker` + `SensitiveType` | 字段级脱敏追溯 |
| 写幂等 | 幂等键 + `IdempotencyRecord` | 写接口防重放 |
| 可观测 | Micrometer + N+1/慢查询监听器（`framework.monitor.detection`） | 指标/链路/慢查询探针 |

## 4. 依赖 DAG（H5 校验通过，无环）
```
common  ← (realtime, kafka, codegen, security)
realtime← common
security← common, realtime
framework← common, realtime
system  ← common, realtime, security
monitor ← common, framework, security, system
job     ← common, monitor, system
workbench← common, system
workflow← common, monitor, realtime, system
admin   ← 全部模块（聚合装配，无可反依赖）
```

## 5. 本次重构说明（V1.0.6）
**目标**：消除重复、合理拆分模块、产出干净基础版本，并保持对外接口与表结构兼容。

**已做**：
1. 文档清理：`docs/archive`（14 份历史归档）与 `manual/` 内规划/历史/冗余文档已删除；生成 `ARCHITECTURE/MODULES/KEY-FLOWS/USAGE` 四份新核心文档。
2. 模块拆分：将 `system` 内的 `security.*` 子包抽出为独立模块 **`scaffold-security`**（依赖 `common`+`realtime`，被 `system`/`monitor`/`admin` 反向依赖，DAG 安全）。
3. 修复既存坏测试：`scaffold-admin` 的 `CoreApiE2eTest` 因 `TaskView` 记录签名变更未同步而编译失败，已对齐 9 组件签名。

**未做（及原因）**：`jooq`（在 `common` 内）与 `config`/`observability`/`monitor.detection`（在 `framework` 内）**未**抽成独立模块——
- `common` 与 `jooq` 存在**双向依赖环**（common 用 `JooqWriters` 写入、jooq 用 common 的 `Result`/`ErrorCode`），强行拆分将导致 Maven 编译顺序崩溃；
- `framework` 内 `config` 依赖自身的 `monitor.detection` 与 `realtime`，子包互相耦合，抽出需先抽取 N+1 探测子系统，连锁风险高。
保持子包形态是当下**唯一不破坏 929+ 测试的解**；若后续要彻底解耦，应先打破 `common↔jooq` 环（把 `JooqWriters` 所依赖的 common 类型下沉或反向整理），再逐步抽离。

**验证**：`mvn -o -pl <受影响模块> -am clean test` 全绿（12 模块、测试全过）；`scripts/check-conventions.py` exit=0（仅一条 benign 警告：config/jooq 为平铺子包，属既存情况）。
