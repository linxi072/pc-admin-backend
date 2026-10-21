import { request } from './http'

// 幂等记录管理（GET/POST /api/system/idempotency-records，权限 system:idempotency:read / :manage）。
// 契约来源：common/idempotency/controller/IdempotencyRecordController.java。
// response_snapshot 契约上不暴露（后端最小必要原则），页面无对应列。
// 不提供单条删除：删除 PROCESSING 中记录会使同 key 在途请求重复执行，仅提供过期清理。

// GET /api/system/idempotency-records/page?page&size&status&apiScope&userId&keyword&startTime&endTime&sortField&sortDirection
//   -> PageResult<IdempotencyRecordView>
export const pageIdempotencyRecords = (params) =>
  request({ method: 'GET', url: '/api/system/idempotency-records/page', params })

// GET /api/system/idempotency-records/summary?（同筛选口径）-> IdempotencyStatsView
export const getIdempotencySummary = (params) =>
  request({ method: 'GET', url: '/api/system/idempotency-records/summary', params })

// POST /api/system/idempotency-records/purge -> Integer（手动清理过期记录，与周期任务同口径）
export const purgeIdempotencyRecords = () =>
  request({ method: 'POST', url: '/api/system/idempotency-records/purge' })
