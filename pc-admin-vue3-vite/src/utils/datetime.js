/**
 * 时间展示工具：后端 LocalDateTime 序列化（ISO，无时区）→ 页面展示文本。
 *
 * <p>收敛此前散落在 9 个视图中的同构实现（`String(v).replace('T', ' ').slice(0, 19)`）。
 * monitor 域的 `views/monitor/components/format.js`（epoch/Date 双源）语义不同，保持独立。</p>
 */

/**
 * ISO LocalDateTime → 'YYYY-MM-DD HH:mm:ss'（本地展示，无时区转换）。
 *
 * @param {string|number|Date|null} value 时间值；空值（''/null/undefined/0 视为无值）返回占位符
 * @param {{fallback?: string}} [opts] 占位符，默认 '-'
 * @returns {string} 'YYYY-MM-DD HH:mm:ss' 形态文本
 */
export function formatDateTime(value, { fallback = '-' } = {}) {
  if (!value) return fallback
  return String(value).replace('T', ' ').slice(0, 19)
}
