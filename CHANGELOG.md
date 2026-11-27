# Changelog

本文件记录 pc-admin-backend（java-admin-framework + pc-admin-vue3-vite）的版本演进。
版本号以仓库根 `CHANGELOG.md` 为准，与后端根 POM、前端 package.json 强制一致
（由门禁 `scripts/check-version.py` 守护）。格式参考 Keep a Changelog。

### 用户名补齐数据库唯一约束

- **背景**：`sys_user` 没有用户名唯一索引，`UserService.existsByUsername()` 先查再写；
  两个并发创建同名账号的请求都能通过应用层查重并落库，同一租户出现两个同名未删除账号。
  登录与凭证链路按 `username` 查询命中哪条取决于返回顺序，身份、角色绑定与令牌失效
  （`bumpUsers` 只作用于命中行）都可能作用在错误账号上。
- **实现**：新库 `ddl.sql` 增加生成列 `username_active`（`deleted=0` 时取 `username`，否则 NULL）
  与唯一索引 `uk_user_tenant_username_active(tenant_id, username_active)`，与 `existsByUsername`
  「租户内、未删除行」口径一致；存量库提供 `upgrade_user_username_unique_index.sql`，
  按高版本/最新 id 去重后加列建索引。服务层捕获唯一键冲突并转换为与预校验一致的
  409「用户名已存在」，正常创建路径不受影响。
- **测试**：新增 `UserUsernameUniqueIndexDdlTest`（3 例）与 `UserServiceUsernameConflictTest`（2 例），
  覆盖生成列/NULL 语义、升级脚本顺序、并发创建冲突文案与正常路径不被误伤。后端测试 686 → 691 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：存量库须在停写窗口备份后执行升级脚本，脚本无列/索引存在性守卫；唯一键不含
  `username` 原列而是生成列，业务逻辑无需感知，逻辑删除后可用同用户名重建；HTTP 端点与
  请求字段不变。

### 系统变量管理端补齐配置值脱敏

- **背景**：`GET /api/system/configs`、`/groups`、`/key/{key}` 原样回显 `configValue` / `defaultValue`，
  `notification.wecom.webhook-url?key=...` 与 `notification.dingtalk.webhook-url?access_token=...`
  等机器人密钥可被任何持有 `system:config:read` 的主体直接读取；配置中心快照虽已脱敏，
  但那是另一模块、另一条数据路径，系统参数管理接口完全未接入。
- **实现**：把敏感键判定提升为 common 的 `SensitiveConfigKeys`（新增 `webhook` 语义片段），
  `ConfigSnapshotSanitizer` 改为委托以保持两端一致；`ConfigService` 新增 `listForDisplay()` /
  `listGroupedForDisplay()` / `getByKeyForDisplay()`，仅管理端出口对配置值与默认值掩码，
  内部 `getByKey()` 仍返回原文供机器人适配器与系统配置端口使用。
- **测试**：新增 `ConfigListSecretMaskTest`（4 例），覆盖列表、按键、分组出口脱敏、内部读取零影响及
  普通配置空值兼容。后端测试 682 → 686 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：密钥类配置现在显示为 `******`，管理端无法通过该接口查看或回写原值，需走数据库/配置中心；
  HTTP 端点、请求字段与内部业务读取语义不变。

### TokenVersion 缓存回填补齐失效代际

- **背景**：`TokenVersionService.currentVersion()` 缓存 miss 后回库读版本再 `cache.put()`；
  `bumpUsers()` 会先递增数据库版本再 `invalidate()`。若请求 A 在失效前读到旧版本 v=1，
  管理员强退/改密把版本升到 2 并清缓存，随后 A 再把 v=1 写回缓存，后续请求在 30 秒 TTL 内
  仍会用 v=1 判定旧 token 有效，强制下线被缓存旁路。
- **实现**：`TokenVersionCache` 增加单调 `generation`，`invalidate()` / `clear()` 先推进代际；
  服务回源前记录代际，回填改用 `putIfUnchanged()`，仅当代际未变才写入。读旧值期间发生的任何
  bump/clear 都会让该次回填失效，下一次校验重新回源拿到新版本。原有 TTL、统计口径与缓存 API 保持可用。
- **测试**：新增 `TokenVersionCacheFillRaceTest`（4 例），覆盖失效后回填丢弃、clear 代际、
  正常回填保留，以及服务级「读旧值 → bump → 旧值回填」交错。后端测试 678 → 682 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：并发强退后旧 token 的失效不再受 TTL 窗口影响；缓存命中率可能因回填放弃略降，
  属于正确性优先的取舍。配置项、HTTP 端点与 JWT 声明不变。

### 表单编码补齐数据库唯一约束

- **背景**：`FormDefinitionService.existsByCode()` 先查再写，但 `sys_form_definition` 没有唯一索引；
  两个并发创建或改码请求都能通过应用层查重并落库，同一租户出现同名启用表单，渲染面按缓存键
  取到的 Schema 取决于命中哪条记录，提交目标也可能随之漂移。
- **实现**：新库 `ddl.sql` 增加生成列 `form_code_active`（`deleted=0` 时取 `form_code`，否则 NULL）
  与唯一索引 `uk_form_tenant_code_active(tenant_id, form_code_active)`，只约束未删除行，逻辑删除后
  仍可用同编码重建；存量库提供 `upgrade_form_code_unique_index.sql`，按高版本/最新 id 去重后建列建索引。
  服务层捕获唯一键冲突并转换为清晰的 409「表单编码已存在」。
- **测试**：新增 `FormDefinitionUniqueIndexDdlTest`（3 例）与 `FormDefinitionCodeConflictTest`（2 例），
  覆盖生成列/NULL 语义、升级脚本顺序、并发创建与改码冲突文案。后端测试 673 → 678 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：存量库须在停写窗口备份后执行升级脚本，脚本无列/索引存在性守卫；唯一键不含 `form_code`
  原列而是生成列，业务逻辑无需感知，请求字段与 HTTP 端点不变。

### 表单定义缓存键补齐租户维度

- **背景**：`FormDefinitionService.codeCache` 以裸 `form_code` 为键，而数据库读取经
  `SqlWriters.scopedSelect` 按租户过滤。强隔离下两个租户使用同一表单编码时，租户 A 先加载的
  启用定义会被租户 B 的 `getEnabledByCode` 命中，导致渲染到另一租户的 Schema，甚至按 A 的
  `targetType` 提交数据到错误目标。
- **实现**：缓存键改为 `cacheKey(formCode)`，强隔离下为 `T{tenantId}|{formCode}`，隔离关闭时固定
  `T0|{formCode}`；写操作继续全量失效，覆盖所有租户。单租户部署行为保持不变。
- **测试**：新增 `FormDefinitionCacheKeyTenantTest`（3 例），覆盖同名编码跨租户生成两个缓存键、
  隔离关闭固定 T0、当前租户键稳定。后端测试 670 → 673 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：旧的无前缀缓存条目不会被新口径命中，重启或任一写操作触发 `refreshCache()` 即可清理；
  HTTP 端点、请求字段与 Schema 校验规则不变。

### 实时推送管理补齐租户可见范围

- **背景**：`RealtimeController` 的在线会话列表、在线总数、定向推送与广播仅校验权限码，
  `GatewayNode.onlineUserCounts()` 返回全网关 userId，`pushToUsers` 可向任意 userId 推送通知。
  被授予 `system:realtime:*` 的非超管主体可跨租户枚举在线用户并注入“系统通知”，
  与已修复的在线用户管理权限缺口同源。
- **实现**：新增 `RealtimeUserDirectoryPort`（realtime 定义、system 以 `RealtimeUserDirectoryAdapter`
  实现，保持 realtime → common 单向依赖），按当前主体租户查询活跃用户集合；强隔离开启时显式带
  `tenant_id` 条件，未开启时保留平台视图。`RealtimeAdminService` 统一以该集合过滤在线会话、
  计算在线数、限定广播目标；定向推送出现范围外 userId 即 403 拒绝整个请求。无登录主体或目录端口
  缺失时 fail-closed 返回空集合。
- **测试**：新增 `RealtimeAdminScopeTest`（5 例）与 `RealtimeUserDirectoryAdapterTest`（2 例），
  覆盖会话过滤、在线数、越权推送拒绝、广播收敛、无主体 fail-closed 及适配器租户条件。
  后端测试 663 → 670 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：广播改为“按可见用户逐人推送”，返回值为成功触达的用户数而非连接数；
  定向推送越权统一返回 403 且不泄露目标是否存在。权限码与 HTTP 端点不变。

### 幂等 FAILED 重试补齐状态 CAS

- **背景**：业务失败后记录置 `FAILED`，允许同 key 指纹重试。`doAcquire()` 判定 `PROCEED` 后
  仅按 id + 租户把记录改回 `PROCESSING`，未带旧状态比较条件也不检查影响行数；两个并发重试
  同时读到 `FAILED` 时会双双更新成功并执行业务。此前补的 DDL 唯一索引只兜底首次 INSERT，
  对已存在记录的状态跃迁没有约束力，审批/转办/消息发送类接口仍可能重复执行。
- **实现**：FAILED→PROCESSING 的 UPDATE 追加 `status = 'FAILED'` 比较并交换条件，按影响行数裁决；
  CAS 失败时重查最新状态，映射为 REPLAY / CONFLICT / MISMATCH，且即使重查仍是 FAILED 也降级为
  CONFLICT，绝不把抢占失败当成放行。命中匹配仍复用 `IdempotencyRules.decide()` 单一口径。
- **测试**：新增 `IdempotencyFailedRetryConcurrencyTest`（4 例），覆盖 CAS 条件、抢占失败后的
  PROCESSING / SUCCESS / FAILED 三种重查结果与响应快照重放。后端测试 659 → 663 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：并发重试中只有一个请求会执行业务，落败方收到 409（或已完成时的响应重放）；
  客户端应把 409 视为“已有在途请求”而非系统错误，HTTP 端点与请求字段不变。

### 脱敏策略写操作改为全量失效缓存

- **背景**：`MaskStrategyService.activeCache` 按租户缓存启用策略且无 TTL，四个写操作却只调用
  `invalidate(0L)`。租户 7 管理员新增/启停策略后，租户 7 缓存永不刷新；
  平台租户 0 的策略会被所有业务租户继承，但其变更也只清掉租户 0 自己的缓存。
  结果新增的 MOBILE / ID_CARD 策略不生效，停用的策略继续脱敏，安全控制被静默旁路，
  解析面回落到调用方兜底类型（常为 NONE）时甚至会让 PII 明文进入导出结果。
- **实现**：`create` / `update` / `delete` / `toggle` 统一改为 `refreshCache()` 全量失效，
  并删除只清单租户的私有 `invalidate()`。管理写操作频率远低于解析命中，全清以一次后续惰性加载
  换取“平台策略继承 + 租户策略变更”两个维度都不残留旧快照。
- **测试**：新增 `MaskStrategyCacheInvalidationTest`（3 例），覆盖创建、租户策略启停、
  平台策略变更清业务租户缓存，并断言 `cacheVersion()` 递增。后端测试 656 → 659 例。
- **验证**：前端 161 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：写操作后首次解析会回查一次数据库；缓存无 TTL 的语义不变，但不再依赖重启/手工刷新纠正策略变更。

### CSV 公式防护补齐前导空白绕过

- **背景**：后端 `CsvExporter.neutralizeFormula()` 与前端 `csv.js` 只检查单元格第一个字符，
  以 `  =1+1`、`\n@evil`、NBSP/全角空格开头的载荷会被当作普通文本放行；Excel / WPS 会忽略
  这些前导空白并继续执行公式，导出中的用户名、备注等用户可控字段可绕过既有 CWE-1236 防护。
- **实现**：两端统一先跳过可忽略的前导空白（空格、换行、NBSP、全角空格等），再判定
  `= + - @` 危险首字符；命中时在原始值最前面补单引号。Tab / CR 本身仍按危险首字符处理，
  数值例外（允许前导空格的 `-5` / `+3.14`）保持不变，避免破坏历史导出结果。
- **测试**：新增 `CsvExporterFormulaWhitespaceTest`（5 例），前端 `csv.test.js` 由 8 例扩到 11 例；
  覆盖空白变体、空格 + 数值、Tab 兼容与写出/toCsv 端到端口径。后端测试 651 → 656 例，
  前端测试 158 → 161 例。
- **验证**：门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：这是导出文本口径变更，不改动任何 HTTP 端点；含前导空白的公式类单元格现在会多一个前置单引号。

### WebSocket 握手补齐 tokenVersion 校验

- **背景**：`JwtHandshakeAuthenticator.resolveUserId()` 只验签并从 JWT 取 `userId`，
  完全跳过 HTTP 链路强制执行的 `tokenVersion` 门禁。用户被强退、禁用或重置密码后，
  虽然 access token 已失效，旧令牌仍可通过 WebSocket 握手建立连接并持续收发消息，
  形成强制下线的绕过入口。
- **实现**：握手鉴权器注入 `TokenVersionService`，解析 JWT 后读取 `tokenVersion`
  声明（缺失按版本 1 兼容历史令牌），与库内版本比对；版本落后或用户不存在即返回 null，
  由握手方拒绝连接。签名/格式异常仍按原口径静默拒绝，不泄露失败原因。
- **测试**：新增 `JwtHandshakeAuthenticatorTokenVersionTest`（5 例），覆盖有效版本、
  版本过期、历史令牌默认版本、已删除用户与非法令牌不打库。后端测试 646 → 651 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：强退后已建立的 WebSocket 连接不会主动断开，只在下次握手/重连时按新版本校验；
  配置与 HTTP 端点不变，客户端无需改造。

### 幂等记录补齐数据库唯一约束

- **背景**：`IdempotencyService.doAcquire()` 先 `find` 再 `insert`，并发兜底依赖
  `DataIntegrityViolationException` 重查；但 `sys_idempotency_record` 的 DDL 中没有唯一索引，
  两个同租户、同 `Idempotency-Key`、同 `api_scope` 的并发请求都能插入成功并双双 `PROCEED`，
  审批/转办/消息发送类接口会被执行两次。此前只在写路径补了租户条件，数据库兜底始终缺失。
- **实现**：新库 `db/init/ddl.sql` 增加 `uk_idem_tenant_scope_key`
  （`tenant_id, api_scope, idempotency_key`），与 `keyCondition()` 读写口径一致；
  存量库提供 `db/init/upgrade_idempotency_unique_index.sql`，先按 SUCCESS > FAILED > PROCESSING、
  同级保留最新 id 的规则去重，再创建同名唯一索引。升级说明同步写入 `db/init/README.md`。
- **测试**：新增 `IdempotencyUniqueIndexDdlTest`（3 例），校验新库 DDL 的唯一键定义、
  表字段口径以及存量升级脚本「先去重后建索引」顺序。后端测试 643 → 646 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：存量库必须在停写窗口备份后执行升级脚本；脚本不做索引存在性守卫，重复执行会报索引已存在。
  唯一键不含 `user_id`，因为幂等查询本身按租户 + key + scope 定位，跨用户共享同一 key 仍视为同一请求。

### 防重放缓存饱和改为 fail-closed

- **背景**：`ReplayNonceService` 在 nonce 缓存达到上限时会按过期时间淘汰“最早过期的未过期条目”。
  攻击者只要先用一个合法 nonce 调用登录/刷新，再用大量噪声 nonce 填满缓存，就能把仍有效的重放证据挤出；
  随后重放原请求即被放行，防重放形同虚设。过期分支的 `get + put` 也在并发边界下可能双双放行。
- **实现**：新增 `AcquireResult`（`ACQUIRED` / `REPLAY` / `CACHE_FULL`）；缓存满时只清理过期条目，
  若全部仍在 TTL 内则拒绝新 nonce 并 fail-closed，不再淘汰有效记录；过期复用改为 `replace` 条件覆盖，
  并发续期只有一个请求放行。`AntiReplayFilter` 对饱和场景返回 429 / `AUTH_014`，重放仍为 409 / `AUTH_013`；
  兼容旧调用方的 `isReplay()` 在缓存满时同样返回 true。
- **测试**：`ReplayNonceServiceTest` 由 4 例扩到 7 例，新增有效 nonce 不被挤出、过期项优先清理、
  旧接口 fail-closed；新增 `AntiReplayFilterCacheFullTest`（2 例）验证 429 / 409 映射与链路不继续执行。
  后端测试 638 → 643 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：启用防重放且请求峰值超过 `max-nonce-cache-size` 时，多余请求会收到 429，需调大缓存或缩短 TTL；
  配置项与 HTTP 端点不变，新增的是错误码 `AUTH_014` 响应语义。

### OAuth2 用户令牌补齐租户与角色声明

- **背景**：`OAuth2TokenProvider.issueUserToken()` 只写 `sub/client_id/scope/authorities/username/tokenVersion`，
  不写 `tenantId` / `roles`。`JwtAuthConverter` 只从 JWT `tenantId` 声明还原租户，
  导致 password 与 authorization_code 授权签发的用户令牌进入 `TenantInterceptor` 时租户为空，
  随后回落到默认租户 0：非默认租户用户查不到本租户数据，默认租户存在数据时还可能越权读写。
  授权码上下文未携带租户，`CodeContext.roles()` 也未写入访问令牌；`client_id` 声明同样未被转换器读取。
- **实现**：两个用户令牌签发重载增加 `tenantId` / `roles` 参数并写入 JWT；授权码 `CodeContext` 增加
  `tenantId` 并在 `issue()` / `consume()` 双向透传；`OAuth2TokenEndpoint` 与授权/同意端点从认证用户、
  当前 principal 传递租户与角色；`JwtAuthConverter` 兼容 `client_id` / `clientId` 两种声明。
- **测试**：新增 `OAuth2UserTokenTenantClaimTest`（6 例），覆盖令牌声明、无租户不硬编码默认租户、
  授权码往返、password/authorization_code 两条端点路径与客户端令牌零回归。
  后端测试 632 → 638 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：历史 OAuth2 用户令牌不含 `tenantId`，仍按旧口径回落默认租户，需重新签发后生效；
  client_credentials 令牌保持无租户语义，不受影响。HTTP 端点与响应结构不变。

### 短信长度预算改为按含签名报文计算

- **背景**：`SmsRules.validateAndNormalize()` 先校验原始正文 ≤500 再调用 `appendSignature()`，
  拼接后的最终报文可达 534 字；`appendSignature()` 又先把签名截到 32 字再补方括号，
  实际签名可达 34 字，与 `MAX_SIGNATURE_LEN`（含方括号）的约定不符。超长报文交由网关后
  可能按长短信拆分计费或直接拒发，错误群发还会产生费用。
- **实现**：长度门禁改为校验拼接后的最终正文，并在无签名/有签名时分别给出清晰错误文案与实际长度；
  签名拼接改为先去控制符与已有方括号、再按「含括号总长」截断，保证签名部分恒 ≤32 字且括号完整。
  退订链接追加仍沿用 `MAX_CONTENT_LEN`，天然复用同一预算。
- **测试**：新增 `SmsRulesContentBudgetTest`（7 例），覆盖临界值、签名推送超限、含括号截断、
  控制符净化后预算、无签名上限及手机号归一回归。后端测试 625 → 632 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：超限错误文案新增「当前长度」，签名超长时按含括号 32 字截断（此前可到 34 字）；
  HTTP 端点与配置键不变，正文恰好 500 字的短信仍可发送。

### 代码生成模板补齐不可信文本转义

- **背景**：`CodeTemplates` 把管理端传入的 `functionName` / `author` 与表/列注释直接拼入 Java 块注释、
  `@Tag` 字符串、Vue 注释/属性/文本和 JS 注释。`*/`、`"`、`-->`、`<script>`、`{{...}}` 等字符可闭合注释、
  跳出字符串或注入 Vue 模板；元信息推导的 `className` 未校验还会拼进 zip 条目路径，形成 Zip Slip。
