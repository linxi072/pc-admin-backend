# 关键流程

> 以下流程的表结构与接口契约以 `scaffold-admin/src/main/resources/db/migration/`（V32 起）为准；权限码由 `ApiResourceAutoSyncRunner` 自动登记。

## 1. 认证授权（登录）
```
用户登录 → AuthService
  ├─ 验证码/Captcha（可选） → 校验账号锁定（PasswordPolicy/LockoutRules）
  ├─ 校验密码（BCrypt + PasswordHistoryService 防复用）
  ├─ 签发 JWT（JwtProvider，HS256/RS256）→ 返回 access + refresh
  ├─ 写入刷新令牌 SysRefreshTokenDO（RefreshTokenServiceImpl）
  └─ 记录登录日志（LoginLogRecorder）→ 触发在线会话（OnlineSessionFilter）
```
Token 版本失效：`TokenVersionService` 在改密/踢人时自增版本，`TokenVersionVerifier` 在校验时比对，旧令牌即时失效。

## 2. OAuth2 资源 / 授权服务器
- **资源服务器**：`CompositeJwtDecoder`（HS256+RS256）校验 Bearer；`JwtAuthConverter` 将 `sub` 数值声明按 `Number→intValue()` 解析为 principal。
- **授权服务器**：`OAuth2AuthorizeEndpoint`/`OAuth2TokenEndpoint`/`OAuth2ConsentEndpoint`/`OAuth2TokenProvider`（JWKS）+ 内省端点；客户端凭据存 `sys_oauth2_client`，经 `OAuth2ClientDataAdapter`（system 提供）读取。

## 3. 数据权限（DataScope）
`DataScopeAspect` 拦截标注 `@DataScope` 的查询，依据当前主体角色的数据范围（`DataScopeType`）注入过滤条件；规则由 `security.permission` 定义，`system` 通过 `PermissionDataAdapter`/`DataScopeDataAdapter` 适配落库。判断「是否为某表」须用 `table.name().equals(USER.name())`，勿用对象身份比较。

## 4. 工作流（Flowable）
```
定义 → WorkflowDesigner 保存 BPMN → listDefinitions 供迁移目标
实例 → 启动（绑定 businessType/businessId）→ 运行列表（runningInstances，状态 RUNNING/APPROVED/REJECTED）
任务 → 认领 claim / 转办 transfer / 完成 complete（Flowable 7.1.0 API）
治理（管理员）→ 改派 reassign / 挂起 suspend / 恢复 resume / 迁移 migrate（WorkflowAdminController，权限 workflow:instance:*）
```
挂起态是引擎态，运行列表 status 恒为 RUNNING；前端以本地集合跟踪挂起标记。

## 5. 多租户隔离
`TenantContext`（不可变 `TenantScope`）贯穿请求；写入层（`JooqWriters`）强制注入租户列；旁路标记用于跨租户运维。隔离自检见 `TenantIsolationStatusController`/`TenantIsolationAuditController`。

## 6. 写接口幂等
`@Idempotency` 或约定幂等键 → `IdempotencyRecord` 去重，重复提交返回首次结果；审计切面统一记录操作日志与脱敏。

## 7. 可观测性
`framework.monitor.detection`（N+1 监听器、慢查询监听器、探针）经 `config` 装配；`NPlusOneMetricsBridge`/`FrameworkObservabilityAdapter` 桥接 Micrometer；`monitor` 模块提供告警收件箱与健康探针。

## 8. 代码生成（M50）
`scaffold-codegen` 读取表元数据 → `CodeGenService`/`CodeTemplates` 渲染 CRUD 骨架 → 经组件扫描注册 `/api/codegen`（CodeGenController）。
