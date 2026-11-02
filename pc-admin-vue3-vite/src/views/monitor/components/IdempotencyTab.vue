<template>
  <div>
    <el-empty v-if="denied" :description="t('monitor.noIdemPerm')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.total ?? '--' }}</div>
            <div class="stat-label">{{ t('monitor.filterTotal') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="summary.processing > 0 ? 'stat-danger' : ''">
              {{ summary.processing ?? 0 }}
            </div>
            <div class="stat-label">{{ t('monitor.processing') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ summary.success ?? 0 }}</div>
            <div class="stat-label">SUCCESS</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failed ?? 0 }}</div>
            <div class="stat-label">FAILED</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="summary.expiredPending > 0 ? 'stat-danger' : ''">
              {{ summary.expiredPending ?? 0 }}
            </div>
            <div class="stat-label">{{ t('monitor.expiredToClean') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtDateTime(summary.lastCreatedAt) }}</div>
            <div class="stat-label">{{ t('monitor.lastRegister') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <div class="filter-group">
              <span class="filter-label">{{ t('common.status') }}</span>
              <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 150px">
                <el-option label="PROCESSING" value="PROCESSING" />
                <el-option label="SUCCESS" value="SUCCESS" />
                <el-option label="FAILED" value="FAILED" />
              </el-select>
            </div>
            <div class="filter-group">
              <span class="filter-label">{{ t('monitor.interfaceDomain') }}</span>
              <el-input v-model="query.apiScope" placeholder="@Idempotent scope" clearable style="width: 160px" />
            </div>
            <div class="filter-group">
              <span class="filter-label">{{ t('monitor.userId') }}</span>
              <el-input v-model="query.userId" :placeholder="t('monitor.exactMatch')" clearable style="width: 120px" />
            </div>
            <div class="filter-group">
              <span class="filter-label">{{ t('common.keyword') }}</span>
              <el-input v-model="query.keyword" :placeholder="t('monitor.idempotencyKeyErr')" clearable style="width: 220px" @keyup.enter="onSearch" />
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
                style="width: 340px"
              />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
              <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
              <el-button type="danger" :icon="Delete" @click="onPurge">{{ t('monitor.cleanExpired') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="rows" v-loading="loading" border stripe>
          <el-table-column prop="idempotencyKey" :label="t('monitor.idempotencyKey')" min-width="220" show-overflow-tooltip />
          <el-table-column prop="apiScope" :label="t('monitor.interfaceDomain')" width="150" show-overflow-tooltip />
          <el-table-column prop="status" :label="t('common.status')" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'FAILED' ? 'danger' : row.status === 'PROCESSING' ? 'warning' : 'success'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="userId" :label="t('monitor.user')" width="90">
            <template #default="{ row }">{{ row.userId ?? t('monitor.anonymous') }}</template>
          </el-table-column>
          <el-table-column prop="errorCode" :label="t('monitor.errorCode')" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorCode || '--' }}</template>
          </el-table-column>
          <el-table-column prop="expiresAt" :label="t('monitor.expireTime')" width="170">
            <template #default="{ row }">
              <span :class="{ 'expired-flag': row.expired }">{{ fmtDateTime(row.expiresAt) }}</span>
              <el-tag v-if="row.expired" type="danger" size="small" style="margin-left: 4px">{{ t('monitor.expired') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" :label="t('monitor.registerTime')" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="requestFingerprint" :label="t('monitor.requestFingerprint')" width="150" show-overflow-tooltip />
          <el-table-column prop="errorMessage" :label="t('monitor.failReason')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
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
        <div class="hint">
          response_snapshot（完整响应体）契约上不暴露、不可检索——重放由幂等切面自动完成。
          PROCESSING 长期残留多为「客户端收到响应前断开」；「已过期待清理」持续增长说明清理任务未运行。
          清理按 expires_at 过期删除，与周期任务同口径（需 system:idempotency:manage）。
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Delete } from '@element-plus/icons-vue'
import { pageIdempotencyRecords, getIdempotencySummary, purgeIdempotencyRecords } from '@/api/idempotencyRecord'
import { fmtDateTime, isForbidden } from './format'

const rows = ref([])
const summary = ref({})
const total = ref(0)
const loading = ref(false)
const denied = ref(false)
const dateRange = ref([])
const query = reactive({
  page: 1,
  size: 10,
  status: '',
  apiScope: '',
  userId: '',
  keyword: ''
})

/** 空值一律 undefined，不传空串（与 SmsLogView 同口径，避免后端把空串当筛选值）。 */
function buildParams() {
  const params = {
    page: query.page,
    size: query.size,
    status: query.status || undefined,
    apiScope: query.apiScope || undefined,
    userId: query.userId || undefined,
    keyword: query.keyword || undefined
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
    rows.value = page?.records || []
    total.value = page?.total || 0
    summary.value = sum || {}
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onSizeChange(sz) {
  query.size = sz
  query.page = 1
  load()
}

function resetQuery() {
  query.status = ''
  query.apiScope = ''
  query.userId = ''
  query.keyword = ''
  dateRange.value = []
  query.page = 1
  load()
}

async function onPurge() {
  try {
    await ElMessageBox.confirm(
      t('monitor.confirmCleanIdem'),
      t('monitor.cleanConfirm'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const removed = await purgeIdempotencyRecords()
    ElMessage.success(t('monitor.cleanedExpired', { removed: removed ?? 0 }))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.error-text {
  color: #f56c6c;
}
.muted {
  color: #a8abb2;
}
.expired-flag {
  color: #f56c6c;
  font-weight: 600;
}
</style>