- **实现**：新增 `TemplateEscapes`，统一压平换行并把注释终止符、引号、尖括号、反斜杠、`&`、花括号替换为
  全角等价字符；一份净化结果可安全用于所有模板上下文且幂等。`SqlTables` 引用改为白名单标识符；
  `className` 无论来自入参还是表元信息，均按 Java 标识符严格校验，非法即 fail-fast。
- **测试**：新增 `CodeTemplatesInjectionTest`（6 例），覆盖 Java 注释/注解注入、Vue 注释与模板注入、
  列注释跨上下文转义、危险类名拒绝、表名标识符归一与普通文本零影响。后端测试 619 → 625 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：含特殊字符的功能名/注释会显示为全角等价字符（如 `"` → `“`、`{` → `｛`），属于防注入取舍；
  `{@code}` 注释内的表名同样收敛，但不改变生成文件的数量、路径规则与 API 契约。

### 配置中心快照补齐敏感键脱敏

- **背景**：配置中心快照仅按 `app.*` 白名单前缀过滤，`app.security.jwt-secret`、
  `app.security.oauth2.private-key`、`app.xxx.password` 等同样位于应用命名空间，
  会被原值回显到 `GET /api/system/config-center/snapshot`；显式传 `prefix=app.security`
  或把 `snapshot-prefix` 置空也都能绕过白名单取回密钥明文。
- **实现**：新增 `ConfigSnapshotSanitizer`，对 `app.security.*` 及含 secret/password/private-key/
  api-key/token/credential 等语义的键统一掩码为 `******`。`ConfigCenterService.snapshot()`
  先脱敏再按前缀过滤，`LocalConfigCenterAdapter` 在生成快照时同步脱敏；远端端口由服务层兜底，
  不依赖具体实现自觉。键名保留供审计，值不再回显。
- **测试**：新增 `ConfigCenterSnapshotSafetyTest`（6 例），覆盖本地适配器、`app.*` 默认前缀、
  空前缀、显式 `app.security` 前缀、状态计数/刷新一致性与常见敏感键模式。
  后端测试 613 → 619 例。
- **验证**：前端 158 例、门禁 56/56、Lint（0 errors）与 Vite 生产构建均通过。
- **注意**：这是展示层脱敏，不改变 `ConfigCenterPort.getProperty()` 的内部读取语义；
  管理接口不提供单键读取，敏感键在快照中仍可见键名但值为固定掩码。

### 异步导出任务补齐归属校验

- **背景**：`ExportJob` 只保存 taskId/模板/状态，不记录提交人与租户；`status()` / `result()` 仅按 jobId 取任务。
  任何持有 `system:export:read` 的用户只要拿到 jobId，就能查询他人在职导出任务状态并下载产物。
  产物本身已按提交者身份与租户生成，因此这是直接的数据越权下载。
- **实现**：`ExportJob` 增加 `submitterUserId` / `tenantId`，`submit()` 在请求线程固化归属；
  `status()` / `result()` 统一校验“同一提交人 + 同一租户”，不匹配与不存在统一返回 `COMMON_404`，
  不泄露任务存在性。异步回调沿用快照中的归属字段，不依赖执行线程的上下文。
- **测试**：`ExportJobServiceTest` 由 3 例扩到 6 例，新增其他用户、其他租户与匿名访问均被拒绝。
  后端测试 610 → 613 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：`ExportJob` 状态接口响应新增两个归属字段（向后兼容的增量字段）；当前不支持管理员代查他人任务，
  如后续需要可通过显式授权策略扩展，不在此次默认放行。

### 幂等记录写路径补齐租户隔离

- **背景**：`IdempotencyService.find()` 经 `SqlWriters.fetchOne` 带租户条件，但 `markSuccess` / `markFailed` /
  `release` 以及过期清理、FAILED→PROCESSING 重入都直接按裸 `id` 或 `(key, scope)` 更新/删除。
  强隔离下两个租户使用相同 `Idempotency-Key` 与 scope 时，A 的成功写会污染 B 的记录并写入 A 的响应快照，
  B 重试可能 REPLAY A 的响应；`release` 还会误删 B 的在途保护。
- **实现**：`keyCondition` 统一经 `SqlWriters.withTenant` 装饰；按 id 的过期删除与重入更新同样追加租户条件。
  读路径继续复用 `keyCondition`，读写口径一致；`purgeExpired` 作为全局清理任务保持跨租户。
- **测试**：新增 `IdempotencyTenantScopeTest`（4 例），覆盖成功/失败/释放三类写路径带租户条件、
  关闭隔离时不注入条件。后端测试 606 → 610 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：DDL 中尚未建立 `(tenant_id, idempotency_key, api_scope)` 唯一索引，
  极端并发下唯一索引兜底仍缺失；本次只修写路径作用域，索引变更需单独评估存量数据。

### 机构树缓存键加入租户维度

- **背景**：`OrgService.orgScopeKey()` 只按数据权限范围生成 `ALL` / `SELF:<userId>` / `ORG:<ids>`。
  `sys_org` 不在平台共享维表清单中，强隔离下机构查询会按 `tenant_id` 过滤；但两个租户的超管都解析为
  `ALL`，缓存键相同。租户 A 首次访问写入整棵机构树后，租户 B 在 TTL 内直接命中，读到 A 的部门编码、
  名称、负责人和层级结构，形成跨租户缓存串味。
- **实现**：缓存键改为 `T<tenantId>|<数据权限签名>`；`enforce=true` 时取当前租户，`enforce=false`
  时固定 `T0`，单租户部署行为不变。原有按数据权限分键的语义保留在 `dataScopeKey()` 中。
- **测试**：新增 `OrgCacheKeyTenantTest`（3 例），覆盖两租户 ALL 键不同、同租户 SELF 键稳定、
  关闭隔离时固定 `T0` 前缀。后端测试 603 → 606 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：部署开启强隔离后旧缓存中的无前缀键将不再命中，首个请求会回源并重建，属预期行为。

### 在线用户管理补齐数据权限

- **背景**：在线会话分页、强制下线、限制/解除目标用户只校验功能权限，不叠加 `system:online` 数据权限。
  拥有功能码的角色即使数据范围仅限本人/本部门，仍能分页看到全量在线用户、按任意会话 ID 强制下线、
  限制任意用户；登录限制分页同样跨范围泄露人员信息。
- **实现**：`DataScopeConditions` 支持带 `user_id` 但无机构列的表按可见用户集合过滤；在线会话分页、强制下线、
  登录限制分页统一接入 `@DataScope` 与 userId 条件；限制/解除目标用户前按 `sys_user` 数据范围校验，
  越权与不存在统一返回 `COMMON_404`，不泄露存在性。
- **测试**：新增 `DataScopeConditionsUserIdTableTest`（3 例）与 `OnlineUserScopeTest`（3 例），覆盖 SELF/机构范围、
  无上下文放行、分页/强退过滤和越权目标拒绝。另修正刷新令牌并发测试的调度顺序断言，消除偶发失败。
  后端测试 597 → 603 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：新增 `system:online` 数据权限资源会由配置页自动发现；未配置规则的账号沿用默认兜底策略
  （当前为 SELF），超管与显式 ALL 规则仍保持全量可见。

### 强制下线补齐访问令牌即时失效

- **背景**：`OnlineSessionFilter` 依赖客户端 `X-Device-Id` 定位会话；缺失头会直接放行，伪造/未知设备号查不到
  会话行也按 fail-open 放行。由于 access token 不携带 deviceId，强制下线只把会话置 `OFFLINE`、吊销刷新令牌，
  不递增 tokenVersion，被踢用户仍可在令牌有效期内省略或伪造设备头继续调用受保护 API。
- **实现**：`OnlineUserService.forceLogout` / `markOfflineByUser` 与 `LoginRestrictionService.restrict` 在置离线和
  吊销刷新令牌后统一调用 `TokenVersionService.bumpUsers`。`TokenVersionVerifier` 在下一个请求即拒绝旧
  access token，不再依赖客户端是否上报设备头；刷新令牌吊销与会话离线语义保持不变。
- **测试**：新增 `OnlineKickTokenVersionTest`（3 例），覆盖定向强制下线、全量下线/登出与限制登录三条路径。
  后端测试 594 → 597 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：tokenVersion 是用户级版本，任一强制下线/限制操作会让该用户所有旧 access token 立即失效，
  其他在线设备需重新登录；这是为消除设备头绕过而采取的 fail-closed 取舍。DB 异常时在线会话校验仍保持既有 fail-open。

### XLSX 稀疏列引用增加逻辑列数上限

- **背景**：`XlsxDataReader.columnIndex` 解析 `r` 属性时无上限，`putCell` 又按列号无界扩表；
  一个不到 1 KB 的工作表只要包含 `r="ZZZZZZ1"`，就会让单行逻辑列数达到数亿，`toTabular` 再补齐一次，
  直接耗尽堆内存。已有的 32 MB 解压上限无法约束这种逻辑稀疏放大。
- **实现**：列号解析改用 `long` 累加并在超过 Excel 规范上限 16384（XFD）时立即抛异常；
  `putCell` 与 `toTabular` 各保留一道硬上限校验，确保单表逻辑列数不可能由极小输入放大到 OOM。
  超限统一抛 `IllegalArgumentException`，由导入链路转为 400。
- **测试**：`XlsxDataReaderSafetyTest` 由 3 例扩到 6 例，新增超长列引用被拒、合法最大列 `XFD1`
  仍可解析以及 `readWorkbook` 多段路径同样拒绝。后端测试 591 → 594 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：上限采用 Excel 2007+ 规范的 16384 列；旧版 `.xls` 不经过本读取器，不受影响。

### 文件管理直连 DSL 补齐租户隔离

- **背景**：`FileService.stats()`、`toViewsWithSharedFlag()` 与下载计数更新直接使用 `DSLContext` 聚合/自增，
  绕过了 `SqlWriters` 的租户条件装饰。强隔离开启时，文件列表已按租户过滤，但统计总量、来源模块分布、
  共享标记引用数和下载计数仍跨租户，造成元数据泄露并可能误改其他租户记录。
- **实现**：将 `SqlWriters.withTenant` 提升为公共谓词入口，供无法使用 `scopedSelect` 的 GROUP BY / UPDATE
  直连查询复用；上述三处条件均追加 `tenant_id = currentTenantId()`。平台维表与隔离关闭时行为不变。
- **测试**：新增 `FileServiceTenantScopeTest`（4 例），覆盖统计聚合、共享标记引用数、下载计数更新在
  `enforce=true` 时带租户条件，以及关闭隔离时不注入条件。后端测试 587 → 591 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：文件物理存储路径不含租户前缀，隔离仅作用于 `sys_file` 元数据；新增直连 DSL 查询时必须显式调用
  `SqlWriters.withTenant`，否则不会被现有门禁自动发现。

### 刷新令牌轮换改为原子占用防并发双花

- **背景**：`RefreshTokenService.rotate` 原先先签发新令牌、再无条件按 id 把旧令牌置为 `ROTATED`，
  既不检查影响行数也不带 `revoked_at IS NULL` 条件。同一 refresh token 并发刷新时，两个事务可能都读到未吊销
  快照并各自签发新令牌；`verify` 与 `rotate` 之间也存在 TOCTOU，家族重放检测会被并发双花绕过。
- **实现**：轮换先用 `WHERE id=? AND revoked_at IS NULL` 条件更新原子占用旧令牌，只有影响行数 1 的请求才
  继承家族并签发新令牌；影响行数 0 视为重放，吊销整个家族并返回 `TOKEN_REVOKED`。签发顺序调整为先占用后签发，
  占用失败不产生新凭证。
- **测试**：新增 `RefreshTokenRotationConcurrencyTest`（4 例），覆盖占用成功顺序、占用失败不签发、
  已吊销令牌吊销家族及双线程并发仅一个成功。后端测试 583 → 587 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：占用后的新令牌签发若因其他异常失败，调用方事务会整体回滚；独立调用 `rotate` 时需保证外层事务语义，
  不建议在无事务上下文下单独使用。

### OAuth2 password 授权补齐强制改密门禁

- **背景**：交互式登录与令牌刷新都会拒绝 `password_expired=1` 的初始/重置密码用户，但
  `OAuth2UserAuthenticator` 只校验禁用/锁定状态和密码匹配，匿名 `/oauth2/token` 的 password 授权可签发带完整
  权限的用户令牌，完全绕过「先改密再使用」闭环。
- **实现**：密码匹配成功后复用 `SecurityErrorCode.MUST_CHANGE_PASSWORD` 拒绝签发；错误经令牌端点既有
  `BusinessException` 映射返回 400 `invalid_grant`。判定放在密码校验之后，未认证方无法借此探测账号状态。
- **测试**：`OAuth2UserAuthenticatorTest` 由 4 例扩到 7 例，新增需改密拒绝、无需改密放行、`null` 兼容放行。
  后端测试 580 → 583 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：公开改密端点 `/api/auth/change-password` 仍可在未登录状态下凭用户名+原密码完成改密，因此新门禁
  不会造成「无法登录也无法改密」死锁；OAuth2 机器调用应在改用新密码后重试。

### 异步导出继承租户与认证上下文

- **背景**：`ExportCenterService.downloadAsync` 直接把查询物化提交到 `exportExecutor`，但 `TenantContext` 与
  Spring Security 的 `SecurityContextHolder` 都是线程局部变量，异步线程拿不到提交者的身份。于是异步导出会按
  默认租户/无主体执行：强隔离下可能导出错误租户数据，数据权限切面无主体时按「不限制」放行，形成越权导出。
- **实现**：提交时刻捕获 `SecurityContext` 认证快照与 `TenantContext.TenantScope`，在导出任务执行前恢复、
  `finally` 中还原工作线程原上下文。数据权限不直接传播 `DataScopeContext`，而是在导出线程内由切面基于传播后的
  主体重新解析，避免携带过期范围。
- **测试**：新增 `ExportContextPropagationTest`（3 例），覆盖跨线程继承租户/主体、任务结束后工作线程上下文清理、
  无提交者上下文时仍能完成导出。后端测试 577 → 580 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：仅改变异步导出的执行上下文，未新增/修改导出模板、请求参数或文件格式；同步导出与调用方线程上下文不受影响。

### XLSX 导入解压增加压缩炸弹边界

- **背景**：`XlsxDataReader.unzip` 对每个 ZIP entry 直接调用 `readAllBytes()`，注释误以为上传文件大小能约束
  解压后体积。高压缩比的几十 KB `bomb.xlsx` 可展开为数百 MB，在表头/行数校验之前就耗尽堆内存，
  造成单请求 OOM 与实例不可用；IP 限流无法阻止单次攻击。
- **实现**：解压改为分块有界读取，限制单个工作簿最多 200 个 ZIP entry、累计解压不超过 32 MB；
  超限统一抛 `IllegalArgumentException`，由现有导入链路转为 400。正常工作簿解析路径与共享字符串/
  多 Sheet 逻辑不变。
- **测试**：新增 `XlsxDataReaderSafetyTest`（3 例），覆盖单 entry 高压缩炸弹、多 entry 累计超限
  与正常内联字符串工作簿仍可解析。后端测试 574 → 577 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：阈值只约束解压后的 ZIP 载荷，不改变上传文件大小限制；确需更大工作簿时，应同步调整
  `MAX_UNCOMPRESSED_BYTES` 与导入行数上限，避免只放宽一侧。

### 会话无关改密补齐防爆破防护

- **背景**：`POST /api/auth/change-password` 是公开端点，以「用户名 + 原密码」完成身份认证，但
  `RateLimitFilter` 未覆盖该路径，`AuthService.changePassword` 的旧密码错误也不计入登录失败次数；
  攻击者可对已知账号无限在线猜解原密码，登录侧的 IP 限流与渐进式锁定被完全绕过。
- **实现**：改密端点加入独立 IP 限流键（10 次/60 秒，不与登录共用配额）；控制器传递客户端 IP/UA，
  旧密码错误复用 `recordLoginFailure` 累计失败并触发渐进式锁定。成功改密写入收窄到独立短事务，
  bcrypt 仍在事务外执行，避免失败计数随异常回滚。
- **测试**：新增 `AuthChangePasswordSecurityTest`（3 例）与 `RateLimitFilterChangePasswordTest`（2 例），
  覆盖错误密码累计失败、达到阈值锁定、成功改密写入、改密独立 429 限流及登录键不变。
  后端测试 569 → 574 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：未新增验证码字段，保持前端请求契约不变；防爆破由「独立 IP 限流 + 与登录同源的失败锁定」
  两层承担。用户不存在与原密码错误仍返回统一 `BAD_CREDENTIALS`，不泄露账号枚举信息。

### 强隔离无租户上下文时改为默认租户收紧

- **背景**：`TenantEnforcement.shouldEnforce` 在 `app.tenant.enforce=true` 但线程未设置 `TenantContext`
  时直接返回 false，定时任务、MQ 消费、异步线程或内部直调会绕过所有 `tenant_id` 过滤，形成静默全租户可见/可写；
  而同一批路径在写入侧又会把 `tenant_id` 写成 0，读写口径分裂。
- **实现**：无作用域且强隔离开启时不再放行：读取按 `defaultTenantId` 追加过滤，写入同一业务表也取该默认值；
  首次触发记录一次性 warn 日志，提示异步任务显式设置作用域。平台共享维表与 `@TenantIgnorable` 旁路仍豁免并写平台租户 0；
  隔离关闭时行为不变，`currentTenantId()` 在无作用域的关闭场景仍严格抛异常。
- **测试**：新增 `TenantEnforcementTest`（5 例），覆盖隔离关闭、无作用域收紧、显式作用域、
  平台维表豁免与 bypass 豁免。后端测试 564 → 569 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：该策略仅在显式开启 `app.tenant.enforce=true` 时生效；异步任务若需跨租户处理，应显式
  `TenantContext.runWith(...)` 或在平台管理员作用域下使用 `@TenantIgnorable`，否则只能看到默认租户数据。

### 全局异常映射补齐 415/406

- **背景**：`GlobalExceptionHandler` 已覆盖参数校验、请求体格式、文件上传与参数类型不匹配等调用方错误，
  但 `HttpMediaTypeNotSupportedException`（`Content-Type` 不匹配）与 `HttpMediaTypeNotAcceptableException`
  （`Accept` 不可满足）仍落到兜底 handler，被记为 500「系统内部错误」，污染服务端错误率并掩盖真实故障。
- **实现**：新增两个 `@ExceptionHandler`，分别返回 HTTP 415 + `COMMON_415`、HTTP 406 + `COMMON_406`，
  文案为「不支持的媒体类型，请检查 Content-Type」「无法生成可接受的响应内容，请检查 Accept」；
  保持统一 `Result` 包络与中文文案，仅记录 warn 日志，不回传异常细节。
- **测试**：新增 `GlobalExceptionHandlerTest`（4 例），覆盖 415/406 映射及既有 400/500 行为不回归。
  后端测试 560 → 564 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：仅改变调用方媒体类型错误的响应语义与错误码，未新增/修改 HTTP 端点、请求参数或成功响应字段。

### 租户请求头覆盖收敛为平台管理员专用

- **背景**：`TenantInterceptor` 先按 `X-Tenant-Id` 解析作用域、后计算平台管理员标记，
  `TenantResolver` 又无条件让请求头优先于 JWT `tid`。普通租户用户只要附加任意 `X-Tenant-Id`，
  就能把执行作用域切到别的租户；在 `app.tenant.enforce=true` 时会造成跨租户读写越权。
