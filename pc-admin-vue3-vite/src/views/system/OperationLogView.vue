<template>
  <div>
    <h2 class="page-title">{{ t('route.operationLog') }}<el-tag v-if="summary.sensitiveVisible" type="warning" size="small" effect="plain" class="title-badge">{{ t('system.plaintextVisibleSystemAuditSensitive') }}</el-tag>
      <el-tag v-else type="info" size="small" effect="plain" class="title-badge">{{ t('system.masked') }}</el-tag>
    </h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.module') }}</span>
            <el-input v-model="query.moduleCode" :placeholder="t('system.moduleCode')" clearable style="width: 140px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.operationType') }}</span>
            <el-select v-model="query.operationType" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('common.query')" value="QUERY" />
              <el-option :label="t('common.add')" value="CREATE" />
              <el-option :label="t('system.update')" value="UPDATE" />
              <el-option :label="t('common.delete')" value="DELETE" />
              <el-option :label="t('common.export')" value="EXPORT" />
              <el-option :label="t('route.loginTitle')" value="LOGIN" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.operator') }}</span>
            <el-input v-model="query.operatorName" :placeholder="t('system.usernameName')" clearable style="width: 140px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('job.result') }}</span>
            <el-select v-model="query.success" :placeholder="t('common.all')" clearable style="width: 110px">
              <el-option :label="t('common.success')" :value="true" />
              <el-option :label="t('common.failed')" :value="false" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input v-model="query.keyword" :placeholder="t('system.operationPathSummary')" clearable style="width: 200px" @keyup.enter="onSearch" />
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
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            <el-divider direction="vertical" />
            <el-button
              type="success"
              :icon="Download"
              :loading="exporting === 'CSV'"
              @click="onExport('CSV')"
            >{{ t('report.exportCsv') }}</el-button>
            <el-button
              type="success"
              plain
              :icon="Download"
              :loading="exporting === 'XLSX'"
              @click="onExport('XLSX')"
            >{{ t('system.exportXlsx') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 留存策略提示（随系统配置联动；留存天数 ≤0 表示禁用自动清理） -->
      <div class="retention-hint">
        <el-icon><InfoFilled /></el-icon>
        <span v-if="retention && retention.retentionDays > 0">
          审计日志留存 <b>{{ retention.retentionDays }}</b> 天，到期（{{ retention.cutoff || '—' }} 之前）由系统自动清理；导出为脱敏归档文件，IP 一律掩码。
        </span>
        <span v-else-if="retention">
          审计日志自动清理已禁用（长期保留）；导出为脱敏归档文件，IP 一律掩码。
        </span>
        <span v-else>{{ t('system.loadingRetentionPolicy') }}</span>
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
            <div class="stat-num stat-ok">{{ summary.successCount ?? '--' }}</div>
            <div class="stat-label">{{ t('common.success') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failureCount ?? '--' }}</div>
            <div class="stat-label">{{ t('common.failed') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.slowCount ?? '--' }}</div>
            <div class="stat-label">慢操作(>1s)</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtMs(summary.avgDurationMs) }}</div>
            <div class="stat-label">{{ t('report.avgCost') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtMs(summary.maxDurationMs) }}</div>
            <div class="stat-label">{{ t('report.maxCost') }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="occurredAt" :label="t('system.time')" width="180" sortable="custom" />
        <el-table-column prop="moduleCode" :label="t('system.module')" width="100" sortable="custom" />
        <el-table-column prop="operationType" :label="t('common.type')" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="opTagType(row.operationType)">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationName" :label="t('common.operation')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('system.request')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="method">{{ row.requestMethod }}</span> {{ row.requestPath }}
          </template>
        </el-table-column>
        <el-table-column prop="requestSummary" :label="t('system.requestSummary')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.requestSummary">{{ row.requestSummary }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultSummary" :label="t('system.resultSummary')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.resultSummary">{{ row.resultSummary }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultCode" :label="t('monitor.errorCode')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.resultCode" type="danger" size="small">{{ row.resultCode }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" :label="t('system.operator')" width="110" sortable="custom" />
        <el-table-column prop="ipAddress" :label="t('system.ipMasked')" width="140" show-overflow-tooltip />
        <el-table-column prop="durationMs" :label="t('system.elapsedMs')" width="100" sortable="custom" />
        <el-table-column label="TraceId" width="150">
          <template #default="{ row }">
            <el-link v-if="row.traceId" type="primary" :underline="false" @click="openTrace(row.traceId)">
              {{ row.traceId }}
            </el-link>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">{{ t('common.detail') }}</el-button>
          </template>
        </el-table-column>
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
      <div class="hint">
        摘要字段按权限脱敏（持有 system:audit:sensitive 可见原文，页面标题处徽标显示当前为「明文」或「已脱敏」状态）；IP 一律掩码。点击 TraceId 查看一次请求的完整操作链路，点击「详情」查看单条记录完整内容。
      </div>
    </el-card>

    <!-- TraceId 链路抽屉 -->
    <el-drawer v-model="traceVisible" :title="t('system.traceChain', { id: activeTraceId })" size="480px">
      <el-table :data="traceRows" v-loading="traceLoading" border stripe size="small">
        <el-table-column prop="occurredAt" :label="t('system.time')" width="170" />
        <el-table-column prop="moduleCode" :label="t('system.module')" width="90" />
        <el-table-column prop="operationName" :label="t('common.operation')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="resultCode" :label="t('monitor.errorCode')" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.resultCode" type="danger" size="small">{{ row.resultCode }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 单条操作日志详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="t('system.operationLogDetail')" size="520px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item :label="t('system.time')">{{ detailRow.occurredAt }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.module')">{{ detailRow.moduleCode }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.type')">
          <el-tag size="small" :type="opTagType(detailRow.operationType)">{{ detailRow.operationType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('common.operation')">{{ detailRow.operationName }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.request')">
          <span class="method">{{ detailRow.requestMethod }}</span> {{ detailRow.requestPath }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('system.ipMasked')">{{ detailRow.ipAddress }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.operator')">{{ detailRow.operatorName }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.elapsed')">{{ fmtMs(detailRow.durationMs) }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.errorCode')">
          <el-tag v-if="detailRow.resultCode" type="danger" size="small">{{ detailRow.resultCode }}</el-tag>
          <span v-else class="muted">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="TraceId">{{ detailRow.traceId || '--' }}</el-descriptions-item>
        <el-descriptions-item :label="t('system.requestSummary')">
          <pre class="detail-json">{{ detailRow.requestSummary || '--' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item :label="t('system.resultSummary')">
          <pre class="detail-json">{{ detailRow.resultSummary || '--' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item :label="t('system.maskingStatus')">
          <el-tag :type="summary.sensitiveVisible ? 'warning' : 'info'" size="small">
            {{ summary.sensitiveVisible ? t('system.plaintextVisible') : t('system.masked') }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft, Download, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageAuditLogs, auditLogSummary, auditLogTrace, auditLogRetention } from '@/api/audit'
import { downloadExport } from '@/api/export'
import { saveBlobFromResponse } from '@/utils/download'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const summary = ref({})
const dateRange = ref([])
const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  moduleCode: '',
  operationType: '',
  operatorName: '',
  success: '',
  keyword: '',
  traceId: '',
  startTime: '',
  endTime: '',
  sortField: '',
  sortDirection: ''
})

const traceVisible = ref(false)
const traceLoading = ref(false)
const traceRows = ref([])
const activeTraceId = ref('')

const detailVisible = ref(false)
const detailRow = ref(null)

// 留存策略（页头展示）与导出态
const retention = ref(null)
const exporting = ref(null)

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function buildParams() {
  const params = {
    page: query.page,
    size: query.size,
    moduleCode: query.moduleCode || undefined,
    operationType: query.operationType || undefined,
    operatorName: query.operatorName || undefined,
    success: query.success === '' ? undefined : query.success,
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
      pageAuditLogs(buildParams()),
      auditLogSummary(buildParams())
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
  query.moduleCode = ''
  query.operationType = ''
  query.operatorName = ''
  query.success = ''
  query.keyword = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  dateRange.value = []
  load()
}

/** 表头排序：走服务端白名单（occurredAt/durationMs/moduleCode/operatorName），不拼 SQL。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

const opTagType = (t) =>
  ({ CREATE: 'success', UPDATE: 'warning', DELETE: 'danger', EXPORT: 'info' })[t] || ''

async function openTrace(traceId) {
  activeTraceId.value = traceId
  traceVisible.value = true
  traceLoading.value = true
  try {
    traceRows.value = (await auditLogTrace(traceId)) || []
  } finally {
    traceLoading.value = false
  }
}

const fmtMs = (v) => (v === null || v === undefined ? '--' : `${v} ms`)

/** 加载留存策略（仅读取配置，与筛选无关）。 */
async function loadRetention() {
  try {
    retention.value = await auditLogRetention()
  } catch (e) {
    // 响应拦截器已弹错误提示
  }
}

/**
 * 按当前筛选条件导出审计日志（复用导出中心 audit-logs 模板，脱敏归档）。
 * 仅透传后端 AuditLogExportTask 实际消费的筛选参数；导出文件即便持敏感权限也一律脱敏。
 */
async function onExport(format = 'CSV') {
  exporting.value = format
  try {
    const params = {
      code: 'audit-logs',
      format,
      moduleCode: query.moduleCode || undefined,
      operationType: query.operationType || undefined,
      operatorName: query.operatorName || undefined,
      keyword: query.keyword || undefined
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const resp = await downloadExport(params)
    const ok = await saveBlobFromResponse(
      resp,
      'audit-log',
      format === 'XLSX' ? 'xlsx' : 'csv',
      t('system.auditLogExportFailed')
    )
    if (ok) ElMessage.success(t('system.exported'))
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    exporting.value = null
  }
}

onMounted(() => {
  load()
  loadRetention()
})
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
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.method {
  font-weight: 600;
  color: var(--brand);
}
.muted { color: #c0c4cc; }
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
.retention-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  margin-top: 10px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 6px;
}
.retention-hint .el-icon {
  color: var(--brand, #409eff);
}
.retention-hint b {
  color: var(--text);
}
.title-badge {
  margin-left: 10px;
  vertical-align: middle;
}
.detail-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
}
</style>
