<template>
  <div>
    <h2 class="page-title">{{ 'OAuth2 令牌管理' }}</h2>
    <p class="scope-hint">{{ '本页列出当前有效的 OAuth2 访问令牌（客户端凭证与用户令牌）。吊销后，持有该令牌的客户端在下一次请求时即被拒绝。令牌由授权服务器签发，仅供治理与排障。' }}</p>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ '客户端ID' }}</span>
            <el-input
              v-model="query.clientId"
              :placeholder="'客户端ID'"
              clearable
              style="width: 200px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ '用户ID' }}</span>
            <el-input
              v-model="query.userId"
              :placeholder="'用户ID'"
              clearable
              style="width: 160px"
              @keyup.enter="onSearch"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ '查询' }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ '重置' }}</el-button>
            <el-button :icon="Refresh" :loading="loading" @click="load">{{ '刷新' }}</el-button>
            <el-button :icon="Download" @click="onExportCsv">{{ '导出当前页 CSV' }}</el-button>
          </div>
        </div>
      </div>

      <!-- 活动令牌列表 -->
      <el-table :data="rows" v-loading="loading" border stripe empty-text=" ">
        <el-table-column prop="jti" :label="'令牌标识 (JTI)'" min-width="220" show-overflow-tooltip />
        <el-table-column prop="clientId" :label="'客户端ID'" width="180" show-overflow-tooltip />
        <el-table-column :label="'用户ID'" width="100">
          <template #default="{ row }">
            <span v-if="row.userId">{{ row.userId }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="'用户名'" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.username">{{ row.username }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="'授权范围'" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="s in splitScopes(row.scope)" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="'令牌类型'" width="130">
          <template #default="{ row }">
            <el-tag :type="row.tokenType === 'USER' ? 'warning' : 'info'" size="small">
              {{ row.tokenType === 'USER' ? '用户令牌' : '客户端凭证' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="'签发时间'" width="170">
          <template #default="{ row }">
            <span v-if="row.issuedAt">{{ formatTime(row.issuedAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="'过期时间'" width="170">
          <template #default="{ row }">
            <span v-if="row.expiresAt" :class="{ expired: isExpired(row.expiresAt) }">{{ formatTime(row.expiresAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="'状态'" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '有效' : '已吊销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="'吊销'" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm :title="'确认吊销该令牌？吊销后持有者下一次请求将被拒绝。'" @confirm="onRevoke(row)">
              <template #reference>
                <el-button link type="danger" :icon="CircleClose">{{ '吊销' }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <span class="muted">{{ '当前没有有效的 OAuth2 令牌。' }}</span>
        </template>
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
    </el-card>
  </div>
</template>

<script setup>

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Refresh, CircleClose, Download } from '@element-plus/icons-vue'
import { pageActiveTokens, revokeToken } from '@/api/oauth2Token'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  clientId: '',
  userId: ''
})

function buildParams() {
  const params = { page: query.page, size: query.size }
  if (query.clientId) params.clientId = query.clientId
  // userId 后端按 Long 精确匹配；仅当输入为合法数字时传递，避免误传空串。
  if (query.userId !== '' && /^\d+$/.test(String(query.userId).trim())) {
    params.userId = String(query.userId).trim()
  }
  return params
}

async function load() {
  loading.value = true
  try {
    const page = await pageActiveTokens(buildParams())
    rows.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

function resetQuery() {
  query.clientId = ''
  query.userId = ''
  query.page = 1
  load()
}

const splitScopes = (s) => (s || '').split(',').map((t) => t.trim()).filter(Boolean)

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '--'
}

function isExpired(t) {
  return t ? new Date(t) <= new Date() : false
}

async function onRevoke(row) {
  try {
    await revokeToken(row.jti)
    ElMessage.success(row.jti + ' ' + '吊销已提交，持有该令牌的客户端在下次请求时即被拒绝。')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

// ---------------- 导出当前页 CSV ----------------
const exportColumns = [
  { label: '令牌标识 (JTI)', get: (r) => r.jti },
  { label: '客户端ID', get: (r) => r.clientId },
  { label: '用户ID', get: (r) => r.userId || '--' },
  { label: '用户名', get: (r) => r.username || '--' },
  { label: '授权范围', get: (r) => splitScopes(r.scope).join(' / ') },
  {
    label: '令牌类型',
    get: (r) => (r.tokenType === 'USER' ? '用户令牌' : '客户端凭证')
  },
  { label: '签发时间', get: (r) => formatTime(r.issuedAt) },
  { label: '过期时间', get: (r) => formatTime(r.expiresAt) },
  {
    label: '状态',
    get: (r) => (r.status === 'ACTIVE' ? '有效' : '已吊销')
  }
]

function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning('当前页无数据可导出')
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`token-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(`已导出 ${data.length} 行`)
}

onMounted(load)
</script>

<style scoped>
.scope-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 16px;
  line-height: 1.6;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar-filters {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.muted {
  color: #c0c4cc;
}
.expired {
  color: #f56c6c;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
