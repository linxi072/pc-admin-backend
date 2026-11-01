# OAuth2 授权服务器（协议级开放平台）

> 适用范围：java-admin-framework（`scaffold-system` 安全模块）  
> 文档目标：说明 OAuth2 自签发令牌端点的定位、授权模式、密钥与公钥暴露、以及资源服务器如何基于 scope 做接口授权。  
> 定位：离线可行子集——实现 **资源服务器 + 自签发令牌端点（RS256） + 授权码模式（authorization_code）+ PKCE + 用户同意（consent）**。授权码 / PKCE / consent 为轻量自研实现（非 `spring-security-oauth2-authorization-server`），授权码与访问令牌仍由同一 RS256 密钥体系签发，与 `client_credentials` 共用复合解码器与 `scope` 权威映射。

---

## 1. 定位与层级

- **定位**：为外部系统 / 第三方应用提供「客户端凭据换取访问令牌」的能力，令牌使用本应用自签名的 **RS256** 密钥签发，外部资源方通过 JWKS 公钥自行验签。
- **层级**：与既有 HS256 登录令牌（用户会话）**并存于同一资源服务器**——复合解码器先尝试 HS256 再回退 RS256，二者算法不同互不干扰。
- **与登录令牌的区别**：OAuth2 客户端令牌 `sub=client_id`（非数字），`userId=null`，不进入 `TokenVersionVerifier` 与 `OnlineSessionFilter` 的用户会话校验；其访问范围由 `scope` 声明驱动。

## 2. 核心组件

| 组件 | 类 | 职责 |
| --- | --- | --- |
| 令牌端点 | `OAuth2TokenEndpoint` | `POST /oauth2/token`，支持 `client_credentials` |
| 令牌签发 | `OAuth2TokenProvider` | RS256 签发 `client_id`/`scope`/`iss`/`aud`/`tokenVersion` 声明 |
| 密钥提供 | `RsaKeyProvider` | 优先读配置固定私钥，否则生成内存临时密钥（重启失效并告警） |
| 公钥暴露 | `JwksEndpoint` | `GET /oauth2/jwks`，暴露 RS256 公钥供外部验签 |
| 解码配置 | `OAuth2ServerConfig` | RS256 解码器 + 复合解码器（`@Primary`，HS256 优先） |
| 权威映射 | `JwtAuthConverter` | `scope` 声明映射为 `SCOPE_<scope>` 权威，供 `@PreAuthorize` 使用 |
| 授权端点 | `OAuth2AuthorizeEndpoint` | `GET /oauth2/authorize`（`response_type=code`）：校验 client/PKCE/scope/redirect_uri，已同意则直签授权码，否则签发 RS256 `consent_token` 引导至同意页 |
| 同意端点 | `OAuth2ConsentEndpoint` | `POST /oauth2/consent`：校验 `consent_token`（RS256 + subject 匹配当前用户），记录同意（可选「记住授权」）并签发一次性授权码 |
| 授权码服务 | `OAuth2AuthorizationCodeService` | 签发/校验一次性授权码，绑定 PKCE 挑战、scope、redirect_uri、用户与客户端上下文 |
| 同意服务 | `OAuth2ConsentService` | 判断是否已批准（覆盖所请求 scope）、记录/查询已批准同意（支持过期与「记住授权」） |
| 同意数据端口 | `OAuth2ConsentDataPort` / `OAuth2ConsentAdapter` | 防腐层：security 定义端口，system 经 jOOQ 落库 `sys_oauth2_consent` |

## 3. 授权模式：client_credentials

### 3.1 请求

```bash
curl -X POST 'http://<host>:<port>/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=<client_id>&client_secret=<client_secret>'
```

- `client_id` / `client_secret` 也可放在 `Authorization: Basic base64(client_id:client_secret)` 头（RFC 6749 §2.3.1，优先于表单参数）。
- 客户端认证复用 `OpenApiClientService#authenticateClient`（BCrypt 密钥哈希 + 状态/过期校验），在 `sys_open_api_client` 表维护。

### 3.2 响应

```json
{
  "access_token": "<RS256 JWT>",
  "token_type": "Bearer",
  "expires_in": 1800,
  "scope": "read write"
}
```

- 失败响应遵循 OAuth2 错误码：`invalid_request`（参数缺失/不支持的 grant_type）、`invalid_client`（客户端认证失败，统一口径不泄露具体原因）。

### 3.3 用令牌调用受保护接口

```bash
curl 'http://<host>:<port>/api/xxx' \
  -H 'Authorization: Bearer <RS256 JWT>'
```

资源服务器端到端链路：`Bearer` → RS256 解码器 → `JwtAuthConverter` 将 `scope` 映射为 `SCOPE_read` / `SCOPE_write` 等权威 → `@PreAuthorize("hasAuthority('SCOPE_read')")` 生效。

## 4. 密钥与公钥配置

