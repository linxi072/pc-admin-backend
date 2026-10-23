<template>
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
// 根组件仅作为路由挂载点；登录页与后台布局由路由表与导航守卫分发。
// 外层 ElConfigProvider 绑定 Element Plus 语言包，使分页 / 日期选择器等组件跟随前端语言切换。
import { ElConfigProvider } from 'element-plus'
import { elLocale } from '@/i18n'
</script>
