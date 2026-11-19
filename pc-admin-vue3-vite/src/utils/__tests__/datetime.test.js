import { describe, it, expect } from 'vitest'
import { formatDateTime } from '../datetime'

describe('formatDateTime', () => {
  it('ISO LocalDateTime 应转为 YYYY-MM-DD HH:mm:ss', () => {
    expect(formatDateTime('2026-11-19T10:30:05')).toBe('2026-11-19 10:30:05')
    expect(formatDateTime('2026-11-19T10:30:05.123456')).toBe('2026-11-19 10:30:05')
  })

  it('空值回落占位符（默认 -）', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
    expect(formatDateTime(0)).toBe('-')
  })

  it('自定义占位符逐字保留（与各页面原占位符一致）', () => {
    expect(formatDateTime(null, { fallback: '--' })).toBe('--')
    expect(formatDateTime('', { fallback: '' })).toBe('')
  })

  it('非空值不受占位符影响', () => {
    expect(formatDateTime('2026-11-19T10:30:05', { fallback: '--' })).toBe('2026-11-19 10:30:05')
  })

  it('已含空格的输入幂等', () => {
    expect(formatDateTime('2026-11-19 10:30:05')).toBe('2026-11-19 10:30:05')
  })
})
