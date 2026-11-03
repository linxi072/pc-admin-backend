// 统计数值格式化纯函数（零依赖，供 StatCard 等组件与 node:test 复用）。

/**
 * 格式化统计数值。
 * @param {number|string|null|undefined} value 原始值
 * @param {{ decimals?: number, unit?: string, compact?: boolean, thousands?: boolean }} [opts]
 *   - decimals: 小数位（默认 0，自动去除无意义尾零）
 *   - unit: 单位后缀（如 '%'、'ms'、'人'）
 *   - compact: 超大数据加千分位（默认 true）
 *   - thousands: 是否强制千分位（默认同 compact）
 * @returns {{ text: string, value: (number|null), unit: string }}
 */
export function formatStat(value, opts = {}) {
  const { decimals = 0, unit = '', compact = true, thousands = compact } = opts
  if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) {
    return { text: '-', value: null, unit }
  }
  const num = Number(value)
  const fixed = num.toFixed(decimals)
  // 仅去除小数部分末尾的无效零（如 12.00 -> 12、12.30 -> 12.3），
  // 整数部分的尾零（如 50、100）必须保留；末尾若残留小数点一并清除。
  let str = fixed.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '')
  if (thousands) {
    const parts = str.split('.')
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    str = parts.join('.')
  }
  return { text: str, value: num, unit }
}

/**
 * 计算同比/环比趋势方向（纯函数）。
 * @param {number} current
 * @param {number} baseline
 * @returns {'up'|'down'|'flat'}
 */
export function trendOf(current, baseline) {
  const cur = Number(current)
  const base = Number(baseline)
  // 基线缺失或非数：无法计算趋势，返回中性 flat
  if (baseline == null || Number.isNaN(base)) {
    return 'flat'
  }
  const delta = cur - base
  if (delta > 0) return 'up'
  if (delta < 0) return 'down'
  return 'flat'
}
