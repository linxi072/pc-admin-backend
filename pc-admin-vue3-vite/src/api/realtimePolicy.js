// 实时通道连接策略（纯函数，零依赖，可被 node:test 直接验证）。
//
// 背景（E2E F-10）：旧实现里 initRealtime() 先置 started=true 再调 connectReal()，
// 而 connectReal() 在 authState.token 为空时直接 return、且不做任何重连安排；
// new WebSocket(url) 构造抛异常时同样直接 return。两种情况 started 都仍是 true，
// 却没有任何定时器被安排，实时通道进入「永久停摆」——登录时序竞争
// （布局挂载早于 token 写入 authState）下尤其容易触发，铃铛只能靠 30s 轮询兜底。
//
// 本模块把「是否连接 / 何时重连 / 状态如何迁移」抽成纯函数，副作用层
// （realtime.js）只负责按策略执行定时器与 WebSocket，从而可被离线单测覆盖。

/** 连接状态机取值。 */
export const REALTIME_STATE = Object.freeze({
  IDLE: 'idle',
  CONNECTING: 'connecting',
  OPEN: 'open',
  RECONNECTING: 'reconnecting',
  CLOSED: 'closed'
})

/** connectReal() 的决策结果。 */
export const CONNECT_ACTION = Object.freeze({
  CONNECT: 'connect', // 正常发起连接
  WAIT_TOKEN: 'wait-token', // token 暂缺：不能连，但必须安排重试（避免永久停摆）
  SKIP: 'skip', // 已有活跃连接：忽略本次触发
  STOP: 'stop' // 未启动 / 已关闭：不连也不重试
})

/**
 * 决定是否发起一次连接尝试。
 *
 * 关键不变量：只要 started=true 且无活跃 socket，返回值就一定不是 STOP——
 * token 暂缺时必须给出 WAIT_TOKEN 让调用方安排重试，绝不能静默放弃。
 *
 * @param {object} ctx
 * @param {boolean} ctx.started initRealtime() 已调用且未 close
 * @param {string} ctx.token 当前认证令牌
 * @param {boolean} ctx.hasActiveSocket 是否已有 OPEN/CONNECTING 中的 socket
 * @returns {string} CONNECT_ACTION 之一
 */
export function resolveConnectAction({ started = false, token = '', hasActiveSocket = false } = {}) {
  if (!started) return CONNECT_ACTION.STOP
  if (hasActiveSocket) return CONNECT_ACTION.SKIP
  if (!token) return CONNECT_ACTION.WAIT_TOKEN
  return CONNECT_ACTION.CONNECT
}

export const RECONNECT_BASE_MS = 1000
export const RECONNECT_MAX_MS = 30000
/** 连接成功后连续失败计数复位值。 */
export const RESET_ATTEMPT = 0

/**
 * 指数退避重连延时：1s, 2s, 4s, 8s, 16s, 30s, 30s...
 * @param {number} attempt 连续失败次数（0 表示第一次重连）
 * @returns {number} 毫秒，恒在 [RECONNECT_BASE_MS, RECONNECT_MAX_MS] 内
 */
export function reconnectDelay(attempt = 0) {
  const n = Number.isFinite(attempt) && attempt > 0 ? Math.floor(attempt) : 0
  return Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** n)
}

/**
 * 状态迁移（纯函数）。事件：start / open / wait-token / close / connect-error / stop。
 * 未知事件保持当前状态，避免副作用层传入拼写错误时把状态机打乱。
 */
export function nextState(event, current = REALTIME_STATE.IDLE) {
  switch (event) {
    case 'start':
      return REALTIME_STATE.CONNECTING
    case 'open':
      return REALTIME_STATE.OPEN
    case 'wait-token':
    case 'close':
    case 'connect-error':
      return REALTIME_STATE.RECONNECTING
    case 'stop':
      return REALTIME_STATE.CLOSED
    default:
      return current
  }
}
