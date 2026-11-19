import { describe, expect, it } from 'vitest'
import {
  CONNECT_ACTION,
  REALTIME_STATE,
  RECONNECT_BASE_MS,
  RECONNECT_MAX_MS,
  nextState,
  reconnectDelay,
  resolveConnectAction
} from '../realtimePolicy.js'

// V1.1.0 测试覆盖重建立项：实时通道策略纯函数回归（E2E F-10 防回归）

describe('resolveConnectAction', () => {
  it('未启动时返回 STOP（不连也不重试）', () => {
    expect(resolveConnectAction({ started: false, token: '', hasActiveSocket: false })).toBe(CONNECT_ACTION.STOP)
  })

  it('已有活跃连接时返回 SKIP', () => {
    expect(
      resolveConnectAction({ started: true, token: 't', hasActiveSocket: true })
    ).toBe(CONNECT_ACTION.SKIP)
  })

  it('关键不变量：已启动且无 socket 且 token 暂缺必须返回 WAIT_TOKEN（防永久停摆）', () => {
    expect(resolveConnectAction({ started: true, token: '', hasActiveSocket: false })).toBe(
      CONNECT_ACTION.WAIT_TOKEN
    )
    expect(resolveConnectAction({ started: true, token: undefined, hasActiveSocket: false })).not.toBe(
      CONNECT_ACTION.STOP
    )
  })

  it('正常条件返回 CONNECT', () => {
    expect(resolveConnectAction({ started: true, token: 'jwt', hasActiveSocket: false })).toBe(
      CONNECT_ACTION.CONNECT
    )
  })
})

describe('reconnectDelay', () => {
  it('指数退避序列：1s, 2s, 4s, 8s, 16s', () => {
    expect(reconnectDelay(0)).toBe(RECONNECT_BASE_MS)
    expect(reconnectDelay(1)).toBe(2000)
    expect(reconnectDelay(2)).toBe(4000)
    expect(reconnectDelay(3)).toBe(8000)
    expect(reconnectDelay(4)).toBe(16000)
  })

  it('封顶于 RECONNECT_MAX_MS', () => {
    expect(reconnectDelay(10)).toBe(RECONNECT_MAX_MS)
    expect(reconnectDelay(100)).toBe(RECONNECT_MAX_MS)
  })

  it('非法输入归零处理且始终在合法区间', () => {
    expect(reconnectDelay(-5)).toBe(RECONNECT_BASE_MS)
    expect(reconnectDelay(NaN)).toBe(RECONNECT_BASE_MS)
    const d = reconnectDelay(1.9)
    expect(d).toBeGreaterThanOrEqual(RECONNECT_BASE_MS)
    expect(d).toBeLessThanOrEqual(RECONNECT_MAX_MS)
  })
})

describe('nextState', () => {
  it('基本迁移路径', () => {
    expect(nextState('start')).toBe(REALTIME_STATE.CONNECTING)
    expect(nextState('open', REALTIME_STATE.CONNECTING)).toBe(REALTIME_STATE.OPEN)
    expect(nextState('wait-token', REALTIME_STATE.CONNECTING)).toBe(REALTIME_STATE.RECONNECTING)
    expect(nextState('close', REALTIME_STATE.OPEN)).toBe(REALTIME_STATE.RECONNECTING)
    expect(nextState('connect-error', REALTIME_STATE.OPEN)).toBe(REALTIME_STATE.RECONNECTING)
    expect(nextState('stop', REALTIME_STATE.OPEN)).toBe(REALTIME_STATE.CLOSED)
  })

  it('未知事件保持当前状态（防拼写错误打乱状态机）', () => {
    expect(nextState('typo', REALTIME_STATE.OPEN)).toBe(REALTIME_STATE.OPEN)
  })
})
