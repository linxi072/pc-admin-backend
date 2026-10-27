<template>
  <div>
    <h2 class="page-title">
      操作日志
      <el-tag v-if="summary.sensitiveVisible" type="warning" size="small" effect="plain" class="title-badge">明文可见 · system:audit:sensitive</el-tag>
      <el-tag v-else type="info" size="small" effect="plain" class="title-badge">已脱敏</el-tag>
    </h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">模块</span>
            <el-input v-model="query.moduleCode" placeholder="模块编码" clearable style="width: 140px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">操作类型</span>
            <el-select v-model="query.operationType" placeholder="全部" clearable style="width: 120px">
              <el-option label="查询" value="QUERY" />
              <el-option label="新增" value="CREATE" />
              <el-option label="更新" value="UPDATE" />
              <el-option label="删除" value="DELETE" />
              <el-option label="导出" value="EXPORT" />
              <el-option label="登录" value="LOGIN" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">操作人</span>
            <el-input v-model="query.operatorName" placeholder="用户名/姓名" clearable style="width: 140px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">结果</span>
            <el-select v-model="query.success" placeholder="全部" clearable style="width: 110px">
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input v-model="query.keyword" placeholder="操作名/请求路径/摘要" clearable style="width: 200px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">时间范围</span>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
              style="width: 360px"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
          </div>
        </div>
      </div>

      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.total ?? '--' }}</div>
            <div class="stat-label">总记录</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ summary.successCount ?? '--' }}</div>
            <div class="stat-label">成功</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failureCount ?? '--' }}</div>
            <div class="stat-label">失败</div>
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
            <div class="stat-label">平均耗时</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtMs(summary.maxDurationMs) }}</div>
            <div class="stat-label">最大耗时</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="occurredAt" label="时间" width="180" sortable="custom" />
        <el-table-column prop="moduleCode" label="模块" width="100" sortable="custom" />
        <el-table-column prop="operationType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="opTagType(row.operationType)">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationName" label="操作" min-width="150" show-overflow-tooltip />
        <el-table-column label="请求" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="method">{{ row.requestMethod }}</span> {{ row.requestPath }}
          </template>
        </el-table-column>
        <el-table-column prop="requestSummary" label="请求摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.requestSummary">{{ row.requestSummary }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultSummary" label="结果摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.resultSummary">{{ row.resultSummary }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultCode" label="错误码" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.resultCode" type="danger" size="small">{{ row.resultCode }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" sortable="custom" />
        <el-table-column prop="ipAddress" label="IP(已掩码)" width="140" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" sortable="custom" />
        <el-table-column label="TraceId" width="150">
          <template #default="{ row }">
            <el-link v-if="row.traceId" type="primary" :underline="false" @click="openTrace(row.traceId)">
              {{ row.traceId }}
            </el-link>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
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
    <el-drawer v-model="traceVisible" :title="`操作链路 · ${activeTraceId}`" size="480px">
      <el-table :data="traceRows" v-loading="traceLoading" border stripe size="small">
        <el-table-column prop="occurredAt" label="时间" width="170" />
        <el-table-column prop="moduleCode" label="模块" width="90" />
        <el-table-column prop="operationName" label="操作" min-width="140" show-overflow-tooltip />
        <el-table-column prop="resultCode" label="错误码" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.resultCode" type="danger" size="small">{{ row.resultCode }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 单条操作日志详情抽屉 -->
    <el-drawer v-model="detailVisible" title="操作日志详情" size="520px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="时间">{{ detailRow.occurredAt }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detailRow.moduleCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag size="small" :type="opTagType(detailRow.operationType)">{{ detailRow.operationType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作">{{ detailRow.operationName }}</el-descriptions-item>
        <el-descriptions-item label="请求">
          <span class="method">{{ detailRow.requestMethod }}</span> {{ detailRow.requestPath }}
        </el-descriptions-item>
        <el-descriptions-item label="IP(已掩码)">{{ detailRow.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailRow.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ fmtMs(detailRow.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="错误码">
          <el-tag v-if="detailRow.resultCode" type="danger" size="small">{{ detailRow.resultCode }}</el-tag>
          <span v-else class="muted">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="TraceId">{{ detailRow.traceId || '--' }}</el-descriptions-item>
        <el-descriptions-item label="请求摘要">
          <pre class="detail-json">{{ detailRow.requestSummary || '--' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="结果摘要">
          <pre class="detail-json">{{ detailRow.resultSummary || '--' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="脱敏状态">
          <el-tag :type="summary.sensitiveVisible ? 'warning' : 'info'" size="small">
            {{ summary.sensitiveVisible ? '明文可见' : '已脱敏' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { pageAuditLogs, auditLogSummary, auditLogTrace } from '@/api/audit'

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
  color: #303133;
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
