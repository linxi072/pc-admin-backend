<template>
  <div>
    <h2 class="page-title">{{ t('route.cacheMonitor') }}</h2>

    <!-- 汇总指标卡 + 操作区 -->
    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">{{ t('system.cacheOverview') }}</span>
          <div class="filter-group">
            <el-checkbox v-model="autoRefresh">{{ t('system.autoRefresh10s') }}</el-checkbox>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
          </div>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.cacheInstances') }}</div>
            <div class="metric-value">{{ caches.length }}</div>
            <div class="metric-sub">{{ t('system.registeredCaches') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.cacheEntryTotal') }}</div>
            <div class="metric-value">{{ summary.entries }}</div>
            <div class="metric-sub">{{ t('system.cacheResidentEntries') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.overallHitRate') }}</div>
            <div class="metric-value">{{ fmtPct(summary.hitRate) }}</div>
            <el-progress :percentage="pctNum(summary.hitRate)" :stroke-width="8" :show-text="false" />
            <div class="metric-sub">命中 {{ summary.hit }} / 未命中 {{ summary.miss }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.cumulativeEvictions') }}</div>
            <div class="metric-value">{{ summary.evict }}</div>
            <div class="metric-sub">{{ t('system.cacheStrategyNote') }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 缓存明细 -->
    <el-card class="page-card">
      <el-table :data="caches" v-loading="loading" border stripe>
        <el-table-column prop="name" :label="t('system.cacheNameSys')" width="160" />
        <el-table-column prop="type" :label="t('system.implType')" width="200" />
        <el-table-column prop="entries" :label="t('system.entryCount')" width="100" sortable />
        <el-table-column prop="hitCount" :label="t('system.colHit')" width="100" sortable />
        <el-table-column prop="missCount" :label="t('system.notHit')" width="100" sortable />
        <el-table-column :label="t('system.hitRateSys')" min-width="160">
          <template #default="{ row }">
            <div class="hit-cell">
              <span class="hit-text">{{ fmtPct(row.hitRate == null ? null : row.hitRate * 100) }}</span>
              <el-progress :percentage="pctNum(row.hitRate == null ? null : row.hitRate * 100)" :stroke-width="6" :show-text="false" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="evictCount" :label="t('system.colEvict')" width="90" sortable />
        <el-table-column label="TTL" width="110">
          <template #default="{ row }">
            {{ row.ttlMillis == null ? t('system.neverExpires') : (row.ttlMillis / 1000) + ' s' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('system.colUsage')" min-width="220" show-overflow-tooltip />
        <el-table-column :label="t('common.operation')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" :icon="Delete" @click="onClear(row)">{{ t('system.cleanup') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="hint">
        口径说明：命中率 = 命中 / (命中 + 未命中)，无访问时展示「--」。清理后下一次读取回源数据库，
        不影响已签发凭证的有效性（版本号事实不变，仅重新加载）。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { getCacheStats, clearCache } from '@/api/monitor'

const caches = ref([])
const loading = ref(false)
const autoRefresh = ref(false)
let timer = null

const summary = computed(() => {
  let entries = 0
  let hit = 0
  let miss = 0
  let evict = 0
  for (const c of caches.value) {
    entries += Number(c.entries || 0)
    hit += Number(c.hitCount || 0)
    miss += Number(c.missCount || 0)
    evict += Number(c.evictCount || 0)
  }
  const total = hit + miss
  return { entries, hit, miss, evict, hitRate: total > 0 ? (hit / total) * 100 : null }
})

const pctNum = (v) => (v === null || v === undefined ? 0 : Math.min(100, Number(v)))
const fmtPct = (v) => (v === null || v === undefined ? '--' : `${Number(v).toFixed(2)}%`)

async function load() {
  loading.value = true
  try {
    caches.value = (await getCacheStats()) || []
  } catch (e) {
    caches.value = []
  } finally {
    loading.value = false
  }
}

async function onClear(row) {
  try {
    await ElMessageBox.confirm(
      t('system.confirmClearCache', { name: row.name }),
      t('monitor.cleanConfirm'),
      { type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await clearCache(row.name)
    ElMessage.success(t('system.cacheCleared', { name: row.name }))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

/** 按勾选状态重建 10s 轮询定时器（先停后排，避免叠加）。 */
function setupAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (autoRefresh.value) {
    timer = setInterval(load, 10000)
  }
}

// 勾选「自动刷新」后接线生效（此前 setupAutoRefresh 定义了却从未调用）
watch(autoRefresh, setupAutoRefresh)

onMounted(load)
onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.metric-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  margin-bottom: 8px;
}
.metric-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.metric-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}
.metric-sub {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hit-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hit-text {
  min-width: 56px;
  font-size: 13px;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
</style>
