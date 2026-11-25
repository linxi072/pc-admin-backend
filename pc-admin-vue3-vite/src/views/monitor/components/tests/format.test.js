import { describe, expect, it } from 'vitest'
import {
  fmtEpoch, fmtDateTime, fmtDuration, fmtShare, isForbidden,
  buildTraceTree, traceSummary
} from '../format.js'

// 可观测中心格式化纯函数回归：时间口径、耗时/占比展示、Trace 父子树与摘要边界。

describe('时间格式化', () => {
  it('fmtEpoch 输出本地时间并兼容空值', () => {
    expect(fmtEpoch(0)).toBe('--')
    expect(fmtEpoch(-1)).toBe('--')
    expect(fmtEpoch(null)).toBe('--')
    const text = fmtEpoch(new Date(2026, 0, 2, 3, 4, 5).getTime())
    expect(text).toMatch(/^2026-01-02 03:04:05$/)
  })

  it('fmtDateTime 兼容 ISO 串、空格分隔、时间数组与空值', () => {
    expect(fmtDateTime('2026-10-20T14:30:00')).toBe('2026-10-20 14:30:00')
    expect(fmtDateTime('2026-10-20 14:30:00.123')).toBe('2026-10-20 14:30:00')
    expect(fmtDateTime([2026, 10, 20, 14, 30, 0])).toBe('2026-10-20 14:30:00')
    expect(fmtDateTime(null)).toBe('--')
    expect(fmtDateTime('')).toBe('--')
  })
})

describe('数值格式化', () => {
  it('fmtDuration 毫秒/秒口径与空值', () => {
    expect(fmtDuration(12)).toBe('12 ms')
    expect(fmtDuration(1000)).toBe('1.00 s')
    expect(fmtDuration(1234)).toBe('1.23 s')
    expect(fmtDuration(null)).toBe('--')
    expect(fmtDuration(-1)).toBe('--')
  })

  it('fmtShare 百分比口径', () => {
    expect(fmtShare(0.1234)).toBe('12.34%')
    expect(fmtShare(null)).toBe('--')
  })

  it('isForbidden 仅识别 403', () => {
    expect(isForbidden({ response: { status: 403 } })).toBe(true)
    expect(isForbidden({ response: { status: 500 } })).toBe(false)
    expect(isForbidden(null)).toBe(false)
  })
})

describe('Trace 调用链树与摘要（C12）', () => {
  const spans = [
    { spanId: 'root', parentSpanId: null, name: 'GET /api', startEpochMillis: 1000, durationMillis: 500, status: 'OK' },
    { spanId: 'db', parentSpanId: 'root', name: 'SELECT', startEpochMillis: 1050, durationMillis: 100, status: 'OK' },
    { spanId: 'rpc', parentSpanId: 'root', name: 'RPC', startEpochMillis: 1100, durationMillis: 300, status: 'ERROR' }
  ]

  it('buildTraceTree 按 parentSpanId 构建层级并计算偏移', () => {
    const roots = buildTraceTree(spans)
    expect(roots).toHaveLength(1)
    expect(roots[0].spanId).toBe('root')
    expect(roots[0].offsetMillis).toBe(0)
    expect(roots[0].children.map((s) => s.spanId)).toEqual(['db', 'rpc'])
    expect(roots[0].children[0].offsetMillis).toBe(50)
    expect(roots[0].children[1].offsetMillis).toBe(100)
  })

  it('buildTraceTree 提升孤儿与自引用节点为根，避免丢失/成环', () => {
    const withOrphan = [
      ...spans,
      { spanId: 'orphan', parentSpanId: 'missing', name: 'orphan', startEpochMillis: 900, durationMillis: 10, status: 'OK' },
      { spanId: 'self', parentSpanId: 'self', name: 'self', startEpochMillis: 950, durationMillis: 10, status: 'OK' }
    ]
    const roots = buildTraceTree(withOrphan)
    // 根节点保持输入顺序：root 先入列，orphan（父缺失）与 self（自引用）随后提升为根
    expect(roots.map((n) => n.spanId)).toEqual(['root', 'orphan', 'self'])
    expect(roots[0].children.map((n) => n.spanId)).toEqual(['db', 'rpc']) // root 仍有正常子节点
    expect(roots[1].children).toEqual([]) // orphan 无子
    expect(roots[2].children).toEqual([]) // self 无子且未成环
  })

  it('buildTraceTree 空数组与非数组安全', () => {
    expect(buildTraceTree([])).toEqual([])
    expect(buildTraceTree(null)).toEqual([])
  })

  it('traceSummary 统计 span 数/错误数/总耗时（覆盖并发 span）', () => {
    expect(traceSummary(spans)).toEqual({ count: 3, errors: 1, totalDuration: 500 })
    expect(traceSummary([])).toEqual({ count: 0, errors: 0, totalDuration: 0 })
    expect(traceSummary(null)).toEqual({ count: 0, errors: 0, totalDuration: 0 })
  })
})
