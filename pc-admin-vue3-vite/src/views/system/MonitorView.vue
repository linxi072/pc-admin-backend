<template>
  <div>
    <h2 class="page-title">系统监控</h2>

    <!-- 运行指标 -->
    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">服务器与运行状态</span>
          <div class="filter-group">
            <span class="filter-label">时间范围</span>
            <el-select v-model="range" style="width: 150px" @change="loadSamples">
              <el-option label="最近1小时" value="1" />
              <el-option label="最近6小时" value="6" />
              <el-option label="最近24小时" value="24" />
              <el-option label="最近7天" value="168" />
            </el-select>
          </div>
          <div class="filter-group">
            <el-checkbox v-model="autoRefresh">自动刷新(30s)</el-checkbox>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshAll">刷新</el-button>
          </div>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">CPU 使用率</div>
            <div class="metric-value">{{ fmtPct(metrics?.cpuUsage) }}</div>
            <el-progress :percentage="pctNum(metrics?.cpuUsage)" :stroke-width="8" :show-text="false" />
            <div class="metric-sub">{{ metrics?.cpuCores ?? '--' }} 核 · 负载 {{ fmtLoad(metrics?.loadAverage) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">JVM 内存</div>
            <div class="metric-value">{{ fmtPct(metrics?.memoryUsage) }}</div>
            <!-- 品牌色统一到全局 --brand 变量 -->
            <el-progress :percentage="pctNum(metrics?.memoryUsage)" :stroke-width="8" :show-text="false" color="var(--brand)" />
            <div class="metric-sub">{{ fmtBytes(metrics?.usedHeapBytes) }} / {{ fmtBytes(metrics?.maxHeapBytes) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">物理内存</div>
            <div class="metric-value">{{ fmtPct(metrics?.systemMemoryUsage) }}</div>
            <el-progress :percentage="pctNum(metrics?.systemMemoryUsage)" :stroke-width="8" :show-text="false" color="#67c23a" />
            <div class="metric-sub">{{ fmtBytes(metrics?.usedMemoryBytes) }} / {{ fmtBytes(metrics?.totalMemoryBytes) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-label">磁盘使用率</div>
            <div class="metric-value">{{ fmtPct(metrics?.diskUsage) }}</div>
            <el-progress :percentage="pctNum(metrics?.diskUsage)" :stroke-width="8" :show-text="false" color="#e6a23c" />
            <div class="metric-sub">{{ metrics?.diskPath || '--' }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 历史趋势：时间范围下拉驱动（修复前 loadSamples 为占位空实现，下拉无任何效果） -->
      <div class="chart-block" v-loading="sampleLoading">
        <BaseChart v-if="samples.length" :option="sampleChartOption" height="280px" />
        <el-empty v-else description="暂无历史采样数据（点击「刷新」可立即采样一次）" :image-size="60" />
      </div>

      <el-descriptions class="runtime-desc" :column="4" border size="small">
        <el-descriptions-item label="JVM">{{ metrics?.jvmName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="Java 版本">{{ metrics?.javaVersion || '--' }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ metrics?.osName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="进程 PID">{{ metrics?.processId ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="运行时长">{{ fmtUptime(metrics?.uptimeMillis) }}</el-descriptions-item>
        <el-descriptions-item label="线程数">{{ metrics?.threadCount ?? '--' }}（峰值 {{ metrics?.peakThreadCount ?? '--' }}）</el-descriptions-item>
        <el-descriptions-item label="已加载类">{{ metrics?.loadedClassCount ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="采样时间">{{ metrics?.sampledAt || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 在线用户 -->
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">在线用户与会话</span>
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input
              v-model="sessionKeyword"
              placeholder="按用户名/姓名筛选"
              clearable
              style="width: 220px"
              @keyup.enter="loadSessions"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button :icon="Search" @click="loadSessions">查询</el-button>
          </div>
        </div>
      </div>

      <el-row :gutter="16" class="online-row">
        <el-col :span="8">
          <div class="online-card">
            <div class="online-num">{{ online?.onlineUsers ?? 0 }}</div>
            <div class="online-label">在线用户数</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="online-card">
            <div class="online-num">{{ online?.activeSessions ?? 0 }}</div>
            <div class="online-label">活跃会话数</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="online-card">
            <div class="online-num">{{ online?.activeTokens ?? 0 }}</div>
            <div class="online-label">有效令牌数</div>
          </div>
        </el-col>
      </el-row>

      <el-table :data="sessions" v-loading="sessionLoading" border stripe max-height="320">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="displayName" label="姓名" width="120" />
        <el-table-column prop="ipAddress" label="IP 地址" width="140" />
        <el-table-column prop="clientId" label="客户端" width="120" />
        <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
        <el-table-column prop="issuedAt" label="签发时间" width="160" />
        <el-table-column prop="lastUsedAt" label="最近活跃" width="160" />
        <el-table-column label="剩余有效期" width="120">
          <template #default="{ row }">{{ row.remainingMinutes }} 分钟</template>
        </el-table-column>
      </el-table>
      <div class="hint">
        口径说明：在线会话 = 刷新令牌中「未吊销且未过期」的记录，按 session_id 去重。
      </div>
    </el-card>

    <!-- 异常日志 -->
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">关键接口 / 任务异常日志</span>
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input
              v-model="errQuery.keyword"
              placeholder="按路径/操作/操作人/traceId"
              clearable
              style="width: 240px"
              @keyup.enter="loadErrors"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="loadErrors">查询</el-button>
          </div>
        </div>
      </div>

      <el-table :data="errors" v-loading="errLoading" border stripe @sort-change="onErrorSortChange">
        <el-table-column prop="occurredAt" label="时间" width="170" sortable="custom" />
        <el-table-column prop="moduleCode" label="模块" width="100" />
        <el-table-column prop="operationName" label="操作" min-width="150" show-overflow-tooltip />
        <el-table-column label="请求" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestMethod }} {{ row.requestPath }}</template>
        </el-table-column>
        <el-table-column prop="resultCode" label="错误码" width="130">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.resultCode || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" sortable="custom" />
        <el-table-column prop="traceId" label="TraceId" width="150" show-overflow-tooltip />
      </el-table>

      <!-- 分页统一到全局形态：<div class="pager"> 包裹 + v-model + current/size 事件 + jumper -->
      <div class="pager">
        <el-pagination
          v-model:current-page="errQuery.page"
          v-model:page-size="errQuery.size"
          :total="errTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadErrors"
          @size-change="onErrorSizeChange"
        />
      </div>
      <div class="hint">默认查询最近 24 小时的失败记录（success=0）。</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import BaseChart from '@/components/BaseChart.vue'
import {
  getServerMetrics,
  getOnlineSummary,
  listOnlineSessions,
  listMonitorSamples,
  pageErrorLogs,
  triggerMonitorSample
} from '@/api/monitor'

const metrics = ref(null)
const online = ref(null)
const sessions = ref([])
const sessionLoading = ref(false)
const sessionKeyword = ref('')
const errors = ref([])
const errLoading = ref(false)
const errTotal = ref(0)
// 默认每页条数统一为 10（与其他列表页一致）
const errQuery = reactive({ page: 1, size: 10, keyword: '', sortField: '', sortDirection: '' })
const range = ref('24')
const autoRefresh = ref(false)
const refreshing = ref(false)
const samples = ref([])
const sampleLoading = ref(false)
let timer = null

const pctNum = (v) => (v === null || v === undefined ? 0 : Math.min(100, Number(v)))
const fmtPct = (v) => (v === null || v === undefined ? '--' : `${Number(v).toFixed(2)}%`)
const fmtLoad = (v) => (v === null || v === undefined || v < 0 ? '--' : Number(v).toFixed(2))

function fmtBytes(bytes) {
  if (bytes === null || bytes === undefined) return '--'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let n = Number(bytes)
  let i = 0
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024
    i++
  }
  return `${n.toFixed(1)} ${units[i]}`
}

function fmtUptime(ms) {
  if (!ms && ms !== 0) return '--'
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d} 天 ${h} 小时`
  if (h > 0) return `${h} 小时 ${m} 分`
  return `${m} 分 ${s % 60} 秒`
}

// 失败统一静默：http 拦截器已弹出提示，此处只保证不产生 Uncaught (in promise)
async function loadMetrics() {
  try {
    metrics.value = await getServerMetrics()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function loadOnline() {
  try {
    online.value = await getOnlineSummary()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    sessions.value = (await listOnlineSessions({ keyword: sessionKeyword.value || undefined, limit: 100 })) || []
  } catch (e) {
    sessions.value = []
  } finally {
    sessionLoading.value = false
  }
}

async function loadErrors() {
  errLoading.value = true
  try {
    const data = await pageErrorLogs({
      page: errQuery.page,
      size: errQuery.size,
      keyword: errQuery.keyword || undefined,
      sortField: errQuery.sortField || undefined,
      sortDirection: errQuery.sortDirection || undefined
    })
    errors.value = data.records || []
    errTotal.value = data.total || 0
  } catch (e) {
    errors.value = []
    errTotal.value = 0
  } finally {
    errLoading.value = false
  }
}

/** 异常日志分页：每页条数变化后回到第一页。 */
function onErrorSizeChange(size) {
  errQuery.size = size
  errQuery.page = 1
  loadErrors()
}

/** 异常日志表头排序：走服务端白名单（occurredAt / durationMs 等）。 */
function onErrorSortChange({ prop, order }) {
  errQuery.sortField = order ? prop : ''
  errQuery.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  errQuery.page = 1
  loadErrors()
}

/**
 * 历史趋势采样：按「时间范围」下拉的小时数取 [now-hours, now] 窗口。
 * 后端按 sampled_at 倒序返回，图表按时间正序展示。
 */
async function loadSamples() {
  const hours = Number(range.value) || 24
  const to = new Date()
  const from = new Date(to.getTime() - hours * 3600 * 1000)
  sampleLoading.value = true
  try {
    samples.value = (await listMonitorSamples({
      from: from.toISOString(),
      to: to.toISOString(),
      limit: 500
    })) || []
  } catch (e) {
    samples.value = []
  } finally {
    sampleLoading.value = false
  }
}

/** 趋势图配置：CPU / JVM 内存 / 物理内存三条使用率曲线（0-100%）。 */
const sampleChartOption = computed(() => {
  const rows = [...samples.value].reverse()
  const label = (t) => (t || '').replace('T', ' ').slice(5, 16)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['CPU %', 'JVM 内存 %', '物理内存 %'], right: 0 },
    grid: { left: 48, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category', boundaryGap: false, data: rows.map((r) => label(r.sampledAt)) },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      { name: 'CPU %', type: 'line', showSymbol: false, smooth: true, data: rows.map((r) => r.cpuUsage ?? null) },
      { name: 'JVM 内存 %', type: 'line', showSymbol: false, smooth: true, data: rows.map((r) => r.memoryUsage ?? null) },
      { name: '物理内存 %', type: 'line', showSymbol: false, smooth: true, data: rows.map((r) => r.systemMemoryUsage ?? null) }
    ]
  }
})

async function refreshAll() {
  refreshing.value = true
  try {
    // 手动触发一次采样落库，保证趋势数据连续
    await triggerMonitorSample()
    await Promise.all([loadMetrics(), loadOnline(), loadSessions(), loadErrors()])
    ElMessage.success('已刷新')
  } catch (e) {
    // 拦截器已提示；采样失败不阻断后续刷新
  } finally {
    refreshing.value = false
  }
}

/** 按勾选状态重建 30s 轮询定时器（先停后排，避免叠加）。 */
function setupAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (autoRefresh.value) {
    timer = setInterval(() => {
      loadMetrics()
      loadOnline()
    }, 30000)
  }
}

// 勾选「自动刷新」后接线生效（此前 setupAutoRefresh 定义了却从未调用）
watch(autoRefresh, setupAutoRefresh)

onMounted(() => {
  loadMetrics()
  loadOnline()
  loadSessions()
  loadErrors()
  loadSamples()
})
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
.chart-block {
  margin: 12px 0 4px;
  min-height: 60px;
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
.runtime-desc {
  margin-top: 12px;
}
.online-row {
  margin-bottom: 12px;
}
.online-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px;
  text-align: center;
}
.online-num {
  font-size: 26px;
  font-weight: 600;
  color: var(--brand);
}
.online-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
</style>
