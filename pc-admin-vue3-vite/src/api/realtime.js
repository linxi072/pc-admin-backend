import { authState } from '@/store/auth'

/**
 * 实时消息客户端（单例）。
 *
 * 设计：连接同源 /ws?token=<JWT>，与后端 GatewayNode 的 wg 二进制协议对齐：
 * <ul>
 *   <li>数据帧（魔数 0x57）：magic | version | type | flags | varint(payLen) | zigzag(seq)
 *       | varint(msgIdLen) msgId | varint(keyLen) key | zigzag(ts) | [traceId] | payload；</li>
 *   <li>控制帧（魔数 0x41）：magic | version | type | flags | [zigzag(seq)]。</li>
 * </ul>
 * 业务负载为 RealtimeMessage JSON（{ type, payload, ts }），解码后按原契约转发订阅者；
 * 心跳为二进制 PING 帧（30s），收到 PONG 即视为链路存活；断线 5s 重连。
 *
 * 业务侧只需在布局初始化时 initRealtime() + onRealtime(handler) 订阅即可。
 * 未读角标等权威值一律以后端接口为准。
 */

// ---- wg 二进制协议（与 scaffold-common FrameCodec / ControlCodec 线格式一致） ----

const FRAME_MAGIC = 0x57 // 'W' 数据帧
const CONTROL_MAGIC = 0x41 // 'A' 控制帧
const VERSION = 0x01
const FLAG_HAS_TRACE_ID = 0x01
const MAX_PAYLOAD = 1 << 20

const TYPE = { DATA: 0x01, ACK: 0x02, PING: 0x03, PONG: 0x04, CLOSE: 0x05, RESUME: 0x06 }

const textDecoder = new TextDecoder('utf-8')

/** 读无符号 varint（LEB128，小端 7-bit/组）；返回 [值, 下一偏移]。 */
function readVarint(view, pos) {
  let result = 0
  let shift = 0
  let bytes = 0
  while (true) {
    if (pos >= view.byteLength || bytes >= 10) throw new Error('varint 越界')
    const b = view.getUint8(pos++)
    bytes++
    result += (b & 0x7f) * Math.pow(2, shift)
    if ((b & 0x80) === 0) break
    shift += 7
  }
  return [result, pos]
}

/** 读 zigzag64（协议内时序/序号均小于 2^53，Number 精确表示）。 */
function readZigZag(view, pos) {
  const [raw, next] = readVarint(view, pos)
  return [raw % 2 === 1 ? -(raw + 1) / 2 : raw / 2, next]
}

function writeVarint(buf, pos, value) {
  while (value > 0x7f) {
    buf[pos++] = (value & 0x7f) | 0x80
    value = Math.floor(value / 128)
  }
  buf[pos++] = value
  return pos
}

function writeZigZag(buf, pos, value) {
  const raw = value >= 0 ? value * 2 : -value * 2 - 1
  return writeVarint(buf, pos, raw)
}

/** 编码一帧数据信封（seq=0 表示上行未分配，由网关补齐）。 */
function encodeEnvelope(type, payloadBytes, msgId, key) {
  const enc = new TextEncoder()
  const msgIdBytes = enc.encode(msgId)
  const keyBytes = enc.encode(key)
  const payload = payloadBytes || new Uint8Array(0)
  const size = 4 // 头四字节
    + writeVarintSize(payload.length) // payLen
    + writeZigZagSize(0) // seq
    + writeVarintSize(msgIdBytes.length) + msgIdBytes.length
    + writeVarintSize(keyBytes.length) + keyBytes.length
    + writeZigZagSize(Date.now()) // ts
    + payload.length
  const buf = new Uint8Array(size)
  let pos = 0
  buf[pos++] = FRAME_MAGIC
  buf[pos++] = VERSION
  buf[pos++] = type
  buf[pos++] = 0
  pos = writeVarint(buf, pos, payload.length)
  pos = writeZigZag(buf, pos, 0)
  pos = writeVarint(buf, pos, msgIdBytes.length)
  buf.set(msgIdBytes, pos)
  pos += msgIdBytes.length
  pos = writeVarint(buf, pos, keyBytes.length)
  buf.set(keyBytes, pos)
  pos += keyBytes.length
  pos = writeZigZag(buf, pos, Date.now())
  buf.set(payload, pos)
  return buf
}

function writeVarintSize(value) {
  let n = 1
  while (value > 0x7f) {
    value = Math.floor(value / 128)
    n++
  }
  return n
}

function writeZigZagSize(value) {
  return writeVarintSize(value >= 0 ? value * 2 : -value * 2 - 1)
}

/** 构造二进制 PING 帧（数据信封形态，msgId/key 非空以满足 Envelope 校验）。 */
function encodePing() {
  return encodeEnvelope(TYPE.PING, new Uint8Array(0), 'ping-' + Date.now(), 'hb')
}

