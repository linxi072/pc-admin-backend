<template>
  <div>
    <h2 class="page-title">开放平台客户端</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">客户端ID</span>
            <el-input
              v-model="query.clientId"
              placeholder="模糊匹配"
              clearable
              style="width: 200px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
            <el-button type="success" :icon="Plus" @click="openCreate">注册客户端</el-button>
            <el-button :icon="Key" @click="openTokenTest()">测试令牌签发</el-button>
          </div>
        </div>
      </div>

      <!-- 客户端列表 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="clientId" label="客户端ID" width="200" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="clientName" label="名称" width="180" show-overflow-tooltip />
        <el-table-column prop="scopes" label="授权范围" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="s in splitScopes(row.scopes)" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="密钥轮换时间" width="170">
          <template #default="{ row }">
            <span v-if="row.secretRotatedAt">{{ formatTime(row.secretRotatedAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="170">
          <template #default="{ row }">
            <span v-if="row.expiredAt" :class="{ expired: isExpired(row.expiredAt) }">{{ formatTime(row.expiredAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Refresh" @click="onRotate(row)">轮换密钥</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该客户端？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
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
        客户端密钥仅以哈希落库，<b>明文仅在注册/轮换成功时展示一次</b>，请立即保存；丢失只能轮换（旧密钥立即失效）。
        协议级令牌签发已由独立端点 <code>POST /oauth2/token</code>（client_credentials + RS256）提供，可点「测试令牌签发」自测。
      </div>
    </el-card>

    <!-- 注册弹窗 -->
    <el-dialog v-model="createVisible" title="注册客户端" width="560px" @closed="resetCreateForm">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="92px">
        <el-form-item label="客户端ID" prop="clientId">
          <el-input v-model="createForm.clientId" placeholder="字母/数字开头，8~64 位，可含中划线/下划线" />
        </el-form-item>
        <el-form-item label="客户端名称" prop="clientName">
          <el-input v-model="createForm.clientName" placeholder="如 合作伙伴订单系统" />
        </el-form-item>
        <el-form-item label="授权范围" prop="scopes">
          <el-input v-model="createForm.scopes" placeholder="逗号分隔，如 read,write；留空默认 read" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="createForm.expiredAt"
            type="datetime"
            placeholder="留空为长期有效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="onCreate">注册</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑客户端" width="560px" @closed="resetEditForm">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="92px">
        <el-form-item label="客户端ID">
          <el-input :model-value="editingClientId" disabled />
        </el-form-item>
        <el-form-item label="客户端名称" prop="clientName">
          <el-input v-model="editForm.clientName" />
        </el-form-item>
        <el-form-item label="授权范围" prop="scopes">
          <el-input v-model="editForm.scopes" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="editForm.expiredAt"
            type="datetime"
            placeholder="留空为长期有效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 一次性密钥弹窗（注册/轮换共用） -->
    <el-dialog v-model="secretVisible" :title="secretTitle" width="560px" @closed="closeSecret">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="明文密钥仅此一次展示，关闭后无法再次查看；请立即复制并妥善保存。"
      />
      <div class="secret-box">
        <span class="secret-text">{{ secretValue }}</span>
        <el-button :icon="CopyDocument" @click="onCopySecret">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- 令牌签发自测弹窗（协议级 OAuth2 client_credentials 验证） -->
    <el-dialog v-model="tokenTestVisible" title="令牌签发自测 (client_credentials)" width="600px" @closed="resetTokenTest">
      <el-form :model="tokenForm" label-width="92px">
        <el-form-item label="客户端ID">
          <el-input v-model="tokenForm.clientId" placeholder="与列表中的客户端ID一致" />
        </el-form-item>
        <el-form-item label="客户端密钥">
          <el-input v-model="tokenForm.clientSecret" type="password" show-password placeholder="注册/轮换时展示的明文密钥" />
        </el-form-item>
      </el-form>
      <el-alert v-if="tokenResult" type="success" :closable="false" show-icon title="令牌签发成功">
        <div class="token-result">
          <div class="token-row">
            <span class="token-label">access_token</span>
            <el-input :model-value="tokenResult.access_token" readonly type="textarea" :rows="3" class="token-value" />
          </div>
          <div class="token-row">
            <span class="token-label">token_type</span>
            <span class="token-text">{{ tokenResult.token_type }}</span>
          </div>
          <div class="token-row">
            <span class="token-label">expires_in</span>
            <span class="token-text">{{ tokenResult.expires_in }} 秒</span>
          </div>
          <div class="token-row">
            <span class="token-label">scope</span>
            <span class="token-text">{{ tokenResult.scope || '（默认）' }}</span>
          </div>
        </div>
      </el-alert>
      <template #footer>
        <el-button @click="tokenTestVisible = false">关闭</el-button>
        <el-button type="primary" :loading="tokenTesting" @click="onIssueToken">签发令牌</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Edit, Search, RefreshLeft, Refresh, CopyDocument, Key } from '@element-plus/icons-vue'
import {
  pageOpenApiClients,
  createOpenApiClient,
  updateOpenApiClient,
  deleteOpenApiClient,
  rotateOpenApiClientSecret,
  issueClientToken
} from '@/api/openApiClient'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  clientId: '',
  status: '',
  sortField: '',
  sortDirection: ''
})

function buildParams() {
  return {
    page: query.page,
    size: query.size,
    clientId: query.clientId || undefined,
    status: query.status || undefined,
    sortField: query.sortField || undefined,
    sortDirection: query.sortDirection || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const page = await pageOpenApiClients(buildParams())
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
  query.status = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  load()
}

/** 服务端排序白名单：clientId/status/secretRotatedAt/expiredAt/createdAt。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

const splitScopes = (s) => (s || '').split(',').map((t) => t.trim()).filter(Boolean)

/** 本地时间展示（与后端 LocalDateTime 序列化对齐，无时区转换）。 */
function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '--'
}

function isExpired(t) {
  return t ? new Date(t) <= new Date() : false
}

// ---- 注册 ----
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = reactive({ clientId: '', clientName: '', scopes: '', expiredAt: '', remark: '' })
const createRules = {
  clientId: [
    { required: true, message: '请输入客户端ID', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9][a-zA-Z0-9_-]{7,63}$/, message: '字母/数字开头，8~64 位，可含中划线/下划线', trigger: 'blur' }
  ],
  clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }],
  scopes: [
    {
      validator: (rule, value, callback) => {
        const scopes = splitScopes(value)
        if (scopes.length === 0) return callback() // 留空走默认 read
        if (scopes.length > 10) return callback(new Error('授权 scope 不超过 10 个'))
        for (const s of scopes) {
          if (!/^[a-z][a-z0-9:_-]{0,31}$/.test(s)) {
            return callback(new Error(`scope 非法：${s}（小写字母开头）`))
          }
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  createVisible.value = true
}

function resetCreateForm() {
  Object.assign(createForm, { clientId: '', clientName: '', scopes: '', expiredAt: '', remark: '' })
  createFormRef.value?.clearValidate()
}

async function onCreate() {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await createOpenApiClient({
      clientId: createForm.clientId,
      clientName: createForm.clientName,
      scopes: createForm.scopes || undefined,
      expiredAt: createForm.expiredAt || undefined,
      remark: createForm.remark || undefined
    })
    createVisible.value = false
    showSecret('注册成功', res)
    load()
  } finally {
    creating.value = false
  }
}

// ---- 编辑 ----
const editVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const editingClientId = ref('')
const editFormRef = ref()
const editForm = reactive({ clientName: '', scopes: '', status: 'ENABLED', expiredAt: '', remark: '' })
const editRules = {
  clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }],
  scopes: [createRules.scopes[0]],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function openEdit(row) {
  editingId.value = row.id
  editingClientId.value = row.clientId
  Object.assign(editForm, {
    clientName: row.clientName,
    scopes: row.scopes || '',
    status: row.status || 'ENABLED',
    expiredAt: row.expiredAt || '',
    remark: row.remark || ''
  })
  editVisible.value = true
}

function resetEditForm() {
  editingId.value = null
  editingClientId.value = ''
  editFormRef.value?.clearValidate()
}

async function onSave() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await updateOpenApiClient(editingId.value, {
      clientName: editForm.clientName,
      scopes: editForm.scopes || undefined,
      status: editForm.status,
      expiredAt: editForm.expiredAt || undefined,
      remark: editForm.remark || undefined
    })
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- 删除 / 轮换 ----
async function onDelete(row) {
  try {
    await deleteOpenApiClient(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onRotate(row) {
  try {
    const res = await rotateOpenApiClientSecret(row.id)
    showSecret('密钥已轮换', res)
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

// ---- 一次性密钥展示 ----
const secretVisible = ref(false)
const secretTitle = ref('')
const secretValue = ref('')

function showSecret(title, res) {
  secretTitle.value = title
  secretValue.value = res?.plainSecret || ''
  secretVisible.value = true
}

function closeSecret() {
  secretValue.value = ''
}

async function onCopySecret() {
  try {
    await navigator.clipboard.writeText(secretValue.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

// ---- 令牌签发自测（协议级 OAuth2 client_credentials） ----
const tokenTestVisible = ref(false)
const tokenTesting = ref(false)
const tokenForm = reactive({ clientId: '', clientSecret: '' })
const tokenResult = ref(null)

function openTokenTest(row) {
  tokenForm.clientId = row?.clientId || ''
  tokenForm.clientSecret = ''
  tokenResult.value = null
  tokenTestVisible.value = true
}

function resetTokenTest() {
  tokenForm.clientId = ''
  tokenForm.clientSecret = ''
  tokenResult.value = null
}

async function onIssueToken() {
  if (!tokenForm.clientId || !tokenForm.clientSecret) {
    ElMessage.warning('请填写客户端ID与密钥')
    return
  }
  tokenTesting.value = true
  tokenResult.value = null
  try {
    // issueClientToken 经 requestRaw 返回原始 OAuth2 响应包络 { access_token, token_type, expires_in, scope }
    const resp = await issueClientToken(tokenForm.clientId, tokenForm.clientSecret)
    tokenResult.value = resp.data
    ElMessage.success('令牌签发成功')
  } catch (e) {
    /* handleHttpError 已统一提示（如「客户端认证失败」） */
  } finally {
    tokenTesting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: #c0c4cc; }
.expired { color: #f56c6c; }
.secret-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 12px;
  background: #f4f4f5;
  border-radius: 6px;
}
.secret-text {
  flex: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #303133;
  word-break: break-all;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
.hint code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: #f4f4f5;
  color: #409eff;
  padding: 1px 5px;
  border-radius: 3px;
}
.token-result {
  margin-top: 4px;
}
.token-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 6px 0;
}
.token-label {
  flex: 0 0 96px;
  font-weight: 600;
  color: #606266;
  font-size: 13px;
  line-height: 28px;
}
.token-value {
  flex: 1;
}
.token-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #303133;
}
</style>
