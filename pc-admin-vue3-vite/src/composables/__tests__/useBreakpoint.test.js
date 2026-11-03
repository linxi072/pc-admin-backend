// 响应式断点纯函数单测（零依赖，node:test 运行）。
// 仅覆盖无 window 依赖的纯逻辑，组件内的 matchMedia 订阅由浏览器环境保证。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { resolveBreakpoint, isMobileWidth, MOBILE_MAX_WIDTH } from '../useBreakpoint.js'

test('resolveBreakpoint：<= 768 为 mobile', () => {
  assert.equal(resolveBreakpoint(0), 'mobile')
  assert.equal(resolveBreakpoint(480), 'mobile')
  assert.equal(resolveBreakpoint(MOBILE_MAX_WIDTH), 'mobile')
})

test('resolveBreakpoint：769–1024 为 tablet', () => {
  assert.equal(resolveBreakpoint(MOBILE_MAX_WIDTH + 1), 'tablet')
  assert.equal(resolveBreakpoint(900), 'tablet')
  assert.equal(resolveBreakpoint(1024), 'tablet')
})

test('resolveBreakpoint：> 1024 为 desktop', () => {
  assert.equal(resolveBreakpoint(1025), 'desktop')
  assert.equal(resolveBreakpoint(1440), 'desktop')
  assert.equal(resolveBreakpoint(1920), 'desktop')
})

test('resolveBreakpoint：非法宽度安全降级为 desktop', () => {
  assert.equal(resolveBreakpoint(NaN), 'desktop')
  assert.equal(resolveBreakpoint('x'), 'desktop')
  assert.equal(resolveBreakpoint(undefined), 'desktop')
})

test('isMobileWidth 与 resolveBreakpoint 一致', () => {
  assert.equal(isMobileWidth(375), true)
  assert.equal(isMobileWidth(768), true)
  assert.equal(isMobileWidth(769), false)
  assert.equal(isMobileWidth(1280), false)
})
