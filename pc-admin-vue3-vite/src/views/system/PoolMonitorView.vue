<template>
  <div>
    <h2 class="page-title">{{ t('route.poolMonitor') }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">{{ t('system.hikariPoolStatus') }}</span>
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

      <!-- 计数不可用（非 Hikari 数据源）时给出明确提示，而不是展示 -1 -->
      <el-alert
        v-if="countsUnavailable"
        type="warning"
        :closable="false"
        :title="t('system.notHikariHint')"
        class="pool-alert"
      />

      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.activeConns') }}</div>
            <div class="metric-value">{{ fmtCount(pool.activeCount) }}</div>
            <div class="metric-sub">{{ t('system.executingSqlConns') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.idleConns') }}</div>
            <div class="metric-value">{{ fmtCount(pool.idleCount) }}</div>
            <div class="metric-sub">{{ t('system.standbyConns') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.totalConns') }}</div>
            <div class="metric-value">{{ fmtCount(pool.totalCount) }}</div>
            <div class="metric-sub">{{ t('system.activePlusIdle') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.waitingThreads') }}</div>
            <div class="metric-value" :class="{ 'metric-danger': waiting > 0 }">
              {{ fmtCount(pool.threadsAwaiting) }}
            </div>
            <div class="metric-sub">{{ t('system.blockedWaitingConns') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.poolUsage') }}</div>
            <div class="metric-value">{{ fmtPct(usagePct) }}</div>
            <el-progress
              :percentage="pctNum(usagePct)"
              :stroke-width="10"
              :show-text="false"
              :color="usageColor"
            />
            <div class="metric-sub">活跃 {{ fmtCount(pool.activeCount) }} / 最大 {{ pool.maximumPoolSize }}</div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="metric-card">
            <div class="metric-label">{{ t('system.healthHint') }}</div>
            <div class="health-text" :class="healthClass">{{ healthText }}</div>
            <div class="metric-sub">{{ t('system.waitThreadsHint') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-descriptions class="pool-desc" :column="3" border size="small">
        <el-descriptions-item :label="t('system.poolName')">{{ pool.poolName || '--' }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.maxConns')">{{ pool.maximumPoolSize ?? '--' }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.minIdleConns')">{{ pool.minimumIdle ?? '--' }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.acquireTimeout')">{{ fmtMs(pool.connectionTimeoutMillis) }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.validationTimeout')">{{ fmtMs(pool.validationTimeoutMillis) }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.idleReclaim')">{{ fmtMs(pool.idleTimeoutMillis) }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.maxLifetime')">{{ fmtMs(pool.maxLifetimeMillis) }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.connAddress')" :span="2">{{ pool.jdbcUrl || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getPoolStats } from '@/api/monitor'

const pool = ref({})
const loading = ref(false)
const autoRefresh = ref(false)
let timer = null

const countsUnavailable = computed(() => pool.value.activeCount === -1)
const waiting = computed(() => (pool.value.threadsAwaiting > 0 ? pool.value.threadsAwaiting : 0))
const usagePct = computed(() =>
  pool.value.usageRate > 0 ? pool.value.usageRate * 100 : null)

const healthText = computed(() => {
  if (countsUnavailable.value) return t('system.countersUnavailable')
  if (waiting.value > 0) return t('system.waitingThreadsDetail', { n: waiting.value })
  if (usagePct.value != null && usagePct.value >= 80) return t('system.poolUsageHigh')
  return t('system.poolHealthy')
})
const healthClass = computed(() => {
  if (waiting.value > 0) return 'health-danger'
  if (usagePct.value != null && usagePct.value >= 80) return 'health-warning'
  return 'health-ok'
})

const usageColor = computed(() => {
  // 统一走全局品牌色变量
  if (usagePct.value == null) return 'var(--brand)'
  if (usagePct.value >= 80) return '#f56c6c'
  if (usagePct.value >= 60) return '#e6a23c'
  return '#67c23a'
})

const pctNum = (v) => (v === null || v === undefined ? 0 : Math.min(100, Number(v)))
const fmtPct = (v) => (v === null || v === undefined ? '--' : `${Number(v).toFixed(2)}%`)
const fmtCount = (v) => (v === null || v === undefined || v < 0 ? '--' : String(v))
const fmtMs = (v) => (v === null || v === undefined || v < 0 ? '--' : `${(v / 1000).toFixed(1)} s`)

async function load() {
  loading.value = true
  try {
    pool.value = (await getPoolStats()) || {}
  } catch (e) {
    pool.value = {}
  } finally {
    loading.value = false
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
.metric-danger {
  color: #f56c6c;
}
.metric-sub {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 6px;
}
.health-text {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  line-height: 1.5;
}
.health-ok { color: #67c23a; }
.health-warning { color: #e6a23c; }
.health-danger { color: #f56c6c; }
.pool-desc {
  margin-top: 12px;
}
.pool-alert {
  margin-bottom: 12px;
}
</style>
