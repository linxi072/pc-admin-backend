<template>
  <div>
    <h2 class="page-title">消息中心</h2>

    <el-tabs v-model="activeTab" class="notice-tabs">
      <el-tab-pane name="announcement">
        <template #label>
          <span class="tab-label">
            <el-icon><Bell /></el-icon>
            系统公告
          </span>
        </template>
        <AnnouncementView embedded />
      </el-tab-pane>

      <el-tab-pane name="message">
        <template #label>
          <span class="tab-label">
            <el-icon><ChatDotRound /></el-icon>
            站内信
            <el-badge v-if="unread > 0" :value="unread" type="danger" class="tab-badge" />
          </span>
        </template>
        <MessageView embedded />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AnnouncementView from './AnnouncementView.vue'
import MessageView from './MessageView.vue'
import { getUnreadCount } from '@/api/message'

const route = useRoute()
const router = useRouter()

// 支持 /system/notice?tab=message 直达站内信（顶部铃铛点击也走这里）
const activeTab = ref(route.query.tab === 'message' ? 'message' : 'announcement')

// 反向同步：组件复用（如顶栏铃铛 push ?tab=message）时 setup 不会重跑，
// 仅靠初始值无法切换页签，故监听 query 回填 activeTab。
// 与下方 activeTab → query 的 watch 不会形成循环：两侧值一致时不再 replace。
watch(
  () => route.query.tab,
  (tab) => {
    const next = tab === 'message' ? 'message' : 'announcement'
    if (activeTab.value !== next) activeTab.value = next
  }
)

watch(activeTab, (val) => {
  if (route.query.tab !== val) {
    router.replace({ path: '/system/notice', query: { ...route.query, tab: val === 'announcement' ? undefined : val } })
  }
})

const unread = ref(0)
let timer = null

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res?.data?.unread ?? res?.unread ?? 0
  } catch (e) {
    // 顶栏轮询失败不打扰用户，仅保留上一次计数
  }
}

function onFocus() {
  loadUnread()
}

onMounted(() => {
  loadUnread()
  // 与顶栏铃铛共享同一份未读数：回到页面时刷新，并每 60 秒兜底轮询
  window.addEventListener('focus', onFocus)
  timer = setInterval(loadUnread, 60000)
})

onUnmounted(() => {
  window.removeEventListener('focus', onFocus)
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.notice-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tab-badge {
  margin-left: 2px;
}
</style>