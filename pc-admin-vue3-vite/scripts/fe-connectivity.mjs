/**
 * 前端前后端联通测试（Node 侧，不依赖浏览器/Vitest，离线可跑）。
 *
 * 覆盖用户要求的四类场景：
 *   1) 正常请求/响应流程：ping 返回包络 data，字段与后端契约一致；
 *   2) 数据格式与字段一致性：统一 Result{code,message,data} 拆包后字段齐全；
 *   3) 常见错误码与异常：2xx 业务错误（code!=0）提取 code 并 reject；
 *      5xx 返回后端错误码（COMMON_500）；
 *   4) 网络超时：后端故意慢响应，axios 抛出 ECONNABORTED。
 *
 * 实现说明：
 *   - 用 node:http 起一个本地 stub 服务端，模拟后端统一包络（沙箱无后端、无出网）；
 *   - 客户端 request() 镜像 src/api/http.js 的响应拦截拆包逻辑（code==='0' 返回 data，
 *     否则 reject 携带 code 的 Error），确保测试验证的就是前端真实依赖的契约；
 *   - 运行：node scripts/fe-connectivity.mjs（需项目已安装 axios，即 npm install 后）。
 */
import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import axios from 'axios'
import { URL } from 'node:url'

const FRAMEWORK = 'java-admin-framework'

// 镜像 src/api/http.js：code==='0' 返回 data；否则 reject(Error)，Error.code = body.code。
async function request(client, config) {
  const { method = 'GET', url, params, data, headers } = config
  try {
    const resp = await client.request({ method, url, params, data, headers })
    const body = resp.data
    if (body && body.code === '0') return body.data
    const msg = body?.message || '请求失败'
    const e = new Error(msg)
    e.code = body?.code
    throw e
  } catch (err) {
    if (err.response) {
      const msg = err.response?.data?.message || err.message || '网络错误'
      const e = new Error(msg)
      e.code = err.response?.data?.code
      e.response = err.response
      throw e
    }
    throw err
  }
}

let server
let baseURL

before(async () => {
  server = http.createServer((req, res) => {
    const u = new URL(req.url, 'http://localhost')
    const envelope = (status, code, message, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
      res.end(JSON.stringify({ code, message, data, traceId: '-', timestamp: new Date().toISOString() }))
    }
    if (u.pathname === '/api/connectivity/ping' && req.method === 'GET') {
      const probe = {
        serverTime: new Date().toISOString(),
        receivedLocale: req.headers['accept-language'] || 'zh-CN',
        nonce: 'n-' + Math.random().toString(36).slice(2),
        framework: FRAMEWORK
      }
      return envelope(200, '0', 'OK', probe)
    }
    if (u.pathname === '/api/connectivity/business-error' && req.method === 'GET') {
      return envelope(200, 'AUTH_001', '用户名或密码错误', null)
    }
    if (u.pathname === '/api/connectivity/http-error' && req.method === 'GET') {
      return envelope(500, 'COMMON_500', '系统内部错误', null)
    }
    if (u.pathname === '/api/connectivity/slow' && req.method === 'GET') {
      // 故意延迟远超客户端超时阈值，触发 axios ECONNABORTED
      const t = setTimeout(() => envelope(200, '0', 'OK', { ok: true }), 3000)
      res.on('close', () => clearTimeout(t))
      return
    }
    return envelope(404, 'COMMON_404', '资源不存在', null)
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address()
  baseURL = `http://127.0.0.1:${port}`
})

after(() => {
  server.close()
})

test('1) ping 正常流程：拆包后得到 data 且字段与后端契约一致', async () => {
  const client = axios.create({ baseURL, timeout: 15000 })
  const data = await request(client, { method: 'GET', url: '/api/connectivity/ping' })
  assert.equal(data.framework, FRAMEWORK, 'framework 标识应固定为约定值')
  assert.ok(typeof data.serverTime === 'string' && data.serverTime.length > 0, 'serverTime 应为非空字符串')
  assert.ok(typeof data.nonce === 'string' && data.nonce.length > 0, 'nonce 应为非空字符串')
  assert.ok(typeof data.receivedLocale === 'string' && data.receivedLocale.length > 0, 'receivedLocale 应非空')
})

test('2) 数据格式一致性：成功包络含 code/message/data/traceId/timestamp', async () => {
  const client = axios.create({ baseURL, timeout: 15000 })
  const resp = await client.request({ method: 'GET', url: '/api/connectivity/ping' })
  const body = resp.data
  assert.equal(body.code, '0')
  assert.equal(body.message, 'OK')
  assert.ok('data' in body && 'traceId' in body && 'timestamp' in body, '包络应包含 data/traceId/timestamp')
})

test('3a) 常见错误码：2xx 但 code!=0，前端应提取 code 并 reject', async () => {
  const client = axios.create({ baseURL, timeout: 15000 })
  await assert.rejects(
    async () => request(client, { method: 'GET', url: '/api/connectivity/business-error' }),
    (err) => err.code === 'AUTH_001' && err.message === '用户名或密码错误',
    '业务错误应携带 code=AUTH_001 且消息为后端文案'
  )
})

test('3b) 异常场景：5xx 返回后端错误码 COMMON_500', async () => {
  const client = axios.create({ baseURL, timeout: 15000 })
  await assert.rejects(
    async () => request(client, { method: 'GET', url: '/api/connectivity/http-error' }),
    (err) => err.code === 'COMMON_500' && err.response?.status === 500,
    'HTTP 错误应回传后端错误码 COMMON_500'
  )
})

test('4) 网络超时：后端慢响应触发 axios ECONNABORTED', async () => {
  const client = axios.create({ baseURL, timeout: 300 })
  await assert.rejects(
    async () => request(client, { method: 'GET', url: '/api/connectivity/slow' }),
    (err) => err.code === 'ECONNABORTED',
    '超时场景 axios 应抛出 ECONNABORTED'
  )
})
