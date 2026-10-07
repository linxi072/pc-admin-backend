<template>
  <div class="topbar">
    <div class="left">
      <el-button text class="hamburger" @click="$emit('toggle')">
        <el-icon size="20"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
      </el-button>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 公告栏：仅展示一条生效公告并循环滚动；无公告时整栏不渲染，不占位 -->
    <div
      v-if="notice"
      class="notice-bar"
      @mouseenter="paused = true"
      @mouseleave="paused = false"
    >
      <el-icon class="notice-icon"><Bell /></el-icon>
      <el-tag v-if="notice.isTop" size="small" type="danger" effect="dark" class="top-tag">置顶</el-tag>
      <div class="notice-viewport">
        <div class="notice-track" :style="trackStyle">
          <!-- 仅在内容超出可视区时才滚动，否则静态展示，避免无意义位移 -->
          <span class="notice-item" @click="openAnnouncement">
            {{ notice.title }}
          </span>
        </div>
      </div>
      <el-button text class="notice-more" @click="goNotice('announcement')">详情</el-button>
    </div>
    <div v-else class="notice-placeholder" aria-hidden="true" />

    <div class="right">
      <el-tooltip content="站内信" placement="bottom">
        <el-badge :value="unread" :hidden="unread === 0" type="danger" class="icon-btn">
          <el-button text class="bell" @click="goNotice('message')">
            <el-icon size="18"><Bell /></el-icon>
          </el-button>
        </el-badge>
      </el-tooltip>
      <el-button text class="icon-btn"><el-icon size="18"><Setting /></el-icon></el-button>
      <el-dropdown @command="onCmd">
        <span class="user">
          <el-avatar :size="28" class="avatar">U</el-avatar>
          <span class="uname">{{ username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item command="settings">账号设置</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 点击公告查看详情 -->
    <el-dialog v-model="detailVisible" title="公告详情" width="680px">
      <h3 class="detail-title">{{ current?.title }}</h3>
      <div class="detail-meta">
        <span class="meta-item">生效：{{ current?.publishAt || '立即' }}</span>
        <span class="meta-item">有效期至：{{ current?.expireAt || '长期有效' }}</span>
        <span class="meta-item">浏览：{{ current?.viewCount ?? 0 }}</span>
      </div>
      <div class="detail-content">{{ current?.content }}</div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="goNotice('announcement')">查看全部公告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authState, clearAuth } from '@/store/auth'
import { logout } from '@/api/auth'
import { pageAnnouncements, getAnnouncement } from '@/api/announcement'
import { getUnreadCount } from '@/api/message'

defineProps({ collapsed: Boolean })
defineEmits(['toggle'])

const route = useRoute()
const router = useRouter()
const username = computed(() => authState.username || 'admin')
const currentTitle = computed(() => route.meta.title || '')

const notice = ref(null)
const unread = ref(0)
const paused = ref(false)
const detailVisible = ref(false)
const current = ref(null)
const overflowing = ref(false)
let timer = null
let offset = 0
let raf = null

const trackStyle = computed(() => ({
  // 仅在溢出且未暂停时位移；否则回到起点
  transform: overflowing.value && !paused.value ? `translateX(${-offset.value}px)` : 'translateX(0)'
}))

/** 单条公告自身循环滚动：先滑出再滑入，超出部分由测量宽度决定 */
function step() {
  const viewport = document.querySelector('.notice-viewport')
  const track = viewport?.querySelector('.notice-track')
  if (viewport && track) {
    const distance = track.scrollWidth - viewport.clientWidth
    if (distance > 2) {
      if (!overflowing.value) overflowing.value = true
      if (!paused.value) {
        // 右侧保留间隙，滚出后停顿再从头开始，形成循环
        const cycle = distance + 40
        offset.value = (offset.value + 0.5) % cycle
      }
    } else {
      overflowing.value = false
      offset.value = 0
    }
  }
  raf = requestAnimationFrame(step)
}

async function loadNotice() {
  try {
    const res = await pageAnnouncements({ page: 1, size: 1, onlyValid: true })
    const rows = res?.data?.records ?? res?.records ?? []
    // 仅展示一条：取第一条生效公告（后端已按置顶 + 发布时间排序）
    notice.value = rows.length ? rows[0] : null
  } catch (e) {
    // 公告加载失败不阻塞顶栏，静默降级为不展示
    notice.value = null
  }
}

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res?.data?.unread ?? res?.unread ?? 0
  } catch (e) {
    // 轮询失败静默处理，保留上一次计数
  }
}

