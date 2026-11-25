import { describe, expect, it } from 'vitest'
import {
  OFFSET_SOFT_LIMIT, canGoNext, canGoPrev, createCursorStack, currentCursor,
  goNext, goPrev, isOffsetLimitError, offsetOf, pageNumber, setNextCursor, shouldUseKeyset
} from '../keyset'

describe('游标分页策略', () => {
  it('offsetOf 非法入参按 page=1 / size=10 兜底', () => {
    expect(offsetOf(1, 10)).toBe(0)
    expect(offsetOf(3, 10)).toBe(20)
    expect(offsetOf(0, 0)).toBe(0)
    expect(offsetOf(NaN, -5)).toBe(0)
    expect(offsetOf(2.8, 10)).toBe(10)
  })

  it('shouldUseKeyset 以超过软阈值为切换条件', () => {
    expect(shouldUseKeyset(500, 10)).toBe(false)
    expect(offsetOf(501, 10)).toBe(5000)
    expect(shouldUseKeyset(501, 10)).toBe(false)
    expect(shouldUseKeyset(502, 10)).toBe(true)
    expect(shouldUseKeyset(2, 10, 5)).toBe(true)
    expect(OFFSET_SOFT_LIMIT).toBe(5000)
  })

  it('isOffsetLimitError 兼容中英文提示与端点片段', () => {
    expect(isOffsetLimitError('偏移过大，请改用游标分页接口')).toBe(true)
    expect(isOffsetLimitError(new Error('请使用 /api/audit-logs/keyset'))).toBe(true)
    expect(isOffsetLimitError('offset too large')).toBe(true)
    expect(isOffsetLimitError('page-keyset')).toBe(true)
    expect(isOffsetLimitError('网络错误')).toBe(false)
    expect(isOffsetLimitError(null)).toBe(false)
  })
})

describe('游标页栈', () => {
  it('初始栈处于首页且无法前进/后退', () => {
    const s = createCursorStack()
    expect(s).toEqual({ cursors: [null], index: 0, nextCursor: '' })
    expect(currentCursor(s)).toBe(null)
    expect(pageNumber(s)).toBe(1)
    expect(canGoPrev(s)).toBe(false)
    expect(canGoNext(s)).toBe(false)
    expect(goPrev(s)).toBe(s)
    expect(goNext(s)).toBe(s)
  })

  it('response nextCursor 压栈后成为下一页起始游标', () => {
    let s = setNextCursor(createCursorStack(), 'c1')
    expect(canGoNext(s)).toBe(true)
    expect(currentCursor(s)).toBe(null)
    s = goNext(s)
    expect(s).toMatchObject({ cursors: [null, 'c1'], index: 1, nextCursor: '' })
    expect(currentCursor(s)).toBe('c1')
    expect(pageNumber(s)).toBe(2)
    expect(canGoNext(s)).toBe(false)
  })

  it('多层前进保留每层起始游标', () => {
    let s = setNextCursor(createCursorStack(), 'c1')
    s = goNext(s)
    s = setNextCursor(s, 'c2')
    s = goNext(s)
    expect(s.cursors).toEqual([null, 'c1', 'c2'])
    expect(currentCursor(s)).toBe('c2')
    expect(pageNumber(s)).toBe(3)
  })

  it('回退后仍可前进（保留游标，不依赖 nextCursor）', () => {
    let s = setNextCursor(createCursorStack(), 'c1')
    s = goNext(s)
    s = setNextCursor(s, 'c2')
    s = goNext(s)
    s = goPrev(s)

    expect(currentCursor(s)).toBe('c1')
    expect(s.nextCursor).toBe('')
    expect(canGoNext(s), '回退后必须仍能前进').toBe(true)
    s = goNext(s)
    expect(currentCursor(s)).toBe('c2')
    expect(pageNumber(s)).toBe(3)
  })

  it('回看时重载失败清空 nextCursor 也不会卡死前进', () => {
    let s = setNextCursor(createCursorStack(), 'c1')
    s = goNext(s)
    s = setNextCursor(s, 'c2')
    s = goNext(s)
    s = setNextCursor(goPrev(s), '')

    expect(canGoNext(s)).toBe(true)
    expect(currentCursor(goNext(s))).toBe('c2')
  })

  it('回看成功后新的 nextCursor 优先于保留的旧游标', () => {
    let s = setNextCursor(createCursorStack(), 'c1')
    s = goNext(s)
    s = setNextCursor(s, 'c2')
    s = goNext(s)
    s = setNextCursor(goPrev(s), 'c2-new')
    s = goNext(s)

    expect(s.cursors).toEqual([null, 'c1', 'c2-new'])
    expect(currentCursor(s)).toBe('c2-new')
  })

  it('非法/缺省栈安全回落到首页，不让整页加载中断', () => {
    expect(currentCursor(undefined)).toBe(null)
    expect(pageNumber(null)).toBe(1)
    expect(canGoPrev(undefined)).toBe(false)
    expect(canGoNext({ cursors: null, index: 9, nextCursor: 1 })).toBe(false)
    expect(currentCursor({ cursors: [null, 'x'], index: 99, nextCursor: 'y' })).toBe(null)
    expect(setNextCursor({ cursors: [null], index: 0, nextCursor: 1 }, 'z').nextCursor).toBe('z')
    expect(goPrev({ cursors: [null, 'x'], index: 1, nextCursor: null }).index).toBe(0)
  })
})
