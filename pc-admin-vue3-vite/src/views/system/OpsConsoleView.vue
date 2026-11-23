<template>
  <div>
    <h2 class="page-title">{{ '统一运维控制台' }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">{{ '特性开关运行态' }}</span>
          <el-tag v-if="overview" type="info" class="gate-summary">
            {{ `已启用 ${overview.enabledCount} / ${overview.totalCount}` }}
          </el-tag>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button :icon="Refresh" :loading="loading" @click="load">{{ '刷新' }}</el-button>
          </div>
        </div>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="warning"
        show-icon
        :closable="false"
        class="gate-alert"
      />

      <el-table :data="gates" v-loading="loading" border stripe>
        <el-table-column prop="label" :label="'特性'" min-width="180" />
        <el-table-column prop="property" :label="'配置项'" min-width="240" show-overflow-tooltip />
        <el-table-column :label="'状态'" width="110">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="dark">
              {{ row.enabled ? '已启用' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="'取值来源'" width="120">
          <template #default="{ row }">
            <el-tag :type="row.fromDefault ? 'warning' : 'success'" effect="plain">
              {{ row.fromDefault ? '默认值' : '显式配置' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !gates.length && !error" :description="'暂无开关数据'" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getGateStatus } from '@/api/ops'

const loading = ref(false)
const gates = ref([])
const overview = ref(null)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getGateStatus()
    overview.value = data || null
    gates.value = data?.gates || []
  } catch (e) {
    gates.value = []
    overview.value = null
    error.value = '获取特性开关状态失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.gate-summary {
  margin-left: 12px;
}
.gate-alert {
  margin-bottom: 16px;
}
</style>
