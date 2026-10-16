import { authState } from '@/store/auth'

/**
 * 实时消息客户端（单例）。
 *
 * 设计：连接同源 /ws?token=<JWT>，收到后端推送的
 * { type: 'UNREAD_MESSAGE' | 'TODO_REMINDER', payload } 后转发给订阅者；
 * 带 30s 心跳（ping/pong）与断线重连（5s）。
 *
 * 业务侧只需在布局初始化时 initRealtime() + onRealtime(handler) 订阅即可。
 * 未读角标等权威值一律以后端接口为准。
 */
const listeners = new Set()
let started = false
let ws = null
let heartbeatTimer = null
let reconnectTimer = null

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
  // 重连前先清掉上一轮遗留的定时器，否则每次重连都会叠加一个心跳定时器
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  const url = `${wsBase()}?token=${encodeURIComponent(token)}`
  try {
    ws = new WebSocket(url)
  } catch (e) {
    return
  }
  ws.onmessage = (ev) => {
    const raw = ev.data
    // 后端以原始字符串 'pong' 回应心跳（非 JSON），先按字符串短路，避免 JSON.parse 抛错被静默吞掉
    if (raw === 'pong' || raw === '"pong"') return
    try {
      const msg = JSON.parse(raw)
      if (msg && msg.type === 'PONG') return
      dispatch(msg)
    } catch (e) {
      // 忽略其他非 JSON 帧
    }
  }
  ws.onclose = () => {
    ws = null
    if (started) {
      reconnectTimer = setTimeout(connectReal, 5000) // 断线 5s 后重连
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

export function initRealtime() {
  if (started) return
  started = true
  connectReal()
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
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  listeners.clear()
}