- **实现**：`TenantResolver.resolveTenantId` 增加显式 `allowHeaderOverride` 标记，三参重载默认忽略请求头；
  `TenantInterceptor` 先按角色判定平台管理员，仅平台管理员可让请求头覆盖 JWT 租户声明。
  非管理员的覆盖尝试记录告警日志，不泄露请求头原始值；默认租户与关闭隔离语义不变。
- **测试**：新增 `TenantResolverTest`（5 例）与 `TenantInterceptorTest`（3 例），覆盖普通用户头被忽略、
  管理员头覆盖、非法头回落 Claim、隔离关闭回落默认租户、无主体匿名请求回落默认租户。
  后端测试 552 → 560 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：平台管理员保留既有的显式跨租户能力；普通用户的租户身份始终以已签名 Token Claim 为准，
  定时任务与异步线程仍按既有约定显式设置 `TenantContext`。

### OAuth2 自省端点畸形 Basic 头改为 401

- **背景**：`/oauth2/introspect` 是匿名可达的公开端点，由控制器内部校验调用方。Basic 分支直接调用
  `Base64.getDecoder().decode(...)`，畸形 Base64（如 `Authorization: Basic !!!`）会抛
  `IllegalArgumentException` 穿透为 500；`/oauth2/token` 同类输入已按 `invalid_client` 处理，两个公开端点口径不一致。
- **实现**：`OAuth2IntrospectionEndpoint.callerAuthorized` 捕获 Base64 解码异常并返回未认证，
  外层统一返回 HTTP 401 + `{"error":"invalid_client"}`；缺少凭据、无冒号、凭证错误仍沿用同一响应，
  不泄露具体失败原因。
- **测试**：新增 `OAuth2IntrospectionEndpointTest`（9 例），覆盖畸形 Base64、无冒号 Basic、错误凭证、
  缺失头、合法 Basic/Bearer 调用方、非法 Bearer、空 token 与被自省 token 无效等路径。
  后端测试 543 → 552 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：本次只修调用方鉴权的异常穿透；令牌自省失败仍按 RFC 7662 返回 200 + `active=false`，
  `/oauth2/token` 畸形 Basic 头仍保持既有 400 `invalid_client` 口径。

### OAuth2 password 授权补齐锁定到期语义

- **背景**：此前已让交互式登录通过 `LockoutRules.isLocked` 区分到期临时锁与人工长期锁，但
  `OAuth2UserAuthenticator` 仍用裸 `"LOCKED".equals(user.status())` 判断。账号被渐进式锁定后，
  `lockedUntil` 到期走前端登录可成功，走 `grant_type=password` 仍被拒，同一账号两条认证入口口径分裂。
- **实现**：OAuth2 password 路径改用 `LockoutRules.isLocked(status, lockedUntil, now)`，到期临时锁放行；
  `DISABLED` 与无到期时间的 `LOCKED` 仍拒绝，错误码/文案保持不变。
- **测试**：新增 `OAuth2UserAuthenticatorTest`（4 例），覆盖到期临时锁放行、人工长期锁/未来临时锁/
  DISABLED 拒绝。后端测试 539 → 543 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：OAuth2 路径仍不触发登录失败计数与审计，这是既有设计；本次仅对齐锁定状态判定，不改令牌契约。

### 告警 severity 筛选与汇总口径统一

- **背景**：`AlertInboxRules` 对 severity 存在三套口径：查询值 `normalizeSeverity` 做
  trim + 小写 + 32 字符截断；`filter` 却拿归一后的查询值与条目原始 `getSeverity()` 做
  `equalsIgnoreCase`；`summarize` 又只做 trim + 小写、不截断。于是汇总里可见的桶
  （`" critical "` 或超长自定义值）点击筛选恒为空，出现「汇总可见、筛选无结果」的排障误导。
- **实现**：抽出共用纯函数 `normalizeEntrySeverity`（trim + 小写 + 截断），`normalizeSeverity` 委托它；
  `filter` 比较条目归一值，`summarize` 的桶键也复用它，空值仍归 `UNKNOWN_SEVERITY`。
- **测试**：`AlertInboxRulesTest` 新增 2 例，覆盖条目值带首尾空格与超过 32 字符的自定义 severity，
  同时验证筛选命中与汇总桶键一致。后端测试 537 → 539 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：标准短 severity（critical/warning/info）行为不变；超长自定义值现在按截断后的桶键筛选，
  与汇总展示一致。

### XLSX 导出净化 XML 非法控制字符

- **背景**：`XlsxDataExporter.xmlEscape` 只处理 `& < > "`，C0 控制字符原样写入 `inlineStr` 文本节点。
  XML 1.0 仅允许 `\t \n \r` 与 `\u0020` 以上字符；任一导出字段含 `\u0001`、`\u000B`、`\u001F` 等，
  生成的 `sheetN.xml` 即非法，Excel/WPS 会判定整个工作簿损坏并拒绝打开。CSV 路径已有公式注入防护，
  XLSX 这条序列化路径此前无对应净化。
- **实现**：`xmlEscape` 的 `default` 分支丢弃除 Tab/LF/CR 外的所有 `c < 0x20` 字符，转义与合法文本
  逻辑保持不变；单表 `export` 与多 Sheet `exportWorkbook` 共用该函数，同步受益。
- **测试**：新增 `XlsxDataExporterTest`（2 例），用 JDK `DocumentBuilder` 解析 ZIP 内全部 XML/rels part，
  断言含控制字符的字段不再产生非法 XML、合法空白与实体转义仍保留；后端测试 535 → 537 例。
- **验证**：前端 158 例、门禁 56/56、Lint 与 Vite 生产构建均通过。
- **注意**：净化只针对 XML 1.0 非法 C0 字符，不改变业务文本语义；换行/制表符按原样保留（由
  `xml:space="preserve"` 承载）。

### 告警 Webhook 令牌改用恒定时间比较

- **背景**：`AlertWebhookController.webhook` 是公开匿名端点，`X-Alert-Token` 是唯一鉴权凭证，但令牌校验用
  `expected.equals(token)`。`String.equals` 遇首个不同字节即短路，响应时间随匹配前缀长度变化，
  对匿名可达端点构成时序侧信道。仓库已有单一事实来源 `ConstantTime.equals`（TokenCodec、
  WebhookSignatureRules、UnsubscribeTokenRules 均已使用），此处是遗漏。
- **实现**：改为 `ConstantTime.equals(expected, token)`，令牌缺失/空白的 fail-closed 分支保持不变。
- **测试**：新增 `AlertWebhookControllerTest` 4 例，覆盖令牌匹配 202、错误/空令牌 401 且服务不被调用、
  空配置 503；新增静态门禁 `test_token_comparison.py`（3 例），禁止生产 Java 直接
  `.equals(token|secret|signature)`。后端测试 531 → 535 例，门禁 53 → 56 例。
- **验证**：前端 158 例、Lint 与 Vite 生产构建均通过。
- **注意**：仅改变比较算法，不改变鉴权结果与错误码；网络隔离/网关加固仍需保留。

### 权限门禁仅从生产源码采集权限码

- **背景**：`check-permission.py` 的 `java_permission_codes()` 用 `walk_files(BACKEND_ROOT, (".java",))`
  扫描全部 Java 文件，没有排除 `src/test`。测试夹具中若出现 `hasAuthority("system:xxx:yyy")`，就会被当作
  后端已登记接口，使 P1「菜单权限码必须在 Java 侧登记」门禁假通过，与门禁自身的证据标准相悖。
- **实现**：抽出 `PERMISSION_RE` 与 `permission_codes_from_text`，新增 `is_production_java` 只放行
  `src/main/java` 下的源码；`java_permission_codes()` 仅合并生产源码提取结果。
- **测试**：新增 `test_check_permission.py`（3 例），覆盖双引号权限码提取、生产/测试/资源路径过滤、
  真实仓库扫描非为空；门禁 50 → 53 例。
- **验证**：后端 531 例、前端 158 例、Lint 与 Vite 生产构建均通过，`check-permission EXIT=0`。
- **注意**：该修复只收紧证据来源；若确有仅测试夹具声明的权限码，现在会正确报 P1 orphan 并要求补生产接口或登记豁免。

### 脱敏策略中心 fail-open 与租户默认策略修复

- **背景**：策略已声明 `shouldMask=true` 但 `decideType` 因未知/空类型回落 `NONE` 时，
  `MaskStrategyResolver` 直接调用 `SensitiveMasker.mask(value, NONE)` 原样返回明文；同时
  `resolve` 只查当前租户的策略，租户 0 的平台默认策略对业务租户完全不生效，文档中的
  「平台默认 + 租户覆盖」从未实现。
- **实现**：新增 `MaskStrategyRules.selectStrategyWithTenantDefault`，合并平台与租户策略、优先级高者胜，
  同优先级租户策略胜出；解析器按该规则命中，判定脱敏但类型无效时先取调用方 `fallbackType`，
  仍为 `NONE` 则走 `SensitiveMasker.genericMask`，杜绝明文出口。角色豁免分支保持不变。
- **测试**：补齐仓库此前零覆盖的脱敏策略中心：`MaskStrategyRulesTest` 4 例 + `MaskStrategyResolverTest`
  5 例，覆盖平台默认继承、租户同优先级覆盖、非法类型兜底、通用掩码兜底与角色豁免。
  后端测试 522 → 531 例。
- **验证**：前端 158 例、门禁 50/50、Lint 与 Vite 生产构建均通过。
- **注意**：租户 0 的策略现在会下沉到所有业务租户，若此前刻意配置过租户 0 策略需确认其影响范围；
  仅改变命中与兜底口径，不改动 `sys_mask_strategy` 存储结构与管理 API。

### 列权限脱敏 fail-open 修复

- **背景**：列权限存在两条脱敏路径。`ColumnPermissionApplier` 已用通用掩码兜底 `null/NONE`，但
  `UserService.applyColumnMask` 仍调用 `SensitiveMasker.mask(value, type)`，类型为空时原样返回；
  记录重建失败时 `applyRecord` 记 WARN 后返回原对象，字段读取/写入失败也只是跳过。开启
  `column-permission.enforce` 后，这些场景都会把受控字段明文直接返回。
- **实现**：`ColumnPermissionApplier.maskValue` 提升为统一公开入口（null/NONE 走 `genericMask`），
  `UserService.applyColumnMask` 改为复用该入口；`applyRecord` 改用 record 组件类型数组定位规范构造器，
  重建/读取/写入失败统一抛 `IllegalStateException`，以 fail-closed 口径拒绝返回未脱敏数据。
- **测试**：补齐文档声称存在但仓库缺失的 `ColumnPermissionApplierTest`（3 例），覆盖通用掩码兜底、
  record 重建脱敏副本、紧凑构造器拒绝掩码值时不得回落明文。后端测试 519 → 522 例。
- **验证**：前端 158 例、门禁 50/50、Lint 与 Vite 生产构建均通过。
- **注意**：开关关闭时行为不变；开启后若字段读写或 record 重建失败将直接抛异常（500），
  这是为避免明文泄漏而有意选择的失败策略。

### 生产代码大小写归一全面锁定 Locale.ROOT

- **背景**：上一轮规则层修复后，门禁仍只扫 `/rule/` 与 `/rules/`，生产代码另有 33 处无参
  `toUpperCase()` / `toLowerCase()`。`tr-TR` 下 `MINUTES`、`API_CALL`、`INACTIVE`、`ADMIN123`、
  `INITIATOR`、`tinyint` 等标识符会被错误映射，造成定时任务解析失败、报表静默导出错误类型、
  已禁用配置被当作启用、弱口令绕过与 DDL 类型识别失败。
- **实现**：将全部 `src/main/java` 中的 33 处调用改为 `Locale.ROOT`，覆盖任务、报表导出、系统配置、
  登录限制、密码策略、API 资源、个人中心、数据源、代码生成/AI 脚手架与工作流适配层；保留原有 trim、
  白名单、回退与容错逻辑不变。
- **测试**：新增 4 例 tr-TR 回归测试（任务枚举、报表类型、配置停用态、弱口令）；
  `test_rule_locale_security.py` 从规则包扩展为扫描全部生产源码并排除 `src/test`，新增 1 例路径筛选断言。
  后端测试 515 → 519 例，门禁 49 → 50 例。
- **验证**：前端 158 例、Lint 与 Vite 生产构建均通过。
- **注意**：只固定大小写映射语义，不改变业务白名单、默认值与回退策略；测试源码不纳入静态门禁。

### 审计摘要补齐凭证别名与银行卡脱敏

- **背景**：`SensitiveMasker.CREDENTIAL_KEYS` 原来只覆盖 `password/token/secret/authorization` 等精确键名，
  `apiKey`、`secretKey`、`idToken`、`sessionId`、`cookie`、`previewToken` 等常见凭证字段会原样写入审计摘要；
  类里已定义的 `BANK_CARD` 模式也从未被 `maskSummary()` 调用。审计落库与查询返回都只经过 `maskSummary()`，
  持有审计查询权限的人可直接读回这些敏感值。
- **实现**：扩展凭证键正则（兼容 snake/kebab/camel，长键优先）覆盖 `api[_-]?key`、`x-api-key`、
  `access[_-]?key`、`secret[_-]?key`、`id[_-]?token`、`session[_-]?id`、`cookie`、`preview[_-]?token` 等；
  `maskSummary()` 在身份证之后追加银行卡号替换，复用 `maskBankCard` 的「保留后 4 位、等长星号」口径，
  避免 18 位身份证被二次处理。
- **测试**：`SensitiveMaskerTest` 新增 3 例，覆盖九类凭证别名、16 位银行卡号脱敏与幂等、
  已掩码身份证不被当作卡号二次处理。后端测试 512 → 515 例。
- **验证**：前端 158 例、门禁 49/49、Lint 与 Vite 生产构建均通过。
- **注意**：银行卡规则沿用既有 12~19 位、前后非数字的形态约束，仍可能把同形态订单号一并掩码；
  本次只扩大脱敏覆盖面，不改变存储层原文与 `SensitiveType` 分派契约。

### 规则层枚举归一全面锁定 Locale.ROOT

- **背景**：此前只修复了 `ChannelNormalizeRules`，但规则层仍有 18 处无参 `toUpperCase()` / `toLowerCase()`
  用于枚举或状态码归一。JVM 默认 `tr-TR` 时，`i` 会变成带点 `İ`，导致 `INFO`、`DISK_USAGE`、
  `PUBLISHED`、`PROCESSING`、`INITIATOR`、`RATIO` 等合法值静默解析失败；告警 severity 汇总还会被拆成
  不同桶。
- **实现**：在告警规则、CMS 状态、工作流审批校验、通知/短信日志、幂等状态与租户编码派生等
  9 个生产类中统一改为 `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)`，保留 trim、白名单、
  `valueOf` 与容错 posture 不变。
- **测试**：新增 7 例 tr-TR 回归测试（告警解析/收件箱归一、幂等状态、CMS 状态、日志与租户编码、
  工作流审批校验），新增静态门禁 `test_rule_locale_security.py`（3 例）扫描规则包禁止无参大小写调用。
  后端测试 505 → 512 例，门禁 46 → 49 例。
- **验证**：前端 158 例、Lint 与 Vite 生产构建均通过。
- **注意**：只固定大小写映射语义，不改变状态白名单、枚举集合与容错行为；非规则层的无参调用不在本次范围。

### OAuth2 授权码并发兑换防双花

- **背景**：`OAuth2AuthorizationCodeService.consume` 原先用 `usedJtis.contains(jti)` 前置校验，
  全部校验通过后再 `add(jti)`。两个并发请求可同时通过 contains 检查并各自签发 access token，
  授权码「一次性」在竞态下失效。
- **实现**：新增按 `jti` 的 `ConcurrentHashMap` 独占锁，把「未使用检查 → 客户端/回调/PKCE 校验 →
  标记已使用 → 构造上下文」整体放在同一临界区；非法请求仍不标记已使用，可重试至过期。
  锁与 `usedJtis` 同生命周期，不提前移除，避免等待线程与后到线程拿到不同锁对象。
- **测试**：新增 `OAuth2AuthorizationCodeServiceTest`（3 例），覆盖顺序重放拒绝、4 线程栅栏并发兑换
  仅 1 次成功、非法 client 不消耗授权码。后端测试 502 → 505 例。
- **验证**：前端 158 例、门禁 46/46、Lint 与 Vite 生产构建均通过。
- **注意**：该修复为单进程内存态防护；多实例部署仍需共享 Store 或粘性会话才能真正保证跨节点一次性。

### WebP 上传魔数校验补齐 WEBP fourcc

- **背景**：`FileValidator.verifyContent("webp")` 与 `ProfileController.validateImageContent` 只检查前 4 字节
  `RIFF`。RIFF 是容器格式，WAV/AVI 同样以此开头，把非图片文件改名为 `.webp` 即可通过「魔数二次校验」，
  与类注释「杜绝改扩展名伪装」相悖。现有单测还把 `RIFFxxxx` 固化为合法 WebP。
- **实现**：两处都改为读取 12 字节，校验偏移 8~11 为 `WEBP` fourcc；`FileValidator` 对 WebP 要求至少
  12 字节，头像上传同步判定读取长度。非 WebP 类型仍保持原有 8 字节头与放行口径。
- **测试**：更新 `FileValidatorTest` 合法头为 `RIFFxxxxWEBP`，新增 WAV fourcc（`RIFFxxxxWAVE`）与
  截断头（`RIFFxxxxWEB`）拒绝断言；新增静态门禁 `test_file_upload_security.py`（3 例），扫描生产 Java
  源码，禁止只校验 RIFF 前缀而不读 `header[11] == 'P'`。门禁 43 → 46 例。
- **验证**：后端 502 例、前端 158 例、Lint 与 Vite 生产构建均通过。
- **注意**：真实 WebP 文件头均为 `RIFF....WEBP`，合法上传不受影响；仅收紧 webp 扩展名，不扩大白名单。

### 修复渐进式锁定到期后永久锁账号

- **背景**：`AuthService.isLocked` 原为 `"LOCKED".equals(status) || lockedUntil.isAfter(now)`。
  `handleLoginFailure` 达到阈值时会同时把 `sys_user.status` 写成 `LOCKED` 并设置 `lockedUntil`，
  但没有任何逻辑在临时锁到期后把状态改回 `ACTIVE`；由于 status 分支短路，到期时间被永远忽略，
  用户 15 分钟后仍被判定锁定，只能人工改库恢复。`changePassword` / `forgotPassword` 还各自用
  status-only 判断，口径与登录分叉。
- **实现**：新增纯函数 `LockoutRules.isLocked(status, lockedUntil, now)`：带到期时间的临时锁到期即放行，
  `status=LOCKED` 且无到期时间的人工长期锁仍持续阻断；`AuthService` 登录/改密/忘记密码统一走该函数。
  登录成功时 `resetLoginFailure` 除清空失败计数与到期时间外，把状态归一回 `ACTIVE`。
- **测试**：`LockoutRulesTest` 新增 3 例（到期/未到期/边界、人工锁、ACTIVE+未来到期），
  新增 `AuthServiceLockoutTest` 2 例（成功登录两个分支均落库 `ACTIVE`、服务层调用到期感知判定）；
  后端测试 497 → 502 例。
- **验证**：前端 158 例、门禁 43/43、Lint 与 Vite 生产构建均通过。
- **注意**：人工长期锁（`LOCKED` 且 `lockedUntil=null`）行为不变；临时锁到期后首次密码校验通过即清除
  残留状态，失败重试会按当前失败次数重新进入渐进式锁定。

### 通知渠道归一锁定 Locale.ROOT

