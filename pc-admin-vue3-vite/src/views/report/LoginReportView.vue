<template>
  <ReportPanel type="LOGIN" :title="t('route.reportLogin')" />
</template>

<script setup>
// 登录统计（原「报表管理」LOGIN 页签，V24 起独立为菜单页）：登录次数 / 活跃用户趋势 / 异常登录记录
import { t } from '@/i18n'
import ReportPanel from '@/components/ReportPanel.vue'
</script>
