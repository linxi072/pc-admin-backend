import { describe, expect, it, beforeEach, vi } from 'vitest'

// 测试环境为 node，无浏览器 localStorage；用内存实现替身，须在 import store 前注入。
const memory = new Map()
vi.stubGlobal('localStorage', {
  getItem: (k) => (memory.has(k) ? memory.get(k) : null),
  setItem: (k, v) => memory.set(k, String(v)),
  removeItem: (k) => memory.delete(k),
  clear: () => memory.clear()
})

const { authState, setAuth, clearAuth, isAuthenticated } = await import('../auth.js')

describe('认证状态 refreshToken 存取（C11 会话续期）', () => {
  beforeEach(() => {
    clearAuth()
    memory.clear()
  })

  it('setAuth 同时持久化 accessToken 与 refreshToken', () => {
    setAuth('at-1', 'alice', 'rt-1')
    expect(authState.token).toBe('at-1')
    expect(authState.refreshToken).toBe('rt-1')
    expect(authState.username).toBe('alice')
    expect(localStorage.getItem('pc_admin_token')).toBe('at-1')
    expect(localStorage.getItem('pc_admin_refresh_token')).toBe('rt-1')
    expect(isAuthenticated()).toBe(true)
  })

  it('未传 refreshToken 时清空旧刷新凭据（兼容旧会话）', () => {
    setAuth('at-1', 'alice', 'rt-1')
    setAuth('at-2', 'alice') // 旧调用点：只传 accessToken
    expect(authState.token).toBe('at-2')
    expect(authState.refreshToken).toBe('')
    expect(localStorage.getItem('pc_admin_refresh_token')).toBe(null)
  })

  it('refresh 轮换后可写回新令牌对', () => {
    setAuth('at-old', 'alice', 'rt-old')
    setAuth('at-new', 'alice', 'rt-new')
    expect(authState.token).toBe('at-new')
    expect(localStorage.getItem('pc_admin_token')).toBe('at-new')
    expect(authState.refreshToken).toBe('rt-new')
    expect(localStorage.getItem('pc_admin_refresh_token')).toBe('rt-new')
  })

  it('clearAuth 一并清除刷新凭据与登录态', () => {
    setAuth('at-1', 'alice', 'rt-1')
    clearAuth()
    expect(authState.token).toBe('')
    expect(authState.refreshToken).toBe('')
    expect(authState.username).toBe('')
    expect(localStorage.getItem('pc_admin_refresh_token')).toBe(null)
    expect(isAuthenticated()).toBe(false)
  })
})
