import { request } from './http'

// N+1 查询探测（GET/DELETE /api/system/monitor/n-plus-one，权限 system:monitor:read）。
// 契约来源：monitor/controller/NPlusOneController.java。
// 注意清空端点用的是 @DeleteMapping("/suspects")，与「删除集合」语义一致。

// GET /api/system/monitor/n-plus-one/suspects?limit -> NPlusOneSuspectView[]（最新在前）
export const listSuspects = (params) =>
  request({ method: 'GET', url: '/api/system/monitor/n-plus-one/suspects', params })

// GET /api/system/monitor/n-plus-one/status -> NPlusOneStatusView（阈值/容量/留存/累计越限）
export const getNPlusOneStatus = () =>
  request({ method: 'GET', url: '/api/system/monitor/n-plus-one/status' })

// DELETE /api/system/monitor/n-plus-one/suspects -> Boolean（清空留存，累计计数不清零）
export const clearSuspects = () =>
  request({ method: 'DELETE', url: '/api/system/monitor/n-plus-one/suspects' })
