# 系统功能详细使用说明

> 适用范围：java-admin-framework（后端管理框架，包名 `com.acme.scaffold.*`）  
> 默认管理员账号：`admin` / `123456`（BCrypt 密文，SUPER_ADMIN 拥有全部菜单与操作权限）  
> 文档目标：面向运维、实施与开发者，按「定位与层级 → 业务价值 → 配置样例 → 操作步骤」四段式说明每个功能模块。

---

## 0. 系统总览

### 0.1 架构分层与 Maven 模块映射

| Maven 模块 | 层级定位 | 主要职责 |
| --- | --- | --- |
| `scaffold-admin` | 装配入口 | Spring Boot 主应用，聚合全部模块并启动 |
| `scaffold-common` | 通用能力层 | 幂等控制、连通性探针、全局异常、jOOQ 动态 DSL、脱敏基础设施 |
| `scaffold-framework` | 框架层 | 多数据源、Spring Cache（Redis）、可观测性导出、租户拦截器、WebSocket 网关装配 |
| `scaffold-system` | 系统核心层 | 用户/角色/菜单/部门/岗位/字典/参数、审计/登录日志、租户/SaaS、通知体系、文件、表单、数据权限、认证 |
| `scaffold-workflow` | 业务引擎 | Flowable 工作流（定义/任务/实例/监控） |
| `scaffold-realtime` | 通道层 | 实时 WebSocket 推送网关 |
| `scaffold-job` | 调度层 | JobRunr 后台作业调度 |
| `scaffold-monitor` | 运维层 | N+1 探测、存活探针、告警收件箱、Kafka 监控 |
| `scaffold-observability` | 观测层 | 请求链路 trace 内存回溯 |
| `scaffold-kafka` | 消息层 | 海量数据有序消费 / Kafka 网关（默认关闭） |
| `scaffold-codegen` | 工具层 | 代码生成 |
| `scaffold-workbench` | 展示层 | 报表大屏 / 工作台 |

### 0.2 通用约定

- **配置外部化**：所有敏感凭据（JWT 密钥、SMTP、短信网关、Webhook 签名、Vault Token 等）均通过**环境变量**注入，不写入仓库。常见变量见各模块「配置样例」。
- **环境 Profile**：`local`（本机）/ `integration`（集成测试）/ `prod`（生产）。激活方式 `SPRING_PROFILES_ACTIVE=prod`；默认 `local`。
- **统一返回包络**：接口统一返回 `Result{code, message, data}`，`code="0"` 表示成功（兼容数字 `0`）。
- **权限码**：菜单/按钮均带 `permission`（如 `system:user:read`），后端 `@PreAuthorize` 校验；新增受控资源会在启动时被 `ApiResourceAutoSyncRunner` 自动登记并授予 SUPER_ADMIN。
- **主应用端口** `server.port=8080`；**运维端口** `management.server.port=9090`（health/metrics/prometheus）。

---

## 1. 认证与授权中心

**1.1 系统定位与所属层级**  
`scaffold-system` 的 `security` 包，导航于「系统管理」之外独立提供登录/令牌能力；是全部其它功能的前置门户。

**1.2 功能意义与业务价值**  
- 账号密码登录、JWT 双令牌（access/refresh）、验证码防爆破、多端登录限制、在线会话管理、OAuth2/JWKS 对接第三方。
- 通过「登录失败计数 + 账号锁定 + 验证码 + 多端挤占」组合，抵御暴力破解与越权，保障系统入口安全。

**1.3 配置样例及关键参数**（`application.yml` → `app.security.*`，验证码在 `application-local.yml` → `auth.*`）

```yaml
app:
  security:
    issuer: java-admin-framework
    audience: admin-api
    access-token-ttl: 30m        # access 令牌有效期
    refresh-token-ttl: 30d       # refresh 令牌有效期
    max-login-failures: 5        # 累计失败次数达上限即锁定
    lock-duration: 15m           # 锁定时长
    max-concurrent-sessions: 3   # 同账号同时在线设备数上限；<=0 不限制
    session-eviction-strategy: evict_oldest  # reject=拒绝新登录 / evict_oldest=淘汰最早
    session-max-inactive: 30m    # 刷新令牌空闲超时被吊销
    jwt-secret: ${JWT_SECRET:}   # 必配（Base64 ≥256 位），缺失则仅进程内临时随机密钥
auth:
  captcha:
    enabled: true                # 登录强制验证码
    expire-seconds: 120
    max-number: 20
    operators: ADD,SUB,MUL,DIV
  anti-replay:
    enabled: false              # 请求级防重放（opt-in）
```

