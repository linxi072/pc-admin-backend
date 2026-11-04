import { request } from './http'

// R15 混沌工程：故障注入治理面（后端 ChaosController，需 app.chaos.enabled=true）。
// 统一经拆包层发出，复用 Result<T> 拆包与统一错误处理；注释不含 URL，以免干扰契约测试计数。

// 查询当前注入中的故障列表
export const listFaults = () =>
  request({ method: 'GET', url: '/api/system/chaos/faults' })

// 注入一条故障（延迟 / 错误 / 异常）
export const injectFault = (body) =>
  request({ method: 'POST', url: '/api/system/chaos/faults', data: body })

// 按 id 解除单条故障
export const releaseFault = (id) => request({ method: 'DELETE', url: `/api/system/chaos/faults/${id}` })

// 清除全部故障注入
export const clearFaults = () =>
  request({ method: 'DELETE', url: '/api/system/chaos/faults' })