```yaml
app:
  security:
    oauth2:
      # Base64 编码的 PKCS#8 DER 私钥（生产必填，否则重启后已签发令牌无法校验、JWKS kid 变化）
      private-key: ${OAUTH2_PRIVATE_KEY:}
      # 可选：自定义密钥标识（kid），默认 oauth2-rs256
      key-id: ${OAUTH2_KEY_ID:oauth2-rs256}
      # issuer / audience / TTL 与既有 JWT 配置同源（JwtProperties）
      issuer: ${OAUTH2_ISSUER:acme-admin}
      audience: ${OAUTH2_AUDIENCE:acme-admin}
      token-ttl: ${OAUTH2_TOKEN_TTL:30m}
```

- 公钥获取：直接 `GET /oauth2/jwks`，或本地用私钥导出。外部资源方应缓存 JWK 并按 `kid` 复用，避免每次请求拉取。
- 临时密钥仅用于本地开发/测试：未配置 `private-key` 时生成内存密钥并打印告警，重启后旧令牌失效。

## 5. 自测验证

- `OAuth2TokenEndpointTest`：令牌端点成功/Basic 头/非法 grant/客户端认证失败。
- `OAuth2TokenProviderTest`：RS256 签发后经公钥解码可还原 `client_id`/`scope`。
- `OAuth2TokenRulesTest`：请求形态校验与响应体拼装。
- `OAuth2ResourceServerContractTest`：真实令牌 → 解码 → 转换，断言映射 `SCOPE_read`/`SCOPE_write` 且 `userId=null`。
- `OAuth2ScopeAuthorizationTest`：真实令牌调 `@PreAuthorize("hasAuthority('SCOPE_read')")` 端点，带 read → 200、缺 read → 403、无 Bearer → 401。

## 6. 授权码模式（authorization_code）+ PKCE + 用户同意（consent）

> 适用场景：第三方应用代表**资源拥有者（终端用户）** 获取访问令牌（三方委托，区别于 `client_credentials` 的机器对机器）。  
> 特性：强制 **PKCE（RFC 7636）**（客户端可声明 `pkce_required`）、**用户同意（consent）**、授权码绑定 PKCE 挑战 / scope / `redirect_uri`。  
> 版本：v1.0.5（#900）。

### 6.1 总体时序

```
第三方客户端                  管理端 SPA(本仓库前端)              授权服务器(/oauth2/*)
   │                                │                                  │
   │ 1. 跳转用户至 /oauth2/authorize  │                                  │
   │   (response_type=code&          │                                  │
   │    client_id&redirect_uri=      │                                  │
   │    scope&state&code_challenge)  │                                  │
   │ ───────────────────────────────▶│  GET /oauth2/authorize ──────────▶│
   │                                │                                  │ 校验 client/PKCE/
   │                                │                                  │ scope/redirect_uri
   │                                │                                  │ 查 consent
   │                                │◀─ consent_required + consent_token─│ (未同意)
   │ ◀─ 重定向 SPA 同意页 ──────────│                                  │
   │   /#/oauth2/consent?           │                                  │
   │   consent_token=...            │                                  │
   │                                │ 2. 展示同意页(解码 token 仅展示)   │
   │                                │  POST /oauth2/consent ────────────▶│ 校验 token(subject
   │                                │   {consent_token,decision,        │ =当前用户)
   │                                │    remember}                      │ 记录同意+签授权码
   │                                │◀──────────── {code,state} ─────────│
   │ ◀─ 浏览器跳转 redirect_uri? ────│                                  │
   │    code=...&state=...          │                                  │
   │                                │                                  │
   │ 3. POST /oauth2/token ────────────────────────────────────────────▶│ grant_type=
   │   (grant_type=authorization_code,                                  │ authorization_code
   │    code, code_verifier,                                         │ + 校验 PKCE
   │    redirect_uri, client_id)                                     │ + 一次性核销
   │ ◀──────── 访问令牌(RS256) ──────────────────────────────────────│
```

### 6.2 授权端点 `GET /oauth2/authorize`

**要求已认证（Bearer）**：本端点不加入 `SecurityConfig#BASE_PUBLIC_API`，由资源服务器过滤器链要求调用方携带合法 Bearer 令牌（即已登录用户）；未认证返回 401 / `login_required`。这与「无状态 Bearer / 无 Cookie 会话」架构一致——登录态由调用方已有的访问令牌证明。

**请求参数**

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `response_type` | 是 | 固定 `code` |
| `client_id` | 是 | 启用中的客户端 |
| `redirect_uri` | 否* | 须命中客户端登记白名单（`sys_open_api_client.redirect_uris`，非空时精确匹配；空=向后兼容不约束） |
| `scope` | 否 | 须为客户端授权范围的子集，逗号分隔 |
| `state` | 否 | 透传防 CSRF，原样回带 |
| `code_challenge` | 否* | PKCE 挑战值（S256/plain） |
| `code_challenge_method` | 否 | `S256`(默认) 或 `plain`；声明 `code_challenge` 时须合法 |

\* 强制 PKCE 客户端（`pkce_required=true`）必须携带 `code_challenge`，否则返回 `invalid_request`。

**响应（JSON，非 `Result<T>` 包裹）**