**1.4 使用步骤与操作指引**  
1. 浏览器访问前端登录页 → 调用 `GET /api/auth/captcha` 获取算式与 `token`。
2. 输入用户名/密码 + 验证码 → `POST /api/auth/login` 返回 `accessToken`/`refreshToken`。
3. 令牌过期用 `POST /api/auth/refresh` 续期；退出用 `POST /api/auth/logout`。
4. 管理员在「系统管理 → 在线用户」查看当前会话，可对异常会话「强制下线」。
5. 对接第三方：访问 `GET /api/oauth2/jwks` 获取公钥，或使用 `POST /api/oauth2/token` 换取令牌。

---

## 2. 系统管理（用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数）

**2.1 系统定位与所属层级**  
`scaffold-system` 核心，导航「系统管理」目录：`用户管理`、`角色管理`、`部门管理`、`菜单管理`（后续可扩展岗位/字典/参数）。

**2.2 功能意义与业务价值**  
RBAC 权限模型的载体：用户绑定角色、角色绑定菜单/按钮权限，实现「最小权限」与职责分离；部门支持树形组织与用户多部门归属（含主部门标记）；字典与参数配置支撑业务可配置化。

**2.3 配置样例及关键参数**  
本模块以数据库 + 菜单种子驱动，无需额外 YAML；关键行为：
- 角色-菜单关联经 `sys_role_menu`；SUPER_ADMIN 默认拥有全部种子菜单。
- 用户可多部门归属（`sys_user_department.is_primary` 标记主部门）。

**2.4 使用步骤与操作指引**  
1. 「用户管理」→ 新增用户（设用户名/密码/绑定部门/分配角色）；首次登录按策略可能强制改密。
2. 「角色管理」→ 新增角色并勾选可访问菜单与按钮权限。
3. 「部门管理」→ 维护树形组织结构。
4. 「菜单管理」→ 查看/调整前端路由与权限码（常规无需改动）。
5. 数据权限（见模块 3）在角色维度进一步约束「能看哪些行」。

---

## 3. 可配置数据权限（M31）

**3.1 系统定位与所属层级**  
`scaffold-system` 的 `security.permission`（`DataScope` 注解 + `DataScopeResourceRegistry`），属安全子域契约；导航「系统管理 → 数据权限」。

**3.2 功能意义与业务价值**  
将「角色 × 资源」的数据行级权限提升为**一等可发现管理页**：启动时自动扫描 `@DataScope` 方法生成资源清单，新增受控资源无需前端硬编码即可出现在配置页；与模块 2 的角色体系打通，实现「菜单权限 + 数据范围」双层管控。

**3.3 配置样例及关键参数**  
- 代码侧：`@DataScope(name = "用户列表", scope = DataScopeType.DEPT)` 标注方法，`name()` 作为展示名。
- 后端只读接口：`GET /api/system/data-scope/resources`（`system:data-scope:read`，自动授予 SUPER_ADMIN）。
- 前端保存复用 `RoleController` 的 `data-scopes` 端点；`CUSTOM` 类型须至少选一个部门。

**3.4 使用步骤与操作指引**  
1. 「系统管理 → 数据权限」查看系统自动登记的受控资源清单。
2. 进入某角色编辑 → 数据权限页，为资源选择范围（全部 / 本部门 / 自定义部门）。
3. 保存后，该角色下用户访问对应接口时仅返回授权范围内的数据。

---

## 4. 审计日志（M1）

**4.1 系统定位与所属层级**  
`scaffold-system`（`AuditLogController`），导航「系统管理 → 审计日志」。

**4.2 功能意义与业务价值**  
对关键写操作留痕（操作人、时间、IP、入参/出参摘要），满足合规审计与问题溯源；异步写入（`app.audit.async=true`）降低主链路开销。

**4.3 配置样例及关键参数**  
```yaml
app:
  audit:
    async: true   # 审计落库异步化，避免阻塞业务
```

