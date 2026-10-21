/**
 * 可观测中心前端契约测试（Node 侧，不依赖浏览器/Vitest，离线可跑）。
 *
 * 覆盖对象：src/api/ 下 5 个运维面 API 模块（observability / kafkaMonitor / nplusOne /
 * idempotencyRecord / alertInbox）与 src/views/monitor/components/format.js 纯函数。
 * 验证三类场景：
 *   1) 正常流程：method/url/params 与后端 Controller 契约一致，Result<T> 拆包后拿到 data；
 *   2) 数据格式一致性：拆包契约（code==='0' → data）由真实 src/api/http.js 拦截器执行；
 *   3) 异常：业务错误（2xx 但 code!=0）reject 携带 code；403 鉴权拒绝 reject 携带
 *      response.status===403（页面据此走 Tab 级降级空态）。
 *
 * 实现说明：
 *   - element-plus 的完整依赖图在 Node 下无法直接 import（沙箱实测被 kill），
 *     故用 module.registerHooks 将其替换为无操作虚拟模块（ElMessage/ElMessageBox 打桩）；
 *   - '@/...' 别名与扩展名省略的相对导入同样由 resolve 钩子解析（镜像 vite 行为）；
 *   - localStorage 打桩（store/auth 模块初始化时读取）；
 *   - axios.defaults.adapter 安装「可编排适配器」：不发起真实网络，按路由表返回
 *     脚本化响应，从而在 Node 内端到端驱动**生产代码**的请求构造与拆包逻辑——
 *     与 fe-connectivity.mjs（真实 HTTP 传输层契约）互补，本文件聚焦 API 形状契约。
 *
 * 运行：node scripts/fe-observability.mjs（需已 npm install；Node >= 22.15 以支持 registerHooks）。
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { registerHooks } from 'node:module'
import axios, { AxiosError } from 'axios'

// ---------- 模块加载环境（须在 import 生产 API 模块前完成） ----------

registerHooks({
  resolve(spec, ctx, next) {
    // element-plus → 虚拟无操作模块（生产代码只用 ElMessage/ElMessageBox 两支）
    if (spec === 'element-plus') {
      return { url: 'virtual:element-plus', shortCircuit: true, format: 'module' }
    }
    // '@/x/y' → src/x/y.js（镜像 vite alias）
    if (spec.startsWith('@/')) {
      return next(new URL('../src/' + spec.slice(2) + '.js', import.meta.url).href, ctx)
    }
    // 扩展名省略的相对导入（'./config' → './config.js'）：先按原样解析，失败再补 .js
    if (spec.startsWith('.') && !/\.[a-z]+$/i.test(spec)) {
      try {
        return next(spec, ctx)
      } catch (e) {
        if (e && e.code === 'ERR_MODULE_NOT_FOUND') return next(spec + '.js', ctx)
        throw e
      }
    }
    return next(spec, ctx)
  },
  load(url, ctx, next) {
    if (url === 'virtual:element-plus') {
      return {
        format: 'module',
        shortCircuit: true,
        source:
          'export const ElMessage = { error() {}, success() {}, warning() {}, info() {} }\n' +
          'export const ElMessageBox = { confirm() {}, alert() {} }\n' +
          'export default {}'
      }
    }
    return next(url, ctx)
  }
})

// store/auth 模块初始化即读 localStorage（Node 无此全局）
globalThis.localStorage = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {}
}

// ---------- 可编排适配器（替代真实网络，驱动生产 http.js 拦截器链） ----------

/** 记录每次请求的形状，供断言。 */
const calls = []
/** 路由表：{ method, test(url), respond(config) -> {status, body} }。 */
let routes = []

axios.defaults.adapter = async (config) => {
  calls.push({
    method: String(config.method).toUpperCase(),
    url: config.url,
    params: config.params,
    data: config.data
  })
  const route = routes.find((r) => r.method === String(config.method).toUpperCase() && r.test.test(config.url))
  const { status = 200, body } = route
    ? route.respond(config)
    : { status: 404, body: { code: 'COMMON_404', message: '资源不存在', data: null } }
  const response = {
    data: body,
    status,
    statusText: status === 200 ? 'OK' : 'ERROR',
    headers: {},
    config,
    request: {}
  }
  // 新版 axios 的 settle（validateStatus 校验）由内置适配器负责；自定义适配器必须自行
  // 对非 2xx 抛 AxiosError（附 response），否则会退化为「成功响应」走拆包拦截器，
  // 页面端就永远拿不到 response.status 做 403 降级判断。
  if (status < 200 || status >= 300) {
    throw new AxiosError(
      'Request failed with status code ' + status,
      AxiosError.ERR_BAD_REQUEST,
      config,
      response.request,
      response
    )
  }
  return response
}

