/**
 * 轻量 CSV 导出工具（纯函数 + 浏览器下载），供各列表视图「导出当前页」复用。
 *
 * 不依赖 element-plus / 后端：纯函数部分（escapeCsvField / toCsv / buildCsvBlobContent）
 * 可在 Node 测试环境单测；仅 downloadCsv 在浏览器中触发下载（视图调用，测试不触达）。
 */

/**
 * CSV 字段转义（纯函数，可单测）。
 *
 * 规则：含逗号、双引号或换行符的字段须用双引号包裹，内部双引号翻倍为两个双引号；
 * 空值（null/undefined）统一返回空串。与 RFC 4180 一致，可被 Excel / 文本编辑器正确解析。
 *
 * @param {*} value 任意值
 * @returns {string}
 */
export function escapeCsvField(value) {
  if (value === null || value === undefined) return ''
  const s = String(value)
  if (/[",\r\n]/.test(s)) {
    return '"' + s.replace(/"/g, '""') + '"'
  }
  return s
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