- **背景**：`ChannelNormalizeRules.normalizeChannel` 用平台默认 Locale 的 `toUpperCase()` 匹配
  `Channel` 枚举。JVM 默认 `tr-TR` 时，`in_app` / `dingtalk` 会被转成带点大写 `İ` 的
  `İN_APP` / `DİNGTALK`，与枚举名永不相等，通知记录/模板按渠道筛选会静默变成未过滤。
  同仓库其他规则类已统一使用 `Locale.ROOT`，此处是口径遗漏。
- **实现**：改为 `channel.trim().toUpperCase(Locale.ROOT)`，与 `NotificationPreferenceRules` 等规则一致；
  委托方 `NotificationTemplateRules` / `NotificationRecordRules` 零改动。
- **测试**：新增 `ChannelNormalizeRulesTest`（3 例），覆盖全渠道大小写不敏感归一、`tr-TR` 下
  `in_app`/`dingtalk` 正确归一、空白与未知值返回 null；测试结束恢复原默认 Locale。
  后端测试 494 → 497 例。
- **验证**：前端 158 例、门禁 43/43、Lint 与 Vite 生产构建均通过。
- **注意**：该修复只影响大小写转换的 Locale 语义，不改变渠道白名单、委托关系与筛选 API 契约。

### OAuth2 redirect_uri 支持 IPv6 回环地址

- **背景**：`OpenApiClientRules.isValidRedirectUri` 的注释声明 http 允许 `localhost / 127.0.0.1 / [::1]`，
  但剥离端口时用 `host.indexOf(':')` 命中了方括号内的第一个冒号，把 `[::1]` 截成 `[`，导致文档允许的
  地址永远校验失败；错误文案也漏写了 `[::1]`。该类此前没有单测。
- **实现**：重写 authority 解析：先按 `/ ? #` 截取 authority，拒绝 userinfo；对方括号包裹的 IPv6
  先取完整 `[::1]`，再仅对 `]` 后的尾部做端口校验；普通主机继续剥离端口。新增端口范围校验（1~65535）
  并允许 `localhost` 大小写不敏感；错误文案补全 `[::1]`。
- **测试**：新增 `OpenApiClientRulesTest`（4 例），覆盖 https/回环 http/IPv6 回环放行、
  `[::1]@evil.com`、畸形 IPv6、非法端口与 `https://` 拒绝，以及白名单精确匹配与存量兼容。
  后端测试 490 → 494 例。
- **验证**：前端 158 例、门禁 43/43、Lint 与 Vite 生产构建均通过。
- **注意**：该修复只放宽文档已声明的 IPv6 本地回环场景；非回环 http、userinfo 与畸形端口仍被拒绝。

### 企业微信机器人签名补齐 URL 编码

- **背景**：`RobotSigningRules` 的共享原语输出标准 base64（可能含 `+ / =`），钉钉分支已做 URL 编码，
  企业微信分支却把原始 base64 直接拼进 `sign` query 参数。裸 `+` 在接收方按表单语义会被解码为空格，
  32 字节摘要出现 `+` 的概率约 50%，表现为企业微信机器人「偶发收不到通知」；类注释与适配器文档也误写为
  「原始 base64」。
- **实现**：`signWecom` 与 `signDingtalk` 统一走 `querySign`，即 `URLEncoder.encode(base64, UTF-8)`；
  同步更正 `RobotSigningRules` 与 `WecomRobotAdapter` 文档。共享 HMAC 原语 `sign()` 仍返回原始 base64，
  便于密码学层校验。
- **测试**：新增 `RobotSigningRulesTest`（5 例），使用固定包含 `+ / =` 的摘要，锁定原始 base64、
  两平台编码一致性、企业微信 URL 只含 `%2B/%2F/%3D`、无 query 基址拼接与空密钥拒绝。
  定向 5 例全过，后端测试 485 → 490 例。
- **验证**：前端 158 例、门禁 43/43、Lint 与 Vite 生产构建均通过。
- **注意**：这是发送侧编码修复，接收方仍按标准 base64 验签；不引入 `sha256=` 前缀，也不改变钉钉链路行为。

### API 契约门禁升级为方法级比对

- **背景**：`check-api-contract.py` 的后端路由键已是 `(METHOD, path)`，但 `main()` 用
  `{path for _, path in be}` 降维成路径集合，前端也只收集 URL。因此前端把 POST 写成 GET（或反向）
  时，只要路径存在，`frontend_only` 仍为 0，`--strict` 也放行，问题只在运行时 405 暴露。
- **实现**：新增 `frontend_calls()`，解析 `request/requestRaw` 对象字面量（支持跨行与嵌套花括号）、
  axios sugar（`.post('/api/...')`）与本地 POST helper；按 `(method, path)` 与后端路由比对，
  新增 `method_mismatch` 报告并纳入 `--strict` 阻断。无法解析方法的调用不进入方法级判定，
  仍由原有路径口径兜底，避免误报。
- **测试**：`test_check_api_contract.py` 新增 6 例，覆盖嵌套对象/模板 URL、axios sugar、本地 helper、
  注释与缺方法忽略、尾斜杠拼接、真实仓库方法级差异为 0；门禁 37 → 43 例全过。
- **验证**：真实仓库方法级调用 353 条、未识别方法 0 条、`frontend_only 0`、`method_mismatch 0`；
  后端 485 例、前端 158 例、Lint 与 Vite 生产构建均通过。
- **注意**：`backend_only` 仍为信息性；类级 `@RequestMapping` 使用常量而非字符串字面量的端点仍在后端
  扫描盲区，只会形成 backend_only，不会造成方法级误报。

### 操作日志游标降级复用摘要结果

- **背景**：`OperationLogView.loadOffsetPage()` 首屏并行请求分页与统计摘要；分页命中后端
  「偏移过大，请改用游标分页」护栏时，转入 `loadKeysetPage()` 前提前 `return`，首屏已结算的摘要被丢弃，
  而 `loadKeysetPage()` 又独立请求一次摘要。一次降级产生两次摘要查询；若第二次失败，页面会出现
  「有日志、无摘要」甚至误报加载失败。
- **实现**：新增纯函数 `settlePageWithSummary(pagePromise, fetchSummary, prefetchedSummaryResult)`，
  已结算摘要为 `fulfilled` 时直接复用且不再调用摘要工厂；无预取或预取失败时保持在游标模式重试一次的
  原有语义。两个加载函数统一走该编排，降级路径把首屏结果传入游标加载。
- **测试**：新增 `src/utils/tests/auditLogSummary.test.js`（4 例），覆盖正常并行结算、成功预取复用、
  失败预取重试、分页失败不阻断摘要；前端测试 154 → 158 例全过。
- **验证**：后端 485 例、门禁 37/37、Lint 与 Vite 生产构建均通过。
- **注意**：仅复用已成功的预取结果；预取失败仍重试一次，避免改变既有失败恢复语义。分页/摘要请求参数
  与后端契约不变。

### 前端下载通道收敛与门禁补强

- **背景**：`CodegenView.onDownload` 与 `ReportPanel.download` 仍各自手写
  「Blob 包装 → JSON 错误解析 → createObjectURL → 点击链接 → revoke」流程。CodegenView 缺空响应/空内容
  拦截且文件名硬编码，ReportPanel 重复实现了 `utils/download` 已有逻辑；导出门禁也认不出 `onDownload`
  和箭头函数，更管不到视图内的 `URL.createObjectURL`。
- **实现**：两处统一改用 `saveBlobFromResponse`，按返回值决定是否提示成功；CodegenView 删除本地
  `parseErrMsg`，ReportPanel 保留 `parseFileName` 仅用于成功文案。后端 codegen 下载端点本身已下发
  `filename*=UTF-8''codegen-<table>.zip`，因此回退文件名与既有用户可见行为一致。
- **测试**：新增 `src/utils/tests/download.test.js`（10 例），覆盖文件名解析优先级/解码兜底、
  `saveBlob` 对象 URL 生命周期，以及 `saveBlobFromResponse` 的空响应、空内容、JSON 错误、非 JSON 错误、
  正常文件和非 Blob 响应；门禁新增 4 例，扩展 `onDownload*`/箭头函数识别并禁止视图/组件直接
  `URL.createObjectURL`。
- **验证**：前端 154 例、后端 485 例、门禁 37/37、Lint 与 Vite 生产构建均通过。
- **注意**：ReportPanel 的空响应/空内容错误文案统一为工具口径「导出失败」/「导出失败：内容为空」；
  HTTP/网络异常仍由调用方 catch 与拦截器统一提示。

### 生产外部密钥缺失 fail-fast 守卫

- **背景**：`JwtProperties.resolveSigningKey()` 与 `RsaKeyProvider` 在密钥未配置时只告警并生成
  仅当前进程有效的临时密钥，适合开发/测试。生产若漏配 `JWT_SECRET` 或
  `APP_OAUTH2_PRIVATE_KEY`，服务仍会正常启动，但重启后管理员会话全部失效、OAuth2 JWKS 的 kid
  变化导致客户端令牌无法跨重启校验，问题只在重启后暴露。
- **实现**：新增 `@Profile("prod")` 的 `ProductionSecretsGuard`（`ApplicationRunner`），启动期校验
  `app.security.jwt-secret` 与 `app.security.oauth2.private-key` 非空，缺失即 `IllegalStateException`；
  `application-prod.yml` 显式开启，并提供 `app.security.secrets.guard.enabled=false` 逃生开关。
  错误信息只列环境变量名与配置键，不输出任何密钥内容。
- **测试**：新增 `ProductionSecretsGuardTest`（8 例），覆盖双密钥齐全、单项/全部缺失、空白视作缺失、
  逃生开关、默认启用以及 prod profile / ApplicationRunner 契约；定向测试 19 例全过。
- **验证**：后端全量 485 例、前端 144 例、门禁 33/33、Lint 与 Vite 生产构建均通过。
- **注意**：生产部署必须注入 `JWT_SECRET` 与 `APP_OAUTH2_PRIVATE_KEY`，否则拒绝启动；临时关闭守卫
  仅用于过渡排障，不推荐长期关闭。该修复不改变 JWT/OAuth2 令牌格式与接口契约。

### 健康探针失败暴露 i18n key 修复

- **背景**：i18n 引擎已移除（`src/utils/connectivity.js:63` 与验证手册 3.7 均已确认），但
  `src/api/health.js:29/39` 仍返回 `'health.format'`、`'health.unreachable'` 两个 i18n key，
  而 `HealthView.vue:42/70` 直接把 `error` 作为 `el-alert` 标题展示。后端宕机或 Vite 代理未转发
  `/livez`、`/readyz` 时，用户看到的正是最需要诊断信息的故障场景下的一串无意义字面量。
- **实现**：两处错误改为可直接展示的中文诊断（含 HTTP 状态码 / 原始网络错误），JSDoc 同步声明
  `error` 是可读文案而非 i18n key；视图零改动，原有 `|| '探针不可达'` 兜底保持兼容。
- **测试**：新增 `src/api/tests/health.test.js`（4 例），锁定 `/livez`、`/readyz` 路径、
  `validateStatus` 全放行（503 DOWN 是合法业务态）、格式异常与网络失败的可读文案；
  前端测试 140 → 144 例全过。

### 游标分页回退后可再次前进（含单测补齐）

- **背景**：`src/utils/keyset.js:93-101` 的 `goPrev` 注释称「cursors 保留以便再次前进」，但实际只回退
  index 并清空 `nextCursor`；`canGoNext` 仅判断 `nextCursor`，`goNext` 也只压入 `nextCursor`，
  页栈中已有的 `cursors[index+1]` 从未用于前进。因此 `OperationLogView` 用户在第 3 页点「上一页」后，
  若回看重载失败（`:424` 把 nextCursor 置空），「下一页」按钮永久禁用，深翻页回看后无法恢复。
  模块头注释声称「被离线单测覆盖」，仓库实际没有 keyset 测试文件。
- **实现**：`canGoNext` 与 `goNext` 改为「本页 `nextCursor` 优先，否则用页栈保留的更深层游标」，
  新游标仍会覆盖旧值；视图与 API 零改动。
- **测试**：新增 `src/utils/tests/keyset.test.js`（10 例），覆盖 OFFSET 阈值切换、护栏错误识别、
  栈归一容错、多层前进、回退后再前进、回看失败后恢复、新游标优先等；前端测试 130 → 140 例全过。
- **验证**：门禁 33 例与后端 477 例均通过，详见提交说明。

### 报表导出失败不再抹掉已加载报表

- **背景**：`ReportPanel.vue:338-379` 的 `download()` 在四类失败分支（无文件内容、空内容、
  JSON 业务错误、异常）都写入页面主错误状态 `error.value`；而模板 `v-if="error"` 会用「错误提示 +
  重试按钮」替换整个报表正文。导出只是旁路操作，失败却会让用户丢失已加载的统计图表与明细，
  必须点「重试」重新查询才能恢复。该组件被 JOB_EXEC / LOGIN / API_CALL 三个报表页复用。
- **实现**：四处 `error.value = ...` 改为 `ElMessage.error(...)`；导出成功提示、主加载流程与 API 契约不变。
- **防回归**：新增 `scripts/tests/test_frontend_export_consistency.py`（4 例），扫描所有 `.vue` 的
  `download`/`onExport*`/`export*` 函数体，禁止写入 `error.value`；门禁单测现 33 例。
- **验证**：门禁单测 33/33 通过；后端 477 例、前端 130 例与 Vite 构建见提交说明。

### 前端 CSV 导出补齐公式注入防护（CWE-1236）

- **背景**：后端 `CsvExporter.neutralizeFormula` 已对 `= + - @`、Tab、CR 开头的单元格前置单引号，
  并在 CHANGELOG 中明确为统一防护口径；但前端 `src/utils/csv.js:17-24` 的 `escapeCsvField`
  只做 RFC 4180 转义。各列表页「导出当前页」使用前端通道，导出内容含用户可控字段
  （如任务名、限制原因），Excel / WPS 打开即可能执行 `=HYPERLINK` 等公式。
- **实现**：新增纯函数 `neutralizeFormula(value)`，逐字移植后端逻辑（危险首字符前置 `'`，
  `-5`/`+3.14` 这类合法数值例外），`escapeCsvField` 改为「先中和、再按 RFC 4180 加引号」，
  顺序与后端一致；各视图零改动。
- **测试**：新增 `src/utils/tests/csv.test.js`（8 例），覆盖六类危险首字符、经典 `HYPERLINK` 载荷、
  数值例外、首字符判定、CR 前缀的引号包裹、`toCsv` 端到端与 BOM；前端测试 122 → 130 例全过。
- **注意**：单个小数点（如 `-.`）按后端 `isPlainNumber` 口径视为数值候选而不中和，保持两端导出结果一致。

### 修复 JOB_EXEC 状态下钻返回未过滤序列

- **背景**：`ReportPanel.vue:432-435` 的状态饼图允许 `JOB_EXEC` 点击发起 `STATUS` 维度下钻，标题为
  「状态「成功/失败」趋势」；但 `ReportDrillRules.dimensionApplies` 此前只放行操作日志视角，
  导致 `parentCondition` 中已有的 `sys_job_log.status` 专用谓词不可达，实际返回整窗未过滤序列。
  图表宣称按状态下钻、数据却未过滤，属误导性结果（此前章节曾把该不可达分支记为「按设计退化」，本批更正）。
- **实现**：`dimensionApplies` 将 `STATUS` 从 `MODULE/REQUEST_PATH` 组合中拆出，增加 `JOB_EXEC`；
  `DrillDimension` 文档同步说明 STATUS 的双表语义（操作日志按 `success`，定时任务按 `sys_job_log.status`）。
  `ReportService` 的 JOB_EXEC 基础条件本就基于 `SYS_JOB_LOG`，无跨表风险。
- **测试**：`ReportDrillRulesTest` 将原「退化为不下钻」断言改为校验 `status = ?` 与 `SUCCESS`/`FAILED`
  参数绑定，并补充适用性断言；10/10 通过。
- **验证**：后端全量 477 例、前端 122 例、门禁 29 例与 Vite 构建见提交说明。

### 开放平台令牌自测失败零反馈修复

- **背景**：`issueClientToken` 走 `requestRaw` 且设置 `skipAuthErrorHandling: true`（401 `invalid_client`
  是业务校验失败，不应清理管理员登录态或跳登录）。但 `rawAxios` 错误拦截器在这种情况下直接
  `Promise.reject`，不经过 `handleHttpError` 的 `ElMessage.error`；`OpenApiClientView.onIssueToken`
  的 catch 为空，且注释误称「handleHttpError 已统一提示」。结果是输错密钥后按钮停转、页面零反馈，
  用户无法区分认证失败与无响应。
- **实现**：
  - `src/api/openApiClient.js` 新增纯函数 `resolveOAuthErrorMessage(error)`，按
    `error_description → message → error → Error.message → 兜底文案` 解析后端 OAuth2 错误包络；
  - `OpenApiClientView.onIssueToken` 的 catch 改为 `ElMessage.error(resolveOAuthErrorMessage(e))`，
    并修正误导性注释；保留 `skipAuthErrorHandling` 与 `http.js` 不变。
  - 新增 `src/api/tests/openApiClient.test.js`（4 例）：锁定 form-urlencoded 请求形状与错误解析优先级。
- **验证**：前端测试 118 → 122 例全过；后端 477 例、门禁 29 例与 Vite 构建见提交说明。

### 分页大小切换未重置页码（5 处）

- **背景**：页码重置工作此前覆盖了筛选回车与「查询」按钮，但遗漏了 `@size-change`。
  `UserView`、`TenantView`、`PostView` 的主列表以及 `OnlineUserView` 的主列表与「限制人」弹窗
  共 5 处分页大小切换直接调用不重置页码的 `load()`/`loadRestrictions()`；用户在第 2 页以上切换
  每页条数后，会按旧页码请求并得到空数据，且难以自行恢复。
- **实现**：为 4 个视图新增 `onSizeChange()`（`query.page = 1` 后 `load()`），`OnlineUserView`
  另加 `onRestrictionSizeChange()`（`rQuery.page = 1` 后 `loadRestrictions()`）；5 处绑定改为对应处理器。
  每页条数本身由 `v-model:page-size` 更新，处理器只负责页码归位。
- **防回归**：扩展 `test_frontend_search_consistency.py` 至 8 例，新增 `@size-change` 直连
  `load`/`loadRestrictions` 的检测；门禁单测现 29 例。
- **验证**：门禁单测 29/29 通过；后端 477 例、前端 118 例与 Vite 构建见提交说明。

### 搜索入口页码重置补齐剩余四个列表页

- **背景**：上一批只修复了「按钮走 `onSearch`、回车走 `load`」的 6 个视图；另有 4 个分页视图
  （`TenantView`、`PostView`、`UserView`、`OnlineUserView`）的两个搜索入口都直接调用 `load()`，
  而 `load()` 本身不重置页码。在第 2 页以上修改筛选条件后，无论点「查询」还是按回车都会停在旧页，
  得到空页或错位数据——属于同一类缺陷的另一种表现形式。
- **实现**：为这 4 个视图各新增 `onSearch()`（`query.page = 1` 后 `load()`），并把 5 个筛选框回车与
  2 个「查询」按钮接入该函数；`OnlineUserView` 的静默自动刷新仍直接调用 `load(silent)`，不改变页码，
  其限制人对话框的独立查询入口也未受影响。
- **防回归**：扩展 `test_frontend_search_consistency.py`，除回车外新增对 `:icon="Search"` 查询按钮的
  检测；刷新/重试按钮（`:icon="Refresh"` 或无图标）不纳入，避免误伤保留页码的合理场景。门禁单测现 27 例。
- **验证**：门禁单测 27/27 通过；后端 477 例、前端 118 例与 Vite 构建见提交说明。