/** 注册一条脚本化路由；返回匹配函数供断言。 */
function stub(method, pattern, body, status = 200) {
  routes.push({ method, test: pattern, respond: () => ({ status, body }) })
}

/** 成功包络（与后端 Result.success 形态一致）。 */
const ok = (data) => ({ code: '0', message: 'OK', data, traceId: '-', timestamp: '2026-10-20T12:00:00' })
/** 业务错误包络（2xx 但 code!=0）。 */
const bizErr = (code, message) => ({ code, message, data: null, traceId: '-', timestamp: '2026-10-20T12:00:00' })

/** 每个用例前重置路由表与调用记录（node:test 同文件内顺序执行）。 */
function reset() {
  routes = []
  calls.length = 0
}

// ---------- 导入被测生产模块（适配器已就位，http.js 的实例将继承） ----------

const observability = await import('../src/api/observability.js')
const kafkaMonitor = await import('../src/api/kafkaMonitor.js')
const nplusOne = await import('../src/api/nplusOne.js')
const idempotencyRecord = await import('../src/api/idempotencyRecord.js')
const alertInbox = await import('../src/api/alertInbox.js')
const fmt = await import('../src/views/monitor/components/format.js')

// =========================== 链路追踪（observability） ===========================

test('OBS-01 listSpans 默认参数：GET /api/observability/traces，limit 透传，拆包得数组', async () => {
  reset()
  const spans = [
    { traceId: 't1', spanId: 's1', name: 'GET /api/users', status: 'OK', durationMillis: 12, startEpochMillis: 1 }
  ]
  stub('GET', /^\/api\/observability\/traces$/, ok(spans))

  const data = await observability.listSpans({ limit: 50 })

  assert.equal(calls.length, 1)
  assert.equal(calls[0].method, 'GET')
  assert.equal(calls[0].url, '/api/observability/traces')
  assert.equal(calls[0].params.limit, 50)
  assert.deepEqual(data, spans, '拆包后应直接得到 data（数组）')
})

test('OBS-02 listSpans 筛选参数：status/traceId 原样透传', async () => {
  reset()
  stub('GET', /^\/api\/observability\/traces$/, ok([]))

  await observability.listSpans({ status: 'ERROR', traceId: 'abc123', limit: 10 })

  assert.equal(calls[0].params.status, 'ERROR')
  assert.equal(calls[0].params.traceId, 'abc123')
  assert.equal(calls[0].params.limit, 10)
})

test('OBS-03 getTrace：traceId 经 encodeURIComponent 进入路径', async () => {
  reset()
  stub('GET', /^\/api\/observability\/traces\/.+$/, ok([]))

  await observability.getTrace('trace 1/2')

  assert.equal(calls[0].url, '/api/observability/traces/trace%201%2F2')
})

test('OBS-04 clearSpans：DELETE /api/observability/traces，返回清理条数', async () => {
  reset()
  stub('DELETE', /^\/api\/observability\/traces$/, ok(7))

  const cleared = await observability.clearSpans()

  assert.equal(calls[0].method, 'DELETE')
  assert.equal(calls[0].url, '/api/observability/traces')
  assert.equal(cleared, 7)
})

test('OBS-05 业务错误（2xx 但 code!=0）：reject 且携带 code', async () => {
  reset()
  stub('GET', /^\/api\/observability\/traces$/, bizErr('AUTH_001', '用户名或密码错误'))

  await assert.rejects(
    () => observability.listSpans({ limit: 50 }),
    (err) => err.code === 'AUTH_001' && err.message === '用户名或密码错误',
    '业务错误应携带 code 与后端文案'
  )
})

// =========================== Kafka 监控（kafkaMonitor） ===========================

test('KFK-01 getConsumerStatus：GET /api/kafka/consumer/status', async () => {
  reset()
  const status = { enabled: true, running: true, handledCount: 100, poisonPending: 2 }
  stub('GET', /^\/api\/kafka\/consumer\/status$/, ok(status))

  const data = await kafkaMonitor.getConsumerStatus()

  assert.equal(calls[0].url, '/api/kafka/consumer/status')
  assert.deepEqual(data, status)
})

test('KFK-02 pagePoison：分页与 key 筛选参数透传，PageResult 拆包', async () => {
  reset()
  const page = { page: 2, size: 20, total: 35, records: [{ id: 'p1', topic: 'orders' }] }
  stub('GET', /^\/api\/kafka\/consumer\/poison$/, ok(page))

  const data = await kafkaMonitor.pagePoison({ page: 2, size: 20, key: 'order-1' })

  assert.equal(calls[0].url, '/api/kafka/consumer/poison')
  assert.equal(calls[0].params.page, 2)
  assert.equal(calls[0].params.size, 20)
  assert.equal(calls[0].params.key, 'order-1')
  assert.equal(data.total, 35)
  assert.equal(data.records.length, 1)
})

