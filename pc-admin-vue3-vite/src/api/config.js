// 真实后端基础路径。开发期由 vite.config.js 的 proxy 将 /api 转发到 http://127.0.0.1:8080（java-admin-framework）。
// 约定：所有页面与组件的数据统一经后端接口获取，前端不保留任何本地模拟数据（无 USE_MOCK 开关）。
//
// 注意：各 API 模块（auth.js / menu.js / user.js …）的请求 url 已自带 /api 前缀（如 '/api/auth/login'），
// 因此此处必须留空，由 vite proxy 统一把 /api 前缀转发到后端。
// 若写成 '/api'，axios 拼出的实际路径会变成 /api/api/auth/login，
// 后端 SecurityConfig 中 permitAll 的 AntPathRequestMatcher("/api/auth/login") 无法匹配，
// 请求落入 anyRequest().authenticated() 而无令牌 → 登录等所有接口返回 401。
export const API_BASE = ''
