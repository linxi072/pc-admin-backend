<template>
  <div>
    <h2 class="page-title">{{ 'CMS 文章管理' }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.title"
            :placeholder="'请输入文章标题'"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="query.categoryId" :placeholder="'选择所属栏目'" clearable style="width: 160px">
            <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
          </el-select>
          <el-select v-model="query.status" :placeholder="'全部状态'" clearable style="width: 140px">
            <el-option :label="'草稿'" value="DRAFT" />
            <el-option :label="'待发布'" value="SCHEDULED" />
            <el-option :label="'已发布'" value="PUBLISHED" />
            <el-option :label="'已下线'" value="OFFLINE" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="onSearch">{{ '搜索' }}</el-button>
        </div>
        <div class="actions">
          <el-button
            type="warning"
            :disabled="selectedRows.length === 0"
            @click="openBatchMove"
          >{{ selectedRows.length ? `批量移动 (${selectedRows.length})` : '批量移动' }}</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ '创建' }}</el-button>
          <el-button :icon="Refresh" @click="load">{{ '刷新' }}</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="title" :label="'文章标题'" min-width="200" show-overflow-tooltip />
        <el-table-column :label="'所属栏目'" width="130">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="author" :label="'作者'" width="110" show-overflow-tooltip />
        <el-table-column prop="viewCount" :label="'浏览量'" width="90" align="center" sortable="custom" />
        <el-table-column :label="'状态'" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="articleStatusTag(row.status).type">{{ articleStatusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" :label="'发布时间'" width="170" sortable="custom">
          <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column :label="'定时发布'" width="170">
          <template #default="{ row }">{{ formatTime(row.scheduledPublishAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="'更新时间'" width="170" sortable="custom">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="'操作'" width="390" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">{{ '详情' }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ '编辑' }}</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" link type="success" @click="onPublish(row)">
              {{ '发布' }}
            </el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="onOffline(row)">
              {{ '下线' }}
            </el-button>
            <el-button link type="info" @click="openVersions(row)">{{ '版本' }}</el-button>
            <el-button link type="info" @click="createPreviewToken(row)">{{ '预览' }}</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">{{ '删除' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close top="6vh">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item :label="'文章标题'" prop="title">
          <el-input v-model="form.title" :placeholder="'请输入文章标题'" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="'所属栏目'" prop="categoryId">
          <el-select v-model="form.categoryId" :placeholder="'选择所属栏目'" style="width: 100%">
            <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="'作者'" prop="author">
          <el-input v-model="form.author" :placeholder="'请输入作者'" />
        </el-form-item>
        <el-form-item :label="'封面图'" prop="coverUrl">
          <el-input v-model="form.coverUrl" :placeholder="'请输入封面图 URL（可选）'" />
        </el-form-item>
        <el-form-item :label="'摘要'" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="'请输入文章摘要（可选）'" />
        </el-form-item>
        <el-form-item :label="'定时发布'" prop="scheduledPublishAt">
          <el-date-picker
            v-model="form.scheduledPublishAt"
            type="datetime"
            :placeholder="'留空则保存后立即生效'"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="'正文内容'" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="12" :placeholder="'请输入文章正文'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ '取消' }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ '确认' }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailData.title" size="50%">
      <div class="detail-meta">
        <el-tag :type="articleStatusTag(detailData.status).type">{{ articleStatusTag(detailData.status).label }}</el-tag>
        <span class="meta-item">{{ '所属栏目' }}：{{ categoryName(detailData.categoryId) }}</span>
        <span class="meta-item">{{ '作者' }}：{{ detailData.author || '-' }}</span>
        <span class="meta-item">{{ '浏览量' }}：{{ detailData.viewCount ?? 0 }}</span>
        <span class="meta-item">{{ '发布时间' }}：{{ formatTime(detailData.publishedAt) }}</span>
        <span class="meta-item">{{ '定时发布' }}：{{ formatTime(detailData.scheduledPublishAt) }}</span>
      </div>
      <el-divider />
      <p v-if="detailData.summary" class="detail-summary">{{ detailData.summary }}</p>
      <div class="detail-content">{{ detailData.content }}</div>
    </el-drawer>

    <!-- 批量移动对话框 -->
    <el-dialog v-model="batchMoveVisible" title="批量移动文章" width="460px" destroy-on-close>
      <el-alert
        :closable="false"
        type="info"
        :title="`已将 ${selectedRows.length} 篇文章选中，移动后其所属栏目将统一变更。`"
      />
      <div class="batch-move-field">
        <span class="batch-move-label">{{ '目标栏目' }}</span>
        <el-select v-model="batchTargetCategoryId" placeholder="请选择目标栏目" style="width: 100%">
          <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="batchMoveVisible = false">{{ '取消' }}</el-button>
        <el-button type="primary" :loading="batchMoveSubmitting" @click="onBatchMove">{{ '确认移动' }}</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉（C4） -->
    <el-drawer v-model="versionVisible" :title="`版本历史 - ${versionArticleTitle}`" size="55%">
      <div v-loading="versionLoading">
        <el-alert
          v-if="!versionLoading && !versionRows.length"
          :closable="false"
          type="info"
          title="暂无版本历史（文章发生内容变更后会自动生成快照）"
        />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in versionRows"
            :key="item.id"
            :timestamp="formatTime(item.createdAt)"
            placement="top"
          >
            <el-card shadow="never" class="version-card">
              <div class="version-head">
                <strong>v{{ item.versionNo }}</strong>
                <span class="version-remark">{{ item.remark || '-' }}</span>
                <el-tag :type="articleStatusTag(item.status).type" size="small">{{ articleStatusTag(item.status).label }}</el-tag>
              </div>
              <div class="version-body">
                <span>{{ '作者' }}：{{ item.author || '-' }}</span>
                <span>{{ '创建人' }}：{{ item.createdBy || '-' }}</span>
              </div>
              <el-button
                class="version-rollback"
                type="warning"
                size="small"
                :loading="rollingBackNo === item.versionNo"
                @click="onRollback(item)"
              >{{ '回滚到此版本' }}</el-button>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <!-- 预览令牌弹窗（C6） -->
    <el-dialog v-model="previewVisible" title="草稿预览令牌" width="520px" destroy-on-close>
      <el-alert :closable="false" type="info" title="令牌有时效限制，过期后链接将失效；请勿对外公开分享。" />
      <div class="preview-token-box">
        <el-input v-model="previewUrl" readonly>
          <template #append>
            <el-button :icon="DocumentCopy" @click="onCopyPreviewUrl">{{ '复制' }}</el-button>
          </template>
        </el-input>
      </div>
      <div class="preview-expiry">{{ '有效期至' }}：{{ formatTime(previewExpiresAt) }}</div>
      <template #footer>
        <el-button @click="previewVisible = false">{{ '关闭' }}</el-button>
        <el-button type="primary" :loading="previewSubmitting" @click="refreshPreviewToken">{{ '重新生成' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

import { ref, reactive, computed, onMounted } from 'vue'
import { formatDateTime } from '@/utils/datetime'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Refresh, View, DocumentCopy } from '@element-plus/icons-vue'
import {
  pageArticles,
  createArticle,
  updateArticle,
  deleteArticle,
  getArticle,
  publishArticle,
  offlineArticle,
  viewArticle,
  listCategories,
  batchMoveArticles,
  listArticleVersions,
  rollbackArticle,
  createArticlePreviewToken
} from '@/api/cms'
import { statusTag } from '@/utils/status'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])
const formRef = ref()
const detailData = ref({})

// 批量移动
const selectedRows = ref([])
const batchMoveVisible = ref(false)
const batchMoveSubmitting = ref(false)
const batchTargetCategoryId = ref(null)

// 版本历史（C4）
const versionVisible = ref(false)
const versionLoading = ref(false)
const versionRows = ref([])
const versionArticleId = ref(null)
const versionArticleTitle = ref('')
const rollingBackNo = ref(null)

// 预览令牌（C6）
const previewVisible = ref(false)
const previewSubmitting = ref(false)
const previewUrl = ref('')
const previewExpiresAt = ref(null)
const previewArticleId = ref(null)

const query = reactive({
  page: 1,
  size: 10,
  categoryId: '',
  title: '',
  status: '',
  sortField: '',
  sortDirection: ''
})

const form = reactive({
  id: null,
  categoryId: null,
  title: '',
  summary: '',
  content: '',
  author: '',
  coverUrl: '',
  scheduledPublishAt: null
})

const rules = {
  title: [{ required: true, message: '请输入文章标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择所属栏目', trigger: 'change' }],
  content: [{ required: true, message: '请输入文章正文', trigger: 'blur' }]
}

const isEdit = computed(() => form.id != null)
const dialogTitle = computed(() => (isEdit.value ? '编辑文章' : '新增文章'))

function articleStatusTag(status) {
  const tag = statusTag(status)
  return tag.label === '—' ? { type: 'info', label: '-' } : tag
}

function categoryName(categoryId) {
  const found = categories.value.find((item) => item.id === categoryId)
  return found ? found.categoryName : categoryId ?? '-'
}

function formatTime(value) {
  return formatDateTime(value, { fallback: '-' })
}

async function load() {
  loading.value = true
  try {
    const data = await pageArticles({ ...query })
    rows.value = data.records || []
    total.value = data.total ?? 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  const data = await listCategories()
  categories.value = Array.isArray(data) ? data : []
}

function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

function openBatchMove() {
  if (!selectedRows.value.length) return
  batchTargetCategoryId.value = null
  batchMoveVisible.value = true
}

async function onBatchMove() {
  if (batchTargetCategoryId.value == null) {
    ElMessage.warning('请选择目标栏目')
    return
  }
  batchMoveSubmitting.value = true
  try {
    const ids = selectedRows.value.map((row) => row.id)
    const result = await batchMoveArticles(ids, batchTargetCategoryId.value)
    const skipped = result?.skippedIds || []
    if (skipped.length) {
      ElMessage.warning(`已移动 ${result?.moved ?? 0} 篇，${skipped.length} 篇已不存在或已删除被跳过`)
    } else {
      ElMessage.success(`已移动 ${result?.moved ?? 0} 篇到「${categoryName(batchTargetCategoryId.value)}」`)
    }
    batchMoveVisible.value = false
    selectedRows.value = []
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一处理，保留对话框供用户重试
  } finally {
    batchMoveSubmitting.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function resetForm() {
  form.id = null
  form.categoryId = null
  form.title = ''
  form.summary = ''
  form.content = ''
  form.author = ''
  form.coverUrl = ''
  form.scheduledPublishAt = null
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  const detail = await getArticle(row.id)
  Object.assign(form, {
    id: detail.id,
    categoryId: detail.categoryId,
    title: detail.title,
    summary: detail.summary || '',
    content: detail.content || '',
    author: detail.author || '',
    coverUrl: detail.coverUrl || '',
    scheduledPublishAt: detail.scheduledPublishAt || null
  })
  dialogVisible.value = true
}

async function openDetail(row) {
  detailData.value = await getArticle(row.id)
  detailVisible.value = true
  // 打开详情即计一次浏览：后端 POST /api/cms/article/{id}/view 为权威计数源（返回 Result<Void>）。
  // 仅在后端自增成功后才在本地 +1，避免后端失败时本地制造虚假计数导致漂移。
  viewArticle(row.id)
    .then(() => {
      row.viewCount = (row.viewCount ?? 0) + 1
      if (detailData.value) detailData.value.viewCount = (detailData.value.viewCount ?? 0) + 1
    })
    .catch(() => {})
}

function onSubmit() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const body = { ...form }
      if (isEdit.value) {
        await updateArticle(body)
        ElMessage.success('更新成功')
      } else {
        delete body.id
        await createArticle(body)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 错误提示由 http 响应拦截器统一处理，保留弹窗供用户修正后重试
    } finally {
      submitting.value = false
    }
  })
}

async function onPublish(row) {
  const confirmed = await ElMessageBox.confirm(`确认发布文章「${row.title}」吗？发布后前台可见。`, '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  try {
    await publishArticle(row.id)
    ElMessage.success('发布成功')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一处理，此处仅吞掉异常避免未捕获的 Promise rejection
  }
}

async function onOffline(row) {
  const confirmed = await ElMessageBox.confirm(`确认下线文章「${row.title}」吗？下线后前台不可见。`, '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  try {
    await offlineArticle(row.id)
    ElMessage.success('下线成功')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一处理
  }
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm(`确认删除文章「${row.title}」吗？`, '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  try {
    await deleteArticle(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一处理
  }
}

// ===== C4：版本历史与回滚 =====

async function openVersions(row) {
  versionArticleId.value = row.id
  versionArticleTitle.value = row.title
  versionRows.value = []
  versionVisible.value = true
  versionLoading.value = true
  try {
    versionRows.value = await listArticleVersions(row.id) || []
  } catch {
    // 错误提示由 http 响应拦截器统一处理，抽屉内保持空列表
  } finally {
    versionLoading.value = false
  }
}

async function onRollback(item) {
  // 目标即最新版本时后端会拒绝（无意义回滚），前端提前拦截给出清晰提示
  if (rollingBackNo.value != null) return
  const sameRow = versionRows.value[versionRows.value.length - 1]
  if (sameRow && sameRow.versionNo === item.versionNo) {
    ElMessage.warning('该版本为当前最新版本，无需回滚')
    return
  }
  const confirmed = await ElMessageBox.confirm(
    `确认回滚到 v${item.versionNo} 吗？当前内容将被覆盖，回滚操作会生成一条新版本记录。`,
    '警告',
    { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!confirmed) return
  rollingBackNo.value = item.versionNo
  try {
    await rollbackArticle(versionArticleId.value, item.versionNo)
    ElMessage.success(`已回滚到 v${item.versionNo}`)
    versionVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一处理，保留抽屉供用户重试
  } finally {
    rollingBackNo.value = null
  }
}

// ===== C6：草稿预览令牌 =====

async function createPreviewToken(row) {
  previewArticleId.value = row.id
  previewSubmitting.value = true
  try {
    const data = await createArticlePreviewToken(row.id)
    previewUrl.value = `${window.location.origin}/api/cms/preview/${data.token}`
    previewExpiresAt.value = data.expiresAt
    previewVisible.value = true
  } catch {
    // 错误提示由 http 响应拦截器统一处理；若提示"资源不存在"，说明 cms.preview.enabled 未开启
  } finally {
    previewSubmitting.value = false
  }
}

async function refreshPreviewToken() {
  if (previewArticleId.value == null) return
  await createPreviewToken({ id: previewArticleId.value })
}

async function onCopyPreviewUrl() {
  if (!previewUrl.value) return
  try {
    await navigator.clipboard.writeText(previewUrl.value)
    ElMessage.success('预览链接已复制')
  } catch (error) {
    // 非安全上下文或权限拒绝时，选中文本作为兜底提示
    ElMessage.warning('自动复制失败，请手动复制链接')
  }
}

onMounted(async () => {
  await loadCategories()
  await load()
})
</script>

<style scoped>
/* 筛选行含 3 个定宽控件 + 查询按钮（约 560px），容器窄时原本不换行会把按钮挤出可视区
   （E2E F5：小屏下「查询」按钮被裁切）。改为可换行 + 行间距，配合 actions 不压缩。 */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-left: auto;
}
.pagination {
  margin-top: 14px;
  justify-content: flex-end;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.detail-summary {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  padding: 10px 12px;
  border-radius: 4px;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.8;
}
.version-card {
  padding: 10px 12px;
}
.version-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.version-remark {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.version-body {
  display: flex;
  gap: 18px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}
.version-rollback {
  margin-top: 2px;
}
.preview-token-box {
  margin: 16px 0 8px;
}
.preview-expiry {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.batch-move-field {
  margin-top: 16px;
}
.batch-move-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
