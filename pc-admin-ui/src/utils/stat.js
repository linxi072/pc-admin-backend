// 统计数值格式化纯函数（零依赖，随组件库发布；与主应用 src/utils/stat.js 同源）。

/**
 * 格式化统计数值。
 * @param {number|string|null|undefined} value 原始值
 * @param {{ decimals?: number, unit?: string, compact?: boolean, thousands?: boolean }} [opts]
 * @returns {{ text: string, value: (number|null), unit: string }}
 */
export function formatStat(value, opts = {}) {
  const { decimals = 0, unit = '', compact = true, thousands = compact } = opts
  if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) {
    return { text: '-', value: null, unit }
  }
  const num = Number(value)
  const fixed = num.toFixed(decimals)
  let str = fixed.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '')
  if (thousands) {
    const parts = str.split('.')
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    str = parts.join('.')
  }
  return { text: str, value: num, unit }
}

/**
 * 计算同比/环比趋势方向。
 * @param {number} current
 * @param {number} baseline
 * @returns {'up'|'down'|'flat'}
 */
export function trendOf(current, baseline) {
  const cur = Number(current)
  const base = Number(baseline)
  if (baseline == null || Number.isNaN(base)) return 'flat'
  const delta = cur - base
  if (delta > 0) return 'up'
  if (delta < 0) return 'down'
  return 'flat'
}