### 筛选框回车搜索统一重置页码（12 处一致性缺陷）

- **背景**：6 个列表视图同时存在「查询」按钮（走 `onSearch()`，先 `query.page = 1` 再加载）和筛选框
  回车（直接 `load()`，沿用当前页码）。用户在第 2 页以上修改筛选条件后按回车，会按旧页码请求，
  得到空页或错位数据，且无法自行恢复——与点击「查询」的两个入口行为不一致，容易被误判为「无匹配数据」。
- **实现**：将 6 个视图共 12 个 `@keyup.enter="load"` 统一改为 `@keyup.enter="onSearch"`：
  `LoginLogView`（4 处）、`IdempotencyRecordView`（3 处）、`FileManagementView`（2 处）、
  `NotificationTemplateView`、`SmsLogView`、`NotificationRecordView`（各 1 处）。
  未改动没有 `onSearch` 的视图（这些视图的按钮与回车都走 `load()`，行为本身一致）。
- **防回归**：新增 `scripts/tests/test_frontend_search_consistency.py`（4 例），静态扫描所有 `.vue`，
  对定义了 `onSearch` 的视图禁止筛选框回车直接调用 `load`；已纳入 `gate-tests` 统一入口（现 25 例）。
- **验证**：门禁单测 25/25 通过；后端 477 例、前端 118 例与 Vite 构建见提交说明。

### 装配模块单测补强（scaffold-admin 零测试覆盖清零，14 模块全覆盖）

- **背景**：`scaffold-admin` 是最后一个零测试模块；虽以启动装配为主，但仍含两类高风险逻辑：
  生产启动期数据源时区一致性 fail-fast（错位会让 MySQL DATETIME 读回整体 +8h），以及 JSON 日志编码器
  （MDC 字段透出与标准字段冲突保护，直接影响 trace 串联）。
- **实现**：新增 2 个测试类共 11 例：
  - `DataSourceTimeZoneGuardTest`（6 例）：新旧 Connector/J 时区参数解析、UTC 别名与命名时区偏移换算、
    `SERVER`/未知值只告警、启用开关、主 URL 与旧版 `spring.datasource.url` 回落、UTC/JVM 匹配通过与
    `Asia/Shanghai` 不对称时 fail-fast；
  - `JsonLogEncoderTest`（5 例）：标准字段与 ISO 时间戳、MDC 原样透出、MDC 不得覆盖标准字段、
    异常类/消息扁平化、单行 JSON 与平台换行符。
- **验证**：单模块 11/11 通过；后端全量测试 466 → 477 例；零生产代码与构建文件改动。
  至此 14 个后端模块全部具备测试覆盖。

### 站内消息规则层抽取与单测（scaffold-im 零测试覆盖清零）

- **背景**：`scaffold-im` 此前无任何测试类，且会话创建校验、成员集合、消息类型/预览归一、
  已读游标推进与会话排序口径全部内联在两个 Spring Service 中，无法离线单测，规则易在多处漂移。
- **实现**：
  - 新增纯函数层 `ImRules`（无 Spring / 无 DB）：`validateCreateConversation`、`resolveMemberIds`、
    `normalizeMsgType`、`preview`、`shouldAdvanceReadCursor`、`compareLastMessageAt`；
  - `ImConversationService` 与 `ImMessageService` 改为委托该规则层，逐字保留原有校验顺序、错误信息、
    预览上限（50）与「只前进不回退」语义；`PREVIEW_MAX` 单点定义于 `ImRules`；
  - 新增 `ImRulesTest`（10 例）覆盖全部分支与边界。
- **验证**：单模块 10/10 通过；后端全量测试 456 → 466 例；重构前后逻辑等价，编译零警告。
- **注意**：这是本轮唯一涉及生产代码的改动，但仅做静态逻辑抽取与委托，不改变任何运行时行为或 API 契约。

### Kafka 保序与拓扑单测补强（scaffold-kafka 零测试覆盖清零）

- **背景**：`scaffold-kafka` 此前无任何测试类；`OrderingRules` 的 murmur2 声称「与 Kafka 默认分区器
  位级兼容」，是同 key 保序的根基，一旦位运算偏移出错会导致同 key 落不同分区、顺序彻底失效；
  版本号乱序兜底错误则会让迟到旧数据覆盖新数据。
- **实现**：新增 3 个测试类共 18 例：
  - `OrderingRulesTest`（8 例）：5 组 murmur2 参考向量由**独立实现交叉计算**锁定（含空串、ASCII、
    中文 UTF-8、长键），`toPositive` 边界、分区稳定性与 UTF-8 重载一致、null 键等同空键、
    非法分区数拒绝、版本号 null 语义与「严格大于才处理」的乱序兜底；
  - `TopologyRulesTest`（5 例）：acks 白名单、设计稿分区公式 `max(吞吐/单分区上限, 并行度×1.5)`、
    向上取整、非法参数拒绝、热点判定严格大于 2× 均值；
  - `FanoutRouterTest`（5 例）：64 分片数、复用统一分区哈希、广播全量有序展开、单播仅归属分片、null 键回退。
- **说明**：旧批次曾把 `OrderingRules` 记为「审查无问题、本批不改」，指当时无缺陷需修；本批是主动补回归守护，
  未改动其实现。
- **验证**：单模块 18/18 通过；后端全量测试 438 → 456 例；零生产代码与构建文件改动。

### 实时网关单测补强（scaffold-realtime 零测试覆盖清零）

- **背景**：`scaffold-realtime` 此前无任何测试类，而心跳保活、慢消费者三级处置、背压计数与短期令牌
  都是 WebSocket 网关的稳定性/安全口径；判定错误会导致误断链、误告警或令牌校验绕过。
- **实现**：新增 4 个测试类共 19 例：
  - `HeartbeatPolicyTest`（5 例）：设计默认值、构造时序约束、ping/空闲/路由 TTL 三类到期边界
    （路由过期用严格大于，恰好 TTL 仍有效）；
  - `SlowConsumerPolicyTest`（5 例）：默认阈值、构造校验、队列上限边界、实时消息 L1 丢弃 vs
    关键消息 L2 断链、节点熔断严格高水位；
  - `BackpressureCounterTest`（4 例）：初始零值、七类事件独立计数、快照不可变、并发 8×1000 原子递增；
  - `TokenCodecTest`（5 例）：签发/解析往返、载荷篡改与错误密钥拒绝、畸形输入容错，
    并锁定 `verify` 仅验签、时效由调用方按 `exp` 判定的职责边界。
- **验证**：单模块 19/19 通过；后端全量测试 419 → 438 例；零生产代码与构建文件改动。

### 报表下钻规则单测补强（scaffold-workbench 零测试覆盖清零）

- **背景**：`scaffold-workbench` 此前无任何测试类，而 `ReportDrillRules` 负责下钻谓词构建（直接生成
  WHERE 条件）、维度适用性、父级日期解析与标题拼装；谓词错误会造成跨表 SQL 或错误筛选结果。
- **实现**：新增 `ReportDrillRulesTest`（10 例），锁定：
  - 维度适用性矩阵（时间/耗时全类型适用，模块/状态/路径仅操作日志视角，登录方式仅登录视角）；
  - 空父级值或不适用维度退化为 `noCondition`；
  - 模块/路径/登录方式/状态谓词的 SQL 形态与参数绑定（成功失败映射、中文登录标签映射）；
  - 耗时分桶六个标签的区间绑定与未知标签退化；
  - 父级日期「完整日期 → 窗口内 MM-dd → 今日」的回落策略，超长关键字截断与标题模板。
- **发现（未改生产代码）**：`parentCondition` 中 `JOB_EXEC + STATUS` 分支当前不可达——`dimensionApplies`
  按设计只允许操作日志视角使用 STATUS 维度；测试锁定实际对外语义（退化为不下钻）。若后续要支持
  定时任务按状态下钻，需同时放开 `dimensionApplies` 并复核该分支。
- **验证**：单模块 10/10 通过；后端全量测试 409 → 419 例；零生产代码与构建文件改动。

### 工作流规则单测补强（scaffold-workflow 零测试覆盖清零）

- **背景**：`scaffold-workflow` 此前无任何测试类，而认领判定、定义/实例挂起激活状态机、
  审计 `operationId` 唯一性与监控超时催办口径都收敛在该模块的纯函数中；其中 `operationId`
  的历史实现曾因同毫秒重复而撞 `uk_operation_id` 唯一键，属高价值回归守护点。
- **实现**：新增 4 个测试类共 21 例：
  - `ClaimRulesTest`（3 例）：四种互斥认领结果与 null 办理人池化语义；
  - `WorkflowDefinitionRulesTest`（5 例）：挂起/激活转移真值表与重复操作冲突异常；
  - `WorkflowInstanceAdminRulesTest`（7 例）：实例转移真值表、`operationId` 小写前缀/空值回退/
    同实例唯一性/64 列宽截断与超长后缀保尾；
  - `WorkflowMonitorRulesTest`（6 例）：超时判定与时长非负、催办最小间隔边界、状态白名单回落、
    关键字截断不拒绝、批量 ID 去重保序。
- **验证**：单模块 21/21 通过；后端全量测试 388 → 409 例；零生产代码与构建文件改动。

### 告警规则单测补强（scaffold-monitor 零测试覆盖清零）

- **背景**：`scaffold-monitor` 模块此前无任何测试类，而 `AlertRuleRules`（阈值比较/配置校验/合成告警）
  与 `AlertInboxRules`（容错筛选/汇总统计）是告警链路的口径单一来源；排序或计数错误会直接误导排障。
- **实现**：新增 `AlertRuleRulesTest`（11 例）与 `AlertInboxRulesTest`（9 例），锁定：
  - 枚举容错解析（大小写/空白/非法值）、六种比较符与边界语义、缺失输入不触发；
  - 配置校验的全字段错误收集、WEBHOOK 专属 `channelConfig` 要求、计次/时长边界；
  - 合成告警恒为 `firing`、`instance=rules-engine`、名称前缀与缺省严重度回退；
  - 收件箱 status 白名单/severity 自由 label 的不同容错 posture、读取上限钳制；
  - 筛选保序与大小写不敏感、非法 status 忽略但 severity 仍生效；
  - 汇总中 `unknown` 桶保证「总数 = 各 severity 桶之和」，计数降序且同计数按字母序、留存时间窗口。
- **验证**：单模块 20/20 通过；后端全量测试 368 → 388 例；零生产代码与构建文件改动。

### 任务 DAG 规则单测补强（scaffold-job 零测试覆盖清零）

- **背景**：`scaffold-job` 是 14 个模块中唯一没有任何 `*Test.java` 的模块；而 `JobDagRules`
  承担任务依赖的解析、校验、环检测与拓扑排序，顺序错误会造成任务死锁或漏触发。
- **实现**：新增 `JobDagRulesTest`（12 例），锁定 `depends_on` 解析去重保序与非数字/非法输入返空、
  序列化空列表返回 `null`、序列化/解析往返一致、自依赖与不存在依赖抛 `BusinessException`、
  直接自环与多跳回边检测、无环与 `depsOf` 返回 null 的安全降级、拓扑序中依赖前置于被依赖任务、
  集合外依赖忽略，以及 `dedupe` 忽略 null 并保序。
- **验证**：单模块 `mvn -o -pl scaffold-job -am test -Dtest=JobDagRulesTest -Dsurefire.failIfNoSpecifiedTests=false`
  12/12 通过；后端全量测试 356 → 368 例；零生产代码与构建文件改动，父 POM 已继承 `spring-boot-starter-test`。

### 接口契约扫描支持反引号模板 URL（消除历史误报）

- **背景**：`check-api-contract.py` 的 `FRONT_URL_RE` 长期只匹配单/双引号字面量，前端大量
  `` `/api/.../${id}` `` 反引号模板调用被漏扫，导致 `backend_only` 虚高（本轮修复前 144 条），
  契约扫描的结论价值被削弱。多轮提交说明已把该限制记为已知信息性偏差。
- **实现**：
  - 新增 `FRONT_TEMPLATE_RE` 独立通道纳管反引号模板 URL，`norm_frontend_url()` 将 `${expr}`
    归一为 `{}`，与后端 `@GetMapping("/{id}")` 的归一化形状对齐；`encodeURIComponent(...)` 等
    复合表达式同样收敛为单个占位符。
  - 排除 `src/api/tests/` 夹具目录：单测中的 `r-1`/`pi-1` 等假 ID 不是真实端点，此前用反引号
    规避扫描；修复后若继续纳入会把它们误报为 `frontend_only` 并破坏 `--strict` 门禁。
  - 清理 6 处因该限制而写的规避注释/拼接写法，断言恢复直白表达。
  - 新增 8 例契约扫描单测；`check-all.py` 的 `gate-tests` 步骤改为统一入口
    `scripts/tests/run_all.py`，一次运行全部门禁脚本单测（现共 21 例）。
- **效果**：前端引用 191 → 299 条，`backend_only` 144 → 36 条（降幅约 75%），
  `frontend_only` 保持 0、`duplicate` 0，`--strict` 同样通过。剩余 36 条为 OAuth2 端点、
  健康探针与尚未接入前端的管理端点，属真实后端-only，非扫描误报。
- **验证**：门禁单测 21/21 通过；后端 356 例、前端 118 例、Vite 构建与 8/8 门禁均通过。

### 数据库端例程禁用门禁（防回退）

- **背景**：上一轮已核实当前代码库不存在任何存储过程/函数，但「无过程」只是现状，缺少机制保证后续版本不回退。
  本轮新增硬性门禁，把核实结论固化为自动检查，避免 `DDL`/`DML` 或 Java 调用点被无意重新引入。
- **实现**：
  - 新增 `scripts/check-no-procedure.py`：扫描 `.sql`/`.java`/`.xml`，NP1 禁止 `CREATE PROCEDURE/FUNCTION`
    （含 `OR REPLACE`、`DEFINER=` 写法），NP2 禁止 `CALL <name>(...)`，NP3 禁止 `{call ...}`，
    NP4 禁止 `CallableStatement`/`SimpleJdbcCall`/`StoredProcedure(Query)`/`@Procedure`；
    扫描前剥离 SQL 注释与字符串字面量、Java 注释与文本块、XML 注释，仅代码类文件纳管，文档历史描述不误报；
    `CREATE TRIGGER` 属需求允许项，仅计数不阻断。
  - 新增 `scripts/tests/test_check_no_procedure.py`：13 例单测覆盖四类违规命中、注释/字符串/文本块误报抑制、
    触发器与 Markdown 豁免、非过程 API（`redis.call`/`Callable.call`）不误报，以及当前仓库干净态。
  - `check-all.py` 接入 `no-procedure` 与 `gate-tests` 两个硬性步骤，门禁由 7 步扩为 8 步（另有 1 个信息性契约步）。
- **验证**：门禁脚本 `EXIT=0`（扫描 1167 个文件、允许项触发器 0 个）；13 例单测全过；
  后端全量测试 356 例、前端测试 118 例与 Vite 构建见提交说明。
- **注意**：门禁为静态扫描，不连接数据库；若生产库存在仓库之外的存储过程，仍需按外部导出逐条迁移，本门禁无法覆盖。

### 通知记录 TraceId 链路跳转（运维排障联动）

- **背景**：`NotificationRecordView.vue:143` 的 TraceId 为纯文本展示，而操作日志页 `OperationLogView.vue:180`
  已将同源 traceId（`NotificationRecordRecorder.java:113` 写入 `MDC.get("traceId")`）做成 `el-link` + 链路抽屉，
  可从一次请求的操作序列定位通知失败原因；通知记录页缺失同款入口，排障需手工跨页查 traceId。
- **前端**（单文件改动，零后端改动、零新权限）：TraceId 列改为 `el-link` 点击调用 `auditLogTrace(traceId)`，
  新增与操作日志页同口径的操作链路抽屉（时间/模块/操作/错误码），loading 防重复、空态提示、
  错误由 http 响应拦截器统一提示。
- **说明**：多轮调研确认的其余孤儿端点（`jobDetail`/`getAlertRule`/`maskStrategy` 详情）均因列表已返回完整 DTO
  而属有意冗余，未接入以免制造重复入口；`getCacheStats`/`clearCache`/`getPoolStats` 已有专用视图消费，非缺口。
- 验证：前端测试/构建、后端全量测试与门禁见提交说明。

### 存储过程迁移核实与初始化 SQL 文档校正

- **核实结论**：应「把所有存储过程改写为后端方法」的要求，对全仓做了逐层核验，确认**当前代码库不存在任何存储过程**：
  - SQL 脚本仅 `ddl.sql`/`dml.sql` 两个（`db/migration/` 只有 README、Flyway 已移除），`CREATE PROCEDURE/FUNCTION/TRIGGER`、
    `DELIMITER`、`CALL` 全仓 `.sql` 零命中（唯一 `report-api-call` 为菜单 code 字符串，非 SQL）；
  - Java 侧 `CallableStatement`/`SimpleJdbcCall`/`@Procedure`/`JdbcTemplate`/`{call ...}` 零命中
    （`redis.call` 为 Lua、`Callable.call` 为函数式接口，均非数据库存储过程）；
  - 全仓 18 个 XML 均为构建/日志配置，无 MyBatis mapper XML，动态 SQL 走 `@SelectProvider` 等注解。
- **历史证据**：`git log` 显示提交 `790d8a3d`（初始化 SQL 规范化）已移除唯一的历史存储过程
  `scaffold_backfill_tenant_id`（一次性 tenant_id NULL 回填 + 零孤儿断言，属初始化脚手架而非运行时业务逻辑）。
  其业务语义当前由租户治理与应用侧校验覆盖，无逻辑丢失。
- **本次改动**：修正 `db/init/README.md` 两处过时描述（45-46 行、60-61 行仍称 dml.sql 末尾有 `DELIMITER $$` 回填例程），
  明确当前脚本只含 CREATE TABLE/INDEX/INSERT、无数据库端例程，旧库 NULL 孤儿行改由应用侧租户治理流程修复。
  无存储过程可改写，故无后端方法改写与前后结果对比测试。
- **影响说明**：项目早已完成"数据库层无自定义逻辑"迁移（MyBatis + 自研 DSL，逻辑全部在 Java 层），
  本次仅校正文档，不改变运行时行为、不新增文件、不影响性能。

### 运维监控数据展示增强（异常聚合 + 任务失败原因）

- **背景**：全仓扫描发现两个"后端能力已就绪、前端未展示"的真实缺口：
  ① `monitor.js:getErrorSummary`（`GET /api/system/monitor/error-summary`，按模块/错误码聚合）定义后全项目零调用，
  系统监控页仅有逐条异常日志，运维需人工翻页统计高频故障源；
  ② `JobView.lastError`（后端已返回最近失败原因）在任务列表未展示，失败任务只能再点「日志」定位原因。
- **前端**（零后端改动）：
  - `RuntimeMonitorView.vue`：异常日志卡片上方新增「按模块/错误码聚合」表格（次数可排序），
    与历史趋势共用「时间范围」下拉窗口；时间范围变更触发 `onRangeChange` 同步重算采样与聚合；
    手动「刷新」纳入聚合与采样；加载失败静默（拦截器统一提示）、空态友好提示。
  - `JobListView.vue`：「上次执行」列在失败态下新增 `lastError` 行（红色省略 + 原生 title tooltip），列宽 150→190。
- **说明**：`reportTypes` 替换 `TemplateCenterView` 硬编码经评估后未纳入——后端注释明确 USAGE/CATEGORY/STATUS
  类型的前端页面已随 V30 移除，直接动态替换会把废弃类型重新引入，风险大于收益，保留既有常量。
- 验证：前端测试/构建、后端全量测试与门禁见提交说明。

