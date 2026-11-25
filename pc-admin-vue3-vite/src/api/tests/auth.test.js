import { describe, expect, it, vi, beforeEach } from 'vitest'

const { request } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request }))

import { refreshToken } from '../auth.js'

describe('认证 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('刷新端点同形（refreshToken 走请求体，公开端点，C11 会话续期）', () => {
    refreshToken('rt-abc')
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: '/api/auth/refresh',
      data: { refreshToken: 'rt-abc' },
      skipAuthRefresh: true
    })
  })
})
