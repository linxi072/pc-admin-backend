<template>
  <div>
    <el-empty v-if="denied" :description="'暂无租户维度指标'">
      <el-button type="primary" @click="load">{{ '重新加载' }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ '租户维度指标' }}</span>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button :icon="Refresh" @click="load">{{ '刷新' }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="metrics" v-loading="loading" border stripe>
          <el-table-column prop="tenantId" :label="'租户 ID'" width="140" />
          <el-table-column prop="requestCount" :label="'请求数'" width="140" sortable />
          <el-table-column prop="errorCount" :label="'错误数'" width="140" sortable />
          <el-table-column prop="errorRate" :label="'错误率'" width="140" sortable>
            <template #default="{ row }">{{ fmtShare(row.errorRate) }}</template>
          </el-table-column>
          <el-table-column prop="avgDurationMillis" :label="'平均时延'" width="160" sortable>
            <template #default="{ row }">{{ fmtDuration(row.avgDurationMillis) }}</template>
          </el-table-column>
          <el-table-column prop="lastSeenEpochMs" :label="'最近活跃'" min-width="180">
            <template #default="{ row }">{{ fmtEpoch(row.lastSeenEpochMs) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !metrics.length" :description="'暂无租户维度指标'" :image-size="80" />
      </el-card>
    </template>
  </div>
</template>

<script setup>

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
