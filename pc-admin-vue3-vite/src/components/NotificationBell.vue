<template>
  <el-tooltip content="通知中心" placement="bottom">
    <el-badge :value="total" :max="99" :hidden="total === 0" type="danger" class="icon-btn">
      <el-button text class="bell" @click="open">
        <el-icon size="18"><Bell /></el-icon>
      </el-button>
    </el-badge>
  </el-tooltip>

  <el-drawer
    v-model="visible"
    title="通知中心"
    direction="rtl"
    size="360px"
    :with-header="true"
  >
    <template #header>
      <div class="drawer-header">
        <span class="drawer-title">通知中心</span>
        <el-button
          link
          type="primary"
          :disabled="total === 0"
          :loading="marking"
          @click="handleMarkAllRead"
        >
          全部已读
        </el-button>
      </div>
    </template>

    <div class="bell-body">
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty
        v-else-if="items.length === 0"
        description="暂无未读通知"
        :image-size="80"
      />
      <ul v-else class="bell-list">
        <li
          v-for="it in items"
          :key="it.source + '-' + it.id"
          class="bell-item"
          @click="openItem(it)"
        >
          <el-icon
            class="bell-item-icon"
            :class="it.source === 'ANNOUNCEMENT' ? 'is-ann' : 'is-msg'"
          >
            <component :is="it.source === 'ANNOUNCEMENT' ? 'Bell' : 'ChatDotRound'" />
          </el-icon>
          <div class="bell-item-main">
            <div class="bell-item-title">
              <el-tag
                size="small"
                effect="plain"
                :type="it.source === 'ANNOUNCEMENT' ? 'warning' : 'info'"
              >
                {{ it.source === 'ANNOUNCEMENT' ? '公告' : '站内信' }}
              </el-tag>
              <span class="bell-item-text">{{ it.title }}</span>
            </div>
            <div class="bell-item-summary">{{ it.summary }}</div>
            <div class="bell-item-time">{{ formatTime(it.occurredAt) }}</div>
          </div>
        </li>
      </ul>
    </div>

    <template #footer>
      <el-button text type="primary" @click="goAll">查看全部</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { getSummary, getRecent, markAllRead } from '@/api/notificationCenter'
import { initRealtime, onRealtime } from '@/api/realtime'

const router = useRouter()
const total = ref(0)
const items = ref([])
const visible = ref(false)
const loading = ref(false)
const marking = ref(false)

let pollTimer = null
let offRealtime = null

/** 未读汇总：铃铛角标权威来源。`@/api/http` 已拆包，直接拿到 data。 */
async function loadSummary() {
  try {
    const data = await getSummary()
    // 防御：后端契约为 {announcementUnread, messageUnread, totalUnread}，任一缺失时本地兜底求和
    if (data && typeof data.totalUnread === 'number') {
      total.value = data.totalUnread
    } else if (data) {
      total.value = (data.announcementUnread || 0) + (data.messageUnread || 0)
    }
  } catch (e) {
    // 轮询/订阅刷新失败静默降级，保留上一次计数，不因瞬时抖动清空角标
  }
}

/** 抽屉打开时加载近期未读条目。 */
async function loadRecent() {
  loading.value = true
  try {
    const data = await getRecent()
    items.value = Array.isArray(data) ? data : []
  } catch (e) {
    items.value = []
  } finally {
    loading.value = false
  }
}

function open() {
  visible.value = true
  loadRecent()
  loadSummary()
}

/** link 形如 /system/notice?tab=announcement&id=123，解析为路由跳转，复用既有消息中心页面。 */
function openItem(it) {
  visible.value = false
  const raw = it?.link || '/system/notice'
  const [path, queryStr] = raw.split('?')
  const query = {}
  if (queryStr) {
    queryStr.split('&').forEach((kv) => {
      const idx = kv.indexOf('=')
      if (idx < 0) return
      const k = kv.slice(0, idx)
      const v = kv.slice(idx + 1)
      if (k) query[k] = decodeURIComponent(v)
    })
  }
  router.push({ path: path || '/system/notice', query })
}

function goAll() {
  visible.value = false
  router.push({ path: '/system/notice' })
}

async function handleMarkAllRead() {
  marking.value = true
  try {
    const res = await markAllRead()
    const marked = res?.marked ?? 0
    ElMessage.success(marked > 0 ? `已将 ${marked} 条通知标记为已读` : '当前没有未读通知')
    total.value = 0
    items.value = []
    await loadSummary()
  } catch (e) {
    // 错误已在 http 层统一提示
  } finally {
    marking.value = false
  }
}

/** 相对时间格式化：刚刚 / N 分钟前 / N 小时前 / 具体日期；occurredAt 可能为空。 */
function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 实时消息处理：未读/待办事件触发角标刷新，抽屉打开时同步刷新列表，并轻量 Toast 提示。 */
function handleRealtime(msg) {
  if (!msg || (msg.type !== 'UNREAD_MESSAGE' && msg.type !== 'TODO_REMINDER')) return
  loadSummary()
  if (visible.value) loadRecent()
  if (msg.type === 'UNREAD_MESSAGE') {
    const p = msg.payload || {}
    ElNotification({
      title: '新消息',
      message: p.title || '您有一条新消息',
      type: 'info',
      duration: 3000
    })
  }
}

onMounted(() => {
  loadSummary()
  // 实时通道：单例，initRealtime 幂等（Topbar 亦可能调用，started 标志防重复连接）
  initRealtime()
  offRealtime = onRealtime(handleRealtime)
  // 30s 轮询兜底：WebSocket 不可用时仍保证角标与后端一致
  pollTimer = setInterval(loadSummary, 30000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (offRealtime) offRealtime()
})

// 暴露刷新能力，便于父组件（Topbar）在登出等场景主动复位
defineExpose({ refresh: loadSummary })
</script>

<style scoped>
.bell {
  color: #ffd04b;
}
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.drawer-title {
  font-weight: 600;
}
.bell-body {
  height: 100%;
}
.bell-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.bell-item {
  display: flex;
  gap: 10px;
  padding: 12px 8px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.18s;
}
.bell-item:hover {
  background: #f7f9fc;
}
.bell-item-icon {
  flex-shrink: 0;
  font-size: 18px;
  margin-top: 2px;
}
.bell-item-icon.is-ann {
  color: #e6a23c;
}
.bell-item-icon.is-msg {
  color: #409eff;
}
.bell-item-main {
  min-width: 0;
  flex: 1;
}
.bell-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.bell-item-text {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bell-item-summary {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.bell-item-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}
</style>
