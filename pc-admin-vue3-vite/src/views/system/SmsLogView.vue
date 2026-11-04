<template>
  <div>
    <h2 class="page-title">{{ t('route.smsRecord') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('job.result') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('common.success')" value="SUCCESS" />
              <el-option :label="t('common.failed')" value="FAILED" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.gatewaySys') }}</span>
            <el-select v-model="query.provider" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('system.logDev')" value="LOG" />
              <el-option :label="t('system.httpProd')" value="HTTP" />
            </el-select>
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
            <el-input v-model="query.keyword" :placeholder="t('system.batchNoOperatorTrace')" clearable style="width: 240px" @keyup.enter="load" />
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
        <el-col :span="8">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtTime(summary.lastSentAt) }}</div>
            <div class="stat-label">{{ t('system.lastSent') }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="sentAt" :label="t('system.time')" width="180" sortable="custom" />
        <el-table-column prop="batchNo" :label="t('system.batchNoSys')" width="180" show-overflow-tooltip />
        <el-table-column prop="mobilesMasked" :label="t('system.recvNumberMasked')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="mobileCount" :label="t('system.numberCountSys')" width="90" sortable="custom" />
        <el-table-column prop="contentSummary" :label="t('system.contentSummary')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="provider" :label="t('system.gatewaySys')" width="110" sortable="custom" />
        <el-table-column :label="t('job.result')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? t('common.success') : t('common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" :label="t('monitor.failReason')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" :label="t('system.operator')" width="120" sortable="custom">
          <template #default="{ row }">
            <span v-if="row.operatorName">{{ row.operatorName }}</span>
            <span v-else class="muted">{{ t('system.system') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="TraceId" width="150" show-overflow-tooltip />
      </el-table>

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
      <div class="hint">{{ t('system.smsLogDesc') }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Download } from '@element-plus/icons-vue'
import { pageSmsRecords, smsRecordSummary } from '@/api/smsLog'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const summary = ref({})
const dateRange = ref([])
const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  status: '',
  provider: '',
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
    status: query.status || undefined,
    provider: query.provider || undefined,
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

async function load() {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      pageSmsRecords(buildParams()),
      smsRecordSummary(buildParams())
    ])
    rows.value = page.records || []
    total.value = page.total || 0
    summary.value = sum || {}
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
  query.status = ''
  query.provider = ''
  query.keyword = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  dateRange.value = []
  load()
}

/** 表头排序：走服务端白名单（sentAt/batchNo/status/provider/mobileCount/operatorName），不拼 SQL。 */
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
  { label: t('system.time'), get: (r) => r.sentAt },
  { label: t('system.batchNoSys'), get: (r) => r.batchNo },
  { label: t('system.recvNumberMasked'), get: (r) => r.mobilesMasked },
  { label: t('system.numberCountSys'), get: (r) => r.mobileCount },
  { label: t('system.contentSummary'), get: (r) => r.contentSummary },
  { label: t('system.gatewaySys'), get: (r) => r.provider },
  { label: t('job.result'), get: (r) => r.status },
  { label: t('monitor.failReason'), get: (r) => r.errorMessage },
  { label: t('system.operator'), get: (r) => r.operatorName },
  { label: 'TraceId', get: (r) => r.traceId }
]
function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning(t('common.exportEmpty'))
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`sms-log-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(t('common.exportSuccess', { n: data.length }))
}

onMounted(load)
</script>

<style scoped>
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
.error-text { color: #f56c6c; }
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
</style>
