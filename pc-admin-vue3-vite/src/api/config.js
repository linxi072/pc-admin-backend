// 真实后端基础路径（方向 B / B4：相对路径，消除直连 host 配置）。
//
// 约定：所有 API 模块（auth.js / menu.js / user.js …）的请求 url 已自带 /api 前缀
// （如 '/api/auth/login'），因此本值默认必须为空串 —— 由 Vite proxy（dev）或
// 网关/同源 Nginx（prod）把 /api 前缀转发到后端。若写成 '/api'，axios 会拼出
// /api/api/auth/login，后端 SecurityConfig 的 permitAll("/api/auth/login") 无法匹配，
// 请求落入鉴权链而全部返回 401。
//
// 改造点（B4）：把 base URL 显式收敛到 .env.* 的 VITE_API_BASE，默认空串=同源相对；
// resolveApiBase 作防御性归一化，拒绝绝对 host 与 /api 双前缀误配，保证相对化契约不被破坏。

/**
 * 归一化 API 基础路径为「同源相对路径」。
 *
 * 规则（fail-safe，任何可疑输入都退化为 ''，即同源相对）：
 * - null/undefined/空白 → ''
 * - 含协议或协议相对（http://、https://、//）→ ''（禁止直连 host，破坏网关/同源部署）
 * - 值为 '/' 或 '/api'（含尾部斜杠变体）→ ''（API 模块已自带 /api，避免双前缀）
 * - 其它相对前缀（如网关子路径 '/console'）→ 去除尾部斜杠后保留
 *
 * 纯函数、无副作用，便于离线单测锁定相对化契约。
 *
 * @param {unknown} raw 原始配置值
 * @returns {string} 归一化后的相对基础路径
 */
export function resolveApiBase(raw) {
  if (typeof raw !== 'string') return ''
  let v = raw.trim()
  if (!v) return ''
  // 禁止绝对 URL 与协议相对 URL（直连 host）
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(v) || v.startsWith('//')) return ''
  // 禁止 /api 前缀重复（API 模块 url 已自带 /api）
  const trimmed = v.replace(/\/+$/, '')
  if (trimmed === '' || trimmed === '/' || trimmed.toLowerCase() === '/api') return ''
  return trimmed
}

/** 运行时 API 基础路径：dev/prod 默认 ''（同源相对），仅允许安全的相对前缀。 */
export const API_BASE = resolveApiBase(
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE) || ''
)
