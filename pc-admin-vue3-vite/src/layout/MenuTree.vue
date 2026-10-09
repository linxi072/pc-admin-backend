<template>
  <!-- 有子节点：渲染为可折叠的分组（分类），index 用 menuCode 仅作唯一标识，不导航 -->
  <el-sub-menu v-if="item.children && item.children.length" :index="item.menuCode">
    <template #title>
      <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
      <span>{{ item.menuName }}</span>
    </template>
    <!-- 递归渲染子节点，天然支持任意层级 -->
    <MenuTree v-for="child in item.children" :key="child.id ?? child.menuCode" :item="child" />
  </el-sub-menu>

  <!-- 叶子节点：index 用 routePath，配合 el-menu 的 router 模式完成导航 -->
  <el-menu-item v-else :index="item.routePath || item.menuCode">
    <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
    <template #title>{{ item.menuName }}</template>
  </el-menu-item>
</template>

<script setup>
// 递归菜单组件：自身文件名即组件名（MenuTree），可在模板中自引用，支持任意深度菜单树。
defineProps({
  item: { type: Object, required: true }
})
</script>
