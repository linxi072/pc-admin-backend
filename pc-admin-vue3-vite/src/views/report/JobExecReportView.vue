<template>
  <ReportPanel type="JOB_EXEC" :title="t('route.reportJobExec')" />
</template>

<script setup>
// 定时任务执行统计（原「报表管理」JOB_EXEC 页签，V24 起独立为菜单页）：成功率 / 失败率 / 耗时分布 / 最近执行明细
import { t } from '@/i18n'
import ReportPanel from '@/components/ReportPanel.vue'
</script>
