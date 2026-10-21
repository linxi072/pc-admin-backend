<template>
  <div>
    <el-empty v-if="denied" description="无「幂等记录读取」权限（system:idempotency:read），请联系管理员授权后重试">
      <el-button type="primary" @click="load">重新加载</el-button>
    </el-empty>

    <template v-else>
      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.total ?? '--' }}</div>
            <div class="stat-label">筛选口径总数</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="summary.processing > 0 ? 'stat-danger' : ''">
              {{ summary.processing ?? 0 }}
            </div>
            <div class="stat-label">PROCESSING（在途残留）</div>
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
            <div class="stat-label">已过期待清理</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtDateTime(summary.lastCreatedAt) }}</div>
            <div class="stat-label">最近登记时间</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <div class="filter-group">
              <span class="filter-label">状态</span>
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
                <el-option label="PROCESSING" value="PROCESSING" />
                <el-option label="SUCCESS" value="SUCCESS" />
                <el-option label="FAILED" value="FAILED" />
              </el-select>
            </div>
            <div class="filter-group">
              <span class="filter-label">接口域</span>
              <el-input v-model="query.apiScope" placeholder="@Idempotent scope" clearable style="width: 160px" />
            </div>
            <div class="filter-group">
              <span class="filter-label">用户 ID</span>
              <el-input v-model="query.userId" placeholder="精确匹配" clearable style="width: 120px" />
            </div>
            <div class="filter-group">
              <span class="filter-label">关键字</span>
              <el-input v-model="query.keyword" placeholder="幂等键/错误信息" clearable style="width: 220px" @keyup.enter="onSearch" />
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
                style="width: 340px"
              />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
              <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
              <el-button type="danger" :icon="Delete" @click="onPurge">清理过期</el-button>
            </div>
          </div>
        </div>

        <el-table :data="rows" v-loading="loading" border stripe>
          <el-table-column prop="idempotencyKey" label="幂等键" min-width="220" show-overflow-tooltip />
          <el-table-column prop="apiScope" label="接口域" width="150" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'FAILED' ? 'danger' : row.status === 'PROCESSING' ? 'warning' : 'success'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="用户" width="90">
            <template #default="{ row }">{{ row.userId ?? '匿名' }}</template>
          </el-table-column>
          <el-table-column prop="errorCode" label="错误码" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorCode || '--' }}</template>
          </el-table-column>
          <el-table-column prop="expiresAt" label="过期时间" width="170">
            <template #default="{ row }">
              <span :class="{ 'expired-flag': row.expired }">{{ fmtDateTime(row.expiresAt) }}</span>
              <el-tag v-if="row.expired" type="danger" size="small" style="margin-left: 4px">已过期</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="登记时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="requestFingerprint" label="请求指纹" width="150" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="失败原因" min-width="180" show-overflow-tooltip>
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
      '确认清理已过期的幂等记录？仅删除 expires_at 已过的记录，与周期清理任务同口径。',
      '清理确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const removed = await purgeIdempotencyRecords()
    ElMessage.success(`已清理 ${removed ?? 0} 条过期记录`)
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