test('KFK-03 retryPoison：POST .../poison/{id}/retry，id 编码', async () => {
  reset()
  stub('POST', /^\/api\/kafka\/consumer\/poison\/.+\/retry$/, ok(true))

  const data = await kafkaMonitor.retryPoison('msg/1')

  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].url, '/api/kafka/consumer/poison/msg%2F1/retry')
  assert.equal(data, true)
})

test('KFK-04 discardPoison：POST .../poison/{id}/discard', async () => {
  reset()
  stub('POST', /^\/api\/kafka\/consumer\/poison\/.+\/discard$/, ok(true))

  await kafkaMonitor.discardPoison('p1')

  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].url, '/api/kafka/consumer/poison/p1/discard')
})

// =========================== N+1 疑似（nplusOne） ===========================

test('NP1-01 listSuspects：limit 透传', async () => {
  reset()
  stub('GET', /^\/api\/system\/monitor\/n-plus-one\/suspects$/, ok([{ fingerprint: 'f1', count: 12 }]))

  const data = await nplusOne.listSuspects({ limit: 50 })

  assert.equal(calls[0].params.limit, 50)
  assert.equal(data[0].count, 12)
})

test('NP1-02 getNPlusOneStatus：GET /status', async () => {
  reset()
  const status = { threshold: 10, capacity: 200, registrySize: 3, totalBreaches: 9, lastBreachAtEpochMillis: 0 }
  stub('GET', /^\/api\/system\/monitor\/n-plus-one\/status$/, ok(status))

  const data = await nplusOne.getNPlusOneStatus()

  assert.equal(calls[0].url, '/api/system/monitor/n-plus-one/status')
  assert.equal(data.threshold, 10)
  assert.equal(data.totalBreaches, 9)
})

test('NP1-03 clearSuspects：DELETE /suspects', async () => {
  reset()
  stub('DELETE', /^\/api\/system\/monitor\/n-plus-one\/suspects$/, ok(true))

  const data = await nplusOne.clearSuspects()

  assert.equal(calls[0].method, 'DELETE')
  assert.equal(calls[0].url, '/api/system/monitor/n-plus-one/suspects')
  assert.equal(data, true)
})

// =========================== 幂等记录（idempotencyRecord） ===========================

test('IDM-01 pageIdempotencyRecords：分页参数透传，空值保持 undefined（不传空串）', async () => {
  reset()
  const page = { page: 1, size: 10, total: 2, records: [{ id: 1, idempotencyKey: 'k1', status: 'PROCESSING' }] }
  stub('GET', /^\/api\/system\/idempotency-records\/page$/, ok(page))

  const data = await idempotencyRecord.pageIdempotencyRecords({
    page: 1,
    size: 10,
    status: undefined,
    apiScope: undefined,
    userId: undefined,
    keyword: undefined
  })

  assert.equal(calls[0].url, '/api/system/idempotency-records/page')
  assert.equal(calls[0].params.page, 1)
  assert.equal(calls[0].params.size, 10)
  assert.equal(calls[0].params.status, undefined, '空值应保持 undefined（与 buildParams 口径一致）')
  assert.equal(data.records[0].status, 'PROCESSING')
})

test('IDM-02 pageIdempotencyRecords：时间范围参数透传', async () => {
  reset()
  stub('GET', /^\/api\/system\/idempotency-records\/page$/, ok({ page: 1, size: 10, total: 0, records: [] }))

  await idempotencyRecord.pageIdempotencyRecords({
    page: 1,
    size: 10,
    startTime: '2026-10-01T00:00:00',
    endTime: '2026-10-20T23:59:59'
  })

  assert.equal(calls[0].params.startTime, '2026-10-01T00:00:00')
  assert.equal(calls[0].params.endTime, '2026-10-20T23:59:59')
})

test('IDM-03 getIdempotencySummary：同筛选口径打到 summary 端点', async () => {
  reset()
  const summary = { total: 10, processing: 1, success: 8, failed: 1, expiredPending: 2, processingShare: 0.1 }
  stub('GET', /^\/api\/system\/idempotency-records\/summary$/, ok(summary))

  const data = await idempotencyRecord.getIdempotencySummary({ status: 'FAILED' })

  assert.equal(calls[0].url, '/api/system/idempotency-records/summary')
  assert.equal(calls[0].params.status, 'FAILED')
  assert.equal(data.expiredPending, 2)
})