**4.4 使用步骤与操作指引**  
1. 「系统管理 → 审计日志」按操作人/时间/模块筛选。
2. 点击明细查看请求快照，用于追责与排障。

---

## 5. 幂等控制（M2）

**5.1 系统定位与所属层级**  
`scaffold-common`（`idempotency` 包，`IdempotencyRecordController`），通用能力层，对所有模块开放。

**5.2 功能意义与业务价值**  
为写接口提供「同一请求重复提交只生效一次」保障（如支付/开户类敏感操作），防止网络重试、双击提交造成重复业务数据。

**5.3 配置样例及关键参数**  
- 由幂等键（`Idempotency-Key` 头或业务键）驱动，命中已有记录直接返回首次结果。
- 记录可经 `IdempotencyRecordController` 查询/清理。

**5.4 使用步骤与操作指引**  
1. 客户端对敏感写请求携带稳定 `Idempotency-Key`。
2. 重复请求在服务端被识别并返回首次结果，不产生副作用。
3. 运维可在幂等记录页查看/清理过期键。

---

## 6. 多租户与 SaaS 开通（M19）

**6.1 系统定位与所属层级**  
`scaffold-system` 的 `tenant` 包（`TenantController` / `TenantGovernanceController` / `TenantIsolationStatusController`），导航「系统管理 → 租户管理 / 租户治理 / 租户隔离态势」。

**6.2 功能意义与业务价值**  
- **多租户隔离**：共享表 + `tenant_id` 强制隔离（enforce 默认关闭、生产配置开启，零线上回归）。
- **SaaS 一键开通**：`TenantProvisioningService` 自动创建租户、默认部门、绑定全量菜单/接口的 `TENANT_ADMIN_<code>` 角色与首位管理员，幂等守卫防重复开通。
- **隔离态势诊断**：`TenantIsolationRules.buildStatus` 以 `OK/INVALID/DISABLED` 三态呈现隔离健康度，复用 `TenantEnforcement` 为唯一事实来源。

**6.3 配置样例及关键参数**  
```yaml
# 生产启用租户强制隔离（默认关闭，详见 docs/manual/m19-production-enablement.md）
# tenant.enforce.enabled: true
```
- 开通接口：`POST /api/system/tenants/{id}/provision`（`system:tenant:manage`）。
- 隔离校验：`TenantEnforcement.validateConfiguration` 启动 fail-fast；`TenantIsolationStatusController` 暴露只读态势。

**6.4 使用步骤与操作指引**  
1. 「租户管理」新增租户（租户编码、名称）。
2. 「租户治理 → 开通」对新建租户执行一键开通，自动产出管理员账号与角色。
3. 「租户隔离态势」查看各租户隔离状态（OK/INVALID/DISABLED），INVALID 时按提示修复配置。
4. 生产环境按 `m19-production-enablement.md` 打开 enforce 护栏。

---

## 7. 通知体系（公告 / 站内信 / 通知中心 / 模板 / 记录 / 偏好 / 免打扰 / 退订 / 重试-故障转移-摘要 / 渠道）

**7.1 系统定位与所属层级**  
`scaffold-system` 的 `notification` / `controller`（公告 `Announcement*`、站内信 `Message*`、通知中心 `NotificationCenter`、模板 `NotificationTemplate`、发送记录 `NotificationRecord`、偏好 `NotificationPreference`、免打扰 `NotificationQuietHours`、退订 `NotificationUnsubscribe`），导航「系统管理 / 消息中心」相关菜单。

**7.2 功能意义与业务价值**  
覆盖「发布-触达-反馈-治理」全链路：
- **公告**：全局广播 + 已读回执（M7）。
- **站内信 / 通知中心**：用户侧消息聚合（M8/M9）。
- **通知模板**（M10）+ **模板化发送**（M11）：消息内容参数化、统一渲染。
- **发送记录**（M12）：每一条外发可追溯。
- **通知偏好中心**（M13）/ **免打扰时段**（M14）：用户自定接收渠道与时间。
- **退订**（M15）、**有界重试 + 故障转移 + 聚合摘要**（M16/M17）：送达保障与降噪。
- **渠道**：站内信（常开）、Webhook、邮件、短信（均默认关闭，配置后外发）。

