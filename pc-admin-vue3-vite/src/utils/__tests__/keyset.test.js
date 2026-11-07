// 游标（keyset）分页前端策略测试（零依赖，node:test 运行）。
//
// 背景（功能梳理 F-11）：后端 6 个 keyset 端点早已存在，前端却零消费；
// 深翻页越过 5000 行 OFFSET 护栏时，后端错误提示还指向「前端根本没实现的接口」。
// 本测试覆盖：offset 阈值判定、后端护栏错误识别、游标页栈（上一页/下一页），
// 并用源码契约断言锁定「6 个 API 端点 + 操作日志页自动切换」的真实接线。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  OFFSET_SOFT_LIMIT, offsetOf, shouldUseKeyset, isOffsetLimitError,
  createCursorStack, currentCursor, setNextCursor, goNext, goPrev,
  canGoNext, canGoPrev, pageNumber
} from '../keyset.js'

const here = dirname(fileURLToPath(import.meta.url))
const srcRoot = resolve(here, '../../..')
const readSrc = (rel) => readFileSync(resolve(srcRoot, rel), 'utf8')

// ---------- 1) offset 阈值判定 ----------

test('offsetOf：标准页码换算与非法入参兜底', () => {
  assert.equal(offsetOf(1, 10), 0)
  assert.equal(offsetOf(2, 10), 10)
  assert.equal(offsetOf(501, 10), 5000)
  assert.equal(offsetOf(0, 10), 0)
  assert.equal(offsetOf(-3, 10), 0)
  assert.equal(offsetOf(2, NaN), 10)
  assert.equal(offsetOf(3.7, 10), 20) // 向下取整
})

test('shouldUseKeyset：严格大于 5000 行软阈值才切换', () => {
  assert.equal(OFFSET_SOFT_LIMIT, 5000)
  assert.equal(shouldUseKeyset(501, 10), false) // offset = 5000，未超
  assert.equal(shouldUseKeyset(502, 10), true) // offset = 5010
  assert.equal(shouldUseKeyset(1, 10), false)
  assert.equal(shouldUseKeyset(1001, 10), true)
  assert.equal(shouldUseKeyset(502, 10, 10000), false) // 自定义阈值
})

// ---------- 2) 后端护栏错误识别 ----------

test('isOffsetLimitError：识别中文护栏提示与 keyset 端点路径', () => {
  assert.equal(isOffsetLimitError(new Error('用户列表分页偏移过大（超过 5000 行），请改用游标分页接口 /api/system/users/page-keyset')), true)
  assert.equal(isOffsetLimitError(new Error('已发送消息分页偏移过大，请改用游标分页接口 /api/system/messages/sent-keyset')), true)
  assert.equal(isOffsetLimitError('站内信分页偏移过大，请改用游标分页接口 /api/system/messages/mine-keyset'), true)
  assert.equal(isOffsetLimitError(new Error('offset exceeds limit')), true)
})

test('isOffsetLimitError：普通业务错误不误判', () => {
  assert.equal(isOffsetLimitError(new Error('网络错误')), false)
  assert.equal(isOffsetLimitError(new Error('无权限访问')), false)
  assert.equal(isOffsetLimitError(null), false)
  assert.equal(isOffsetLimitError(undefined), false)
})

// ---------- 3) 游标页栈 ----------

test('初始栈：首页无游标、不可前进/后退、页码为 1', () => {
  const s = createCursorStack()
  assert.deepEqual(s.cursors, [null])
  assert.equal(currentCursor(s), null)
  assert.equal(canGoNext(s), false)
  assert.equal(canGoPrev(s), false)
  assert.equal(pageNumber(s), 1)
})

test('前进：nextCursor 压栈成为下一页起始游标', () => {
  let s = createCursorStack()
  s = setNextCursor(s, 'C1')
  assert.equal(canGoNext(s), true)
  s = goNext(s)
  assert.equal(pageNumber(s), 2)
  assert.equal(currentCursor(s), 'C1')
  assert.equal(canGoNext(s), false) // 新页响应未回来前不可再前进
  assert.equal(canGoPrev(s), true)
})

test('多页前进 + 回退：游标可复用，回退仅移动下标', () => {
  let s = createCursorStack()
  s = goNext(setNextCursor(s, 'C1'))
  s = goNext(setNextCursor(s, 'C2'))
  assert.equal(pageNumber(s), 3)
  assert.equal(currentCursor(s), 'C2')
  assert.deepEqual(s.cursors, [null, 'C1', 'C2'])

  s = goPrev(s)
  assert.equal(pageNumber(s), 2)
  assert.equal(currentCursor(s), 'C1')
  assert.deepEqual(s.cursors, [null, 'C1', 'C2']) // 保留以供再次前进

  s = goPrev(s)
  assert.equal(pageNumber(s), 1)
  assert.equal(currentCursor(s), null)
  assert.equal(goPrev(s), s) // 首页不可再退，原样返回
})

