// 实时通道连接策略测试（零依赖，node:test 运行）。
//
// 背景（E2E F-10）：旧实现的 connectReal() 在 token 为空 / WebSocket 构造失败时
// 直接 return，而 initRealtime() 已置 started=true，导致实时通道「永久停摆」。
// 本测试分两层：
//   1) realtimePolicy.js 纯函数行为（决策、退避、状态迁移）；
//   2) realtime.js 源码契约静态断言——纯函数测试无法验证副作用层的真实接线，
//      用源码文本锁定「no-token/构造失败必须调度重连、无固定 5s、状态 API 已暴露」，
//      防止后续改动把接线悄悄回退。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  REALTIME_STATE,
  CONNECT_ACTION,
  resolveConnectAction,
  reconnectDelay,
  nextState,
  RECONNECT_BASE_MS,
  RECONNECT_MAX_MS,
  RESET_ATTEMPT
} from '../realtimePolicy.js'

const here = dirname(fileURLToPath(import.meta.url))
const realtimeSrc = readFileSync(resolve(here, '../realtime.js'), 'utf8')

// ---------- 1) resolveConnectAction ----------

test('未启动（started=false）恒为 STOP：不连接、不重试', () => {
  assert.equal(resolveConnectAction({ started: false, token: 'jwt', hasActiveSocket: false }), CONNECT_ACTION.STOP)
  assert.equal(resolveConnectAction({ started: false, token: '', hasActiveSocket: false }), CONNECT_ACTION.STOP)
  assert.equal(resolveConnectAction(), CONNECT_ACTION.STOP)
})

test('已有活跃 socket 时为 SKIP，避免重复建连', () => {
  assert.equal(
    resolveConnectAction({ started: true, token: 'jwt', hasActiveSocket: true }),
    CONNECT_ACTION.SKIP
  )
})

test('核心不变量：started=true 且 token 暂缺时必须返回 WAIT_TOKEN（而非静默放弃）', () => {
  const action = resolveConnectAction({ started: true, token: '', hasActiveSocket: false })
  assert.equal(action, CONNECT_ACTION.WAIT_TOKEN)
  // 只要已启动且无活跃连接，返回值绝不能是 STOP
  assert.notEqual(action, CONNECT_ACTION.STOP)
  assert.notEqual(resolveConnectAction({ started: true, token: undefined, hasActiveSocket: false }), CONNECT_ACTION.STOP)
  assert.notEqual(resolveConnectAction({ started: true, token: null, hasActiveSocket: false }), CONNECT_ACTION.STOP)
})

test('token 就绪且无活跃连接时为 CONNECT', () => {
  assert.equal(
    resolveConnectAction({ started: true, token: 'jwt', hasActiveSocket: false }),
    CONNECT_ACTION.CONNECT
  )
})

// ---------- 2) reconnectDelay 指数退避 ----------

test('退避序列 1s→2s→4s→8s→16s→封顶 30s', () => {
  assert.equal(reconnectDelay(0), 1000)
  assert.equal(reconnectDelay(1), 2000)
  assert.equal(reconnectDelay(2), 4000)
  assert.equal(reconnectDelay(3), 8000)
  assert.equal(reconnectDelay(4), 16000)
  assert.equal(reconnectDelay(5), 30000) // 32000 被钳制
  assert.equal(reconnectDelay(10), 30000)
})

test('退避边界：负数 / NaN / 小数 / 超大均收敛到合法区间', () => {
  assert.equal(reconnectDelay(-3), RECONNECT_BASE_MS)
  assert.equal(reconnectDelay(NaN), RECONNECT_BASE_MS)
  assert.equal(reconnectDelay(undefined), RECONNECT_BASE_MS)
  assert.equal(reconnectDelay(2.9), 4000) // 向下取整
  assert.equal(reconnectDelay(Infinity), RECONNECT_BASE_MS)
  for (const n of [0, 1, 3, 7, 99]) {
    assert.ok(reconnectDelay(n) >= RECONNECT_BASE_MS && reconnectDelay(n) <= RECONNECT_MAX_MS)
  }
})

// ---------- 3) nextState 状态迁移 ----------

test('状态迁移覆盖 start/open/close/wait-token/connect-error/stop', () => {
  assert.equal(nextState('start'), REALTIME_STATE.CONNECTING)
  assert.equal(nextState('open'), REALTIME_STATE.OPEN)
  assert.equal(nextState('close'), REALTIME_STATE.RECONNECTING)
  assert.equal(nextState('wait-token'), REALTIME_STATE.RECONNECTING)
  assert.equal(nextState('connect-error'), REALTIME_STATE.RECONNECTING)
  assert.equal(nextState('stop'), REALTIME_STATE.CLOSED)
})

test('未知事件保持当前状态（副作用层拼写错误不会打乱状态机）', () => {
  assert.equal(nextState('typo', REALTIME_STATE.OPEN), REALTIME_STATE.OPEN)
  assert.equal(nextState(undefined, REALTIME_STATE.CLOSED), REALTIME_STATE.CLOSED)
  assert.equal(RESET_ATTEMPT, 0)
})

// ---------- 4) realtime.js 源码契约（防止接线回退） ----------

test('realtime.js 引入策略模块并暴露状态 API', () => {
  assert.match(realtimeSrc, /from '\.\/realtimePolicy'/)
  assert.match(realtimeSrc, /export function getRealtimeState\(/)
  assert.match(realtimeSrc, /export function onRealtimeState\(/)
})

test('realtime.js 不再存在「token 为空直接 return」的永久停摆分支', () => {
  assert.doesNotMatch(realtimeSrc, /if \(!token\) return/)
  assert.match(realtimeSrc, /action === CONNECT_ACTION\.WAIT_TOKEN/)
  // WAIT_TOKEN 分支必须调度重连
  const waitBranch = realtimeSrc.slice(
    realtimeSrc.indexOf('action === CONNECT_ACTION.WAIT_TOKEN'),
    realtimeSrc.indexOf('// 重连前先清掉上一轮遗留的定时器')
  )
  assert.match(waitBranch, /scheduleReconnect\(\)/)
})

test('realtime.js 构造失败分支也调度重连，且不再有裸 return', () => {
  const catchBlock = realtimeSrc.slice(
    realtimeSrc.indexOf('ws = new WebSocket(url)'),
    realtimeSrc.indexOf('setState(nextState(\'start\'))')
  )
  assert.match(catchBlock, /catch \(e\)/)
  assert.match(catchBlock, /scheduleReconnect\(\)/)
  assert.doesNotMatch(catchBlock, /catch \(e\) \{\s*return\s*\}/)
})

test('realtime.js 断线重连改指数退避：不再硬编码 5000ms，onopen 复位计数', () => {
  assert.doesNotMatch(realtimeSrc, /setTimeout\(connectReal, 5000\)/)
  assert.match(realtimeSrc, /const delay = reconnectDelay\(retryAttempt\)/)
  assert.match(realtimeSrc, /retryAttempt = RESET_ATTEMPT/)
  assert.match(realtimeSrc, /ws\.onclose = \(\) => \{[\s\S]*?scheduleReconnect\(\)/)
})

test('closeRealtime 主动关闭会摘掉 onclose 并清理重连定时器', () => {
  assert.match(realtimeSrc, /ws\.onclose = null/)
  assert.match(realtimeSrc, /export function closeRealtime\(\) \{[\s\S]*?clearTimeout\(reconnectTimer\)/)
})
