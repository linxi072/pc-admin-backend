import { request } from './http'

// E2 统一运维控制台：后端 OpsConsoleController（/api/system/ops），始终注册。
// 聚合返回各 feature-gate 的运行态（含显式配置与默认值识别）。

// 查询各特性开关运行态
export const getGateStatus = () =>
  request({ method: 'GET', url: '/api/system/ops/gates' })
