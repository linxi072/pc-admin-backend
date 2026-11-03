<template>
  <el-container class="app-root">
    <el-header class="app-header" height="56px">
      <Topbar :collapsed="collapsed" @toggle="onToggle" />
    </el-header>
    <el-container class="app-body">
      <!-- 桌面端：常驻侧边栏（可折叠为图标栏） -->
      <el-aside v-if="!isMobile" :width="collapsed ? '64px' : '220px'" class="app-aside">
        <Sidebar :collapsed="collapsed" />
      </el-aside>
      <!-- 移动端：侧边栏改为抽屉式浮层，由顶栏汉堡按钮唤起，避免窄屏占用内容区 -->
      <el-drawer
        v-else
        :model-value="drawerOpen"
        :with-header="false"
        direction="ltr"
        size="220px"
        class="app-drawer"
        @update:model-value="drawerOpen = $event"
      >
        <Sidebar :collapsed="false" @navigate="drawerOpen = false" />
      </el-drawer>
      <el-main class="app-main" :class="{ 'is-mobile': isMobile }">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
    <el-footer v-if="!isMobile" class="app-footer" height="40px">
      {{ t('app.footer') }}
      <span class="muted">{{ t('app.internalUseOnly') }}</span>
    </el-footer>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Topbar from './Topbar.vue'
import Sidebar from './Sidebar.vue'
import { authState, isAuthenticated, loadMenus } from '@/store/auth'
import { t } from '@/i18n'
import { useBreakpoint } from '@/composables'

const { isMobile } = useBreakpoint()
const collapsed = ref(false)
const drawerOpen = ref(false)

// 顶栏汉堡：移动端唤起/收起抽屉；桌面端切换侧边栏折叠
function onToggle() {
  if (isMobile.value) {
    drawerOpen.value = !drawerOpen.value
  } else {
    collapsed.value = !collapsed.value
  }
}

// 登录后（含刷新直接进入）加载当前用户角色菜单树，驱动侧边栏动态渲染
onMounted(() => {
  if (isAuthenticated() && (!authState.menus || authState.menus.length === 0)) {
    loadMenus().catch(() => {})
  }
})
</script>

<style scoped>
.app-root {
  height: 100vh;
}
.app-header {
  padding: 0;
  background: var(--chrome);
}
.app-body {
  height: calc(100vh - 96px);
}
.app-aside {
  background: var(--chrome);
  transition: width 0.2s ease;
  overflow: hidden;
}
.app-main {
  background: var(--bg);
  padding: 16px;
  overflow: auto;
}
/* 移动端内容区占满宽度、收紧内边距，提升小屏可读性 */
.app-main.is-mobile {
  padding: 12px;
}
.app-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--footer-bg);
  color: var(--footer-text);
  font-size: 12px;
  border-top: 1px solid var(--footer-border);
}
.app-footer .muted {
  opacity: 0.7;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
