import { describe, expect, it } from 'vitest'
import { formatStat, trendOf } from './stat.js'

// 库包纯函数契约：与 pc-admin-vue3-vite/src/utils/stat.js 同源，锁定迁移后行为零漂移。

describe('formatStat', () => {
  it('去尾零且保留整数部分尾零', () => {
    expect(formatStat(12).text).toBe('12')
    expect(formatStat(12.5, { decimals: 1 }).text).toBe('12.5')
    expect(formatStat(12.30, { decimals: 2 }).text).toBe('12.3')
    expect(formatStat(100).text).toBe('100')
  })

  it('千分位与单位（unit 单独返回，不拼进 text）', () => {
    expect(formatStat(1234567, { compact: true }).text).toBe('1,234,567')
    const withUnit = formatStat(12.3, { unit: '%', decimals: 1, thousands: false })
    expect(withUnit.text).toBe('12.3')
    expect(withUnit.unit).toBe('%')
  })

  it('空值与非法值显示占位符', () => {
    expect(formatStat(null).text).toBe('-')
    expect(formatStat(undefined).text).toBe('-')
    expect(formatStat('').text).toBe('-')
    expect(formatStat('abc').text).toBe('-')
  })
})

describe('trendOf', () => {
  it('三态判定', () => {
    expect(trendOf(110, 100)).toBe('up')
    expect(trendOf(90, 100)).toBe('down')
    expect(trendOf(100, 100)).toBe('flat')
  })

  it('基线缺失返回 flat', () => {
    expect(trendOf(110, null)).toBe('flat')
    expect(trendOf(110, undefined)).toBe('flat')
  })
})
