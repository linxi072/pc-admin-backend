/**
 * 颜色工具：16 进制混合 / 提亮 / 压暗 / 主色色阶生成。
 * 用于需要「具体色值」的场景（如 ECharts 调色板按主题派生），
 * CSS 变量层面优先使用 color-mix，仅在 JS 侧需要字面量时调用本模块。
 */

function clamp(n) {
  return Math.max(0, Math.min(255, Math.round(n)))
}

function hexToRgb(hex) {
  const h = String(hex).replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16)
  ]
}

function rgbToHex([r, g, b]) {
  return '#' + [r, g, b].map((x) => clamp(x).toString(16).padStart(2, '0')).join('')
}

/** 混合两个 16 进制颜色，weight 为 a 的占比（0~1）。 */
export function mixHex(a, b, weight = 0.5) {
  const A = hexToRgb(a)
  const B = hexToRgb(b)
  return rgbToHex([0, 1, 2].map((i) => A[i] * (1 - weight) + B[i] * weight))
}

/** 向白色提亮 amt（0~1）。 */
export function lighten(hex, amt) {
  return mixHex(hex, '#ffffff', amt)
}

/** 向黑色压暗 amt（0~1）。 */
export function darken(hex, amt) {
  return mixHex(hex, '#000000', amt)
}

/** 由主色派生 Element Plus 风格色阶，返回字面量对象。 */
export function generatePrimaryShades(hex) {
  return {
    base: hex,
    light3: mixHex(hex, '#ffffff', 0.3),
    light5: mixHex(hex, '#ffffff', 0.5),
    light7: mixHex(hex, '#ffffff', 0.7),
    light8: mixHex(hex, '#ffffff', 0.8),
    light9: mixHex(hex, '#ffffff', 0.9),
    dark2: mixHex(hex, '#000000', 0.2)
  }
}