**7.3 配置样例及关键参数**（`app.notifications.*`）

```yaml
app:
  notifications:
    webhook:
      enabled: false                       # 启用并配置后才外发
      default-url: https://hooks.example.com/recv
      signing-key: ${NOTIFICATION_WEBHOOK_SIGNING_KEY:}  # HMAC-SHA256 签名防篡改
      timeout-ms: 5000
      connect-timeout-ms: 2000
    email:
      enabled: false
      from: noreply@example.com
      host: smtp.example.com
      port: 587
      username: ${NOTIFICATION_EMAIL_USERNAME:}
      password: ${NOTIFICATION_EMAIL_PASSWORD:}
      starttls: true
      timeout-ms: 5000
    sms:
      enabled: false
      provider: LOG        # LOG=仅脱敏日志不真发(联调) / HTTP=通用网关
      gateway-url: "https://sms-gateway.example.com/api/v1/send"
      app-key: ${NOTIFICATION_SMS_APP_KEY:}
      signature: "【Acme】"
      timeout-ms: 5000
    unsubscribe:
      enabled: false
      secret: ${NOTIFICATION_UNSUBSCRIBE_SECRET:}
      base-url: "https://admin.example.com"
      ttl-hours: 72
    retry:                 # M16 有界重试
      enabled: true
      max-attempts: 3
      backoff-ms: 200
    failover:              # 主适配器失败切换备用（默认关，避免重复投递）
      enabled: false
    digest:                # M17 聚合摘要（默认关）
      enabled: false
      msg-types: [NOTICE]
      threshold: 5
      max-wait-minutes: 60
```

**7.4 使用步骤与操作指引**  
1. **配置渠道**：在对应 profile 开启 `email/sms/webhook` 并注入凭据（环境变量）。
2. **建模板**：「通知模板」新建模板（渠道、标题、正文，变量用 `${var}`）。
3. **发公告/站内信**：选择范围（全员/部门/指定用户）+ 模板/正文 → 发送；公告可查看已读回执。
4. **看记录**：「发送记录」按状态（成功/失败/重试中）追踪。
5. **用户侧**：个人中心设置通知偏好与免打扰时段；邮件/短信内含退订链接（开启 `unsubscribe` 后）。
6. **退订端点**：`GET /api/notification/unsubscribe?token=...` 始终公开，但 secret/base-url 未配时任何令牌验签失败（安全失败）。

---

## 8. 实时推送（realtime，M29/M30）

**8.1 系统定位与所属层级**  
`scaffold-realtime`（`RealtimeController`），导航「系统管理 → 实时推送」。

**8.2 功能意义与业务价值**  
基于 WebSocket 网关向在线用户实时推送（未读消息 / 待办提醒 / 通知 / 心跳）。M30 支持广播/定向推送**可选模板**，无模板回落原生 payload，有模板走 `RealtimeTemplateAdapter` 复用通知模板渲染。

**8.3 配置样例及关键参数**（`realtime.gateway.*`）

```yaml
realtime:
  gateway:
    enabled: ${REALTIME_GATEWAY_ENABLED:true}
    max-connections-per-node: 200000
    rate-per-conn-per-sec: 100      # 单连接限速
    burst-per-conn: 500
    heartbeat:
      ping-interval-ms: 30000
      idle-timeout-ms: 90000
    slow-consumer:
      queue-limit: 1000
    hmac:
      enabled: false                # 开启则走 HMAC 短期令牌握手（须配 secret）
      secret: ${REALTIME_GATEWAY_HMAC_SECRET:}
```
推送类型常量：`UNREAD_MESSAGE` / `TODO_REMINDER` / `NOTIFICATION` / `PONG`。

**8.4 使用步骤与操作指引**  
1. 「实时推送」页选择推送范围（全部在线 / 指定用户）。
2. 可选模板：下拉选择通知模板并填变量，或服务端「预览」渲染结果。
3. 发送：广播或定向；前端在线用户即时收到提示。
4. 运维关注：`rate-per-conn` / `slow-consumer.queue-limit` 防止慢消费者拖垮节点。

---

## 9. 低代码表单（M22）

**9.1 系统定位与所属层级**  
`scaffold-system` 的 `form` 包（`FormDefinitionController` / `FormRenderController`），导航「系统管理 → 表单定义」。

