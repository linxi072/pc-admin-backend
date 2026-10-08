import { USE_MOCK } from './config'
import { authState } from '@/store/auth'

/**
 * 实时消息客户端（单例）。
 *
 * 设计：
 * - 真实模式（USE_MOCK=false）：连接同源 /ws?token=<JWT>，收到后端推送的
 *   { type: 'UNREAD_MESSAGE' | 'TODO_REMINDER', payload } 后转发给订阅者；
 *   带 30s 心跳（ping/pong）与断线重连。
 * - Mock 模式（USE_MOCK=true）：不建立真实连接，改为定时模拟一条「未读消息」事件，
 *   驱动顶栏角标实时变化（演示实时性）。真实未读计数仍以 mock 接口为准。
 *
 * 业务侧只需在布局初始化时 initRealtime() + onRealtime(handler) 订阅即可。
 */
const listeners = new Set()
let started = false
let ws = null
let heartbeatTimer = null
let mockTimer = null

function dispatch(msg) {
  listeners.forEach((cb) => {
    try {
      cb(msg)
    } catch (e) {
      // 单个订阅者异常不应影响其余订阅者
    }
  })
}

function wsBase() {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws`
}

function connectReal() {
  const token = authState.token
  if (!token) return
  const url = `${wsBase()}?token=${encodeURIComponent(token)}`
  try {
    ws = new WebSocket(url)
  } catch (e) {
    return
  }
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg && msg.type === 'PONG') return
      dispatch(msg)
    } catch (e) {
      // 忽略非 JSON 帧
    }
  }
  ws.onclose = () => {
    ws = null
    if (started) {
      setTimeout(connectReal, 5000) // 断线 5s 后重连
    }
  }
  ws.onerror = () => {
    try {
      ws.close()
    } catch (e) {
      // ignore
    }
  }
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send('ping')
    }
  }, 30000)
}

function connectMock() {
  // 模拟服务端定时推送未读消息，演示实时角标刷新（演示环境无真实后端）
  mockTimer = setInterval(() => {
    dispatch({ type: 'UNREAD_MESSAGE', payload: { simulated: true, delta: 1 } })
  }, 40000)
}

export function initRealtime() {
  if (started) return
  started = true
  if (USE_MOCK) {
    connectMock()
  } else {
    connectReal()
  }
}

export function onRealtime(cb) {
  listeners.add(cb)
  return () => listeners.delete(cb)
}

export function closeRealtime() {
  started = false
  if (ws) {
    try {
      ws.close()
    } catch (e) {
      // ignore
    }
    ws = null
  }
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  if (mockTimer) clearInterval(mockTimer)
  listeners.clear()
}
