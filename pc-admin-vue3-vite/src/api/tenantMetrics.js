import { request } from './http'

// R14 租户维度指标：可观测中心新增 Tab（后端 TenantMetricsController）。
// 统一经拆包层发出，复用 Result<T> 拆包与统一错误处理；注释不含 URL，以免干扰契约测试计数。

// 拉取各租户维度的请求量 / 错误率 / 平均时延汇总
export const listTenantMetrics = () =>
  request({ method: 'GET', url: '/api/observability/tenant-metrics' })
