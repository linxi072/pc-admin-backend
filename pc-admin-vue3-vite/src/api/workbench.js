import { request } from './http'

/**
 * GET /api/workbench/stats -> WorkbenchStatsVO
 *
 * WorkbenchStatsVO: { cards: StatCardVO[] }
 * StatCardVO:
 *   key           String  卡片标识（NEW_USER_TODAY / NEW_INSTANCE_TODAY / MY_TODO_TASKS / RUNNING_INSTANCE / ACTIVE_API）
 *   label         String  展示名称（后端下发，前端不二次映射）
 *   value         Number  当前值
 *   previousValue Number  上一周期值（快照型指标与当前值相同，仅用于占位）
 *   changeRate    Number  变化率百分比（保留 1 位；上一周期为 0 时记 0 或 100）
 *   trend         String  UP / DOWN / FLAT（FLAT 表示持平或无可比周期）
 *   unit          String  单位（人 / 个 / 条）
 */
export const workbenchStats = () =>
  request({ method: 'GET', url: '/api/workbench/stats' })
