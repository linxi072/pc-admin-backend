<template>
  <div>
    <h2 class="page-title">{{ t('route.configCenter') }}</h2>

    <el-empty v-if="denied" :description="t('configCenter.featureDisabled')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never" :header="t('configCenter.status')">
        <el-descriptions :column="3" border v-loading="statusLoading">
          <el-descriptions-item :label="t('configCenter.centerType')">{{ status.centerType || '--' }}</el-descriptions-item>
          <el-descriptions-item :label="t('configCenter.dynamicRefresh')">
            <el-tag :type="status.dynamicRefreshSupported ? 'success' : 'info'" size="small">
              {{ status.dynamicRefreshSupported ? t('configCenter.dynamicYes') : t('configCenter.dynamicNo') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('configCenter.effectiveCount')">{{ status.effectiveCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="lastRefreshCount != null" class="hint">{{ t('configCenter.lastRefresh', { count: lastRefreshCount }) }}</div>
      </el-card>

      <el-card class="page-card" shadow="never" :header="t('configCenter.snapshot')">
        <div class="toolbar">
          <div class="toolbar-filters">
            <el-input v-model="prefix" :placeholder="t('configCenter.prefix')" clearable style="width: 260px"
              @keyup.enter="load" @clear="load" />
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :loading="refreshing" :icon="Refresh" @click="onRefresh">
                {{ refreshing ? t('configCenter.refreshing') : t('configCenter.refresh') }}
              </el-button>
              <el-button :icon="Search" @click="load">{{ t('common.search') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="snapshotRows" v-loading="loading" border stripe>
          <el-table-column prop="key" :label="t('configCenter.key')" min-width="280" show-overflow-tooltip />
          <el-table-column prop="value" :label="t('configCenter.value')" min-width="280" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!loading && !snapshotRows.length" :description="t('configCenter.emptySnapshot')" :image-size="80" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
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
    ElMessage.success(t('configCenter.refreshSuccess', { count: cnt }))
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
</script>
