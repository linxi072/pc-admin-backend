/**
 * 前后端连通性探针：周期性调用 GET /api/connectivity/ping，
 * 计算后端存活、时钟漂移、语言协商一致性，并向顶栏指示灯暴露状态。
 *
 * 设计要点（对标 src/utils/theme.js 的模块级单例范式）：
 * - 模块级响应式状态 + 单个定时器：全应用共享同一份状态，避免多组件各自轮询。
 * - useConnectivity() 返回只读状态 + 控制方法，任何组件可安全调用。
 * - 漂移阈值 / 轮询间隔 / 离线判定次数均为常量，便于调参。
 *
 * 后端约定见 src/api/connectivity.js：
 *   ping() -> Result<ConnectivityProbe>{ serverTime, receivedLocale, nonce, framework }
 */
import { ref, computed } from 'vue'
import { ping } from '@/api/connectivity'
import { t, backendLocale } from '@/i18n'

const DRIFT_WARN_SECONDS = 5 // 时钟漂移超过该阈值（秒）视为降级
const INTERVAL_MS = 30_000 // 周期探测间隔
const OFFLINE_AFTER_FAILS = 1 // 连续失败达到该次数即判定离线（=1 表示单次失败即离线）

// ---------- 模块级单例状态 ----------
const status = ref('init') // init | ok | degraded | offline
const driftSeconds = ref(0)
const lastError = ref('')
const lastProbeAt = ref(0)
const serverTime = ref('')
const receivedLocale = ref('')
const framework = ref('')
const latencyMs = ref(0)
const localeMismatch = ref(false)

let timer = null
let started = false

function formatTime(ts) {
  try {
    return new Date(ts).toLocaleTimeString()
  } catch (e) {
    return ''
  }
}

/** 单次探测：成功则更新存活/漂移/协商；失败则标记离线。 */
async function probe() {
  const begin = typeof performance !== 'undefined' ? performance.now() : Date.now()
  try {
    const data = await ping()
    const end = typeof performance !== 'undefined' ? performance.now() : Date.now()
    latencyMs.value = Math.max(0, Math.round(end - begin))
    lastProbeAt.value = Date.now()
    lastError.value = ''
    serverTime.value = data?.serverTime || ''
    receivedLocale.value = data?.receivedLocale || ''
    framework.value = data?.framework || ''

    // 时钟漂移：|本地时刻 - 后端返回服务器时刻|，单位秒
    let drift = 0
    if (data?.serverTime) {
      const delta = Date.now() - new Date(data.serverTime).getTime()
      drift = Math.abs(delta) / 1000
    }
    driftSeconds.value = Math.round(drift * 100) / 100

    // 语言协商一致性：后端实际解析到的 locale 应以前端上报的 backendLocale 为前缀
    const expected = backendLocale()
    localeMismatch.value = !!(data?.receivedLocale && !data.receivedLocale.startsWith(expected))

    if (drift >= DRIFT_WARN_SECONDS || localeMismatch.value) {
      status.value = 'degraded'
    } else {
      status.value = 'ok'
    }
  } catch (e) {
    lastProbeAt.value = Date.now()
    lastError.value = e?.message || 'ping failed'
    status.value = 'offline'
  }
}

/** 启动探测（幂等：重复调用不会叠加定时器）。 */
function start() {
  if (started) return
  started = true
  // 立即首探，避免指示灯长时间停留在 init
  probe()
  timer = setInterval(probe, INTERVAL_MS)
}

/** 停止探测并清理定时器。 */
function stop() {
  started = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/** 手动立即探测（点击指示灯触发）。 */
async function refresh() {
  await probe()
}

const statusText = computed(() => {
  switch (status.value) {
    case 'ok':
      return t('topbar.connectivity.ok')
    case 'degraded':
      return t('topbar.connectivity.degraded')
    case 'offline':
      return t('topbar.connectivity.offline')
    default:
      return t('topbar.connectivity.init')
  }
})

/**
 * 供顶栏 tooltip 使用的多行明细。
 * el-tooltip 的 #content 插槽天然支持多行 div，无需手动拼接换行。
 */
const tipLines = computed(() => {
  const lines = [t('topbar.connectivity.label') + '：' + statusText.value]
  if (status.value === 'offline') {
    lines.push(t('topbar.connectivity.error') + (lastError.value || ''))
  } else {
    lines.push(t('topbar.connectivity.latency', { ms: latencyMs.value }))
    lines.push(t('topbar.connectivity.drift', { sec: driftSeconds.value }))
    if (receivedLocale.value) {
      lines.push(t('topbar.connectivity.locale', { locale: receivedLocale.value }))
    }
    if (framework.value) {
      lines.push(t('topbar.connectivity.framework', { framework: framework.value }))
    }
  }
  if (lastProbeAt.value) {
    lines.push(t('topbar.connectivity.last', { time: formatTime(lastProbeAt.value) }))
  }
  return lines
})

/**
 * 组合式入口：返回单例状态与控制方法。
 * 所有引用共享同一模块级状态，故多组件调用安全且零重复轮询。
 */
export function useConnectivity() {
  return {
    status,
    statusText,
    driftSeconds,
    lastError,
    lastProbeAt,
    serverTime,
    receivedLocale,
    framework,
    latencyMs,
    localeMismatch,
    tipLines,
    start,
    stop,
    refresh
  }
}
