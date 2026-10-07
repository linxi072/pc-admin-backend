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
    <div class="right">
      <el-badge :value="3" type="danger" class="icon-btn">
        <el-button text><el-icon size="18"><Bell /></el-icon></el-button>
      </el-badge>
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
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authState, clearAuth } from '@/store/auth'
import { logout } from '@/api/auth'

defineProps({ collapsed: Boolean })
defineEmits(['toggle'])

const route = useRoute()
const router = useRouter()
const username = computed(() => authState.username || 'admin')
const currentTitle = computed(() => route.meta.title || '')

async function onCmd(cmd) {
  if (cmd === 'logout') {
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
</script>

<style scoped>
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  color: #fff;
}
.left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hamburger {
  color: #c0c4cc;
}
.right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.icon-btn {
  display: flex;
  align-items: center;
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
</style>
