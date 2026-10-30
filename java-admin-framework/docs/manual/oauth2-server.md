# OAuth2 授权服务器（协议级开放平台）

> 适用范围：java-admin-framework（`scaffold-system` 安全模块）  
> 文档目标：说明 OAuth2 自签发令牌端点的定位、授权模式、密钥与公钥暴露、以及资源服务器如何基于 scope 做接口授权。  
> 定位：离线可行子集——仅实现 **资源服务器 + 自签发令牌端点（RS256）**，不含 `spring-security-oauth2-authorization-server` 授权码流程。

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