test('IDM-04 purgeIdempotencyRecords：POST /purge，返回删除条数', async () => {
  reset()
  stub('POST', /^\/api\/system\/idempotency-records\/purge$/, ok(5))

  const removed = await idempotencyRecord.purgeIdempotencyRecords()

  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].url, '/api/system/idempotency-records/purge')
  assert.equal(removed, 5)
})

// =========================== 告警收件箱（alertInbox） ===========================

test('ALR-01 listAlerts：severity/status/limit 透传', async () => {
  reset()
  stub('GET', /^\/api\/monitoring\/alerts\/inbox$/, ok([{ id: 'a1', status: 'firing', severity: 'critical' }]))

  const data = await alertInbox.listAlerts({ severity: 'critical', status: 'firing', limit: 100 })

  assert.equal(calls[0].url, '/api/monitoring/alerts/inbox')
  assert.equal(calls[0].params.severity, 'critical')
  assert.equal(calls[0].params.status, 'firing')
  assert.equal(calls[0].params.limit, 100)
  assert.equal(data[0].severity, 'critical')
})

test('ALR-02 getAlertStats：GET /inbox/stats（注意不与列表路由混淆）', async () => {
  reset()
  const stats = { capacity: 200, total: 3, firingCount: 1, resolvedCount: 2, severityCounts: { critical: 1 } }
  stub('GET', /^\/api\/monitoring\/alerts\/inbox\/stats$/, ok(stats))

  const data = await alertInbox.getAlertStats()

  assert.equal(calls[0].url, '/api/monitoring/alerts/inbox/stats')
  assert.equal(data.firingCount, 1)
})

test('ALR-03 clearAlerts：DELETE /api/monitoring/alerts/inbox，返回清理条数', async () => {
  reset()
  stub('DELETE', /^\/api\/monitoring\/alerts\/inbox$/, ok(3))

  const cleared = await alertInbox.clearAlerts()

  assert.equal(calls[0].method, 'DELETE')
  assert.equal(calls[0].url, '/api/monitoring/alerts/inbox')
  assert.equal(cleared, 3)
})

// =========================== 跨模块：鉴权拒绝降级 ===========================

test('XUT-01 403 鉴权拒绝：reject 携带 response.status===403（页面据此走降级空态）', async () => {
  reset()
  stub('GET', /^\/api\/observability\/traces$/, bizErr('COMMON_003', '无访问权限'), 403)

  await assert.rejects(
    () => observability.listSpans({ limit: 50 }),
    (err) => err.response?.status === 403 && fmt.isForbidden(err) === true,
    '403 应可被 isForbidden 识别'
  )
})

// =========================== format.js 纯函数 ===========================

test('FMT-01 fmtEpoch：0/负数/null 为无值，合法毫秒本地时区格式化', () => {
  assert.equal(fmt.fmtEpoch(0), '--')
  assert.equal(fmt.fmtEpoch(-1), '--')
  assert.equal(fmt.fmtEpoch(null), '--')
  // 2026-10-20T12:00:00+08:00 → 本地时区展示（测试机为 GMT+8 时固定）
  assert.equal(fmt.fmtEpoch(Date.parse('2026-10-20T12:00:00+08:00')), '2026-10-20 12:00:00')
})

test('FMT-02 fmtDateTime：ISO 串/空格串/数组/null 四种形态', () => {
  assert.equal(fmt.fmtDateTime('2026-10-20T14:30:00'), '2026-10-20 14:30:00')
  assert.equal(fmt.fmtDateTime('2026-10-20 14:30:00'), '2026-10-20 14:30:00')
  assert.equal(fmt.fmtDateTime('2026-10-20T14:30:00.123'), '2026-10-20 14:30:00')
  assert.equal(fmt.fmtDateTime([2026, 10, 20, 14, 30, 0]), '2026-10-20 14:30:00')
  assert.equal(fmt.fmtDateTime(null), '--')
  assert.equal(fmt.fmtDateTime(''), '--')
})

test('FMT-03 fmtDuration：毫秒/秒折算', () => {
  assert.equal(fmt.fmtDuration(12), '12 ms')
  assert.equal(fmt.fmtDuration(1500), '1.50 s')
  assert.equal(fmt.fmtDuration(null), '--')
})

test('FMT-04 fmtShare：占比百分比与空值', () => {
  assert.equal(fmt.fmtShare(0.1234), '12.34%')
  assert.equal(fmt.fmtShare(null), '--')
})

test('FMT-05 isForbidden：仅 403 为真', () => {
  assert.equal(fmt.isForbidden({ response: { status: 403 } }), true)
  assert.equal(fmt.isForbidden({ response: { status: 500 } }), false)
  assert.equal(fmt.isForbidden({}), false)
  assert.equal(fmt.isForbidden(undefined), false)
})
