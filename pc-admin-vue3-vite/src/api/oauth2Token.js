import { request } from './http'

// OAuth2 令牌管理（M43）：活动令牌分页查询与吊销治理面。
// 后端 base-path：/api/system/oauth2-tokens
// 权限：查询 system:oauth2-token:read；吊销 system:oauth2-token:revoke
// 说明：page 仅返回「有效且未过期」的令牌（status=ACTIVE AND expires_at>now）。
// 吊销经 OAuth2TokenRevocationFilter 在请求期实时生效，持有者下一次请求即被拒绝。

/** 分页查询活动 OAuth2 令牌（支持 clientId 模糊 / userId 精确，服务端按 expiresAt desc 排序）。 */
export const pageActiveTokens = (params) =>
  request({ method: 'GET', url: '/api/system/oauth2-tokens/page', params })

/** 吊销指定 jti 的 OAuth2 令牌（立即生效）。 */
export const revokeToken = (jti) =>
  request({ method: 'POST', url: `/api/system/oauth2-tokens/${jti}/revoke` })