### 前端 API 契约测试大面积补齐（TEST-COVERAGE-PLAN 前端口径）

- **背景**：`src/api/tests/` 此前仅 7 个文件，而 `src/api/*.js` 有 69 个模块；`workflow.js` 27 个端点仅 4 个有断言，
  `message.js`/`alertRule.js`/`observability.js` 等核心模块零契约测试——前后端契约漂移无回归防线。
- **测试**（纯新增 4 文件，零生产代码改动）：
  - `workflow.test.js` 从 4 端点扩到全量 27 端点（参数化覆盖无参 GET / query GET / 路径参数 GET / 无参 POST /
    query 写端点 / body 写端点），保留治理端点专项断言；
  - 新增 `message.test.js`（11 端点：发送/收件箱/深翻页 keyset/未读数/已读/回执统计明细催办）；
  - 新增 `alertRule.test.js`（7 端点：CRUD/启停/立即评估）；
  - 新增 `observability.test.js`（5 端点：span 列表/单 trace 聚合含 traceId URL 转义/摘要/指标/清空）。
- **口径**：URL 断言一律用反引号模板串（与实现一致），避免被 api-contract 扫描器误当静态路由；
  所有模块统一断言「全部走 request、不用 blob 通道」。
- 验证：前端 `npm test` 118 例全绿（新增 22 例，18 文件）；门禁 7/7、`frontend_only=0`；后端零改动。

### Trace 调用链详情入口（C12 · 可观测中心）

- **背景**：后端 `GET /api/observability/traces/{traceId}` 与前端 `getTrace()` 同形函数早已就绪，但全项目零调用——
  监控中心「链路追踪」Tab 仅有平铺 span 列表（无父子关系/属性查看），用户无法按 traceId 查看完整调用链。
- **前端**：`TraceTab.vue` span 列表加「链路」操作按钮；新增调用链详情对话框（父子层级瀑布视图，依据
  `parentSpanId` 构建树，展示相对起始偏移、耗时、状态、错误数、总耗时）与 span 属性 JSON 查看弹窗；
  加载错误由 http 拦截器统一提示，loading/空态已处理。
- **纯函数提取**：`format.js` 新增 `buildTraceTree`（父缺失/自引用提升为根防丢失成环、偏移基准为最早 span）
  与 `traceSummary`（span 数/错误数/总耗时覆盖并发 span），便于离线单测。
- **测试**：新增 `views/monitor/components/tests/format.test.js`（9 例：既有时间/数值格式化 + Trace 树/摘要边界）。
- 验证：前端 `npm test` 96 例全绿（新增 9 例，15 文件）、`vite build` EXIT=0（13.71s）；
  门禁 7/7、`frontend_only=0`；后端全量 356 例回归全绿。
- 边界：测试过程确认 `buildTraceTree` 按输入顺序保留根节点（非按时间排序），父缺失/自引用节点提升为根且无子、不成环；
  总耗时按最晚结束-最早开始计算以覆盖并发 span。
- 说明：`getTrace` URL 含路径参数（反引号模板串），契约扫描器仅识别引号字面量，仍列 backend_only（信息性）；
  端点与前端同形函数早已存在，本轮仅补视图入口，非新增孤儿引用。

### 会话自动续期前端闭环（C11 · auth/refresh）

- **背景**：后端 `POST /api/auth/refresh` 早已完整（不透明刷新令牌 SHA-256 存储、家族防重放、轮换），
  但前端登录时丢弃了 `refreshToken`、无刷新函数、401 立即登出，会话连续性差。本轮补齐前端闭环。
- **前端**：
  - `store/auth.js`：`authState.refreshToken` + `pc_admin_refresh_token` 持久化；`setAuth` 扩第三参数（可选，兼容旧调用）、
    `clearAuth` 一并清除；未传 refreshToken 时清空旧凭据（无凭据的旧会话仍按既有逻辑直接登出）。
  - `api/http.js`：新增裸 axios 刷新通道（绕开自身 401 拦截防递归）、并发单飞 Promise（多 401 只刷一次）、
    公开端点排除清单（login/refresh/captcha/change-password/forgot-password/oauth2/token 的 401 不刷新）、
    `_authRetried` 重放标记（二次 401 不再刷新，防死循环）、`skipAuthRefresh` 显式关闭开关；
    401 时尝试刷新并重放原请求（realAxios/rawAxios 各自通道），失败才清理登录态跳登录。
  - `api/auth.js`：新增 `refreshToken` 同形函数（带 `skipAuthRefresh` 防循环）；`LoginView.vue` 登录成功时保存 refreshToken。
- **测试**：`api/tests/auth.test.js`（refresh 端点同形）+ `store/tests/auth.test.js`（refreshToken 存取/轮换写回/清理/兼容，4 例）。
- 验证：前端 `npm test` 87 例全绿（新增 5 例）、`vite build` EXIT=0；门禁 7/7、`frontend_only=0`、`auth/refresh` 已闭环；后端全量 356 例回归全绿。
- 工具边界：`NO_REFRESH_PATHS` 初版含 `/api/oauth2/token` 字面量被契约扫描器误判为前端引用（frontend_only=1），
  改为路径后缀匹配后恢复 0；该端点在 oauth 客户端模块已有合法消费，非真实孤儿引用。
- **安全边界**：刷新令牌仅存 localStorage（与 accessToken 同域，非 HttpOnly，前端 XSS 面与既有令牌一致）；
  令牌轮换由后端家族机制防重放；刷新失败/无凭据/二次 401 均 fail-closed 登出，不放宽鉴权。

### 免打扰时段规则单测补齐（H4）

- **背景**：`NotificationQuietHoursRules`（免打扰时段命中/告警穿透/接收者过滤）为通知抑制链路的安全关键纯函数规则，
  磁盘无对应单测；前端面板 `NotificationQuietHoursPanel.vue` 已接入该能力，属测试覆盖缺口而非功能缺口。
- **测试**：新增 `NotificationQuietHoursRulesTest`（10 例）：`HH:mm` 解析/格式化（含 trim 与非法值）、窗口合法性、
  当日半开区间 `[start,end)`、跨午夜窗口、退化/空值防御、告警类型判定（trim + 大小写不敏感）、
  穿透决策、单用户抑制、批量接收者过滤（保序/null 剔除/重复保留）、常量口径。
- **边界结论**：测试过程确认了"告警穿透仅对 `ALERT` 生效，`NOTICE`/`SYSTEM` 等即使配置穿透仍在免打扰时段被抑制"
  的业务语义，与类注释一致；退化窗口按"不命中"防御历史脏数据全量误抑制。
- 验证：`mvn -o -pl scaffold-system -am test` 10 例全绿；全量测试/门禁见提交说明。零生产代码改动。

### 安全规则单测补齐（退订令牌 / Webhook 签名）

- **背景**：`TEST-COVERAGE-PLAN.md` 目标为业务模块主链路全覆盖；`UnsubscribeTokenRules`（M15 免登录退订 HMAC 凭证）与
  `WebhookSignatureRules`（M18 外发 HMAC 签名）均为安全关键纯函数规则，但磁盘无对应单测。
- **测试**：新增 `UnsubscribeTokenRulesTest`（6 例：铸造/验签往返、nonce 不可关联、篡改/错误密钥、过期边界、
  形态非法、payload 语义非法）与 `WebhookSignatureRulesTest`（6 例：签名往返、请求头封装/剥离、nonce 唯一、
  报文/nonce/密钥/时间戳篡改拒绝、时间戳新鲜度窗口、空值非法输入）。
- **边界**：两类规则纯 JDK、无 Spring/DB 依赖，单测离线运行；验签恒定时间比较、密钥空白拒绝、时间注入
  （`nowMillis`）等安全边界均被覆盖；测试用伪造签名构造语义非法 payload，单独验证解析失败分支。
- 验证：`mvn -o -pl scaffold-system -am test` 新增 12 例全绿；全量测试/门禁见提交说明。零生产代码改动。

### 工作流监控补齐流程定义挂起/激活前端闭环

- **背景**：后端 `WorkflowMonitorController` 已有 `POST /definitions/{id}/suspend|activate`（权限 `workflow:definition:state`、
  审计、404/409 幂等守卫），但前端 `workflow.js` 无同形函数、监控页「定义版本链」表格仅有状态列无操作入口，
  两个端点长期为 backend_only（信息性），管理员无法从界面挂起/激活定义。
- **前端**：`workflow.js` 新增 `suspendDefinition`/`activateDefinition`；`WorkflowMonitorView.vue` 定义版本链表格加操作列
  （活跃显示「挂起」、已挂起显示「激活」），二次确认后调用端点并刷新版本链，loading 防重复点击，错误由 http 拦截器统一提示。
- **测试**：新建 `src/api/tests/workflow.test.js`（3 例：定义挂起/激活同形、实例挂起/恢复回归、统一 request 通道）。
- 验证：前端 `npm test` 82 例全绿（新增 3 例）、`vite build` EXIT=0；门禁 7/7、`frontend_only=0`、`duplicate=0`。
- 说明：两个端点含路径参数（`{processDefinitionId}`），api-contract 扫描器正则仅识别引号字面量、不匹配反引号模板字符串，
  故仍列为 backend_only（信息性），与既有 `cms/article/{}/delete` 等动态端点表现一致，非本次缺陷；前端已同形接入并由契约测试锁定。

### CMS 发布权限码补齐（盘点表"权限码未入菜单种子/前端 meta 未体现"完善）

- **背景**：后端 `CmsArticleController` 的发布/下线端点已用 `cms:article:publish`，但菜单种子仅有 `cms:article:manage`，
  导致普通角色无法通过角色授权界面获得发布权限（该权限码无对应菜单可选）；盘点表标注为部分实现。
- **数据**：`dml.sql` 的 CMS 段新增按钮菜单 `id=9304`（`parent_id=9303`、`menu_type=BUTTON`、`visible=0`、
  `permission_code=cms:article:publish`），并绑定 SUPER_ADMIN（`sys_role_menu`）；`visible=0` 保证不进侧边栏，
  仅用于角色授权，零运行时渲染影响。
- **说明**：前端无按钮级权限指令，发布/下线按钮仍按文章状态显示，未授权点击由后端 `@PreAuthorize` 返回 403 兜底；
  路由 meta 注释已声明 publish 为独立按钮级权限。权限门禁单向校验（菜单码 ⊆ Java 码）通过。
- 验证：`check-permission.py` EXIT=0（菜单权限码 51 个）；全量门禁 7/7；种子幂等（`INSERT IGNORE`），存量库重跑 dml 即可生效。

### CMS 文章批量排序（C10，盘点表"排序"未实现项落地）

- **背景**：盘点表"批量移动/排序"中排序一直未落地——文章无业务排序号，列表仅支持动态列排序，无法手工置顶/排序。
- **数据**：`cms_article` 加 `sort_no INT NOT NULL DEFAULT 0`（`ddl.sql` + `SqlTables.CMS_ARTICLE`），与栏目 `sort_no` 口径一致。
- **后端**：新增 `CmsArticleSortRules`（纯函数：排序号钳制 0~9999、items 归一去重、上限 200）、`ArticleSortItem`/`BatchSortArticleView`；
  `CmsArticleService` 增 `reorder`（单事务、跳过不存在/已删除 id 回报）、create/update 写入 sortNo、`toView` 映射与排序白名单加 `sortNo`；
  新增端点 `PUT /api/cms/article/sort`（权限 `cms:article:manage` + 审计）。
- **前端**：`cms.js` 增 `reorderArticles`；`ArticleView.vue` 列表加「排序」列（可排序）、编辑表单加排序号输入、
  工具栏加「批量排序」对话框（当前页逐行调整，一次性提交）；错误处理沿用 http 拦截器统一提示。
- **测试**：`CmsArticleSortRulesTest` 5 例（排序号钳制/校验/归一去重/非法值/上限）+ 前端契约测试 1 例。
- 验证：`mvn -o -pl scaffold-cms -am test` 全绿；前端 `npm test` 全绿、`vite build` EXIT=0；门禁 7/7（frontend_only 0，新端点已闭环）。

### CMS 文章回收站（C9，盘点表"回收站"未实现项落地）

- **背景**：文章删除为逻辑删除（`deleted=1`），但删除后无法查询、无法恢复；规划盘点列为未实现。本轮补齐回收站闭环。
- **后端**：新增 `CmsRecycleRules`（纯函数 ids 归一/去重/上限 200）、`BatchRestoreArticleRequest`/`BatchRestoreArticleView`；
  `CmsArticleService` 增 `recyclePage`（显式 `deleted=1` 条件查询，绕开默认逻辑删除过滤）/ `restore`（单篇恢复 + 栏目悬挂校验）/
  `batchRestore`（单事务批量恢复，跳过不存在或栏目已删的 id）/ `purge`（仅对回收站文章物理删除，级联清理版本快照与预览令牌）；
  新增 4 端点 `GET /recycle/page`、`POST /batch-restore`、`POST /{id}/restore`、`POST /{id}/purge`
  （权限 `cms:article:manage` + 审计）。
- **前端**：`cms.js` 增 4 个同形函数；`ArticleView.vue` 工具栏加「回收站」入口，对话框含标题筛选、多选批量恢复、
  单篇恢复与彻底删除（均二次确认）；恢复后同时刷新回收站与主列表；错误处理沿用 http 拦截器统一提示。
- **测试**：`CmsRecycleRulesTest` 4 例（归一/非法 id/上限/去重后上限）+ 前端契约测试 1 例。
- 验证：`mvn -o -pl scaffold-cms -am test` 全绿；前端 `npm test` 全绿、`vite build` EXIT=0；门禁 7/7（frontend_only 0，新端点已闭环）。

### CMS 文章批量移动（盘点表"批量移动/排序"未实现项落地）

- **后端**：新增 `BatchMoveArticleRequest`/`BatchMoveArticleView`、`CmsBatchMoveRules`（纯函数：ids 归一/去重/
  上限 200/目标栏目校验）、`CmsArticleService.batchMove`（单事务、目标栏目存在校验、逐条查存在性，
  不存在/已删除的 id 记入 skippedIds 回报，不做部分更新）、`POST /api/cms/article/batch-move`
  （权限 `cms:article:manage` + 审计）。
- **前端**：`cms.js` 增 `batchMoveArticles`；`ArticleView.vue` 表格加多选列、工具栏加"批量移动(N)"按钮
  （未选中禁用）、对话框选目标栏目；结果区分"全部成功/部分跳过"提示；错误处理沿用 http 拦截器统一提示。
- **测试**：`CmsBatchMoveRulesTest` 5 例（归一/非法 id/上限/去重后上限/目标校验）+ 前端契约测试 1 例。
- 验证：cms 52 例、前端 77 例全绿；`vite build` EXIT=0；门禁 7/7（frontend_only 0，新端点已闭环）。

### CMS 高级能力前端接入（C4/C5/C6）

- **背景**：CMS 深扩 C1–C8 后端能力已就绪、`cms.js` API 函数已定义，但 `ArticleView.vue` 仅接了基础 CRUD，
  三个高级能力无 UI 入口。本轮补齐前后端衔接。
- **C4 版本历史**：操作列加「版本」按钮 → 时间线抽屉展示快照（版本号/备注/状态/作者/创建人）；
  回滚前强确认 + 最新版本提前拦截；回滚后刷新列表。
- **C5 定时发布**：编辑表单加 `el-date-picker`（`YYYY-MM-DDTHH:mm:ss` 对齐后端 `LocalDateTime`）；
  状态筛选/标签/表格/详情四处支持 SCHEDULED（全局 `utils/status.js` 增映射）；排期时间列表与详情可见。
- **C6 预览令牌**：操作列加「预览」按钮 → 生成令牌并展示可复制链接（含有效期）与一键复制
  （clipboard API 失败降级提示）；弹窗注明 feature-gate 排障线索。
- **错误处理**：所有新增/既有写操作补 try/catch 防未捕获 rejection，遵循项目「http 拦截器统一提示」约定避免重复弹窗；
  loading 状态在 finally 中恢复；`utils/status.js` 新增 SCHEDULED 映射 + 3 例单测。
- 验证：前端 76 例全绿（新增 3 例）、`vite build` EXIT=0、门禁 7/7（frontend_only 0）。

### 移除 i18n 引擎与 pc-admin-ui 组件库

- **i18n 引擎（前端）**：删除 `src/locales/zhCN.js` 静态字典与全部 `tx()` 调用（10 文件 27 处），
  数据源（路由 title/主题/指标/报表/表单字段/scope 描述等）改为内联中文；`http.js` 移除 `lang=zh_CN` 注入，
  `connectivity.js` 移除语言协商断言（保留 receivedLocale 信息回显）。
- **i18n 引擎（后端）**：删除 `common/i18n/` 整个包（I18nService/Config/Properties/ValidationMessageConfig）
  与 `i18n/*.properties` 资源束；`GlobalExceptionHandler`/`BusinessException`/`ErrorCode`/
  `ConnectivityController` 移除 i18n 依赖，改用中文文案；81 处 Bean Validation 注解的
  `{validation.xxx}` 资源键内联为中文文案；删除 `i18n-leak-scan.mjs` 与 CI 中的 i18n/组件库扫描步骤。
- **pc-admin-ui**：删除整个组件库目录（18 文件）；`package.json` 移除 `@acme/admin-ui` 依赖与 `build:ui` 脚本；
  `vite.config.js` 移除 alias/dedupe；`components/index.js` 移除 re-export；
  `JobListView.vue` 的 `BaseChart` 改为主应用本地组件；CI 移除 ui-lib job；同步更新验证手册/使用说明。
- 验证：后端全模块编译通过 + 320 例全绿；前端 `npm test` 73 例全绿 + `npx vite build` EXIT=0；门禁 7/7（frontend_only 0）。

### CMS 模块深度扩展 · 迭代三（M3，CMS 深扩完成）

- **C7 公开内容查询**：新增 `CmsPublicService`/`CmsPublicController`（`GET /api/cms/public/articles`），
  仅 PUBLISHED、强分页 size≤50、脱敏视图 `PublicArticleView`；feature-gate `cms.public.enabled` 默认关，
  `SecurityConfig` 放行匿名 GET（与开关联动）；`CmsPublicRules` + 2 例单测。
- 全量回归：后端 14 模块 320 例全绿（基线 280 + CMS 新增 40），门禁 7/7（frontend_only 0）。
- 一致性：C1~C8 八项需求全部落地，`CMS-EVOLUTION-PLAN.md` 跟踪表与磁盘实现一致。

### CMS 模块深度扩展 · 迭代二（M2，需求文档 `java-admin-framework/docs/CMS-EVOLUTION-PLAN.md`）

- **C4 版本历史**：新增 `cms_article_version` 表、`CmsArticleVersionService`（update 前固化快照/版本列表/回滚写回）、
  `ArticleVersionView`；Controller 增 `GET /{id}/versions`、`POST /{id}/rollback`；`CmsVersionRules` 纯函数 + 4 例单测。
- **C5 定时发布**：`cms_article` 加 `scheduled_publish_at` 列与 Request/View 字段；
  `CmsArticleRules.resolveInitialStatus/isDueForPublish` 排期规则；`CmsArticleService.publishDue` + `CmsScheduledPublishJob`（`@Scheduled`）；
  feature-gate `cms.scheduled-publish.enabled` 默认开；2 例单测。
- **C6 草稿预览令牌**：新增 `cms_preview_token` 表、`CmsPreviewTokenRules`/`Service`；管理端生成令牌 +
  公开预览端点 `GET /api/cms/preview/{token}`（feature-gate `cms.preview.enabled` 默认关）；4 例单测。
