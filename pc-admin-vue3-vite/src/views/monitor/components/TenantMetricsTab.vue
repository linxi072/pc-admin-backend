<template>
  <div>
    <el-empty v-if="denied" :description="t('monitor.tmNoData')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ t('monitor.tenantMetrics') }}</span>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="metrics" v-loading="loading" border stripe>
          <el-table-column prop="tenantId" :label="t('monitor.tmTenantId')" width="140" />
          <el-table-column prop="requestCount" :label="t('monitor.tmRequestCount')" width="140" sortable />
          <el-table-column prop="errorCount" :label="t('monitor.tmErrorCount')" width="140" sortable />
          <el-table-column prop="errorRate" :label="t('monitor.tmErrorRate')" width="140" sortable>
            <template #default="{ row }">{{ fmtShare(row.errorRate) }}</template>
          </el-table-column>
          <el-table-column prop="avgDurationMillis" :label="t('monitor.tmAvgDuration')" width="160" sortable>
            <template #default="{ row }">{{ fmtDuration(row.avgDurationMillis) }}</template>
          </el-table-column>
          <el-table-column prop="lastSeenEpochMs" :label="t('monitor.tmLastSeen')" min-width="180">
            <template #default="{ row }">{{ fmtEpoch(row.lastSeenEpochMs) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !metrics.length" :description="t('monitor.tmNoData')" :image-size="80" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listTenantMetrics } from '@/api/tenantMetrics'
import { fmtEpoch, fmtDuration, fmtShare, isForbidden } from './format'

const metrics = ref([])
const loading = ref(false)
const denied = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await listTenantMetrics()
    metrics.value = data || []
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    metrics.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
