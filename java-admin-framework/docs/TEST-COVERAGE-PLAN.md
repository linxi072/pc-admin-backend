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
| pc-admin-vue3-vite | `src/utils/__tests__/tree.test.js` | flattenTree/collectSubtreeIds（缩进/成环守卫/自定义键） | 9 |
| pc-admin-vue3-vite | `src/utils/__tests__/formSchema.test.js` | 表单 Schema（默认值/条件可见性/校验规则/离线校验） | 13 |

## 3. 第二批（1.2.0，按优先级）

1. **scaffold-security**：TokenService（签发/刷新/吊销轮换）、数据权限 SQL 织入、限流器、
   OAuth2 回调、在线会话管理——安全核心，优先级最高。
   - 进展（1.2.0 第一批）：`TokenVersionRulesTest`（5 例：claim 归一/版本失效 >= 语义/历史 token 兼容）、
     `SessionLimitRulesTest`（7 例：上限判定/淘汰顺序/tie-break/不变量）、
     `DataScopeResultTest`（5 例：fail-closed 兜底/不可变集合）、
     `RateLimiterTest`（5 例：固定窗口/窗口重置/并发上限/fail-closed）。
2. **scaffold-common**：幂等（IdempotentStore）、审计（AuditRecorder）、导入导出（Importer/Exporter）、
   树构建（TreeBuilder）、租户上下文（TenantContext）。
   - 进展（1.1.0）：`TreeBuilderTest` 已落地（6 例：层级/保序/森林升根/成环守卫/空输入/孤儿忽略）。
   - 进展（1.2.0 第一批）：`IdempotencyRecordRulesTest`（6 例：容错查询归一/时间窗收敛/过期判定）、
     `TenantContextTest`（9 例：严格模式/嵌套恢复/旁路语义/线程隔离）。
3. **scaffold-system**：用户/角色/菜单/接口资源服务主链路（内存 H2 或 Mock Mapper）。
   - 进展（1.1.0）：`JavaTypeMapperTest` 已落地（11 例：全类型矩阵/tinyint(1)/大小写归一/
     needsImport/toJavaField/toClassName 前缀剥离）。
   - 进展（1.2.0 收尾批）：`MenuTypeRulesTest`（4 例：全名/单字母双字面量兼容）、
     `PostRulesTest`（6 例：编码校验/岗位集合去重与上限）、
     `FileValidatorTest`（4 例：扩展名白名单/图片与PDF魔数）、
     `FileDedupRulesTest`（5 例：SHA-256/哈希格式/删除引用计数）。
     - 修复：`PostRules` 编码正则原拒绝中文，与注释「总经理-001 合法」及调用方预期不符，
       已扩充白名单支持 CJK 统一表意文字（行为修正，测试锁定）。

> 第二批累计新增 61 例纯单元测试（12 个测试类），后端累计 138 例全绿。集成测试采用「纯单测 + 内存 fake」
> 形态（Q4 决策，零新增依赖）；JaCoCo 插件离线不可用，CI 阈值维持起步线 0.10/0.05（Q3 决策）。
> T1 剩余：`SessionCleanupRulesTest`（5 例：会话空闲回收，last_used 优先/issued 兜底/禁用与边界）。

## 4. 执行方式

- 后端：`mvn -B verify`（根 POM 已绑定 surefire + jacoco）；报告位于 `target/site/jacoco/`。
- CI：`.github/workflows/ci.yml` 的 `coverage-gate` job 解析 JaCoCo XML 并按 §1 阈值阻断。
- 收紧阈值时同步修改 `ci.yml` 内 `MIN_INSTRUCTION / MIN_BRANCH` 并更新本表。
