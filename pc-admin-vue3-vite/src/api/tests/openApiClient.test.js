import { beforeEach, describe, expect, it, vi } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({ data: {} }))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import { issueClientToken, resolveOAuthErrorMessage } from '../openApiClient.js'

describe('开放平台客户端 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('令牌签发走 form-urlencoded 且关闭 401 登出跳转', () => {
    issueClientToken('cli-1', 'sec-1')
    const cfg = requestRaw.mock.calls[0][0]
    expect(cfg).toMatchObject({
      method: 'POST',
      url: '/oauth2/token',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      skipAuthErrorHandling: true
    })
    expect(cfg.data instanceof URLSearchParams).toBe(true)
    expect(cfg.data.get('grant_type')).toBe('client_credentials')
    expect(cfg.data.get('client_id')).toBe('cli-1')
    expect(cfg.data.get('client_secret')).toBe('sec-1')
  })

  it('优先展示 OAuth2 error_description', () => {
    expect(resolveOAuthErrorMessage({
      response: { data: { error: 'invalid_client', error_description: '客户端认证失败' } }
    })).toBe('客户端认证失败')
  })

  it('兼容 message / error 码 / 字符串响应体', () => {
    expect(resolveOAuthErrorMessage({ response: { data: { message: '服务异常' } } })).toBe('服务异常')
    expect(resolveOAuthErrorMessage({ response: { data: { error: 'invalid_grant' } } })).toBe('invalid_grant')
    expect(resolveOAuthErrorMessage({ response: { data: 'bad request' } })).toBe('bad request')
  })

  it('无响应体时回退到 Error.message 与兜底文案', () => {
    expect(resolveOAuthErrorMessage(new Error('Network Error'))).toBe('Network Error')
    expect(resolveOAuthErrorMessage({})).toBe('令牌签发失败')
    expect(resolveOAuthErrorMessage(null)).toBe('令牌签发失败')
  })
})
