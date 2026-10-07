<template>
  <el-menu
    :collapse="collapsed"
    :default-active="activeIndex"
    router
    background-color="#001529"
    text-color="#c0c4cc"
    active-text-color="#ffffff"
    class="app-menu"
  >
    <template v-for="item in menus" :key="item.index">
      <el-sub-menu v-if="item.children" :index="item.index">
        <template #title>
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </template>
        <el-menu-item v-for="c in item.children" :key="c.index" :index="c.index">
          <el-icon><component :is="c.icon" /></el-icon>
          <template #title>{{ c.title }}</template>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else :index="item.index">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineProps({ collapsed: Boolean })

const route = useRoute()
const activeIndex = computed(() => route.path)

const menus = [
  { index: '/dashboard', title: '工作台', icon: 'Odometer' },
  {
    index: 'g-system',
    title: '系统管理',
    icon: 'Setting',
    children: [
      { index: '/system/user', title: '用户管理', icon: 'User' },
      { index: '/system/role', title: '角色管理', icon: 'Avatar' },
      { index: '/system/menu', title: '菜单管理', icon: 'Menu' },
      { index: '/system/department', title: '部门管理', icon: 'OfficeBuilding' },
      { index: '/system/api-resource', title: '接口资源管理', icon: 'Connection' },
      { index: '/system/dict', title: '字典管理', icon: 'Notebook' },
      { index: '/system/config', title: '系统变量', icon: 'Coin' },
      { index: '/system/notice', title: '消息中心', icon: 'Bell' },
      { index: '/system/monitor', title: '系统监控', icon: 'Odometer' }
    ]
  },
  {
    index: 'g-workflow',
    title: '工作流',
    icon: 'Share',
    children: [
      { index: '/workflow/task', title: '我的待办', icon: 'Tickets' },
      { index: '/workflow/instance', title: '我发起的流程', icon: 'Document' }
    ]
  }
]
</script>

<style scoped>
.app-menu {
  border-right: none;
  width: 100%;
  height: 100%;
}
</style>
