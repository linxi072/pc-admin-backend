import { describe, expect, it } from 'vitest'
import { statusTag, STATUS_TAGS } from '../status'

describe('statusTag（全局状态映射）', () => {
  it('CMS 定时发布待发布态映射为 warning+待发布', () => {
    expect(statusTag('SCHEDULED')).toEqual({ label: '待发布', type: 'warning' })
    expect(STATUS_TAGS.SCHEDULED).toEqual({ label: '待发布', type: 'warning' })
  })

  it('既有 CMS 文章状态映射无回归', () => {
    expect(statusTag('DRAFT')).toEqual({ label: '草稿', type: 'info' })
    expect(statusTag('PUBLISHED')).toEqual({ label: '已发布', type: 'success' })
    expect(statusTag('OFFLINE')).toEqual({ label: '已下线', type: 'info' })
  })

  it('未登记状态回落为原值 + info，空值回落为破折号', () => {
    expect(statusTag('MYSTERY')).toEqual({ label: 'MYSTERY', type: 'info' })
    expect(statusTag('')).toEqual({ label: '—', type: 'info' })
    expect(statusTag(null)).toEqual({ label: '—', type: 'info' })
  })
})
