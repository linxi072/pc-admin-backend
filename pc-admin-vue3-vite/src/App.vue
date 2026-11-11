<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
// 根组件仅作为路由挂载点；登录页与后台布局由路由表与导航守卫分发。
// 外层 ElConfigProvider 绑定 Element Plus 简体中文语言包（前端语言已固定为 zh-CN，不再运行时切换）。
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

</script>
