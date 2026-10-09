<template>
  <el-container class="app-root">
    <el-header class="app-header" height="56px">
      <Topbar :collapsed="collapsed" @toggle="collapsed = !collapsed" />
    </el-header>
    <el-container class="app-body">
      <el-aside :width="collapsed ? '64px' : '220px'" class="app-aside">
        <Sidebar :collapsed="collapsed" />
      </el-aside>
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
    <el-footer class="app-footer" height="40px">
      © 2026 运营管理后台 · 基于 Vue3 + Element Plus + Vite ·
      <span class="muted">仅供内部使用</span>
    </el-footer>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Topbar from './Topbar.vue'
import Sidebar from './Sidebar.vue'
import { authState, isAuthenticated, loadMenus } from '@/store/auth'

const collapsed = ref(false)

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
  background: #001529;
}
.app-body {
  height: calc(100vh - 96px);
}
.app-aside {
  background: #001529;
  transition: width 0.2s ease;
  overflow: hidden;
}
.app-main {
  background: var(--app-bg);
  padding: 16px;
  overflow: auto;
}
.app-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  color: #8a919f;
  font-size: 12px;
  border-top: 1px solid #ebeef5;
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
