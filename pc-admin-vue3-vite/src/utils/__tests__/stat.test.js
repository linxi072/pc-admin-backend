import { test } from 'node:test'
import assert from 'node:assert/strict'
import { formatStat, trendOf } from '../stat.js'

// ---------- formatStat ----------
test('formatStat: 非数 / 空值返回占位符与原始单位', () => {
  for (const bad of [null, undefined, '', 'abc', NaN]) {
    const r = formatStat(bad, { unit: '%' })
    assert.equal(r.text, '-')
    assert.equal(r.value, null)
    assert.equal(r.unit, '%')
  }
})

test('formatStat: 普通整数与千分位', () => {
  assert.equal(formatStat(1234).text, '1,234')
  assert.equal(formatStat(1234567).text, '1,234,567')
})

test('formatStat: thousands=false 不加千分位', () => {
  assert.equal(formatStat(1234567, { thousands: false }).text, '1234567')
})

test('formatStat: 小数位与去尾零', () => {
  assert.equal(formatStat(12.0, { decimals: 2 }).text, '12')
  assert.equal(formatStat(12.3, { decimals: 2 }).text, '12.3')
  assert.equal(formatStat(12.345, { decimals: 2 }).text, '12.35') // 四舍五入
  assert.equal(formatStat(1234.5, { decimals: 1 }).text, '1,234.5')
})

test('formatStat: 整数 0 不得被误清为空串', () => {
  const r = formatStat(0)
  assert.equal(r.text, '0')
  assert.equal(r.value, 0)
  // 0 也走千分位（parts[0] 为 '0'）
  assert.equal(formatStat(0, { thousands: true }).text, '0')
})

test('formatStat: 单位透传', () => {
  const r = formatStat(50, { unit: '%' })
  assert.equal(r.text, '50')
  assert.equal(r.unit, '%')
})

test('formatStat: 返回 value 为原始数值', () => {
  assert.equal(formatStat('998').value, 998)
})

// ---------- trendOf ----------
test('trendOf: 三态方向', () => {
  assert.equal(trendOf(10, 5), 'up')
  assert.equal(trendOf(5, 10), 'down')
  assert.equal(trendOf(5, 5), 'flat')
})

test('trendOf: 基线 0 视为有效数值按差值判定；基线缺失/非数返回 flat', () => {
  assert.equal(trendOf(5, 0), 'up') // 5 - 0 > 0
  assert.equal(trendOf(-5, 0), 'down') // -5 - 0 < 0
  assert.equal(trendOf(3, 'abc'), 'flat') // 基线非数
  assert.equal(trendOf(5, null), 'flat') // 基线缺失
})

test('trendOf: 微小差异仍按符号判定', () => {
  assert.equal(trendOf(0.0001, 0), 'up')
  assert.equal(trendOf(-0.0001, 0.0002), 'down')
})
