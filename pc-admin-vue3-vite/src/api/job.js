import { request } from './http'

/**
 * GET /api/jobs/meta -> { schedulerAvailable, smallestIntervalSeconds }
 *
 * 调度引擎可用性与最小间隔（前端据此给出提示）。
 */
export const jobMeta = () => request({ method: 'GET', url: '/api/jobs/meta' })

/**
 * GET /api/jobs/handlers -> JobHandlerOptionVO[]
 *
 * 可选任务处理器列表（供「处理器」下拉）。
 */
export const jobHandlers = () => request({ method: 'GET', url: '/api/jobs/handlers' })

/**
 * GET /api/jobs -> PageResult<JobVO>
 *
 * @param {Object} params
 * @param {string} [params.jobName]     任务名称（模糊）
 * @param {string} [params.jobGroup]    任务分组
 * @param {string} [params.status]      PAUSED / RUNNING / COMPLETED
 * @param {string} [params.triggerType] CRON / SIMPLE / ONE_TIME
 * @param {string} [params.taskKey]     处理器标识
 * @param {number} [params.page]
 * @param {number} [params.size]
 */
export const jobPage = (params) => request({ method: 'GET', url: '/api/jobs', params })

/** GET /api/jobs/{id} -> JobVO */
export const jobDetail = (id) => request({ method: 'GET', url: `/api/jobs/${id}` })

/** GET /api/jobs/{id}/logs -> PageResult<JobLogVO> */
export const jobLogs = (id, params) => request({ method: 'GET', url: `/api/jobs/${id}/logs`, params })

/**
 * POST /api/jobs -> id
 * @param {Object} data 任务配置
 *   jobName/taskKey/triggerType/cronExpression/intervalValue/intervalUnit/oneTimeAt/jobParams/remark
 *   dependsOn?: number[]  前置依赖的任务 ID 列表（依赖编排 DAG；为空表示无依赖）
 */
export const jobCreate = (data) => request({ method: 'POST', url: '/api/jobs', data })

/** POST /api/jobs/{id}/trigger-downstream -> 实际触发的就绪下游数量 */
export const jobTriggerDownstream = (id) => request({ method: 'POST', url: `/api/jobs/${id}/trigger-downstream` })

/** PUT /api/jobs/{id}（运行中会先暂停并按新配置重启）
 *  @param {Object} data 同 jobCreate；编辑时亦支持更新 dependsOn（依赖编排）
 */
export const jobUpdate = (id, data) => request({ method: 'PUT', url: `/api/jobs/${id}`, data })

/** POST /api/jobs/{id}/start（立即注册到调度器） */
export const jobStart = (id) => request({ method: 'POST', url: `/api/jobs/${id}/start` })

/** POST /api/jobs/{id}/pause（立即取消调度） */
export const jobPause = (id) => request({ method: 'POST', url: `/api/jobs/${id}/pause` })

/** DELETE /api/jobs/{id}（逻辑删除） */
export const jobDelete = (id) => request({ method: 'DELETE', url: `/api/jobs/${id}` })
