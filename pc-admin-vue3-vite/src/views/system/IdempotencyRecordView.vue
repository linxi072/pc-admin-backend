<template>
  <div>
    <h2 class="page-title">{{ t('route.idempotency') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.idempotency.status') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 130px">
              <el-option :label="t('system.idempotency.processing')" value="PROCESSING" />
              <el-option :label="t('system.idempotency.success')" value="SUCCESS" />
              <el-option :label="t('system.idempotency.failed')" value="FAILED" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.idempotency.scope') }}</span>
            <el-input v-model="query.apiScope" :placeholder="t('system.idempotency.scope')" clearable style="width: 160px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.idempotency.userId') }}</span>
            <el-input v-model="query.userId" :placeholder="t('system.idempotency.userId')" clearable style="width: 130px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input v-model="query.keyword" :placeholder="t('system.idempotency.key')" clearable style="width: 220px" @keyup.enter="load" />
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
              style="width: 360px"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            <el-button type="warning" :icon="Delete" :loading="purging" @click="onPurge">{{ t('system.idempotency.purge') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.total ?? '--' }}</div>
            <div class="stat-label">{{ t('system.idempotency.total') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-warn">{{ summary.processing ?? '--' }}</div>
            <div class="stat-label">{{ t('system.idempotency.processing') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ summary.success ?? '--' }}</div>
            <div class="stat-label">{{ t('system.idempotency.success') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failed ?? '--' }}</div>
            <div class="stat-label">{{ t('system.idempotency.failed') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.expiredPending ?? '--' }}</div>
            <div class="stat-label">{{ t('system.idempotency.expiredPending') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtPct(summary.processingShare) }}</div>
            <div class="stat-label">{{ t('system.idempotency.processingShare') }}</div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" class="summary-row">
        <el-col :span="24">
          <div class="stat-card stat-wide">
            <div class="stat-num stat-time">{{ fmtTime(summary.lastCreatedAt) }}</div>
            <div class="stat-label">{{ t('system.idempotency.lastCreatedAt') }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="idempotencyKey" :label="t('system.idempotency.key')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="apiScope" :label="t('system.idempotency.scope')" width="150" show-overflow-tooltip />
        <el-table-column prop="userId" :label="t('system.idempotency.userId')" width="100" />
        <el-table-column prop="requestFingerprint" :label="t('system.idempotency.fingerprint')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('system.idempotency.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">
              {{ statusTag(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" :label="t('system.idempotency.errorCode')" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorCode">{{ row.errorCode }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.idempotency.errorMsg')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" :label="t('system.idempotency.expiresAt')" width="180" sortable="custom" />
        <el-table-column prop="createdAt" :label="t('system.idempotency.createdAt')" width="180" sortable="custom" />
        <el-table-column prop="updatedAt" :label="t('system.idempotency.updatedAt')" width="180" sortable="custom" />
        <el-table-column :label="t('system.idempotency.expired')" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.expired" type="danger" size="small">{{ t('system.idempotency.expired') }}</el-tag>
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
      <div class="hint">{{ t('system.idempotency.desc') }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Delete } from '@element-plus/icons-vue'
import { pageIdempotencyRecords, getIdempotencySummary, purgeIdempotencyRecords } from '@/api/idempotencyRecord'

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
  if (status === 'SUCCESS') return { type: 'success', label: t('system.idempotency.success') }
  if (status === 'FAILED') return { type: 'danger', label: t('system.idempotency.failed') }
  if (status === 'PROCESSING') return { type: 'warning', label: t('system.idempotency.processing') }
  return { type: 'info', label: status || '--' }
}

/** 手动清理过期记录：二次确认 + 加载态 + 成功提示，失败由拦截器统一提示。 */
async function onPurge() {
  try {
    await ElMessageBox.confirm(t('system.idempotency.purgeConfirm'), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  purging.value = true
  try {
    const n = await purgeIdempotencyRecords()
    ElMessage.success(t('system.idempotency.purged', { n: n ?? 0 }))
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
