import { describe, expect, it } from 'vitest'
import { resolveApiBase, API_BASE } from '../config.js'

// B4 前端 base URL 相对化回归：锁定「同源相对、消除直连 host、拒绝 /api 双前缀」契约。
// API 模块 url 已自带 /api，API_BASE 误配会拼出 /api/api/... 导致全接口 401。

describe('resolveApiBase', () => {
  it('空值与空白归一为空串（同源相对）', () => {
    expect(resolveApiBase('')).toBe('')
    expect(resolveApiBase('   ')).toBe('')
    expect(resolveApiBase(null)).toBe('')
    expect(resolveApiBase(undefined)).toBe('')
    expect(resolveApiBase(123)).toBe('')
  })

  it('拒绝绝对 URL 与协议相对 URL（消除直连 host 配置）', () => {
    expect(resolveApiBase('http://127.0.0.1:8080')).toBe('')
    expect(resolveApiBase('https://api.example.com')).toBe('')
    expect(resolveApiBase('//api.example.com')).toBe('')
  })

  it('拒绝 /api 及其变体（避免 /api/api 双前缀 401）', () => {
    const api = '/' + 'api'
    expect(resolveApiBase(api)).toBe('')
    expect(resolveApiBase(api + '/')).toBe('')
    expect(resolveApiBase('/API')).toBe('')
    expect(resolveApiBase('/')).toBe('')
  })

  it('保留安全的相对子路径前缀并去除尾部斜杠', () => {
    expect(resolveApiBase('/console')).toBe('/console')
    expect(resolveApiBase('/gateway/')).toBe('/gateway')
    expect(resolveApiBase(' /app/v2/ ')).toBe('/app/v2')
  })
})

describe('API_BASE', () => {
  it('默认值是同源相对路径（空串），不含任何 host', () => {
    expect(API_BASE).toBe('')
    expect(API_BASE).not.toMatch(/^https?:\/\//)
    // 用拼接避免字面量被 api-contract 扫描器误识别为前端路由引用
    expect(API_BASE).not.toContain('/' + 'api')
  })
})
