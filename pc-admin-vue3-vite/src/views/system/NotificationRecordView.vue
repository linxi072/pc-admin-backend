<template>
  <div>
    <h2 class="page-title">通知记录</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">渠道</span>
            <el-select v-model="query.channel" placeholder="全部" clearable style="width: 140px">
              <el-option label="站内信" value="IN_APP" />
              <el-option label="Webhook" value="WEBHOOK" />
              <el-option label="WebSocket" value="WEBSOCKET" />
              <el-option label="邮件" value="EMAIL" />
              <el-option label="短信" value="SMS" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">结果</span>
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
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
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input v-model="query.keyword" placeholder="渠道/模板/摘要/失败原因/traceId/操作人" clearable style="width: 260px" @keyup.enter="load" />
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
            <div class="stat-num stat-ok">{{ summary.success ?? '--' }}</div>
            <div class="stat-label">成功</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.failure ?? '--' }}</div>
            <div class="stat-label">失败</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ fmtPct(summary.failureRate) }}</div>
            <div class="stat-label">失败率</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtTime(summary.lastSentAt) }}</div>
            <div class="stat-label">最近发送</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="sentAt" label="时间" width="180" sortable="custom" />
        <el-table-column label="渠道" width="110">
          <template #default="{ row }">
            <el-tag :type="channelTagType(row.channel)" size="small" effect="light">
              {{ channelLabel(row.channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="templateCode" label="来源模板" width="150" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.templateCode">{{ row.templateCode }}</span>
            <span v-else class="muted">直发</span>
          </template>
        </el-table-column>
        <el-table-column prop="titleSummary" label="标题摘要" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contentSummary" label="内容摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="receiverSummary" label="接收方（掩码）" min-width="200" show-overflow-tooltip />
        <el-table-column prop="receiverCount" label="目标数" width="90" sortable="custom" />
        <el-table-column prop="delivered" label="投递数" width="90" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="120" sortable="custom">
          <template #default="{ row }">
            <span v-if="row.operatorName">{{ row.operatorName }}</span>
            <span v-else class="muted">系统</span>
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
      <div class="hint">默认查询最近 7 天记录；覆盖 IN_APP/WEBHOOK/WEBSOCKET/EMAIL/SMS 全渠道的每次投递尝试（含被拒与失败），标题/正文为摘要、接收方已掩码；短信网关对账请到「短信记录」页；「来源模板」为 M11 按模板发送回填，直发为空。</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { pageNotificationRecords, notificationRecordSummary } from '@/api/notificationRecord'

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
  IN_APP: { label: '站内信', type: 'primary' },
  WEBHOOK: { label: 'Webhook', type: 'warning' },
  WEBSOCKET: { label: 'WebSocket', type: 'success' },
  EMAIL: { label: '邮件', type: 'info' },
  SMS: { label: '短信', type: 'danger' }
}
const channelLabel = (c) => (CHANNEL_META[c] ? CHANNEL_META[c].label : c || '--')
const channelTagType = (c) => (CHANNEL_META[c] ? CHANNEL_META[c].type : 'info')

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
