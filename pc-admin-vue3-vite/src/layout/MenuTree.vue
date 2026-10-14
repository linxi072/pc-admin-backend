<template>
  <!-- 有子节点：渲染为可折叠的分组（分类），index 用 menuCode 仅作唯一标识，不导航 -->
  <el-sub-menu v-if="item.children && item.children.length" :index="item.menuCode">
    <template #title>
      <el-icon v-if="safeIcon"><component :is="safeIcon" /></el-icon>
      <span>{{ item.menuName }}</span>
    </template>
    <!-- 递归渲染子节点，天然支持任意层级 -->
    <MenuTree v-for="child in item.children" :key="child.id ?? child.menuCode" :item="child" />
  </el-sub-menu>

  <!-- 叶子节点：index 用 routePath，配合 el-menu 的 router 模式完成导航 -->
  <el-menu-item v-else :index="item.routePath || item.menuCode">
    <el-icon v-if="safeIcon"><component :is="safeIcon" /></el-icon>
    <template #title>{{ item.menuName }}</template>
  </el-menu-item>
</template>

<script setup>
// 递归菜单组件：自身文件名即组件名（MenuTree），可在模板中自引用，支持任意深度菜单树。
import { computed } from 'vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const props = defineProps({
  item: { type: Object, required: true }
})

/**
 * 图标名合法性校验：仅当 icon 是 Element Plus 图标库真实导出的组件名时才渲染。
 *
 * <p>数据库中的 icon 为自由文本（历史上存在手输错名、迁移遗留空值等情况），
 * 直接 {@code <component :is>} 未注册名会触发 Vue 运行时告警且该图标静默丢失。
 * 此处做一次白名单收敛：非法/缺失一律不渲染图标（仅显示文字），保证侧边栏稳定。
 */
const safeIcon = computed(() => {
  const name = props.item.icon
  return name && Object.prototype.hasOwnProperty.call(ElementPlusIconsVue, name) ? name : null
})
</script>
