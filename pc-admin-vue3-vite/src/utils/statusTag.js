/**
 * 状态 → 标签映射工具：收敛「枚举状态 → el-tag type + 中文文案」的查表样板。
 *
 * <p>用法：各页面声明自己的映射表，查不到时统一回落 `{ type: 'info', label: 状态原文 }`。</p>
 */

/**
 * 查状态标签。
 *
 * @param {Object<string, {type: string, label: string}>} map 状态映射表
 * @param {string} status 状态枚举值
 * @param {{fallbackLabel?: string}} [opts] 未命中时 label 的回落值，默认状态原文；空状态回落 '--'
 * @returns {{type: string, label: string}} el-tag 所需的 type 与展示文案
 */
export function statusTag(map, status, { fallbackLabel } = {}) {
  const hit = map[status]
  if (hit) return hit
  return { type: 'info', label: status || fallbackLabel || '--' }
}