async function openAnnouncement() {
  if (!notice.value) return
  try {
    const res = await getAnnouncement(notice.value.id)
    current.value = res?.data ?? res
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('公告详情加载失败')
  }
}

function goNotice(tab) {
  router.push({ path: '/system/notice', query: tab === 'message' ? { tab: 'message' } : {} })
}

async function onCmd(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'settings') {
    // 账号设置与个人中心同页，靠 hash 定位到对应 Tab
    router.push('/profile#security')
  } else if (cmd === 'logout') {
    // 二次确认：避免误触直接退出
    try {
      await ElMessageBox.confirm('退出后需要重新登录才能继续使用，确认退出吗？', '退出登录', {
        confirmButtonText: '确认退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return // 用户取消
    }
    try {
      await logout()
    } catch (e) {
      // mock 直接返回；真实后端失败时仍清理本地会话
    }
    clearAuth()
    ElMessage.success('已退出登录')
    router.replace('/login')
  } else {
    ElMessage.info('演示环境暂未实现该页面')
  }
}

onMounted(async () => {
  await loadNotice()
  loadUnread()
  raf = requestAnimationFrame(step)
  // 未读数每 60 秒轮询一次
  timer = setInterval(loadUnread, 60000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (raf) cancelAnimationFrame(raf)
})
</script>

<style scoped>
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  color: #fff;
  gap: 12px;
}
.left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.hamburger {
  color: #c0c4cc;
}
.notice-bar {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  border-radius: 17px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}
/* 无公告时保留与公告栏相同的伸缩占位，避免面包屑与右侧区域左右跳动 */
.notice-placeholder {
  flex: 1;
  min-width: 0;
}
.notice-icon {
  color: #ffd04b;
  flex-shrink: 0;
}
.notice-viewport {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  height: 34px;
  display: flex;
  align-items: center;
  mask-image: linear-gradient(to right, transparent, #000 6%, #000 94%, transparent);
}
.notice-track {
  display: flex;
  align-items: center;
  white-space: nowrap;
  will-change: transform;
}
.notice-item {
  font-size: 13px;
  color: #e6e8eb;
  cursor: pointer;
  display: inline-block;
  max-width: 100%;
  /* 未溢出时超长标题省略，溢出滚动时由容器裁切 */
  overflow: hidden;
  text-overflow: ellipsis;
}
.notice-item:hover {
  color: #ffd04b;
}
.top-tag {
  transform: scale(0.85);
}
.notice-more {
  color: #c0c4cc;
  flex-shrink: 0;
  font-size: 12px;
}
.right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.icon-btn {
  display: flex;
  align-items: center;
}
.bell {
  color: #ffd04b;
}
:deep(.icon-btn .el-button),
:deep(.hamburger) {
  color: #c0c4cc;
}
.user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #fff;
  outline: none;
}
.avatar {
  background: var(--brand);
}
.detail-title {
  margin: 0 0 8px;
  font-size: 18px;
}
.detail-meta {
  display: flex;
  gap: 16px;
  color: #8a919f;
  font-size: 12px;
  margin-bottom: 12px;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #303133;
  max-height: 420px;
  overflow: auto;
}

/* ========== 响应式：移动端与桌面端适配 ========== */
/* 平板及以下：压缩间距、隐藏用户名文字，仅留头像 */
@media (max-width: 1024px) {
  .uname {
    display: none;
  }
}
/* 手机：隐藏详情按钮与面包屑第二级，公告栏独占一行且高度略降 */
@media (max-width: 768px) {
  .topbar {
    gap: 8px;
    padding: 0 10px;
  }
  .left {
    gap: 6px;
  }
  .notice-more {
    display: none;
  }
  .notice-bar {
    height: 30px;
    padding: 0 8px;
    gap: 6px;
  }
  .notice-viewport {
    height: 30px;
  }
  .notice-item {
    font-size: 12px;
  }
  .right {
    gap: 2px;
  }
}
/* 超窄屏：隐藏设置按钮，优先保证公告与铃铛可用 */
@media (max-width: 480px) {
  .notice-icon {
    display: none;
  }
  .top-tag {
    display: none;
  }
}
</style>