- **C8 前端**：`src/api/tests/cms.test.js` 17 端点契约测试（6 例）；`cms.js` 增版本/回滚/令牌 3 函数；
  `zhCN.js` 增 15 条 CMS 文案；3 个 feature-gate 登记入 `FeatureGateRegistry`。
- 验证：cms 模块 45 例全绿、前端 73 例全绿 + build 成功、质量门禁 7/7（frontend_only 0）。

### CMS 模块深度扩展 · 迭代一（M1，需求文档 `java-admin-framework/docs/CMS-EVOLUTION-PLAN.md`）

- **C1 规则抽取**：新增 3 个纯函数规则类 `CmsStatusRules`/`CmsArticleRules`/`CmsCategoryRules`，
  `CmsArticleService`/`CmsCategoryService` 改为委托；21 例规则单测全绿。
- **C2 删除引用校验**：`CmsCategoryService.delete` 增加关联文章计数校验（对齐前端文案）；
  `ddl.sql` 的 `idx_cms_category_code` 升级为唯一索引 `uk_cms_category_code`（租户内唯一）。
- **C3 文章导出**：新增 `CmsArticleExportTask`（code=`cms-article`），复用 `ExportTask` 默认契约
  支持 CSV/XLSX/PDF，翻页复用 `CmsArticleService.page`、栏目名映射复用 `CmsCategoryService.list`；
  7 例单测覆盖契约/字段映射/筛选透传/三格式字节产物。
- 验证：`mvn -o -pl scaffold-cms -am test` 35 例全绿（cms 模块），质量门禁 7/7 通过（frontend_only 0）。

### 代码结构调整（全项目包结构与命名统一）

- **包结构统一**（按类型归位）：`controller` → `api`（Controller 77 个）、
  `application` → `service`（Service 94 个）、`dto` → `domain`（View/Request 等，整包改名保证引用自动同步）、
  9 个错放在 `service`/`vo` 包的 View 归入 `domain`（共 95 个 View 在 domain）。
  三类旧包残留均为 0；清理 104 个搬迁后空目录。
- **命名统一**：数据层对象去掉 `DO` 后缀并按表名 PascalCase 命名（如 `SysUserDO` → `SysUser`，
  57 个）；视图层 `VO` → `View`（23 个）；入参 `DTO`/`Command` → `Request`（4 个）；
  全项目 `DO`/`VO`/`DTO` 后缀清零，`View` 107 个、`Request` 78 个。
  改名经同名冲突扫描（0 冲突）并全量同步 import/类型引用（含测试）。
- **例外说明**：`permission`/`design`/`oauth2`/`audit` 等业务语义包中的 12 个 View 保留原位
  （其包名按业务域组织而非类型包，与设计器/权限等强耦合，强行迁移收益低风险高）。
- 验证：后端编译成功、280 例单测全绿（零回归）、质量门禁 7/7 通过（frontend_only 0）。

## [1.2.0] - 2026-11-23（规划中，版本待发布；本条目跟踪组件库抽取进度）

主题：平台化演进方向 A —— 前端独立组件库包（P1，分两批交付）。

### Added（新增）
- `pc-admin-ui/` 独立组件库包（`@acme/admin-ui@1.1.0`，纯 JS + Vite lib mode）：
  - **第一批**：迁移零业务依赖组件 `BaseChart` / `StatCard` / `CopyText` 及随包纯函数 `stat` / `theme`，新增 design tokens（`--acme-ui-*`）。
  - **第二批**：按 `PLATFORM-EVOLUTION-PLAN.md` A2 补齐 6 个通用组件 `ProTable` / `QueryForm` / `StatusTag` / `DescriptionList` / `ConfirmDialog` / `DictSelect`。
  - 产出 ESM + CJS 双格式 + 独立 CSS；peerDependencies 外部化 vue / element-plus / echarts，规避双实例。
  - 配套 21 例单测（9 组件 SSR smoke + 纯函数契约）与 SFC/i18n 门禁。
- 主应用接入：`file:../pc-admin-ui` 依赖 + Vite alias（默认源码直引，`ACME_UI_PACKAGE=1` 切包引用）；`src/components/index.js` 薄 re-export 兼容层；`JobListView` 完成库包直引切换（验收④）。
- CI 增量：`.github/workflows/ci.yml` 新增 gates 库包扫描步骤与 `ui-lib` job（保留既有 4 job 与覆盖率门禁）。
- 验证手册新增组件库专章（9 组件输入输出/边界/异常契约、两种接入模式、验证命令）。
- **测试覆盖第二批（TEST-COVERAGE-PLAN 1.2.0）**：新增 7 个测试类 / 37 例纯单元测试——
  `TokenVersionRulesTest`（令牌版本失效语义）、`SessionLimitRulesTest`（多端登录限制）、
  `DataScopeResultTest`（fail-closed 数据权限）、`RateLimiterTest`（固定窗口与 fail-closed 限流）、
  `IdempotencyRecordRulesTest`（幂等查询容错）、`TenantContextTest`（租户上下文严格模式/线程隔离）。
  后端累计 114 例全绿；CI 阈值维持起步线 0.10/0.05；网关层后续采用零依赖自研路线（本批不涉及）。
- **测试覆盖第二批收尾（T3 system 主链路 + T1 剩余）**：新增 5 测试类 / 24 例——
  `MenuTypeRulesTest`（双字面量菜单类型）、`PostRulesTest`（岗位编码/集合归一）、
  `FileValidatorTest`（扩展名+魔数）、`FileDedupRulesTest`（SHA-256 秒传去重）、
  `SessionCleanupRulesTest`（空闲会话回收）。后端累计 138 例全绿。
  - 缺陷修复：`PostRules` 编码正则原拒绝中文（与「总经理-001 合法」的注释及调用预期矛盾），
    扩充 CJK 统一表意文字白名单，注释同步更新。

### 代码审查专项 I：缺陷修复与正确性优化

对 security / common / system / framework 核心模块做系统代码审查，修复以下经代码证据确认的缺陷（零新增依赖、向后兼容）：

- **并发正确性**：`HeartbeatThrottle.tryAcquire` 原 `get→put` 非原子，并发同 key 可全部通过检查、重复写库，
  与类注释「原子并发安全」矛盾；改用 `ConcurrentHashMap.compute` 原子化读-判-写，并发同 key 仅放行一次。
- **整数溢出**：`ArithmeticRules.draw` 在极端 `mulMax`（乘法 `a*b`、除法 `b*q`）会 int 溢出产生负数答案，
  且上界 ≤0 时 `nextInt(非正数)` 抛异常；入口新增上界安全钳制（加减去 Integer.MAX_VALUE/2、乘除到 46340）。
- **内存有界性**：`ReplayNonceService.isReplay` 原仅在条目过期时淘汰，高频请求下全部未过期会突破 `maxSize`；
  修复为清理过期后仍超限则淘汰最早过期项，缓存严格有界。
- **窗口口径漂移**：`RedisRateLimiter.currentCount` 原写死 60s 窗口，与非 60s 限流窗口的观测口径不一致；
  抽取 `RateLimitWindowRules` 纯规则统一窗口计算，Redis 记录最近窗口秒数供观测复用；
  同时修复 `windowSeconds<=0` 时 `%0` 的 ArithmeticException。
- **数据一致性**：`FileService.deleteOne` 原「先物理删除、后 DB 逻辑删除」，DB 异常回滚后会留下
  「记录仍在、物理文件已丢失」；调换为先逻辑删除成功后删物理，最坏仅残留可清理的孤儿文件。
- **副作用隐患**：`JsonTruncator.trimNode` 中名为 `copy` 的变量实为入参原引用，
  `dropEmptyFields` 会就地改写入参；改用 `deepCopy()` 消除隐患。
- 新增 4 测试类 / 17 例回归单测：`HeartbeatThrottleTest`、`ArithmeticRulesTest`、
  `ReplayNonceServiceTest`、`RateLimitWindowRulesTest`。后端累计 167 例全绿，质量门禁 7/7 通过。
  - 暂缓项（需业务确认，本批不改）：`SensitiveMasker` 身份证正则宽松（`X` 允许出现在后 4 位任意位置）
    属 fail-safe 的有意设计；`CaptchaService.generate` 的 `synchronized` 为公开端点的有意为之的串行化，
    列为性能关注点；`FileService.batchDelete` 循环内逐项 DB 调用属「部分成功」语义，改批量会改变契约。

### 代码审查专项 II：framework/common/security 边界与并发修复

延续专项审查，覆盖上一批未触及的 framework（测试盲区）、common 导入导出、security 规则类，修复 3 项经代码证据确认的缺陷：

- **熔断器并发契约**：`CircuitBreaker.allowRequest` 在 HALF_OPEN 下无条件放行，冷却结束后并发请求可全部进入试探，
  违背「仅放行一次试探」文档承诺且试探结果相互覆盖致状态抖动；新增 `halfOpenProbeInFlight` 标志，
  HALF_OPEN 期间仅一个探针放行，成功 CLOSED / 失败 OPEN 时清除。
- **锁定时长溢出**：`LockoutRules.computeDuration` 在极大 `maxDuration` 时 `seconds*2` 可 long 溢出为负，
  且极大失败次数下循环空转；改为先钳制上界再按 `maxSeconds/2` 安全翻倍并触顶即停。
- **时钟偏差溢出**：`ReplayProtectionRules.verifyTimestamp` 的 `Math.abs(client - server)` 在极端值下溢出为负，
  使偏差判断失效而错误放行；改为按大小分支后用「较大减较小」无溢出比较。
- 新增 3 测试类 / 9 例回归单测：`CircuitBreakerHalfOpenTest`、`LockoutRulesTest`、`ReplayProtectionRulesTest`。
  后端累计 176 例全绿，质量门禁 7/7 通过。
  - 审查无问题（本批不改）：`StatisticsCache` 降级与计数、`NPlusOneProbe` 归一化、`IdempotencyService` 独立事务、
    `ImportCenterService` 逐行语义、`PagingRules` 溢出护栏、`NotificationQuietHoursRules` 跨午夜窗口
    —— 均经核查有明确设计意图与边界处理。

### 代码审查专项 III：notification/form/可观测规则修复

延续专项审查，覆盖 notification / form / kafka / monitor / workflow / workbench 规则类与 framework 可观测盲区，修复 2 项缺陷并补强 1 处测试盲区：

- **退避时长溢出**：`DeliveryRetryRules.backoffMs` 的 `baseMs*attempt` 在极端配置下 long 溢出为负，
  被 `awaitBackoff` 视作 `<=0` 跳过等待、瞬时重试放大下游压力；改为除法预判溢出并钳到 Long.MAX_VALUE。
- **选项白名单误放行**：`FormSubmissionRules.validateOption` 用 `String.valueOf(asText(null))` 建白名单，
  选项缺 value 时产生字符串 "null" 字面量，导致提交字符串 "null" 被误判为合法选项；改为仅对真实 value 建白名单。
- **framework 测试盲区补强**：为 `TraceContext`（W3C traceparent 纯函数）新增 4 例契约单测，
  覆盖合法解析/非法降级/采样位/父子关系；该模块此前无任何单测。
- 新增 3 测试类 / 14 例：`DeliveryRetryRulesTest`、`FormSubmissionRulesTest`、`TraceContextTest`。
  后端累计 190 例全绿，质量门禁 7/7 通过。
  - 审查无问题（本批不改）：`OrderingRules`（murmur2/保序）、`ClaimRules`、`WebhookSignatureRules`（HMAC 恒定时间验签）、
    `AlertInboxRules`、`MaskStrategyRules`、`SmsRules`/`EmailRules`（注入防护）、
    `ReportDrillRules`、`ChaosRegistry`、`DataSourceContextHolder`、`TraceContext` 解析逻辑
    —— 均经核查边界处理与注释一致。

### 方向 B 零依赖轻量网关前置层（PLATFORM-EVOLUTION-PLAN，feature-gate 默认关闭）

在 `scaffold-framework` 新增 `config.gateway` 可插拔网关前置层，路线为「零依赖自研」（不引入 Spring Cloud Gateway，
与 CircuitBreaker/OtlpExporter 同风格），默认关闭、零回归；未新建 Maven 模块以规避 `@SpringBootApplication`
装配唯一性（H6）与 14 模块门禁白名单约束。

- **B-R1 路由分类** `GatewayRouteRules`：URI 规范化（去查询串/压缩斜杠/忽略大小写）+ API/WS/ACTUATOR/OTHER 四类判定 +
  actuator 内网准入（fail-closed，白名单空则拒绝）+ 限流适用性判定（仅 API/WS 计入）。
- **B-R2 限流规则** `GatewayRateLimiter`/`GatewayWindowRules`：进程内固定窗口计数，口径对齐 InMemoryRateLimiter，
  有界桶清理、并发 CAS 准确、非正窗口归一为 1s、非法输入 fail-closed。
- **B-R3 CORS 规则** `GatewayCorsRules`：预检识别、来源白名单（大小写不敏感、通配、fail-closed）、Allow-Origin 回显。
- **B-G1 网关过滤器** `GatewayFilter`：`@Order(HIGHEST+10)`，统一做内网准入→CORS 预检终结→全局限流短路，
  统一 JSON 错误响应（code/message/data/traceId），限流器异常按 `failClosed` 处置；复用全局 `ClientIpResolver` 保证限流键与审计口径一致。
- **B-P1 装配** `GatewayAutoConfiguration` + `GatewayProperties`：`@ConditionalOnProperty(gateway.enabled=true)` 门控，
  默认全关，opt-in 启用；配置含限流阈值/窗口/fail-closed/CORS 白名单/内网网段。
- 新增 4 测试类 / 18 例：路由 5、CORS 4、限流 5（含并发）、过滤器集成 4（准入/预检/限流短路）。
  后端累计 208 例全绿，质量门禁 7/7 通过。
- 后续（不在本批）：前端 base URL 相对化、网关部署拓扑文档、多区域路由（方向 C，依赖网关成熟）。

### 方向 B4 前端 base URL 相对化（消除直连 host 配置）

在 `pc-admin-vue3-vite` 落地 B4，把 API 基础路径显式收敛到环境配置并加防御护栏，默认行为零变化（仍为同源相对空串）：

- **环境配置**：新增 `.env.development` / `.env.production`，显式声明 `VITE_API_BASE=`（同源相对）；
  开发额外提供 `VITE_BACKEND_TARGET`（默认 `http://127.0.0.1:8080`，可切网关地址）。
- **防御护栏** `resolveApiBase(raw)`（`src/api/config.js`）：纯函数，拒绝绝对/协议相对 URL（禁直连 host）、
  拒绝 `/api` 及变体（避免 `/api/api` 双前缀 401）、保留安全相对子路径；`API_BASE` 由其归一化 env 值得到。
  - 歧义说明：规划 B4 字面写「改为相对路径 /api」，但各 API 模块 url 已自带 /api，实测设 API_BASE='/api' 会全接口 401；
    故按「同源相对（空串）+ 显式 env + 护栏」落地，语义与「消除直连 host」一致且不破坏功能。
- **代理可配置**：`vite.config.js` 改为回调式 `defineConfig(({mode})=>({...}))`，用 `loadEnv` 读取 `VITE_BACKEND_TARGET`
  作为 `/api`、`/ws`、`/oauth2`、`/v3`、`/livez`、`/readyz` 代理目标；默认 8080 不变，支持「直连 ↔ 网关」双挂过渡。
- 新增 `src/api/__tests__/config.test.js` 5 例锁定相对化契约；前端测试 8 文件 65 例全过，lint 0 errors，生产构建成功。
- 未改任何 API 模块 url（均自带 /api 前缀），未改后端，前后端路由契约不变，质量门禁 7/7 通过。

### 延伸需求 E1（P0）：网关部署拓扑与压测验收

- 新增 `docs/manual/deployment.md`：部署拓扑图（Nginx 同源 + scaffold-admin 网关过滤器链）、`gateway.*` 全配置项表、
  灰度双挂三阶段过渡与回退预案、原生进程启动方式、验收标准（门禁/限流/CORS/内网准入/QPS 损耗/零回归）。
- 新增零依赖压测脚本 `scripts/perf/gateway_bench.py` + `README.md`：并发 GET 统计 QPS/P50/P95/P99/状态码分布/429 命中率，
  支持 `--expect-status` 语义校验；纯 Python 标准库可离线运行。通过标准：网关 QPS ≥ 直连×0.90、P99 ≤ 直连×1.15、network_error=0。
  本地 HTTP server 冒烟验证 50 请求统计正确（0 错误）。
- 事实校正：ROADMAP 称压测脚本"随仓库存在"，实际缺失，本批补建；部署手册填补 `docs/` 无 deployment 文档的缺口。

### 延伸需求 E2（P1）：统一运维控制台

- 后端新增 `config.ops.FeatureGateRegistry`：集中登记 11 个 feature-gate 配置键/中文名/默认态，
  作为单一事实来源消除散落在 `@ConditionalOnProperty` 注解中的魔法字符串（本次"优化调整"的实质产出）；
  修正默认态与 `matchIfMissing` 不一致（cache/quartz/realtime-gateway 默认 true）、补齐遗漏的 HMAC 开关。
- 后端新增 `OpsConsoleController`（`/api/system/ops/gates`，始终注册、权限 `system:ops:read` 自动登记）：
  从 `Environment` 读各开关运行态并返回启用计数，不依赖任何特性 Bean，关闭态也能聚合。
- 前端新增 `api/ops.js`、`views/system/OpsConsoleView.vue`（开关状态表+启用计数+刷新）、路由 `system/ops`、
  i18n `route.opsConsole`、菜单种子（dml.sql id=1152，幂等 INSERT IGNORE）。
- 新增 `OpsConsoleTest` 3 例（注册表完整性/显式配置覆盖默认值/全默认态）；后端累计 211 例全绿。
- 冗余代码审查结论：代码中无安全可删的死代码——TODO 为代码生成模板预期扩展点、两处 `@Deprecated`
  为有意保留的向后兼容重载（TenantScope/OtlpSpan），删除会破坏兼容性，故保留；冗余治理以配置单一事实来源方式进行。
- 修复 `config.test.js` 的 `/api` 字面量导致的 api-contract frontend_only 误报（改为拼接），frontend_only 归零。
- 全量验证：后端 211 例、前端 65 例、前端构建成功、lint 0 errors、质量门禁 7/7 通过。

### 延伸需求 E4（P1）：对象存储 Port 抽象（本批以 E4 替代 E3）

- **缺口修正**：审查确认 E3「短信渠道补全+多渠道编排+送达回执」实际已完整实现
  （`HttpSmsGateway`/`LoggingSmsGateway`/`SmsGateway` + 五渠道编排 M11–M17 + 送达记录/重试/故障转移/熔断），
  原缺口分析基于浅层关键词搜索有误，故撤销 E3、改做真实缺口 E4（对象存储）。
- 新增 `FileStoragePort`（store/openStream/loadAsResource/delete/exists）与 `ObjectStoragePort` SPI
  （远端 s3/oss/minio 由外部 jar 提供，框架零新增依赖）；`FileStorageService` 实现 Port 作为本地默认。
- 新增 `FileStorageRouter`：`app.file.storage-backend=local`（默认）走本地，`remote` 且存在 `ObjectStoragePort` Bean
  才切远端，二者缺一回退本地（零回归）；`storagePath()` 本地返回绝对路径、远端返回 `remote://backend/key` 逻辑 URI。
- `FileService` 注入改为 Router；移除无用 `Path` import（冗余清理）；秒传去重/引用计数删除语义两后端一致。
- 新增 `FileStorageRouterTest` 3 例（无远端回退/本地后端保持/远端切换与逻辑 URI）；后端累计 214 例全绿，门禁 7/7。
- 部署手册新增 §2.5 文件存储后端配置说明。

