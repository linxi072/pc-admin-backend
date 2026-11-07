<template>
  <div>
    <h2 class="page-title">{{ t('route.loginLog') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('common.username') }}</span>
            <el-input v-model="query.username" :placeholder="t('system.usernameFuzzy')" clearable style="width: 160px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('job.result') }}</span>
            <el-select v-model="query.result" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('common.success')" value="SUCCESS" />
              <el-option :label="t('common.failed')" value="FAILURE" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.loginType') }}</span>
            <el-select v-model="query.loginType" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option label="PASSWORD" value="PASSWORD" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.failCodeSys') }}</span>
            <el-input v-model="query.failureCode" :placeholder="t('system.phFailureCode')" clearable style="width: 140px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">IP</span>
            <el-input v-model="query.ipAddress" :placeholder="t('system.ipBlurred')" clearable style="width: 140px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('monitor.timeRange') }}</span>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              :range-separator="t('common.to')"
              :start-placeholder="t('common.startTime')"
              :end-placeholder="t('monitor.endTime')"
              value-format="YYYY-MM-DDTHH:mm:ss"
              :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
              style="width: 360px"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input v-model="query.keyword" :placeholder="t('system.usernameIpCodeTrace')" clearable style="width: 200px" @keyup.enter="load" />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            <el-button :icon="Download" @click="onExportCsv">{{ t('common.exportCurrentPageCsv') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.total ?? '--' }}</div>
            <div class="stat-label">{{ t('system.totalRecords') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ summary.success ?? '--' }}</div>
            <div class="stat-label">{{ t('common.success') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failure ?? '--' }}</div>
            <div class="stat-label">{{ t('common.failed') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtPct(summary.failureRate) }}</div>
            <div class="stat-label">{{ t('system.failureRate') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.distinctUsers ?? '--' }}</div>
            <div class="stat-label">{{ t('system.uniqueUsers') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtTime(summary.lastLoginAt) }}</div>
            <div class="stat-label">{{ t('system.lastSuccessLogin') }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 加载失败：与「暂无数据」区分，避免把超时/后端异常误读成「本来就没有日志」 -->
      <el-alert
        v-if="loadError"
        class="load-error"
        type="error"
        :title="t('common.loadFailed')"
        :closable="false"
        show-icon
      >
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="load">{{ t('common.retry') }}</el-button>
      </el-alert>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="occurredAt" :label="t('system.time')" width="180" sortable="custom" />
        <el-table-column prop="username" :label="t('common.username')" width="140" sortable="custom" />
        <el-table-column prop="userId" :label="t('monitor.userId')" width="90" />
        <el-table-column prop="loginType" :label="t('system.loginType')" width="110" />
        <el-table-column :label="t('job.result')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'SUCCESS' ? t('common.success') : t('common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureCode" :label="t('system.failCodeSys')" width="120">
          <template #default="{ row }">
            <span v-if="row.failureCode">{{ row.failureCode }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" :label="t('common.ipAddress')" width="150" sortable="custom" />
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
      <div class="hint">{{ t('system.loginLogDesc') }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
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
      loadError.value = pageResult.reason?.message || t('common.loadFailed')
    }
    summary.value = summaryResult.status === 'fulfilled' ? (summaryResult.value || {}) : {}
    if (!loadError.value && summaryResult.status === 'rejected') {
      loadError.value = summaryResult.reason?.message || t('common.loadFailed')
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
  { label: t('system.time'), get: (r) => r.occurredAt },
  { label: t('common.username'), get: (r) => r.username },
  { label: t('monitor.userId'), get: (r) => r.userId },
  { label: t('system.loginType'), get: (r) => r.loginType },
  { label: t('job.result'), get: (r) => r.result },
  { label: t('system.failCodeSys'), get: (r) => r.failureCode },
  { label: 'IP', get: (r) => r.ipAddress },
  { label: 'User-Agent', get: (r) => r.userAgent },
  { label: 'TraceId', get: (r) => r.traceId }
]
function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning(t('common.exportEmpty'))
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`login-log-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(t('common.exportSuccess', { n: data.length }))
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