**9.2 功能意义与业务价值**  
以 `sys_form_definition` 描述表单结构，提交落库选「独立 `form_submission` 表、零运行时 DDL」；试点 `contact-feedback`。让非研发也能快速搭建数据采集表单，降低定制开发成本。

**9.3 配置样例及关键参数**  
- 纯函数校验 `FormSubmissionRules`；提交写入 `form_submission`，不改动业务表结构。
- 无额外 YAML 配置，依赖 DB 表与前端渲染引擎。

**9.4 使用步骤与操作指引**  
1. 「表单定义」新建表单（字段类型、校验规则、布局）。
2. 保存后生成表单页；前端 `FormRenderController` 按定义动态渲染。
3. 用户提交 → 落入 `form_submission`；管理员在列表查看/导出提交数据。

---

## 10. 工作流（Flowable，M4）

**10.1 系统定位与所属层级**  
`scaffold-workflow`（`WorkflowDefinitionController` / `TaskController` / `InstanceController` / `WorkflowMonitorController` / `WorkflowAdminController`）。

**10.2 功能意义与业务价值**  
内置 Flowable 7 引擎，支撑审批流：流程定义部署、任务认领（claim）/ 转办（transfer）/ 完成（complete）、流程实例跟踪与监控。

**10.3 配置样例及关键参数**（`flowable.*`）

```yaml
flowable:
  database-schema-update: true
  async-executor-activate: true
  history-level: audit
  process-definition-location-prefix: classpath*:/processes/
  rest-enabled: false          # 仅用引擎 API，关闭自带 REST/IDM
```

**10.4 使用步骤与操作指引**  
1. 「工作流 → 流程定义」上传/部署 BPMN。
2. 「任务」页对待办任务「认领 → 处理/转办 → 完成」。
3. 「流程实例」跟踪运行状态；「监控」查看执行指标与异常。

---

## 11. 文件管理（M25）

**11.1 系统定位与所属层级**  
`scaffold-system`（`FileController`），导航「系统管理 → 文件管理」。

**11.2 功能意义与业务价值**  
统一的文件上传/下载/管理，含扩展名白名单 + 魔数校验（拒绝可执行文件伪装），单文件大小上限可配。

**11.3 配置样例及关键参数**（`app.file.*`）

```yaml
app:
  file:
    storage-dir: ${FILE_STORAGE_DIR:./uploads/file}
    max-bytes: 10485760                       # 10MB
    allowed-extensions: jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar,7z
  upload:
    avatar-dir: ${AVATAR_DIR:./uploads/avatar}
    max-avatar-bytes: 2097152                 # 2MB
```

**11.4 使用步骤与操作指引**  
1. 「文件管理」上传文件（受白名单与大小约束）。
2. 列表查看/下载/删除；头像上传走独立 `avatar-dir` 约束。

---

## 12. 导入导出中心（M26）

**12.1 系统定位与所属层级**  
`scaffold-system`（`ImportController` / `ExportController`），导航「系统管理 → 导入 / 导出」。

**12.2 功能意义与业务价值**  
组织数据（部门/用户等）批量导入导出，支持 CSV，降低初始化与迁移成本；导出经 `ExportController` 异步生成。

**12.3 配置样例及关键参数**  
- 以 DB 表 + 模板驱动，无独立 YAML；导入校验失败行回显。
- 参考迁移：`V33__file_management`、`V34__csv_export_center`、`V36__import_center`。

**12.4 使用步骤与操作指引**  
1. 「导入」下载模板 → 填充数据 → 上传 → 查看校验结果（成功/失败行）。
2. 「导出」选择范围与格式 → 后台生成 → 下载文件。

---

## 13. 脱敏策略中心（M21）

**13.1 系统定位与所属层级**  
`scaffold-system`（`MaskStrategyController`），导航「系统管理 → 脱敏策略」。

**13.2 功能意义与业务价值**  
集中管理敏感字段（手机号/身份证/邮箱等）的展示脱敏规则，统一在前端/接口层按策略掩码，降低数据泄露风险、满足隐私合规。

**13.3 配置样例及关键参数**  
- 策略以 `sys_mask_strategy` 存储，无独立 YAML；规则可针对字段类型配置掩码格式。

