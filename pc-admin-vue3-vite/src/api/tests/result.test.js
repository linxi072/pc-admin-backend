import { describe, expect, it } from 'vitest'
import { isSuccessResult, ResultError, unwrapResult } from '../result.js'

// 统一响应契约回归：固定 "0" 与 0 双成功码兼容，防止 http.js 拆包行为回潮。

describe('isSuccessResult', () => {
  it('字符串 "0" 视为成功', () => {
    expect(isSuccessResult({ code: '0', data: 'x' })).toBe(true)
  })

  it('数字 0 视为成功（历史跨语言兼容）', () => {
    expect(isSuccessResult({ code: 0, data: 'x' })).toBe(true)
  })

  it('非成功码与非对象均视为失败', () => {
    expect(isSuccessResult({ code: 'COMMON_404' })).toBe(false)
    expect(isSuccessResult({ code: '1' })).toBe(false)
    expect(isSuccessResult(null)).toBe(false)
    expect(isSuccessResult(undefined)).toBe(false)
    expect(isSuccessResult('0')).toBe(false)
  })
})

describe('unwrapResult', () => {
  it('成功时返回 data（含 null/undefined/false 等假值）', () => {
    expect(unwrapResult({ code: '0', data: { id: 1 } })).toEqual({ id: 1 })
    expect(unwrapResult({ code: 0, data: null })).toBeNull()
    expect(unwrapResult({ code: '0', data: false })).toBe(false)
  })

  it('失败时抛出 ResultError，携带 message/code/data', () => {
    try {
      unwrapResult({ code: 'COMMON_404', message: '资源不存在', data: { field: 'id' } })
      throw new Error('应当抛出 ResultError')
    } catch (e) {
      expect(e).toBeInstanceOf(ResultError)
      expect(e.name).toBe('ResultError')
      expect(e.message).toBe('资源不存在')
      expect(e.code).toBe('COMMON_404')
      expect(e.data).toEqual({ field: 'id' })
    }
  })

  it('失败响应无 message 时使用默认文案', () => {
    expect(() => unwrapResult({ code: 'ERR' })).toThrow('请求失败')
  })
})

describe('ResultError', () => {
  it('是标准 Error 的子类且保留 name', () => {
    const err = new ResultError('boom', 'E001', { k: 1 })
    expect(err).toBeInstanceOf(Error)
    expect(err.name).toBe('ResultError')
    expect(err.code).toBe('E001')
    expect(err.data).toEqual({ k: 1 })
  })
})
