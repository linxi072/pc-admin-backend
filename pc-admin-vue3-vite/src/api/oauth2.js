import { requestRaw } from './http'

// OAuth2 授权码 + PKCE + 用户同意（consent）流程的前端适配（#900）。
// 后端授权端点 GET /oauth2/authorize、同意端点 POST /oauth2/consent。
//
// 流程：
//   1) 第三方客户端先调用 GET /oauth2/authorize?response_type=code&...
//      - 若用户尚未同意：返回 { error:'consent_required', consent_token, client_id, scopes, state }
//        客户端把浏览器重定向到管理端同意页 /#/oauth2/consent?consent_token=...
//      - 已同意（或「记住授权」命中）：直接返回 { code, state }
//   2) 同意页解码 consent_token（仅展示，不验签）呈现授权范围与回调地址；
//   3) 用户点击「允许」→ POST /oauth2/consent { consent_token, decision:'allow', remember }
//      → 返回 { code, state }，前端再带 code+state 回跳客户端 redirect_uri 完成授权。

/**
 * 解码 JWT（JWS）的 payload 段，仅用于「展示」，不做签名验签。
 * 安全校验由后端 RS256 验签承担（consent_token 的 subject/client_id/scope 等以服务端验签后为准）。
 * @param {string} token RS256 签名的 JWT
 * @returns {object|null} payload 对象；解码失败返回 null
 */
export function decodeJwtPayload(token) {
  if (!token || typeof token !== 'string') return null
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    let b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const pad = b64.length % 4
    if (pad) b64 += '='.repeat(4 - pad)
    const json = decodeURIComponent(
      atob(b64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

/** 把 scope 字符串拆成数组（去空、去重保序）。 */
export const splitScopes = (s) =>
  Array.from(new Set((s || '').split(',').map((t) => t.trim()).filter(Boolean)))

/**
 * 构造授权端点 URL（供客户端跳转用户至授权页；本 SPA 通常不直接调用，仅作工具）。
 */
export function buildAuthorizeUrl({
  baseUrl = '',
  clientId,
  redirectUri,
  scope,
  state,
  codeChallenge,
  codeChallengeMethod = 'S256'
}) {
  const u = new URL('/oauth2/authorize', baseUrl || (typeof window !== 'undefined' ? window.location.origin : ''))
  u.searchParams.set('response_type', 'code')
  u.searchParams.set('client_id', clientId)
  if (redirectUri) u.searchParams.set('redirect_uri', redirectUri)
  if (scope) u.searchParams.set('scope', scope)
  if (state) u.searchParams.set('state', state)
  if (codeChallenge) u.searchParams.set('code_challenge', codeChallenge)
  if (codeChallengeMethod) u.searchParams.set('code_challenge_method', codeChallengeMethod)
  return u.toString()
}

/**
 * 提交用户同意决策（POST /oauth2/consent，JSON）。
 * body: { consent_token, decision: 'allow'|'deny', remember?: boolean }
 * 成功（allow）返回后端原始包络 { code, state? }（非 Result<T> 包裹，故走 requestRaw）。
 * 该端点要求已认证（Bearer），依赖 http 层注入的访问令牌；其余错误由 http 拦截器统一提示。
 * @returns {Promise<object>} 解析后的响应体 { code, state? }
 */
export const submitConsent = (body) =>
  requestRaw({ method: 'POST', url: '/oauth2/consent', data: body }).then((resp) => resp.data)

/**
 * 拼接「授权码回跳客户端」的完整 URL：redirect_uri?code=...&state=...
 * @param {string} redirectUri 客户端登记的回调地址（来自 consent_token，展示用）
 * @param {string} code 授权码
 * @param {string} [state] 透传的 state
 * @returns {string|null} 完整回跳 URL；redirectUri 缺失返回 null
 */
export function buildCodeRedirectUrl(redirectUri, code, state) {
  if (!redirectUri) return null
  const sep = redirectUri.includes('?') ? '&' : '?'
  let url = `${redirectUri}${sep}code=${encodeURIComponent(code)}`
  if (state) url += `&state=${encodeURIComponent(state)}`
  return url
}