**13.4 使用步骤与操作指引**  
1. 「脱敏策略」新建/编辑策略（字段、脱敏算法、展示样式）。
2. 关联需脱敏的接口/字段；生效后对应数据按策略掩码返回。

---

## 14. 开放平台 / OpenAPI 客户端（M24）

**14.1 系统定位与所属层级**  
`scaffold-system`（`OpenApiClientController`），导航「系统管理 → 开放平台」。

**14.2 功能意义与业务价值**  
管理对外 OpenAPI 客户端（appId/密钥/作用域/限流），支撑第三方系统集成与开放生态，自带签名校验与配额。

**14.3 配置样例及关键参数**  
- 客户端凭证存 `sys_openapi_client`；密钥经环境变量或密钥源注入。

**14.4 使用步骤与操作指引**  
1. 「开放平台」新建客户端（名称、作用域、限流阈值）。
2. 向第三方分发 `appId` 与密钥；第三方按签名规范调用 OpenAPI。
3. 监控调用与配额消耗，按需吊销。

---

## 15. 调度中心（JobRunr）

**15.1 系统定位与所属层级**  
`scaffold-job`（`JobController`），导航「系统管理 → 调度管理」。替代 XXL-JOB。

**15.2 功能意义与业务价值**  
内嵌 BackgroundJobServer 执行定时/周期任务，存储复用业务库（`jobrunr_*` 表），**无需独立调度中心、不依赖 Redis/Docker**；支持任务注册、执行监控、失败重试。

**15.3 配置样例及关键参数**（`org.jobrunr.*`）

```yaml
org:
  jobrunr:
    job-scheduler:
      enabled: true
    background-job-server:
      enabled: true
      worker-count: 4
    dashboard:                       # 仅 local 可开启，默认关避免端口冲突
      enabled: ${JOBRUNR_DASHBOARD_ENABLED:false}
      port: ${JOBRUNR_DASHBOARD_PORT:8001}
    database:
      type: sql
      skip-create: false            # 首次由 JobRunr 自动建表
```
> 注意：local profile 下 Dashboard 默认关闭；需观察任务时以 `JOBRUNR_DASHBOARD_ENABLED=true` 启动（默认 8001 端口，已避开 8000 冲突）。

**15.4 使用步骤与操作指引**  
1. 「调度管理」查看已注册任务、下次触发时间与执行状态。
2. 手动触发/暂停任务；查看失败任务并重试。
3. 开启 Dashboard 时访问 `http://localhost:8001` 观测队列与执行详情。

---

## 16. 缓存管理

**16.1 系统定位与所属层级**  
`scaffold-framework`（Spring Cache + Redis），运维入口 `GET /api/system/monitor/caches`。

**16.2 功能意义与业务价值**  
对热点数据（如权限聚合、字典）做 TTL 缓存，降低 DB 压力；提供命中率/条目数统计与清理入口。缓存为惰性能力，未使用 `@Cacheable` 时不产生 Redis 流量。

**16.3 配置样例及关键参数**（`app.cache.*`）

```yaml
app:
  cache:
    ttl: 30m                  # @Cacheable 未指定时的默认 TTL
    key-prefix: "acme:cache:" # 与业务 key 隔离，便于批量清理
```

**16.4 使用步骤与操作指引**  
1. 访问 `GET /api/system/monitor/caches` 查看各缓存命中率与条目数（按实例口径）。
2. 异常时调用清理入口刷新缓存；生产建议结合 Redis 监控。

---

## 17. 可观测性与监控

**17.1 系统定位与所属层级**  
`scaffold-monitor`（N+1、探针、告警收件箱、Kafka 监控）+ `scaffold-observability`（trace 回溯），导航「系统监控」相关菜单。

**17.2 功能意义与业务价值**  
- **N+1 探测**：同构 SQL 在单次请求内达阈值即告警，辅助性能治理（`app.jooq.nplus1-threshold`，默认 10）。
- **慢 SQL**：`app.jooq.slow-query-threshold-ms`（默认 500）。
- **链路回溯**：`observability.recorder` 内存留存最近 N 条 span，`GET /api/observability/traces` 查看。
- **告警收件箱**：作为 Alertmanager Webhook 真实接收端（`monitoring.alert.webhook`）。
- **探针/存活**：`ProbeController` 供 K8s/网关探活。

