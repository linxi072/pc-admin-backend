<template>
  <div>
    <h2 class="page-title">{{ t('route.openApiClient') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('oauth2Token.clientId') }}</span>
            <el-input
              v-model="query.clientId"
              :placeholder="t('system.fuzzyMatch')"
              clearable
              style="width: 200px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.status') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('common.enabled')" value="ENABLED" />
              <el-option :label="t('system.disable')" value="DISABLED" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            <el-button type="success" :icon="Plus" @click="openCreate">{{ t('system.registerClient') }}</el-button>
            <el-button :icon="Key" @click="openTokenTest()">{{ t('system.testTokenIssue') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 客户端列表 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="clientId" :label="t('oauth2Token.clientId')" width="200" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="clientName" :label="t('common.name')" width="180" show-overflow-tooltip />
        <el-table-column prop="scopes" :label="t('oauth2Token.scopes')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="s in splitScopes(row.scopes)" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? t('common.enabled') : t('system.disable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.secretRotatedAt')" width="170">
          <template #default="{ row }">
            <span v-if="row.secretRotatedAt">{{ formatTime(row.secretRotatedAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.expireTime')" width="170">
          <template #default="{ row }">
            <span v-if="row.expiredAt" :class="{ expired: isExpired(row.expiredAt) }">{{ formatTime(row.expiredAt) }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Refresh" @click="onRotate(row)">{{ t('system.rotateSecret') }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('system.deleteThisClient')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
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
        客户端密钥仅以哈希落库，<b>{{ t('system.plaintextShownOnlyOnceOnRegisterRotateSuccess') }}</b>，请立即保存；丢失只能轮换（旧密钥立即失效）。
        协议级令牌签发已由独立端点 <code>POST /oauth2/token</code>（client_credentials + RS256）提供，可点「测试令牌签发」自测。
      </div>
    </el-card>

    <!-- 注册弹窗 -->
    <el-dialog v-model="createVisible" :title="t('system.registerClient')" width="560px" @closed="resetCreateForm">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="92px">
        <el-form-item :label="t('oauth2Token.clientId')" prop="clientId">
          <el-input v-model="createForm.clientId" :placeholder="t('system.lettersDigitsFirst864CharsAllowHyphenUnderscore')" />
        </el-form-item>
        <el-form-item :label="t('system.clientName')" prop="clientName">
          <el-input v-model="createForm.clientName" :placeholder="t('system.eGPartnerOrderSystem')" />
        </el-form-item>
        <el-form-item :label="t('oauth2Token.scopes')" prop="scopes">
          <el-input v-model="createForm.scopes" :placeholder="t('system.commaSeparatedEGReadWriteBlankDefaultsToRead')" />
        </el-form-item>
        <el-form-item :label="t('monitor.expireTime')">
          <el-date-picker
            v-model="createForm.expiredAt"
            type="datetime"
            :placeholder="t('system.blankForPermanent')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('job.remark')">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="onCreate">{{ t('system.register') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="t('system.editClient')" width="560px" @closed="resetEditForm">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="92px">
        <el-form-item :label="t('oauth2Token.clientId')">
          <el-input :model-value="editingClientId" disabled />
        </el-form-item>
        <el-form-item :label="t('system.clientName')" prop="clientName">
          <el-input v-model="editForm.clientName" />
        </el-form-item>
        <el-form-item :label="t('oauth2Token.scopes')" prop="scopes">
          <el-input v-model="editForm.scopes" :placeholder="t('common.commaSeparated')" />
        </el-form-item>
        <el-form-item :label="t('common.status')" prop="status">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ENABLED" />
            <el-option :label="t('system.disable')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('monitor.expireTime')">
          <el-date-picker
            v-model="editForm.expiredAt"
            type="datetime"
            :placeholder="t('system.blankForPermanent')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('job.remark')">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 一次性密钥弹窗（注册/轮换共用） -->
    <el-dialog v-model="secretVisible" :title="secretTitle" width="560px" @closed="closeSecret">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="t('system.thePlaintextSecretIsShownOnlyThisOnceAfterClosingItCannotBeViewedAgainCopyAndStoreItSafelyNow')"
      />
      <div class="secret-box">
        <span class="secret-text">{{ secretValue }}</span>
        <el-button :icon="CopyDocument" @click="onCopySecret">{{ t('system.copy') }}</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">{{ t('system.savedByMe') }}</el-button>
      </template>
    </el-dialog>

    <!-- 令牌签发自测弹窗（协议级 OAuth2 client_credentials 验证） -->
    <el-dialog v-model="tokenTestVisible" :title="t('system.tokenSelfTestClientCredentials')" width="600px" @closed="resetTokenTest">
      <el-form :model="tokenForm" label-width="92px">
        <el-form-item :label="t('oauth2Token.clientId')">
          <el-input v-model="tokenForm.clientId" :placeholder="t('system.mustMatchAClientIdInTheList')" />
        </el-form-item>
        <el-form-item :label="t('system.clientSecret')">
          <el-input v-model="tokenForm.clientSecret" type="password" show-password :placeholder="t('system.plaintextSecretShownOnRegisterRotate')" />
        </el-form-item>
      </el-form>
      <el-alert v-if="tokenResult" type="success" :closable="false" show-icon :title="t('system.tokenIssued')">
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
            <span class="token-text">{{ tokenResult.scope || t('system.default') }}</span>
          </div>
        </div>
      </el-alert>
      <template #footer>
        <el-button @click="tokenTestVisible = false">{{ t('common.close') }}</el-button>
        <el-button type="primary" :loading="tokenTesting" @click="onIssueToken">{{ t('system.issueToken') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
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
import { validateOrWarn } from '@/utils/validate'

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
    { required: true, message: t('system.enterClientId'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9][a-zA-Z0-9_-]{7,63}$/, message: t('system.lettersDigitsFirst864CharsAllowHyphenUnderscore'), trigger: 'blur' }
  ],
  clientName: [{ required: true, message: t('system.enterClientName'), trigger: 'blur' }],
  scopes: [
    {
      validator: (rule, value, callback) => {
        const scopes = splitScopes(value)
        if (scopes.length === 0) return callback() // 留空走默认 read
        if (scopes.length > 10) return callback(new Error(t('system.noMoreThan10AuthorizedScopes')))
        for (const s of scopes) {
          if (!/^[a-z][a-z0-9:_-]{0,31}$/.test(s)) {
            return callback(new Error(t('system.scopeInvalid', { s })))
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
  if (!(await validateOrWarn(createFormRef.value))) return
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
    showSecret(t('system.registered'), res)
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
  clientName: [{ required: true, message: t('system.enterClientName'), trigger: 'blur' }],
  scopes: [createRules.scopes[0]],
  status: [{ required: true, message: t('system.selectStatus'), trigger: 'change' }]
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
  if (!(await validateOrWarn(editFormRef.value))) return
  saving.value = true
  try {
    await updateOpenApiClient(editingId.value, {
      clientName: editForm.clientName,
      scopes: editForm.scopes || undefined,
      status: editForm.status,
      expiredAt: editForm.expiredAt || undefined,
      remark: editForm.remark || undefined
    })
    ElMessage.success(t('system.updated'))
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
    ElMessage.success(t('common.deleted'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onRotate(row) {
  try {
    const res = await rotateOpenApiClientSecret(row.id)
    showSecret(t('system.secretRotated'), res)
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
    ElMessage.success(t('system.copiedToClipboard'))
  } catch (e) {
    ElMessage.warning(t('system.copyFailedPleaseSelectAndCopyManually'))
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
    ElMessage.warning(t('system.fillClientIdAndSecret'))
    return
  }
  tokenTesting.value = true
  tokenResult.value = null
  try {
    // issueClientToken 经 requestRaw 返回原始 OAuth2 响应包络 { access_token, token_type, expires_in, scope }
    const resp = await issueClientToken(tokenForm.clientId, tokenForm.clientSecret)
    tokenResult.value = resp.data
    ElMessage.success(t('system.tokenIssued'))
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
  color: var(--text);
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
  color: var(--text);
}
</style>
