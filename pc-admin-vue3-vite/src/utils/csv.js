/**
 * 轻量 CSV 导出工具（纯函数 + 浏览器下载），供各列表视图「导出当前页」复用。
 *
 * 不依赖 element-plus / 后端：纯函数部分（neutralizeFormula / escapeCsvField / toCsv /
 * buildCsvBlobContent）可在 Node 测试环境单测；仅 downloadCsv 在浏览器中触发下载。
 */

/**
 * CSV 字段转义（纯函数，可单测）。
 *
 * 规则：先做公式注入中和（与后端 CsvExporter.neutralizeFormula 同口径），再按 RFC 4180
 * 对含逗号、双引号或换行符的字段加引号，内部双引号翻倍；空值统一返回空串。
 * 顺序不可颠倒——先加引号会让首字符变成引号，中和逻辑便无法生效。
 *
 * @param {*} value 任意值
 * @returns {string}
 */
export function escapeCsvField(value) {
  if (value === null || value === undefined) return ''
  const safe = neutralizeFormula(value)
  if (/[",\r\n]/.test(safe)) {
    return '"' + safe.replace(/"/g, '""') + '"'
  }
  return safe
}

/** 是否为纯数字（可含一个小数点），用于区分负数值与以 -/+ 开头的公式。 */
function isPlainNumber(s) {
  if (!s) return false
  let dotSeen = false
  for (const ch of s) {
    if (ch >= '0' && ch <= '9') continue
    if (ch === '.' && !dotSeen) {
      dotSeen = true
      continue
    }
    return false
  }
  return true
}

/**
 * CSV 公式注入防护（CWE-1236）：以 = + - @、Tab、CR 开头的单元格在 Excel / WPS 中会被当作公式执行。
 * 前置单引号强制按文本处理；-5 / +3.14 这类合法数值例外，避免数值列整体转文本。
 * 移植自后端 CsvExporter.neutralizeFormula，保持两端导出口径一致。
 *
 * @param {*} value 任意值
 * @returns {string}
 */
export function neutralizeFormula(value) {
  const s = String(value ?? '')
  if (!s) return s
  const first = s[0]
  if (first !== '=' && first !== '+' && first !== '-' && first !== '@' && first !== '\t' && first !== '\r') {
    return s
  }
  if ((first === '-' || first === '+') && isPlainNumber(s.slice(1))) return s
  return `'${s}`
}

/**
 * 将表头与二维数据渲染为 CSV 文本（不含 BOM，下载时由 buildCsvBlobContent 补）。
 *
 * @param {Array<string>} headers 表头列
 * @param {Array<Array<*>>} rows 每行数据，列顺序与 headers 对齐
 * @returns {string}
 */
export function toCsv(headers, rows) {
  const lines = [headers.map(escapeCsvField).join(',')]
  for (const row of rows) {
    lines.push(row.map(escapeCsvField).join(','))
  }
  return lines.join('\r\n')
}

/** 为 CSV 文本加 UTF-8 BOM，保证 Excel 正确识别中文（纯函数）。 */
export function buildCsvBlobContent(csv) {
  // U+FEFF 字节顺序标记，前置以保证 Excel 以 UTF-8 解析。
  return '﻿' + csv
}

/**
 * 浏览器端导出当前页为 CSV 文件：不依赖后端，导出的是当前已加载的数据。
 *
 * @param {string} filename 文件名（含 .csv）
 * @param {string} csv toCsv 产物（CSV 文本）
 */
export function downloadCsv(filename, csv) {
  const blob = new Blob([buildCsvBlobContent(csv)], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
