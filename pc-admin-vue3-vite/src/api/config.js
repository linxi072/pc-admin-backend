// 全局开关：USE_MOCK=true 时所有请求走前端内置 mock（无需后端即可演示）；
// 置为 false 并 npm run dev 后，请求经 vite proxy 转发到 http://127.0.0.1:8080（java-admin-framework）。
export const USE_MOCK = true

// 真实后端基础路径（USE_MOCK=false 时生效）。开发期由 vite.config.js 的 proxy 转发。
export const API_BASE = '/api'
