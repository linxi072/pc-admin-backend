import { request } from './http'

// R16 配置中心：状态 / 快照 / 热刷新（后端 ConfigCenterController，需 app.config-center.enabled=true）。
// 统一经拆包层发出，复用 Result<T> 拆包与统一错误处理；注释不含 URL，以免干扰契约测试计数。

// 读取配置中心运行状态（类型 / 是否支持动态刷新 / 生效配置数）
export const getConfigStatus = () =>
  request({ method: 'GET', url: '/api/system/config-center/status' })

// 拉取配置快照，prefix 可选按前缀过滤
export const getConfigSnapshot = (params) =>
  request({ method: 'GET', url: '/api/system/config-center/snapshot', params })

// 触发一次热刷新（重新加载并广播变更）
export const refreshConfig = () => request({ method: 'POST', url: '/api/system/config-center/refresh' })
