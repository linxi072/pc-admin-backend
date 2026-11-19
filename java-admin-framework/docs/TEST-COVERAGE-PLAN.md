# 测试覆盖重建计划（TEST-COVERAGE-PLAN）

> 状态：重建立项（V1.1.0）。V1.0.9 提交 `c9d248e` 删除了后端 929+ 测试，本计划分批恢复
> 关键模块覆盖，并按阶段收紧 JaCoCo 阈值。CI 覆盖率门禁以本计划口径为准。

## 1. 目标与阈值演进

| 阶段 | INSTRUCTION | BRANCH | 生效版本 | 说明 |
|------|-------------|--------|----------|------|
| 起步线 | ≥ 0.10 | ≥ 0.05 | 1.1.0 | 仅防「零测试」回退（CI coverage-gate 已按此阻断） |
| 第二批 | ≥ 0.40 | ≥ 0.30 | 1.2.0 | common/security 全量 + system 主链路 |
| 目标线 | ≥ 0.70 | ≥ 0.60 | 1.3.0 | 业务模块主链路全覆盖 |

## 2. 已恢复（1.1.0）

| 模块 | 测试类 | 覆盖目标 | 用例数 |
|------|--------|----------|--------|
| scaffold-common | `api/ResultTest` | 统一响应封装 ok/fail/分页 | 6 |
| scaffold-common | `sensitive/SensitiveMaskerTest` | 手机号/身份证/邮箱/姓名脱敏与边界 | 12 |
| scaffold-common | `resilience/CircuitBreakerTest` | 熔断状态机（CLOSED→OPEN→HALF_OPEN→CLOSED）、半开探针 | 7 |
| scaffold-security | `password/PasswordPolicyTest` | 密码策略（长度/复杂度/弱口令库） | 8 |
| scaffold-codegen | `ai/port/CloudLlmProviderTest` | 云大模型响应解析/JSON 提取/类型归一/501 语义 | 8 |
| scaffold-codegen | `ai/service/AiScaffoldServiceFallbackTest` | 编排降级语义（回退/501 不静默/无云 Bean） | 5 |
| pc-admin-vue3-vite | `src/api/__tests__/realtimePolicy.test.js` | 实时通道策略（连接/退避/恢复） | 9 |

## 3. 第二批（1.2.0，按优先级）

1. **scaffold-security**：TokenService（签发/刷新/吊销轮换）、数据权限 SQL 织入、限流器、
   OAuth2 回调、在线会话管理——安全核心，优先级最高。
2. **scaffold-common**：幂等（IdempotentStore）、审计（AuditRecorder）、导入导出（Importer/Exporter）、
   树构建（TreeBuilder）、租户上下文（TenantContext）。
3. **scaffold-system**：用户/角色/菜单/接口资源服务主链路（内存 H2 或 Mock Mapper）。

## 4. 执行方式

- 后端：`mvn -B verify`（根 POM 已绑定 surefire + jacoco）；报告位于 `target/site/jacoco/`。
- CI：`.github/workflows/ci.yml` 的 `coverage-gate` job 解析 JaCoCo XML 并按 §1 阈值阻断。
- 收紧阈值时同步修改 `ci.yml` 内 `MIN_INSTRUCTION / MIN_BRANCH` 并更新本表。
