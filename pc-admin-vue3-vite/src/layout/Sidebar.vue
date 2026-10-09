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
    <!-- 菜单完全由后端「我的菜单」接口按当前用户角色动态返回，无前端硬编码 -->
    <MenuTree v-for="item in menus" :key="item.id ?? item.menuCode" :item="item" />
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { authState } from '@/store/auth'
import MenuTree from './MenuTree.vue'

defineProps({ collapsed: Boolean })

const route = useRoute()
const activeIndex = computed(() => route.path)
const menus = computed(() => authState.menus || [])
</script>

<style scoped>
.app-menu {
  border-right: none;
  width: 100%;
  height: 100%;
}
</style>
