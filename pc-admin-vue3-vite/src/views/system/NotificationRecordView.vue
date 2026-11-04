<template>
  <div>
    <h2 class="page-title">{{ t('route.notificationRecord') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('common.channel') }}</span>
            <el-select v-model="query.channel" :placeholder="t('common.all')" clearable style="width: 140px">
              <el-option :label="t('common.stationMsg')" value="IN_APP" />
              <el-option label="Webhook" value="WEBHOOK" />
              <el-option label="WebSocket" value="WEBSOCKET" />
              <el-option :label="t('common.mail')" value="EMAIL" />
              <el-option :label="t('common.sms')" value="SMS" />
              <el-option :label="t('system.wecomBot')" value="WECOM" />
              <el-option :label="t('system.dingtalkBot')" value="DINGTALK" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('job.result') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('common.success')" value="SUCCESS" />
              <el-option :label="t('common.failed')" value="FAILED" />
              <el-option :label="t('system.skippedSys')" value="SKIPPED" />
              <el-option :label="t('system.aggregatingSys')" value="DIGEST_PENDING" />
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
            <el-input v-model="query.keyword" :placeholder="t('system.channelTemplateSummaryTraceOperator')" clearable style="width: 260px" @keyup.enter="load" />
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
            <div class="stat-num stat-warning">{{ summary.skipped ?? '--' }}</div>
            <div class="stat-label">{{ t('system.skippedSys') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-info">{{ summary.digestPending ?? '--' }}</div>
            <div class="stat-label">{{ t('system.aggregatingSys') }}</div>
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
        <el-table-column :label="t('common.channel')" width="110">
          <template #default="{ row }">
            <el-tag :type="channelTagType(row.channel)" size="small" effect="light">
              {{ channelLabel(row.channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="templateCode" :label="t('system.sourceTemplate')" width="150" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.templateCode">{{ row.templateCode }}</span>
            <span v-else class="muted">{{ t('system.directSys') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="titleSummary" :label="t('system.titleSummary')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contentSummary" :label="t('system.contentSummary')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="receiverSummary" :label="t('system.recipientMasked')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="receiverCount" :label="t('system.targetCountSys')" width="90" sortable="custom" />
        <el-table-column prop="delivered" :label="t('system.deliverCountSys')" width="90" />
        <el-table-column :label="t('job.result')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" :label="t('system.failSkipReason')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" :class="row.status === 'SKIPPED' ? 'skip-text' : 'error-text'">{{ row.errorMessage }}</span>
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
      <div class="hint">{{ t('system.notifyRecordDesc') }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Download } from '@element-plus/icons-vue'
import { pageNotificationRecords, notificationRecordSummary } from '@/api/notificationRecord'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const summary = ref({})
const dateRange = ref([])
const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  channel: '',
  status: '',
  keyword: '',
  startTime: '',
  endTime: '',
  sortField: '',
  sortDirection: ''
})

/** 渠道展示名与标签色调（与后端 Channel 枚举一一对应）。 */
const CHANNEL_META = {
  IN_APP: { label: t('common.stationMsg'), type: 'primary' },
  WEBHOOK: { label: 'Webhook', type: 'warning' },
  WEBSOCKET: { label: 'WebSocket', type: 'success' },
  EMAIL: { label: t('common.mail'), type: 'info' },
  SMS: { label: t('common.sms'), type: 'danger' },
  WECOM: { label: t('system.wecomBot'), type: 'success' },
  DINGTALK: { label: t('system.dingtalkBot'), type: 'primary' }
}
const channelLabel = (c) => (CHANNEL_META[c] ? CHANNEL_META[c].label : c || '--')
const channelTagType = (c) => (CHANNEL_META[c] ? CHANNEL_META[c].type : 'info')

/** 结果四态（M13 起支持 SKIPPED：定向接收者全部退订，非故障不计入失败；
 *  M17 起支持 DIGEST_PENDING：消息进入聚合摘要队列等待合并投递，属独立生命周期态、不计失败）。 */
const STATUS_META = {
  SUCCESS: { label: t('common.success'), type: 'success' },
  FAILED: { label: t('common.failed'), type: 'danger' },
  SKIPPED: { label: t('system.skippedSys'), type: 'warning' },
  DIGEST_PENDING: { label: t('system.aggregatingSys'), type: 'info' }
}
const statusLabel = (s) => (STATUS_META[s] ? STATUS_META[s].label : s || '--')
const statusTagType = (s) => (STATUS_META[s] ? STATUS_META[s].type : 'info')

/** 统一把时间范围与筛选条件映射为查询参数（空值一律 undefined，不传空串）。 */
function buildParams() {
  const params = {
    page: query.page,
    size: query.size,
    channel: query.channel || undefined,
    status: query.status || undefined,
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
      pageNotificationRecords(buildParams()),
      notificationRecordSummary(buildParams())
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
  query.channel = ''
  query.status = ''
  query.keyword = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  dateRange.value = []
  load()
}

/** 表头排序：走服务端白名单（sentAt/channel/templateCode/receiverCount/status/operatorName），不拼 SQL。 */
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

/** 导出当前页为 CSV：仅导出已加载的明细，不触发后端请求；渠道/结果用可读标签。 */
const exportColumns = [
  { label: t('system.time'), get: (r) => r.sentAt },
  { label: t('common.channel'), get: (r) => channelLabel(r.channel) },
  { label: t('system.sourceTemplate'), get: (r) => r.templateCode },
  { label: t('system.titleSummary'), get: (r) => r.titleSummary },
  { label: t('system.contentSummary'), get: (r) => r.contentSummary },
  { label: t('system.recipientMasked'), get: (r) => r.receiverSummary },
  { label: t('system.targetCountSys'), get: (r) => r.receiverCount },
  { label: t('system.deliverCountSys'), get: (r) => r.delivered },
  { label: t('job.result'), get: (r) => statusLabel(r.status) },
  { label: t('system.failSkipReason'), get: (r) => r.errorMessage },
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
  downloadCsv(`notification-record-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
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
.stat-warning { color: #e6a23c; }
.stat-info { color: #909399; }
.stat-time { font-size: 15px; line-height: 26px; }
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.muted { color: #c0c4cc; }
.error-text { color: #f56c6c; }
.skip-text { color: #e6a23c; }
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
</style>