**17.3 配置样例及关键参数**

```yaml
app:
  jooq:
    slow-query-threshold-ms: 500
    nplus1-threshold: 10
    nplus1-max-suspects: 200
observability:
  otlp:
    enabled: false                       # 具备 Collector 时开启
    endpoint: ${OTLP_ENDPOINT:http://localhost:4318/v1/traces}
  recorder:
    enabled: true
    capacity: 200
monitoring:
  alert:
    webhook:
      enabled: true
      token: ${ALERT_WEBHOOK_TOKEN:}
    inbox-capacity: 200
```

**17.4 使用步骤与操作指引**  
1. 「监控 → N+1 / 慢 SQL」查看疑似事件，定位 ORM 懒加载/循环查询。
2. 「可观测性 → 链路」查看最近请求 trace，定位耗时环节。
3. 「告警收件箱」查看 Alertmanager 推送的告警并处理；webhook 需带匹配 `X-Alert-Token`。

---

## 18. 报表大屏 / 工作台（M27）

**18.1 系统定位与所属层级**  
`scaffold-workbench`（`ReportController` / `WorkbenchController`）+ `scaffold-system`（`DashboardController`），导航「报表大屏 / 工作台」。

**18.2 功能意义与业务价值**  
运营总览统计 API（含时间序列趋势）、N+1 与链路打通看板，帮助管理者实时掌握系统运行与业务态势。

**18.3 配置样例及关键参数**  
- 统计基于 `SYS_LOGIN_LOG.occurred_at` 等真实字段（已修正误用 bug）。
- 前端 `DashboardView.vue` + 路由与菜单种子（V52）驱动。

**18.4 使用步骤与操作指引**  
1. 「报表大屏」查看运营总览卡片与趋势图。
2. 「工作台」查看个人待办/通知聚合。
3. 时间范围筛选，导出或下钻查看明细。

---

## 19. 代码生成（codegen）

**19.1 系统定位与所属层级**  
`scaffold-codegen`（`CodeGenController`），工具层，导航「系统工具 → 代码生成」。

**19.2 功能意义与业务价值**  
基于表结构一键生成 CRUD 代码（Controller/Service/DO/DTO/前端页），减少重复样板，加速模块交付；与 jOOQ 代码生成层互补。

**19.3 配置样例及关键参数**  
- 以表元数据 + 模板驱动；模板见 `CodeTemplates`。

**19.4 使用步骤与操作指引**  
1. 「代码生成」选择数据源表。
2. 配置生成选项（包名、字段映射、前端框架）。
3. 预览并下载生成代码，合并入工程。

---

## 附：常用环境变量速查

| 变量 | 用途 | 缺省 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 签名密钥（Base64 ≥256 位，必配生产） | 空→进程内临时随机 |
| `SPRING_PROFILES_ACTIVE` | 激活环境 | local |
| `DB_PRIMARY_URL/USERNAME/PASSWORD` | 主库连接 | 本机 scaffold/root |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `NOTIFICATION_EMAIL_USERNAME/PASSWORD` | 邮件 SMTP 凭据 | 空 |
| `NOTIFICATION_SMS_APP_KEY` | 短信网关密钥 | 空 |
| `NOTIFICATION_WEBHOOK_SIGNING_KEY` | Webhook HMAC 签名密钥 | 空 |
| `NOTIFICATION_UNSUBSCRIBE_SECRET` | 退订令牌 HMAC 密钥 | 空 |
| `ALERT_WEBHOOK_TOKEN` | 告警 Webhook 校验令牌 | 空 |
| `FILE_STORAGE_DIR` / `AVATAR_DIR` | 文件/头像存储根 | ./uploads/* |
| `JOBRUNR_DASHBOARD_ENABLED` / `JOBRUNR_DASHBOARD_PORT` | 本地看板开关/端口 | false / 8001 |
| `KAFKA_*` / `OTLP_ENDPOINT` / `VAULT_*` | 消息/链路/密钥源（默认关） | — |

> 文档与代码同源：如功能随版本演进，请结合 `docs/manual/` 下专项文档（如 `m19-production-enablement.md`、`m27-dashboard.md`、`feature-gap-analysis.md`）交叉核对。
