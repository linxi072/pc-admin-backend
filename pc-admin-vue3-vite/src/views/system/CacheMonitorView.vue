<template>
  <div>
    <h2 class="page-title">缓存监控</h2>

    <!-- 汇总指标卡 + 操作区 -->
    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">进程内缓存总览</span>
          <div class="filter-group">
            <el-checkbox v-model="autoRefresh">自动刷新(10s)</el-checkbox>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">缓存实例数</div>
            <div class="metric-value">{{ caches.length }}</div>
            <div class="metric-sub">当前进程内注册的缓存</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">缓存条目总数</div>
            <div class="metric-value">{{ summary.entries }}</div>
            <div class="metric-sub">各缓存当前驻留条目</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">综合命中率</div>
            <div class="metric-value">{{ fmtPct(summary.hitRate) }}</div>
            <el-progress :percentage="pctNum(summary.hitRate)" :stroke-width="8" :show-text="false" />
            <div class="metric-sub">命中 {{ summary.hit }} / 未命中 {{ summary.miss }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">累计淘汰数</div>
            <div class="metric-value">{{ summary.evict }}</div>
            <div class="metric-sub">过期清理 + 主动失效 + 手动清理</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 缓存明细 -->
    <el-card class="page-card">
      <el-table :data="caches" v-loading="loading" border stripe>
        <el-table-column prop="name" label="缓存名称" width="160" />
        <el-table-column prop="type" label="实现类型" width="200" />
        <el-table-column prop="entries" label="条目数" width="100" sortable />
        <el-table-column prop="hitCount" label="命中" width="100" sortable />
        <el-table-column prop="missCount" label="未命中" width="100" sortable />
        <el-table-column label="命中率" min-width="160">
          <template #default="{ row }">
            <div class="hit-cell">
              <span class="hit-text">{{ fmtPct(row.hitRate == null ? null : row.hitRate * 100) }}</span>
              <el-progress :percentage="pctNum(row.hitRate == null ? null : row.hitRate * 100)" :stroke-width="6" :show-text="false" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="evictCount" label="淘汰" width="90" sortable />
        <el-table-column label="TTL" width="110">
          <template #default="{ row }">
            {{ row.ttlMillis == null ? '不过期' : (row.ttlMillis / 1000) + ' s' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="用途" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" :icon="Delete" @click="onClear(row)">清理</el-button>
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { getCacheStats, clearCache } from '../../api/monitor'

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
  } finally {
    loading.value = false
  }
}

async function onClear(row) {
  try {
    await ElMessageBox.confirm(
      `确认清理缓存「${row.name}」？清理后下一次读取将回源数据库。`,
      '清理确认',
      { type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  await clearCache(row.name)
  ElMessage.success(`已清理缓存「${row.name}」`)
  load()
}

function setupAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (autoRefresh.value) {
    timer = setInterval(load, 10000)
  }
}

onMounted(load)
onUnmounted(() => timer && clearInterval(timer))
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
  color: #303133;
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