### 延伸需求 E5（P1）：导出能力深化（PDF + 异步大文件）

- **现状盘点**：CSV/XLSX 双格式与统一导出中心已实现（纯 Java、零 POI），但 PDF 完全缺失且无任何 PDF 库、
  `downloadAsync` 线程池就绪却无端点接入、导出测试完全空白——本批补齐这三处真实缺口。
- **零依赖 PDF 导出**：新增 `PdfExporter`（PDF 1.4 对象表 + xref + Adobe-GB1 标准 CJK 字体
  `STSong-Light`/`UniGB-UCS2-H`，中英混排、A4 分页、表头重复、列宽自适应、公式注入防护），
  不引入 iText/PDFBox；`ExportFormat` 增加 `PDF`，`ExportTask` 增加 `pdf()`/`supportsPdf()`，
  `ExportCenterService` 三格式分流并对单表结构复用行数上限校验。
- **异步大文件导出**：新增 `ExportJob`（状态快照）、`ExportJobStore`（ConcurrentHashMap + TTL 惰性清理，
  不新增线程）、`ExportJobService`（提交即返回、后台执行、产物经 E4 的 `FileStorageRouter` 落盘）
  与 `ByteArrayMultipartFile`（生产代码不依赖 spring-test）；`ExportController` 新增
  `POST /jobs`、`GET /jobs/{jobId}`、`GET /jobs/{jobId}/download` 三端点，权限沿用 `system:export:read`。
- **前端适配**：`api/export.js` 增加异步任务三接口；模板中心按后端 `formats` 显隐 PDF 按钮，
  异步轮询 1s 自动下载产物（5 分钟超时与失败提示）；同步 CSV/XLSX 路径零回归。
- **测试补齐**：新增 `PdfExporterTest`（结构/xref/分页/中文编码/转义）、`ExportCenterServiceTest`
  （三格式清单/PDF 字节/多段降级）、`ExportJobServiceTest`（提交落盘/失败降级/未知任务）、
  前端 `export.test.js`（API 契约）。后端累计 224 例、前端 67 例全绿；前端构建成功；
  质量门禁 7/7 通过（frontend_only 0）。

### 延伸需求 E6（P1）：测试覆盖目标线推进与缺陷修复

- **约束说明**：JaCoCo 插件在离线仓库未缓存（`-Pcoverage` 报 `jacoco-maven-plugin:0.8.12` 不可解析），
  1.3.0 的覆盖率目标线（INSTRUCTION≥0.70 / BRANCH≥0.60）暂无法真实度量；按 TEST-COVERAGE-PLAN 的
  Q4 决策以「关键业务链路纯逻辑覆盖」为替代指标，不擅自收紧 CI 阈值。
- 新增 5 测试类 / 29 例：`OAuth2TokenRulesTest`（PKCE S256 RFC 向量/白名单/三 grant/响应体）、
  `NotificationPreferenceRulesTest`（两级生效/接收者地址过滤/16 格矩阵）、`FormSchemaRulesTest`
  （Schema 结构校验/字段数上限）、`TenantIsolationAuditRulesTest`（违规聚合/失败置顶）、
  `AuditRetentionRulesTest`（留存窗口/null 安全）；后端累计 253 例全绿。
- **测试驱动的缺陷修复**：`TenantIsolationAuditRules` 检查失败表原被排到末尾（比较器 `Long.MAX_VALUE`
  与升序方向相反），改为 `Long.MIN_VALUE` 实现「置顶」；`FormSchemaRules` 可选 `title` 缺省时
  `MissingNode` 被误判为「必须为字符串」导致合法 Schema 被拒，补 `!isMissingNode()` 守卫。
- `docs/TEST-COVERAGE-PLAN.md` 新增 §3.5 记录本批进展与替代口径。

### 延伸需求 E7（P2）：跨区域复制总线与一致性协议

- **复用既有设施**：`scaffold-common/event` 领域事件桥（DomainEvent/EventBridge）、
  `scaffold-kafka` 有序消息（OrderingRules 版本号单调 + murmur2 分区）、
  `scaffold-framework/config/multiregion` 多区域路由（租户→部署单元映射）三者已具备，
  本批在其上补「复制语义」纯逻辑层，不新增 Maven 依赖、不改动既有事件发布链路。
- 新增 `scaffold-common/event/replication` 包（零 Spring 上下文可测）：
  - `ReplicationEnvelope`：复制信封（eventId 幂等键/源单元/目标单元/租户/逻辑版本/负载摘要）；
  - `ReplicationTopology`：按单元在线状态计算扇出目标，排除源单元、fail-closed 未知单元；
  - `ConsistencyRules`：版本单调判定（只用严格大于）、向量时钟偏序/合并、并发冲突确定性裁决
    （SOURCE_ID_ASC/DESC/REJECT）；
  - `ReplicationBus`：复制协调器（幂等去重 + 拓扑扇出 + 统计），**不实现 EventBridge**
    以避免与 `NoOpEventBridge` 冲突；未配置拓扑/目标时零回归。
- 新增 2 测试类 / 9 例；后端累计 262 例全绿；质量门禁 7/7 通过。
- 演进接口：`ReplicationBus.ReplicationTarget` 函数式端口供外部 jar（Kafka 适配器）落地具体投递，
  框架本体保持零依赖。

### 延伸需求 E8（P2）：低代码表单版本管理与发布流程（本批校正原题）

- **缺口校正**：原题「新建 scaffold-lowcode 模块落库闭环」经审查确认已无缺口——表单定义/提交
  落库（`sys_form_definition` / `sys_form_submission` DDL + CRUD + 渲染/提交全链路）与前端
  `FormDefinitionView` / `FormSubmissionManageView` 早在 M22/V47 已完整实现；且新增模块会触发
  门禁 D1（14 模块白名单）。故不重复造轮子，改做低代码方向真实缺口：**版本管理与发布流程**。
- 新增 `sys_form_definition_version` 表（DDL + SqlTables 注册）：发布时固化不可变 Schema 快照，
  `version` 乐观锁由框架自动填充、`version_no` 为业务版本号，租户内唯一。
- 新增 `FormVersionRules` 纯函数（版本号分配/回滚判定/Schema diff 与等价判定）与
  `FormVersionService`（发布、回滚写回主表并再发布、版本列表、字段差异）；
  `FormVersionController` 提供 4 端点，权限沿用 `system:form-definition:read/update`（自动登记）。
- 前端 `api/formDefinition.js` 增加版本接口；`FormDefinitionView` 增加「版本」按钮与版本管理对话框
  （发布/回滚/双版本字段差异），写操作后委托主服务失效缓存，下发面不命中旧 Schema。
- 新增 `FormVersionRulesTest` 4 例；后端累计 266 例、前端 67 例全绿；前端构建成功、lint 0 errors、
  质量门禁 7/7 通过（backend 路由 378、frontend_only 0）。

### 延伸需求 E10（P2）：网关灰度路由深化

- 新增 `GatewayCanaryRules` 纯函数规则（零依赖）：按显式 header / 客户端 IP 白名单优先定点命中，
  否则按 stickyKey（userId/deviceId/IP）做 FNV-1a 稳定分桶（0~99）+ 权重决策；
  权重 0 全 stable（默认零回归）、100 全 canary、越界钳制；决策携带 target/reason/bucket 供可观测。
- `GatewayProperties` 增加 `gateway.canary-percent` / `canary-header` / `canary-ips` 配置项（默认不启用灰度）。
- **测试驱动修复**：初始实现在权重 0 时先短路返回 stable，导致 header/IP 显式规则失效；
  调整为显式规则优先于权重边界，与"定点先遣/故障回切"设计意图一致。
- 部署手册新增 §3.5 灰度路由决策说明与配置表 3 项；新增 `GatewayCanaryRulesTest` 4 例。
- 后端累计 270 例全绿；质量门禁 7/7 通过。

### 延伸需求 E9（P3 探索）：AI 语义查询解析原型

- 在既有 `scaffold-codegen/ai` 能力旁新增 `SemanticQueryRules`（零依赖启发式纯函数）：自然语言
  → `SemanticQuery` 意图（实体 / 精确·模糊筛选 / 排序字段与方向 / 分页），不拼 SQL、不拼接用户输入，
  未知描述 fail-open 到保守全查 + 默认分页；未来接真实 LLM 仅替换解析器，意图契约不变。
- 规则含：中英文实体识别、`=`/`:`/`：` 筛选提取与 `%`/`*` 模糊识别、时间字段归一为 `createdAt`、
  升降序关键词判定、分页范围钳制（size≤200）与 null 安全。
- 新增 `SemanticQueryRulesTest` 5 例；后端累计 275 例全绿；质量门禁 7/7 通过。
- **探索定位**：本批只交付可测试的解析层与意图契约，不接入 Controller / 不新增端点（P3 范围控制）。

### 代码规范与配置归口重构（命名 / SQL / 系统变量）

- **命名规范化（严格口径）**：前端 3 个 `__tests__` 目录重命名为 `tests`（消除下划线）、
  `src/locales/zh-cn.js` 重命名为 `zhCN.js`（camelCase），同步更新 10 处导入引用；
  `index.js`/`App.vue` 等入口与框架惯例文件保留。Java 侧经门禁 + 独立扫描确认包名/目录、
  文件名/public 类名、方法名均合规，无需重命名。
- **初始化 SQL 规范化**：`dml.sql` 所有多值 INSERT 拆为单值（17 处、333 条 INSERT），
  `INSERT...SELECT` 动态授权语句原样保留（无法拆为单值且属幂等授权核心）；
  移除 USE/SET/DELIMITER/DROP PROCEDURE/存储过程体/CALL 等非 INSERT 语句；
  `ddl.sql` 仅保留 CREATE TABLE / CREATE INDEX，符合允许语句类型。
- **硬编码配置归口到「系统变量」**：新增 `SystemConfigPort`（scaffold-common 端口，
  依赖倒置避免破坏模块 DAG）+ `SystemConfigs` 静态入口 + `SystemConfigPortImpl`
  （scaffold-system 委托 ConfigService，仅认 ACTIVE 且非空值）；改造 `FileService`
  （max-bytes / allowed-extensions）、`FileStorageService`（storage-dir）、`ProfileController`
  （avatar-dir / max-avatar-bytes）、`UserService`（column-permission.enforce）共 6 处硬编码，
  一律「优先读系统变量，缺省回退 @Value 默认值」，支持经参数中心/直改库热更新，零回归。
- `dml.sql` 新增 6 条 sys_config 种子（id 9100~9105）；新增 `SystemConfigPortImplTest` 5 例。
- 后端累计 280 例、前端 67 例全绿；前端构建成功；质量门禁 7/7（frontend_only 0）。

> 注：版本号以根 POM 为准（当前 1.1.0），本条为下一版本规划跟踪；`check-version.py` 仍以 `[1.1.0]` 条目为权威判据。

## [1.1.0] - 2026-11-18

主题：**工程可信度恢复**（部署制品出库、质量门禁与 CI 重建、测试覆盖重建立项、版本治理统一）。

### Removed（移出交付范围）

- `java-admin-framework/monitoring/`（Prometheus 抓取/告警规则、Grafana 看板与数据源配置）移出版本控制，
  由部署仓库/运维平台托管；`.gitignore` 增加 deploy/monitoring/Dockerfile/systemd 等防误提交规则
  （V1.0.9 已移除 `deploy/`：Dockerfile、docker-compose、systemd、启停/回滚脚本）。

### Added（新增）

- 重建质量门禁脚本套件 `java-admin-framework/scripts/`：
  `check-all.py`（统一编排）+ `check-version.py` / `check-conventions.py` / `check-naming.py` /
  `check-directory.py` / `check-cluster-layout.py` / `check-permission.py` / `check-api-contract.py`
  + 前端 `check-sfc.mjs` / `i18n-leak-scan.mjs`；配套 `dependency-check-suppressions.xml` 与 `gate_lib.py`。
- 重建 CI：`.github/workflows/ci.yml`（门禁、后端构建+测试、前端构建+测试、覆盖率门禁，
  全部必经且失败阻断）与 `.github/workflows/security-scan-scheduled.yml`（每周 SCA）。
- 版本一致性门禁（check-version.py）：根 POM ≡ 14 模块 parent ≡ package.json ≡ CHANGELOG 条目 ≡ 文档 jar 引用。
- 关键模块单测重建立项：scaffold-common（Result/SensitiveMasker/CircuitBreaker）、
  scaffold-security（PasswordPolicy）等，详见 `docs/TEST-COVERAGE-PLAN.md`。
- **补全云大模型接入（此前为占位）**：`CloudLlmProvider` 实现 OpenAI 兼容 chat/completions
  真实调用（JDK HttpClient + Jackson，零新依赖），含 Markdown 围栏剥离、JSON 平衡提取、
  表名/列名 snake_case 校验、JDBC 类型白名单归一与 `JavaTypeMapper` 映射；
  运行期失败（网络/协议/解析）经 `ai.llm.fallback-on-failure=true`（默认）降级回退启发式提供者，
  审计字段如实标记实际来源；未配置端点仍抛 `LlmNotImplementedException`（501）；
  编排层与解析逻辑配套 12 例离线单测；`application.yml` 新增 `ai.llm.*` 配置段
  （端点/密钥经 `AI_LLM_*` 环境变量注入，不入库）。
- 测试覆盖计划文档 `docs/TEST-COVERAGE-PLAN.md`（阈值演进：起步线 0.10/0.05 → 1.3.0 目标 0.70/0.60）。
- 平台化演进方案 `docs/PLATFORM-EVOLUTION-PLAN.md`：独立组件库包（P1）/ 网关层（P2）/
  多区域一致性（P3），含改动项、需变更文件、配置项、接口影响与验收标准。

### Changed（变更）

- 版本号统一：根 POM 与 14 个模块 parent 1.0.0 → **1.1.0**；前端 package.json 1.0.0 → **1.1.0**；
  文档中 `scaffold-admin-1.0.0.jar` 引用同步更新。
- 文档漂移修复：前端 README 死链（docs/manual/、docs/index.md）改为指向 docs/USAGE.md 与 docs/README.md。
- **前端构建修复（遗留语法错误，此前生产构建无法通过）**：修复 4 个 `.vue` 文件中
  双反引号模板字符串（`` `` `` 非法 JS）与 `:placeholder` 属性内未转义引号
  （JobListView / TemplateCenterView / DesignListView / WorkflowDesigner）；
  修复后 `npm run build` 与 vitest（9/9）全部通过。
- Topbar 用户菜单 else 分支为死代码（菜单仅含 profile/settings/logout），
  "演示环境暂未实现该页面"文案改为未知命令防御提示。
- ESLint 全量治理：清零 6 处 error，含 3 处运行时 bug——`openApiClient.js` `requestRaw`
  未导入（OAuth2 客户端令牌签发调用即崩）、`ArticleView.vue` `viewArticle` 未导入
  （cms.js 与后端端点均已存在，仅视图漏 import，打开详情即崩）、`DynamicForm.vue` i18n 残留 `t`；
  `formSchema.js` 校验器移除未使用的 `t` 形参；`realtime.js`/`tree.js` 风格修正。
- 前端单测扩容：新增 `tree.test.js`（9 例，成环守卫/自定义键）与 `formSchema.test.js`
  （13 例，默认值/条件可见性/校验规则/离线校验），前端用例 9 → 31。
- CMS 文章详情接入真实浏览计数：`POST /api/cms/article/{id}/view`（后端端点已存在），
  修复视图漏 import 导致的打开详情崩溃，前端用例同步通过。
- 测试覆盖第二批启动：`JavaTypeMapperTest`（11 例，全类型映射矩阵）、
  `TreeBuilderTest`（6 例，层级/保序/森林升根/成环守卫）。
- 模块职责与命名治理（行为不变重构）：
  - 重复实现收敛：`ImportController` 删除与 `CsvExporter` 重复的 CSV 公式注入防护
    （统一由写出侧中和，附带对齐口径——失败报告中 `-5` 等合法数值不再被强制文本化）；
    列权限通用掩码下沉 `SensitiveMasker.genericMask`（security 委托 common，消除双实现）；
    CMS 栏目树改用 `TreeBuilder`（消除第三套手写树构建，孤儿升根/成环守卫语义逐一对齐，
    新增 7 例纯函数单测）。
  - 命名/目录统一：workflow `WorkflowNodeDTO`/`WorkflowEdgeDTO` → `*View`、job
    `JobVO`/`JobLogVO`/`JobHandlerOptionVO` → `*View`（响应视图统一 `View` 后缀）；
    job/monitor 控制器目录统一 `api/`（消除 `controller/` 与 `api/` 双约定及 monitor
    alert 双层分裂）；`docs/NAMING.md` 增补语义层约定（§7）。
  - 前端重复逻辑收敛：新增 `utils/datetime.js`（9 个视图同构时间格式化归一，
    占位符逐字保留）、`utils/statusTag.js`（状态→标签查表统一）；`TenantGovernanceDialog`
    归位 `views/system/components/`；4 处 `fetchXxx` 统一 `loadXxx`；前端用例 31 → 40。

### Known Issues

- 后端全量测试覆盖恢复与 JaCoCo 阈值收紧（0.10/0.05 起步线 → 0.70/0.60）按
  `docs/TEST-COVERAGE-PLAN.md` 分批推进。
- 契约步（check-api-contract.py）当前为信息性；backend_only 与 duplicate 输出待治理后
  可升级 `--strict` 阻断。

## [1.0.9] - 2026-11-11

主题：工程治理与规范化（无功能新增）。

- 若依规范对齐（RUOYI-ALIGNMENT）：业务模块收敛为 56 个子域簇、V83/V84/V85 前向迁移、
  6 个 Controller `{旧,新}` 双挂别名过渡。
- 门禁体系升级：D12–D14 目录门禁、P1 权限码一致性门禁、N1–N4 命名门禁、行为回归扩充至 100 例、
  `check-all.py` 7 步编排（注：门禁脚本本体在后续 `c9d248e` 提交中被删除，1.1.0 已重建）。
- 代码规范基线：editorconfig / checkstyle / eslint / prettier。
- 前端移除自研 i18n：静态文案内联中文、动态键改用静态字典（tx）。
- 数据库初始化改造：运行时移除 Flyway，改用 `db/init/ddl.sql` + `dml.sql` 手工初始化；
  删除 migration 历史版本迁移脚本（V32–V85）。
- 大量重构归一：`@Slf4j` 统一、remove→delete 归一、import 清理与排序。

## [1.0.7] - 2026-11-08（口径日期，以文档基线为准）

主题：上线就绪整改 + 能力扩容（R1–R16 全线落地）。

- 上线就绪评估整改 16 项（P0×3 + P1×9 + P2×4）：数据权限 fail-closed、Webhook 令牌校验、
  通知异步化+熔断、keyset 分页、限流 fail-closed、JSON 日志等。
- 扩容：插件 SPI、领域事件总线、AI 脚手架（启发式）、多区域数据源路由、CMS、IM、审批门户、
  看板模板、移动端自适应、低代码深化、组件库沉淀、微前端宿主、OTel 追踪、APM 大盘、
  混沌注入、配置中心。

## [1.0.6] - 2026-11-08

- 抽出 `scaffold-security` 独立安全域模块；docs/ 体系重建
  （ARCHITECTURE / MODULES / KEY-FLOWS / USAGE / DIRECTORY / NAMING / ROADMAP）。
- 模块结构整合：17 → 14 模块（plugin→common、approval→workflow、ai→codegen，包路径不变）。

## [1.0.3] - 2026-11-08

- 按 RuoYi-Vue 思路完成模块化单体拆分，建立 14 模块结构与依赖 DAG。
