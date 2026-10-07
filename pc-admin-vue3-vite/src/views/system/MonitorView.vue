<template>
  <div>
    <h2 class="page-title">系统监控</h2>

    <!-- 运行指标 -->
    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <span class="card-title">服务器与运行状态</span>
        <span class="spacer" />
        <el-select v-model="range" style="width: 150px" @change="loadSamples">
          <el-option label="最近1小时" value="1" />
          <el-option label="最近6小时" value="6" />
          <el-option label="最近24小时" value="24" />
          <el-option label="最近7天" value="168" />
        </el-select>
        <el-checkbox v-model="autoRefresh">自动刷新(30s)</el-checkbox>
        <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshAll">刷新</el-button>
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
            <el-progress :percentage="pctNum(metrics?.memoryUsage)" :stroke-width="8" :show-text="false" color="#409eff" />
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
        <span class="card-title">在线用户与会话</span>
        <span class="spacer" />
        <el-input
          v-model="sessionKeyword"
          placeholder="按用户名/姓名筛选"
          clearable
          style="width: 220px"
          @keyup.enter="loadSessions"
        />
        <el-button :icon="Search" @click="loadSessions">查询</el-button>
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
        <span class="card-title">关键接口 / 任务异常日志</span>
        <span class="spacer" />
        <el-input
          v-model="errQuery.keyword"
          placeholder="按路径/操作/操作人/traceId"
          clearable
          style="width: 240px"
          @keyup.enter="loadErrors"
        />
        <el-button type="primary" :icon="Search" @click="loadErrors">查询</el-button>
      </div>

      <el-table :data="errors" v-loading="errLoading" border stripe>
        <el-table-column prop="occurredAt" label="时间" width="170" />
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
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="traceId" label="TraceId" width="150" show-overflow-tooltip />
      </el-table>

      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next"
        :total="errTotal"
        v-model:current-page="errQuery.page"
        v-model:page-size="errQuery.size"
        :page-sizes="[10, 20, 50]"
        @change="loadErrors"
      />
      <div class="hint">默认查询最近 24 小时的失败记录（success=0）。</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  getServerMetrics,
  getOnlineSummary,
  listOnlineSessions,
  pageErrorLogs,
  triggerMonitorSample
} from '../../api/monitor'

const metrics = ref(null)
const online = ref(null)
const sessions = ref([])
const sessionLoading = ref(false)
const sessionKeyword = ref('')
const errors = ref([])
const errLoading = ref(false)
const errTotal = ref(0)
const errQuery = reactive({ page: 1, size: 20, keyword: '' })
const range = ref('24')
const autoRefresh = ref(false)
const refreshing = ref(false)
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

async function loadMetrics() {
  metrics.value = await getServerMetrics()
}

async function loadOnline() {
  online.value = await getOnlineSummary()
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    sessions.value = (await listOnlineSessions({ keyword: sessionKeyword.value || undefined, limit: 100 })) || []
  } finally {
    sessionLoading.value = false
  }
}

async function loadErrors() {
  errLoading.value = true
  try {
    const data = await pageErrorLogs({ page: errQuery.page, size: errQuery.size, keyword: errQuery.keyword || undefined })
    errors.value = data.records || []
    errTotal.value = data.total || 0
  } finally {
    errLoading.value = false
  }
}

function loadSamples() {
  // 预留：历史趋势接口（/monitor/samples），如需图表可在此接入
}

async function refreshAll() {
  refreshing.value = true
  try {
    // 手动触发一次采样落库，保证趋势数据连续
    await triggerMonitorSample()
    await Promise.all([loadMetrics(), loadOnline(), loadSessions(), loadErrors()])
    ElMessage.success('已刷新')
  } finally {
    refreshing.value = false
  }
}

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

onMounted(() => {
  loadMetrics()
  loadOnline()
  loadSessions()
  loadErrors()
})
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
  color: #409eff;
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
