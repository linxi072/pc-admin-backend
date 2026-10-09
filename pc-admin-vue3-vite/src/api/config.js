// 真实后端基础路径。开发期由 vite.config.js 的 proxy 转发到 http://127.0.0.1:8080（java-admin-framework）。
// 约定：所有页面与组件的数据统一经后端接口获取，前端不保留任何本地模拟数据（无 USE_MOCK 开关）。
export const API_BASE = '/api'