- 已同意 / 「记住授权」命中：直接返回授权码
  ```json
  { "code": "<一次性授权码>", "state": "<原样回带，可选>" }
  ```
- 需用户同意：返回 `consent_required` 与签名 `consent_token`
  ```json
  {
    "error": "consent_required",
    "consent_token": "<RS256 JWT, 5分钟有效, 含 client_id/scope/redirect_uri/state/code_challenge>",
    "client_id": "c1",
    "scopes": ["read", "write"],
    "state": "<可选>"
  }
  ```
- 失败：标准 OAuth2 错误码（`invalid_client` / `invalid_scope` / `invalid_request` / `unsupported_response_type` / `login_required`），HTTP 4xx + `{error, error_description}`。

> 说明：RFC 6749 授权码应以 302 重定向下发；本平台 API 优先，直接以 JSON 返回，由调用方（通常是前端 SPA）将 `code` 交给令牌端点兑换，等效达成授权委托。

### 6.3 同意端点 `POST /oauth2/consent`

**请求体（JSON，`application/json`）**

```json
{ "consent_token": "<RS256 JWT>", "decision": "allow", "remember": true }
```

- `consent_token`：授权端点签发的 RS256 令牌（5 分钟有效）。后端 RS256 验签，且 `sub` 须等于当前登录用户，杜绝跨用户授权。
- `decision`：`allow`（允许）或 `deny`（拒绝）。`deny` 返回 `access_denied`。
- `remember`：是否「记住授权」（缺省视为 `true`）；为 `true` 时在 `sys_oauth2_consent` 记录覆盖所请求 scope 的已批准同意，后续同客户端同 scope 跳过同意页（直至过期）。

**响应（JSON，非 `Result<T>` 包裹）**

```json
{ "code": "<一次性授权码>", "state": "<原样回带，可选>" }
```

调用方（前端）拿到 `code` + `state` 后，将浏览器跳转至 `redirect_uri?code=...&state=...`，由第三方客户端兑换令牌。

### 6.4 PKCE（RFC 7636）

- 授权端点：`code_challenge` / `code_challenge_method` 经 `OAuth2TokenRules.validatePkce` 结构校验（仅 `S256`/`plain`，长度与字符集）。
- 授权码绑定：签发授权码时写入 `code_challenge` 与 `code_challenge_method`（`OAuth2AuthorizationCodeService.CodeContext`）。
- 令牌端点：兑换授权码时校验 `code_verifier` 经 `S256(code_verifier)` 须等于 `code_challenge`（plain 则相等），失败返回 `invalid_grant`。授权码一次性核销（用后即焚）。

### 6.5 令牌兑换 `POST /oauth2/token`

在既有 `client_credentials` 基础上新增 `grant_type=authorization_code`：

```bash
curl -X POST 'http://<host>:<port>/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=authorization_code' \
  -d 'client_id=<client_id>' \
  -d 'client_secret=<client_secret>' \
  -d 'code=<授权码>' \
  -d 'code_verifier=<PKCE verifier>' \
  -d 'redirect_uri=<redirect_uri>'
```

> 客户端认证复用 `OpenApiClientService#authenticateClient`（与 `client_credentials` 同口径）；`redirect_uri` 须与授权时一致。返回包络同 §3.2。

### 6.6 前端同意页（pc-admin-vue3-vite）

前端 `src/views/oauth2/ConsentView.vue` + 路由 `/oauth2/consent`（独立于后台布局的全屏页，路由守卫对已认证用户放行、不依赖菜单树）。关键适配见 `src/api/oauth2.js`：

- `decodeJwtPayload(token)`：仅解码 `consent_token` 的 payload 段用于**展示**（client_id / scope / redirect_uri / state），不做签名验签——安全校验由后端 RS256 验签承担。
- `submitConsent(body)`：走 `requestRaw`（因端点返回原始包络非 `Result<T>`），成功解析 `{code, state}`。
- `buildCodeRedirectUrl(redirectUri, code, state)`：拼接 `redirect_uri?code=...&state=...` 全页回跳。

vite 代理已新增 `/oauth2` 转发到后端 8080，dev 下可直接联调授权流程。

### 6.7 自测验证

- `OAuth2AuthorizeEndpointTest`：PKCE 强制校验、scope 越权、redirect_uri 白名单、已同意直签授权码、未同意返回 `consent_required` + `consent_token`。
- `OAuth2ConsentEndpointTest`：Jackson 绑定（`consent_token`/`decision`/`remember` snake_case）、`sub` 与当前用户不匹配拒签、`allow` 返回 `{code,state}`、`deny` 返回 `access_denied`。
- `OAuth2AuthorizationCodeServiceTest`：授权码一次性核销、绑定 PKCE 挑战与 scope。
- `OAuth2ConsentServiceTest`：已批准判定（覆盖子集）、`remember=true` 记录规范化 scope（顺序无关）。
- 后端门禁：`mvn -o -pl scaffold-system -am clean test`（948 例全绿）+ `python3 scripts/check-conventions.py`（exit 0）。
