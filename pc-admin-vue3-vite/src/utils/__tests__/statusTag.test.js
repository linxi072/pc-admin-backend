import { describe, it, expect } from 'vitest'
import { statusTag } from '../statusTag'

const TAGS = {
  SUCCESS: { type: 'success', label: '成功' },
  FAILED: { type: 'danger', label: '失败' }
}

describe('statusTag', () => {
  it('命中映射返回原对象', () => {
    expect(statusTag(TAGS, 'SUCCESS')).toEqual({ type: 'success', label: '成功' })
    expect(statusTag(TAGS, 'FAILED')).toEqual({ type: 'danger', label: '失败' })
  })

  it('未命中回落 info + 状态原文', () => {
    expect(statusTag(TAGS, 'UNKNOWN')).toEqual({ type: 'info', label: 'UNKNOWN' })
  })

  it('空状态回落 --', () => {
    expect(statusTag(TAGS, '')).toEqual({ type: 'info', label: '--' })
    expect(statusTag(TAGS, null)).toEqual({ type: 'info', label: '--' })
  })

  it('fallbackLabel 自定义空状态文案', () => {
    expect(statusTag(TAGS, '', { fallbackLabel: '未知' })).toEqual({ type: 'info', label: '未知' })
  })
})
