/**
 * 可观测中心各 Tab 共享的格式化助手（纯函数，无框架依赖，便于 node:test 直接覆盖）。
 *
 * 后端时间字段有两种形态，本模块统一收敛：
 *   - epoch 毫秒（SpanView.startEpochMillis / NPlusOneSuspectView.timestampMillis /
 *     AlertInboxItemView.receivedAtEpochMillis 等）：服务端已折算，前端本地时区展示；
 *   - ISO 本地时间串（IdempotencyRecordView.expiresAt 等 LocalDateTime，
 *     Jackson write-dates-as-timestamps=false → "2026-10-20T14:30:00"）。
 *     MySQL DATETIME(3) 经 JDBC 读到的是「数据库会话时区」下的本地时间，
 *     服务端不再做时区换算，前端按原样展示（与 SmsLogView 等既有页面同口径）。
 */

/** epoch 毫秒 → "YYYY-MM-DD HH:mm:ss"；0/负数/null 视为无值返回 '--'。 */
export function fmtEpoch(millis) {
  if (millis == null || millis <= 0) return '--'
  const d = new Date(millis)
  if (Number.isNaN(d.getTime())) return '--'
  return fmtParts(d.getFullYear(), d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds())
}

/**
 * ISO 本地时间串 → "YYYY-MM-DD HH:mm:ss"。兼容两种输入：
 *   - "2026-10-20T14:30:00" / "2026-10-20 14:30:00"（含可选 .SSS 毫秒）
 *   - Jackson 时间数组 [2026, 10, 20, 14, 30, 0]（write-dates-as-timestamps=true 的兜底形态）
 * 空值返回 '--'。
 */
export function fmtDateTime(value) {
  if (value == null || value === '') return '--'
  if (Array.isArray(value)) {
    const [y, mo, d, h, mi, s] = value
    if (y == null) return '--'
    return fmtParts(y, mo ?? 1, d ?? 1, h ?? 0, mi ?? 0, s ?? 0)
  }
  if (typeof value !== 'string') return '--'
  const m = value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/)
  if (!m) return value
  return fmtParts(+m[1], +m[2], +m[3], +m[4], +m[5], m[6] == null ? 0 : +m[6])
}

function fmtParts(y, mo, d, h, mi, s) {
  const p = (n) => String(n).padStart(2, '0')
  return `${y}-${p(mo)}-${p(d)} ${p(h)}:${p(mi)}:${p(s)}`
}

/** 毫秒耗时 → "12 ms" / "1.20 s"（>=1000ms 折算秒，保留两位小数）。 */
export function fmtDuration(millis) {
  if (millis == null || millis < 0) return '--'
  if (millis < 1000) return `${millis} ms`
  return `${(millis / 1000).toFixed(2)} s`
}

/** 0~1 占比 → "12.34%"；null/undefined → '--'。 */
export function fmtShare(share) {
  if (share == null) return '--'
  return `${(Number(share) * 100).toFixed(2)}%`
}

/** 判断 HTTP 错误是否为 403（后端 AccessDeniedException → 403 + Result 包络），用于 Tab 级降级。 */
export function isForbidden(err) {
  return err?.response?.status === 403
}

/**
 * 平铺 span（后端按时间正序）→ 父子层级树，用于 Trace 调用链瀑布视图（C12）。
 *
 * - 父 span 不在结果中（被有界留存淘汰）或自引用时提升为根，避免丢失节点或成环；
 * - offsetMillis 以本 trace 最早 span 为基准，便于观察调用先后；
 * - 不改变入参顺序（同级按后端时间正序）。
 */
export function buildTraceTree(list) {
  const spans = Array.isArray(list) ? list : []
  const earliest = spans.length ? Math.min(...spans.map((x) => x.startEpochMillis || 0)) : 0
  const nodes = spans.map((s) => ({
    ...s,
    offsetMillis: (s.startEpochMillis || 0) - earliest,
    children: []
  }))
  const byId = new Map(nodes.map((n) => [n.spanId, n]))
  const roots = []
  for (const node of nodes) {
    const parent = node.parentSpanId ? byId.get(node.parentSpanId) : null
    if (parent && parent !== node) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

/** Trace 摘要：span 数、错误数、总耗时（最晚结束 - 最早开始）。 */
export function traceSummary(list) {
  const spans = Array.isArray(list) ? list : []
  if (!spans.length) return { count: 0, errors: 0, totalDuration: 0 }
  const starts = spans.map((s) => s.startEpochMillis || 0)
  const ends = spans.map((s) => (s.startEpochMillis || 0) + (s.durationMillis || 0))
  return {
    count: spans.length,
    errors: spans.filter((s) => s.status === 'ERROR').length,
    totalDuration: Math.max(...ends) - Math.min(...starts)
  }
}