test('末页：空 nextCursor 时 goNext 原样返回，setNextCursor 归一为空串', () => {
  let s = createCursorStack()
  s = setNextCursor(s, '')
  assert.equal(canGoNext(s), false)
  assert.equal(goNext(s), s)
  assert.deepEqual(setNextCursor(s, undefined), { ...s, nextCursor: '' })
})

test('页栈非法入参归一：null / 坏栈不抛错，全部回落为首页状态', () => {
  const badStacks = [
    null, undefined, {}, { cursors: [] }, { cursors: [null], index: 99 },
    { cursors: 'oops', index: 0 }, 'oops', 42
  ]
  for (const bad of badStacks) {
    assert.equal(currentCursor(bad), null)
    assert.equal(canGoNext(bad), false)
    assert.equal(canGoPrev(bad), false)
    assert.equal(pageNumber(bad), 1)
    assert.doesNotThrow(() => goNext(bad))
    assert.doesNotThrow(() => goPrev(bad))
    assert.equal(setNextCursor(bad, 'C1').nextCursor, 'C1')
  }
})

test('合法栈仍保持对象身份：首页不可退 / 末页不可进时原样返回', () => {
  const s = createCursorStack()
  assert.equal(goPrev(s), s)
  assert.equal(goNext(s), s)
  const withCursor = setNextCursor(s, 'C1')
  assert.equal(goPrev(withCursor), withCursor)
})

// ---------- 4) 源码契约：6 个 API 端点消费已补齐 ----------

const endpointCases = [
  ['src/api/audit.js', 'pageAuditLogsKeyset', '/api/system/audit-logs/keyset'],
  ['src/api/user.js', 'pageUsersKeyset', '/api/system/users/page-keyset'],
  ['src/api/message.js', 'pageMyMessagesKeyset', '/api/system/messages/mine-keyset'],
  ['src/api/message.js', 'pageSentMessagesKeyset', '/api/system/messages/sent-keyset'],
  ['src/api/file.js', 'pageFilesKeyset', '/api/system/files/keyset'],
  ['src/api/loginLog.js', 'pageLoginLogsKeyset', '/api/system/login-logs/keyset']
]

test('6 个 keyset 端点前端均有消费函数，URL 与后端 Controller 一致', () => {
  for (const [file, fn, url] of endpointCases) {
    const src = readSrc(file)
    assert.match(src, new RegExp(`export const ${fn}\\b`), `${file} 缺少 ${fn}`)
    assert.ok(src.includes(`url: '${url}'`), `${file} 缺少 ${url}`)
  }
})

// ---------- 5) 源码契约：操作日志页自动切换接线 ----------

test('OperationLogView.vue 同时消费 page/keyset 端点并实现自动切换', () => {
  const src = readSrc('src/views/system/OperationLogView.vue')
  assert.match(src, /pageAuditLogsKeyset/)
  assert.match(src, /shouldUseKeyset\(query\.page, query\.size\)/)
  assert.match(src, /isOffsetLimitError\(pageResult\.reason\)/)
  assert.match(src, /enterKeysetMode\(\)/)
  assert.match(src, /buildKeysetParams\(\)/)
  // 游标端点不接受 sortField/sortDirection：参数构造里不得带上
  const paramsBody = src.slice(src.indexOf('function buildKeysetParams'), src.indexOf('/** 切进游标模式'))
  assert.doesNotMatch(paramsBody, /sortField|sortDirection/)
  // 筛选变化必须重置游标栈，避免筛选条件与游标错配
  assert.match(src, /function resetKeyset\(\)/)
  for (const fn of ['onSearch', 'onSizeChange', 'resetQuery', 'onSortChange']) {
    const body = src.slice(src.indexOf(`function ${fn}(`), src.indexOf(`function ${fn}(`) + 400)
    assert.match(body, /resetKeyset\(\)/, `${fn} 未重置游标栈`)
  }
})

test('i18n 两端均补齐 keyset 文案（键集合一致由 messages.test.js 兜底）', () => {
  const src = readSrc('src/i18n/messages.js')
  for (const key of ['keysetMode', 'keysetHint', 'keysetPage', 'keysetPrev', 'keysetNext', 'keysetBack']) {
    assert.equal((src.match(new RegExp(`\\b${key}:`, 'g')) || []).length, 2, `${key} 应在中英两端各出现一次`)
  }
})
