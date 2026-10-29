<template>
  <div>
    <h2 class="page-title">{{ $t('route.oauth2Token') }}</h2>
    <p class="scope-hint">{{ $t('oauth2Token.scope') }}</p>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ $t('oauth2Token.clientId') }}</span>
            <el-input
              v-model="query.clientId"
              :placeholder="$t('oauth2Token.clientId')"
              clearable
              style="width: 200px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ $t('oauth2Token.userId') }}</span>
            <el-input
              v-model="query.userId"
              :placeholder="$t('oauth2Token.userId')"
              clearable
              style="width: 160px"
              @keyup.enter="onSearch"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ $t('oauth2Token.reset') }}</el-button>
            <el-button :icon="Refresh" :loading="loading" @click="load">{{ $t('oauth2Token.refresh') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 活动令牌列表 -->
      <el-table :data="rows" v-loading="loading" border stripe empty-text=" ">
        <el-table-column prop="jti" :label="$t('oauth2Token.jti')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="clientId" :label="$t('oauth2Token.clientId')" width="180" show-overflow-tooltip />
        <el-table-column :label="$t('oauth2Token.userId')" width="100">
          <template #default="{ row }">
            <span v-if="row.userId">{{ row.userId }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="$t('oauth2Token.username')" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.username">{{ row.username }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('oauth2Token.scopes')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="s in splitScopes(row.scope)" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('oauth2Token.type')" width="130">
          <template #default="{ row }">
            <el-tag :type="row.tokenType === 'USER' ? 'warning' : 'info'" size="small">
              {{ row.tokenType === 'USER' ? $t('oauth2Token.typeUser') : $t('oauth2Token.typeClient') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('oauth2Token.issuedAt')" width="170">
          <template #default="{ row }">
            <span v-if="row.issuedAt">{{ formatTime(row.issuedAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('oauth2Token.expiresAt')" width="170">
          <template #default="{ row }">
            <span v-if="row.expiresAt" :class="{ expired: isExpired(row.expiresAt) }">{{ formatTime(row.expiresAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('oauth2Token.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? $t('oauth2Token.active') : $t('oauth2Token.revoked') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('oauth2Token.revoke')" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm :title="$t('oauth2Token.revokeConfirm')" @confirm="onRevoke(row)">
              <template #reference>
                <el-button link type="danger" :icon="CircleClose">{{ $t('oauth2Token.revoke') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <span class="muted">{{ $t('oauth2Token.empty') }}</span>
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
import { Search, RefreshLeft, Refresh, CircleClose } from '@element-plus/icons-vue'
import { pageActiveTokens, revokeToken } from '@/api/oauth2token'

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
    ElMessage.success(row.jti + ' ' + $t('oauth2Token.revokedHint'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
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
