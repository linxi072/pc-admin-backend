<template>
  <div>
    <h2 class="page-title">{{ '配置中心' }}</h2>

    <el-empty v-if="denied" :description="'配置中心未启用（app.config-center.enabled=false），后端控制器未注册，暂不可操作。'">
      <el-button type="primary" @click="load">{{ '重新加载' }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never" :header="'运行状态'">
        <el-descriptions :column="3" border v-loading="statusLoading">
          <el-descriptions-item :label="'中心类型'">{{ status.centerType || '--' }}</el-descriptions-item>
          <el-descriptions-item :label="'动态刷新'">
            <el-tag :type="status.dynamicRefreshSupported ? 'success' : 'info'" size="small">
              {{ status.dynamicRefreshSupported ? '支持' : '不支持' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="'生效配置数'">{{ status.effectiveCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="lastRefreshCount != null" class="hint">{{ `上次刷新：成功生效 ${lastRefreshCount} 条配置` }}</div>
      </el-card>

      <el-card class="page-card" shadow="never" :header="'配置快照'">
        <div class="toolbar">
          <div class="toolbar-filters">
            <el-input v-model="prefix" :placeholder="'前缀过滤'" clearable style="width: 260px"
              @keyup.enter="load" @clear="load" />
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :loading="refreshing" :icon="Refresh" @click="onRefresh">
                {{ refreshing ? '刷新中…' : '热刷新' }}
              </el-button>
              <el-button :icon="Search" @click="load">{{ '搜索' }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="snapshotRows" v-loading="loading" border stripe>
          <el-table-column prop="key" :label="'配置项'" min-width="280" show-overflow-tooltip />
          <el-table-column prop="value" :label="'配置值'" min-width="280" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!loading && !snapshotRows.length" :description="'暂无配置快照'" :image-size="80" />
      </el-card>
    </template>
  </div>
</template>

<script setup>

import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getConfigStatus, getConfigSnapshot, refreshConfig } from '@/api/configCenter'

const status = ref({})
const statusLoading = ref(false)
const loading = ref(false)
const refreshing = ref(false)
const denied = ref(false)
const prefix = ref('')
const lastRefreshCount = ref(null)
const snapshotRows = ref([])

function isForbidden(err) {
  return err?.response?.status === 403
}

async function load() {
  loading.value = true
  statusLoading.value = true
  try {
    const [st, snap] = await Promise.all([
      getConfigStatus(),
      getConfigSnapshot(prefix.value ? { prefix: prefix.value } : undefined)
    ])
    status.value = st || {}
    const map = snap || {}
    snapshotRows.value = Object.keys(map).map((k) => ({ key: k, value: map[k] }))
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    snapshotRows.value = []
  } finally {
    loading.value = false
    statusLoading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    const res = await refreshConfig()
    const cnt = res?.effectiveCount ?? 0
    lastRefreshCount.value = cnt
    ElMessage.success(`热刷新成功，已生效 ${cnt} 条`)
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
</script>
