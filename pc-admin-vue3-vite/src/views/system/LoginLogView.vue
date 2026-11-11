<template>
  <div>
    <h2 class="page-title">{{ '登录日志' }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ '用户名' }}</span>
            <el-input v-model="query.username" :placeholder="'用户名（模糊）'" clearable style="width: 160px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '结果' }}</span>
            <el-select v-model="query.result" :placeholder="'全部'" clearable style="width: 120px">
              <el-option :label="'成功'" value="SUCCESS" />
              <el-option :label="'失败'" value="FAILURE" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '登录类型' }}</span>
            <el-select v-model="query.loginType" :placeholder="'全部'" clearable style="width: 120px">
              <el-option label="PASSWORD" value="PASSWORD" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '失败码' }}</span>
            <el-input v-model="query.failureCode" :placeholder="'如 AUTH_001'" clearable style="width: 140px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">IP</span>
            <el-input v-model="query.ipAddress" :placeholder="'IP（模糊）'" clearable style="width: 140px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '时间范围' }}</span>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              :range-separator="'至'"
              :start-placeholder="'开始时间'"
              :end-placeholder="'结束时间'"
              value-format="YYYY-MM-DDTHH:mm:ss"
              :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
              style="width: 360px"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '关键字' }}</span>
            <el-input v-model="query.keyword" :placeholder="'用户名/IP/失败码/traceId'" clearable style="width: 200px" @keyup.enter="load" />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ '查询' }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ '重置' }}</el-button>
            <el-button :icon="Download" @click="onExportCsv">{{ '导出当前页 CSV' }}</el-button>
          </div>
        </div>
      </div>

      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.total ?? '--' }}</div>
            <div class="stat-label">{{ '总记录' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ summary.success ?? '--' }}</div>
            <div class="stat-label">{{ '成功' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failure ?? '--' }}</div>
            <div class="stat-label">{{ '失败' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtPct(summary.failureRate) }}</div>
            <div class="stat-label">{{ '失败率' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.distinctUsers ?? '--' }}</div>
            <div class="stat-label">{{ '独立用户' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtTime(summary.lastLoginAt) }}</div>
            <div class="stat-label">{{ '最近成功登录' }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 加载失败：与「暂无数据」区分，避免把超时/后端异常误读成「本来就没有日志」 -->
      <el-alert
        v-if="loadError"
        class="load-error"
        type="error"
        :title="'数据加载失败'"
        :closable="false"
        show-icon
      >
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="load">{{ '重试' }}</el-button>
      </el-alert>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="occurredAt" :label="'时间'" width="180" sortable="custom" />
        <el-table-column prop="username" :label="'用户名'" width="140" sortable="custom" />
        <el-table-column prop="userId" :label="'用户 ID'" width="90" />
        <el-table-column prop="loginType" :label="'登录类型'" width="110" />
        <el-table-column :label="'结果'" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureCode" :label="'失败码'" width="120">
          <template #default="{ row }">
            <span v-if="row.failureCode">{{ row.failureCode }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" :label="'IP 地址'" width="150" sortable="custom" />
        <el-table-column prop="userAgent" label="User-Agent" min-width="220" show-overflow-tooltip />
        <el-table-column prop="traceId" label="TraceId" width="150" show-overflow-tooltip />
      </el-table>

      <!-- 分页统一到全局形态：<div class="pager"> 包裹 + v-model + current/size 事件 + jumper -->
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
      <div class="hint">{{ '默认查询最近 7 天记录；登录失败时用户可能不存在，「用户 ID」为空属正常。' }}</div>
    </el-card>
  </div>
</template>

<script setup>

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Download } from '@element-plus/icons-vue'
import { pageLoginLogs, loginLogSummary } from '@/api/loginLog'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const summary = ref({})
/** 页内加载失败提示：与全局 ElMessage 并存，把「加载失败」和「确实没数据」区分开。 */
const loadError = ref('')
const dateRange = ref([])
const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  username: '',
  result: '',
  loginType: '',
  failureCode: '',
  ipAddress: '',
  keyword: '',
  startTime: '',
  endTime: '',
  sortField: '',
  sortDirection: ''
})

/** 统一把时间范围与筛选条件映射为查询参数（空值一律 undefined，不传空串）。 */
function buildParams() {
  const params = {
    page: query.page,
    size: query.size,
    username: query.username || undefined,
    result: query.result || undefined,
    loginType: query.loginType || undefined,
    failureCode: query.failureCode || undefined,
    ipAddress: query.ipAddress || undefined,
    keyword: query.keyword || undefined,
    sortField: query.sortField || undefined,
    sortDirection: query.sortDirection || undefined
  }
  if (dateRange.value && dateRange.value.length === 2) {
    params.startTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  return params
}

/**
 * 明细与统计并行加载（与操作日志页 F2 同一形态）：
 * 用 allSettled 让任一请求失败都不会把页面伪装成「空数据」，失败原因页内可见且可重试；
 * 统计失败独立降级为空摘要，不覆盖明细的失败提示，也不阻断明细正常展示。
 */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [pageResult, summaryResult] = await Promise.allSettled([
      pageLoginLogs(buildParams()),
      loginLogSummary(buildParams())
    ])
    if (pageResult.status === 'fulfilled') {
      rows.value = pageResult.value?.records || []
      total.value = pageResult.value?.total || 0
    } else {
      rows.value = []
      total.value = 0
      loadError.value = pageResult.reason?.message || '数据加载失败'
    }
    summary.value = summaryResult.status === 'fulfilled' ? (summaryResult.value || {}) : {}
    if (!loadError.value && summaryResult.status === 'rejected') {
      loadError.value = summaryResult.reason?.message || '数据加载失败'
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

/** 每页条数变化后回到第一页。 */
function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

function resetQuery() {
  query.username = ''
  query.result = ''
  query.loginType = ''
  query.failureCode = ''
  query.ipAddress = ''
  query.keyword = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  dateRange.value = []
  load()
}

/** 表头排序：走服务端白名单（occurredAt/username/ipAddress），不拼 SQL。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

const fmtPct = (v) => {
  if (v === null || v === undefined) return '--'
  return `${(Number(v) * 100).toFixed(2)}%`
}
const fmtTime = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '--')

/** 导出当前页为 CSV：仅导出已加载的明细，不触发后端请求。 */
const exportColumns = [
  { label: '时间', get: (r) => r.occurredAt },
  { label: '用户名', get: (r) => r.username },
  { label: '用户 ID', get: (r) => r.userId },
  { label: '登录类型', get: (r) => r.loginType },
  { label: '结果', get: (r) => r.result },
  { label: '失败码', get: (r) => r.failureCode },
  { label: 'IP', get: (r) => r.ipAddress },
  { label: 'User-Agent', get: (r) => r.userAgent },
  { label: 'TraceId', get: (r) => r.traceId }
]
function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning('当前页无数据可导出')
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`login-log-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(`已导出 ${data.length} 行`)
}

onMounted(load)
</script>

<style scoped>
.load-error {
  margin-bottom: 12px;
}
.summary-row {
  margin-bottom: 12px;
}
.stat-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  text-align: center;
}
.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
}
.stat-ok { color: #67c23a; }
.stat-danger { color: #f56c6c; }
.stat-time { font-size: 15px; line-height: 26px; }
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.muted { color: #c0c4cc; }
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
</style>
