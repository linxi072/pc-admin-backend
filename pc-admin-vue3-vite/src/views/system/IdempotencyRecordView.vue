<template>
  <div>
    <h2 class="page-title">{{ '幂等记录' }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ '状态' }}</span>
            <el-select v-model="query.status" :placeholder="'全部'" clearable style="width: 130px">
              <el-option :label="'处理中'" value="PROCESSING" />
              <el-option :label="'成功'" value="SUCCESS" />
              <el-option :label="'失败'" value="FAILED" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '接口域' }}</span>
            <el-input v-model="query.apiScope" :placeholder="'接口域'" clearable style="width: 160px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '用户ID' }}</span>
            <el-input v-model="query.userId" :placeholder="'用户ID'" clearable style="width: 130px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '关键字' }}</span>
            <el-input v-model="query.keyword" :placeholder="'幂等键'" clearable style="width: 220px" @keyup.enter="load" />
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
              style="width: 360px"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ '查询' }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ '重置' }}</el-button>
            <el-button :icon="Download" @click="onExportCsv">{{ '导出当前页 CSV' }}</el-button>
            <el-button type="warning" :icon="Delete" :loading="purging" @click="onPurge">{{ '清理过期记录' }}</el-button>
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
            <div class="stat-num stat-warn">{{ summary.processing ?? '--' }}</div>
            <div class="stat-label">{{ '处理中' }}</div>
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
            <div class="stat-num stat-danger">{{ summary.failed ?? '--' }}</div>
            <div class="stat-label">{{ '失败' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.expiredPending ?? '--' }}</div>
            <div class="stat-label">{{ '待清理(已过期)' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtPct(summary.processingShare) }}</div>
            <div class="stat-label">{{ '处理中占比' }}</div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" class="summary-row">
        <el-col :span="24">
          <div class="stat-card stat-wide">
            <div class="stat-num stat-time">{{ fmtTime(summary.lastCreatedAt) }}</div>
            <div class="stat-label">{{ '最近登记' }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="idempotencyKey" :label="'幂等键'" min-width="220" show-overflow-tooltip />
        <el-table-column prop="apiScope" :label="'接口域'" width="150" show-overflow-tooltip />
        <el-table-column prop="userId" :label="'用户ID'" width="100" />
        <el-table-column prop="requestFingerprint" :label="'请求指纹'" min-width="160" show-overflow-tooltip />
        <el-table-column :label="'状态'" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">
              {{ statusTag(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" :label="'错误码'" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorCode">{{ row.errorCode }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="'失败原因'" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" :label="'过期时间'" width="180" sortable="custom" />
        <el-table-column prop="createdAt" :label="'登记时间'" width="180" sortable="custom" />
        <el-table-column prop="updatedAt" :label="'更新时间'" width="180" sortable="custom" />
        <el-table-column :label="'已过期'" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.expired" type="danger" size="small">{{ '已过期' }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
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
      <div class="hint">{{ '幂等记录用于防止重复提交：同一幂等键在有效期内仅首次生效。PROCESSING 多为客户端中断的残留；「待清理」持续增长说明清理任务未运行。' }}</div>
    </el-card>
  </div>
</template>

<script setup>

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Delete, Download } from '@element-plus/icons-vue'
import { pageIdempotencyRecords, getIdempotencySummary, purgeIdempotencyRecords } from '@/api/idempotencyRecord'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const purging = ref(false)
const rows = ref([])
const total = ref(0)
const summary = ref({})
const dateRange = ref([])
const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  status: '',
  apiScope: '',
  userId: '',
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
    apiScope: query.apiScope || undefined,
    userId: query.userId || undefined,
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
      pageIdempotencyRecords(buildParams()),
      getIdempotencySummary(buildParams())
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
  query.apiScope = ''
  query.userId = ''
  query.keyword = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  dateRange.value = []
  load()
}

/** 表头排序：透传后端白名单字段（createdAt/updatedAt/expiresAt/status）。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

/** 状态标签：PROCESSING→warning / SUCCESS→success / FAILED→danger。 */
function statusTag(status) {
  if (status === 'SUCCESS') return { type: 'success', label: '成功' }
  if (status === 'FAILED') return { type: 'danger', label: '失败' }
  if (status === 'PROCESSING') return { type: 'warning', label: '处理中' }
  return { type: 'info', label: status || '--' }
}

/** 手动清理过期记录：二次确认 + 加载态 + 成功提示，失败由拦截器统一提示。 */
async function onPurge() {
  try {
    await ElMessageBox.confirm('确认清理全部已过期幂等记录？该操作不可撤销，仅移除 expires_at 早于当前的记录。', '提示', { type: 'warning' })
  } catch {
    return
  }
  purging.value = true
  try {
    const n = await purgeIdempotencyRecords()
    ElMessage.success(`已清理 ${n ?? 0} 条过期幂等记录`)
    load()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    purging.value = false
  }
}

const fmtPct = (v) => {
  if (v === null || v === undefined) return '--'
  return `${(Number(v) * 100).toFixed(2)}%`
}
const fmtTime = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '--')

/** 导出当前页为 CSV：仅导出已加载的明细，不触发后端请求。 */
const exportColumns = [
  { label: '幂等键', get: (r) => r.idempotencyKey },
  { label: '接口域', get: (r) => r.apiScope },
  { label: '用户ID', get: (r) => r.userId },
  { label: '请求指纹', get: (r) => r.requestFingerprint },
  { label: '状态', get: (r) => r.status },
  { label: '错误码', get: (r) => r.errorCode },
  { label: '失败原因', get: (r) => r.errorMessage },
  { label: '过期时间', get: (r) => r.expiresAt },
  { label: '登记时间', get: (r) => r.createdAt },
  { label: '更新时间', get: (r) => r.updatedAt },
  { label: '已过期', get: (r) => r.expired }
]
function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning('当前页无数据可导出')
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`idempotency-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(`已导出 ${data.length} 行`)
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
.stat-wide {
  text-align: left;
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
}
.stat-ok { color: #67c23a; }
.stat-danger { color: #f56c6c; }
.stat-warn { color: #e6a23c; }
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