/** 构造控制帧（0x41）：PONG / ACK（ACK 携带 zigzag seq）。 */
function encodeControl(type, seq) {
  const parts = [CONTROL_MAGIC, VERSION, type, type === TYPE.ACK ? 0x01 : 0]
  if (type === TYPE.ACK) {
    // zigzag(seq) 按实际宽度追加（固定 9 字节会残留未消费尾字节，Java expectEnd 拒绝）
    const raw = seq >= 0 ? seq * 2 : -seq * 2 - 1
    const tail = []
    let v = raw
    while (v > 0x7f) {
      tail.push((v & 0x7f) | 0x80)
      v = Math.floor(v / 128)
    }
    tail.push(v)
    return new Uint8Array([...parts, ...tail])
  }
  return new Uint8Array(parts)
}

/**
 * 解码一帧（数据帧或控制帧）。
 * @returns {{kind:'data'|'control', type:number, seq:number, msgId:string, key:string, ts:number, payload:Uint8Array}|null}
 */
function decodeFrame(arrayBuffer) {
  const view = new DataView(arrayBuffer)
  if (view.byteLength < 4) return null
  const magic = view.getUint8(0)
  let pos = 2
  if (magic === CONTROL_MAGIC) {
    const type = view.getUint8(2)
    const flags = view.getUint8(3)
    let seq = 0
    pos = 4
    if ((flags & 0x01) !== 0) {
      ;[seq, pos] = readZigZag(view, pos)
    }
    if (pos !== view.byteLength) return null // 控制帧无尾字节
    return { kind: 'control', type, seq, msgId: '', key: '', ts: 0, payload: new Uint8Array(0) }
  }
  if (magic !== FRAME_MAGIC) return null
  const type = view.getUint8(2)
  const flags = view.getUint8(3)
  pos = 4
  const [payLen, p1] = readVarint(view, pos)
  if (payLen < 0 || payLen > MAX_PAYLOAD) return null
  const [seq, p2] = readZigZag(view, p1)
  const [msgIdLen, p3] = readVarint(view, p2)
  const msgId = textDecoder.decode(new Uint8Array(arrayBuffer, p3, msgIdLen))
  const [keyLen, p4] = readVarint(view, p3 + msgIdLen)
  const key = textDecoder.decode(new Uint8Array(arrayBuffer, p4, keyLen))
  const [ts, p5] = readZigZag(view, p4 + keyLen)
  let traceId = ''
  if ((flags & FLAG_HAS_TRACE_ID) !== 0) {
    const [traceLen, p6] = readVarint(view, p5)
    traceId = textDecoder.decode(new Uint8Array(arrayBuffer, p6, traceLen))
    pos = p6 + traceLen
  } else {
    pos = p5
  }
  const payload = new Uint8Array(arrayBuffer, pos, payLen)
  if (pos + payLen !== view.byteLength) return null // 帧尾必须对齐
  return { kind: 'data', type, seq, msgId, key, ts, traceId, payload }
}

// ---- 连接管理（单例） ----

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

/** 收帧处理：控制帧（pong/ack 忽略，ping 回应）与数据帧（业务 JSON 分发）。 */
function onFrame(arrayBuffer) {
  let frame = null
  try {
    frame = decodeFrame(arrayBuffer)
  } catch (e) {
    return // 坏帧静默丢弃：协议由服务端 4003 断链兜底
  }
  if (!frame) return
  if (frame.kind === 'control') {
    if (frame.type === TYPE.PING) {
      // 服务端探测：回控制帧 PONG（与 ControlCodec 形态一致）
      if (ws && ws.readyState === WebSocket.OPEN) ws.send(encodeControl(TYPE.PONG))
    }
    // PONG / ACK / CLOSE：无需业务动作（CLOSE 由 onclose 走重连）
    return
  }
  if (frame.type !== TYPE.DATA) return
  try {
    const msg = JSON.parse(textDecoder.decode(frame.payload))
    dispatch(msg)
  } catch (e) {
    // 非 JSON 负载（协议演进期的兼容帧）静默忽略
  }
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
  ws.binaryType = 'arraybuffer'
  ws.onopen = () => {
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(encodePing())
      }
    }, 30000)
  }
  ws.onmessage = (ev) => {
    const raw = ev.data
    if (typeof raw === 'string') {
      // 兼容旧服务端文本语义：'pong' 为心跳回应，其余按 JSON 业务消息处理
      if (raw === 'pong' || raw === '"pong"') return
      try {
        const msg = JSON.parse(raw)
        if (msg && msg.type === 'PONG') return
        dispatch(msg)
      } catch (e) {
        // 忽略其他非 JSON 帧
      }
      return
    }
    if (raw instanceof ArrayBuffer) {
      onFrame(raw)
    }
    // Blob（binaryType 未生效的极端情况）忽略
  }
  ws.onclose = () => {
    ws = null
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
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
