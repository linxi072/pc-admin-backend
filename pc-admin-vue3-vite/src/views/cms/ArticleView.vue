<template>
  <div>
    <h2 class="page-title">{{ t('route.cmsArticle') }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.title"
            :placeholder="t('cms.article.titlePlaceholder')"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="query.categoryId" :placeholder="t('cms.article.categoryPlaceholder')" clearable style="width: 160px">
            <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
          </el-select>
          <el-select v-model="query.status" :placeholder="t('cms.article.statusPlaceholder')" clearable style="width: 130px">
            <el-option :label="t('cms.status.draft')" value="DRAFT" />
            <el-option :label="t('cms.status.published')" value="PUBLISHED" />
            <el-option :label="t('cms.status.offline')" value="OFFLINE" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.search') }}</el-button>
        </div>
        <div class="actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('common.create') }}</el-button>
          <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="title" :label="t('cms.article.title')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('cms.article.category')" width="130">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="author" :label="t('cms.article.author')" width="110" show-overflow-tooltip />
        <el-table-column prop="viewCount" :label="t('cms.article.viewCount')" width="90" align="center" sortable="custom" />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="articleStatusTag(row.status).type">{{ articleStatusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" :label="t('cms.article.publishedAt')" width="170" sortable="custom">
          <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('cms.article.updatedAt')" width="170" sortable="custom">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.action')" width="270" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">{{ t('cms.article.detail') }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" link type="success" @click="onPublish(row)">
              {{ t('cms.article.publish') }}
            </el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="onOffline(row)">
              {{ t('cms.article.offlineAction') }}
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
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
        <el-form-item :label="t('cms.article.title')" prop="title">
          <el-input v-model="form.title" :placeholder="t('cms.article.titlePlaceholder')" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('cms.article.category')" prop="categoryId">
          <el-select v-model="form.categoryId" :placeholder="t('cms.article.categoryPlaceholder')" style="width: 100%">
            <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('cms.article.author')" prop="author">
          <el-input v-model="form.author" :placeholder="t('cms.article.authorPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('cms.article.coverUrl')" prop="coverUrl">
          <el-input v-model="form.coverUrl" :placeholder="t('cms.article.coverUrlPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('cms.article.summary')" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="t('cms.article.summaryPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('cms.article.content')" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="12" :placeholder="t('cms.article.contentPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailData.title" size="50%">
      <div class="detail-meta">
        <el-tag :type="articleStatusTag(detailData.status).type">{{ articleStatusTag(detailData.status).label }}</el-tag>
        <span class="meta-item">{{ t('cms.article.category') }}：{{ categoryName(detailData.categoryId) }}</span>
        <span class="meta-item">{{ t('cms.article.author') }}：{{ detailData.author || '-' }}</span>
        <span class="meta-item">{{ t('cms.article.viewCount') }}：{{ detailData.viewCount ?? 0 }}</span>
        <span class="meta-item">{{ t('cms.article.publishedAt') }}：{{ formatTime(detailData.publishedAt) }}</span>
      </div>
      <el-divider />
      <p v-if="detailData.summary" class="detail-summary">{{ detailData.summary }}</p>
      <div class="detail-content">{{ detailData.content }}</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Refresh, View } from '@element-plus/icons-vue'
import {
  pageArticles,
  createArticle,
  updateArticle,
  deleteArticle,
  getArticle,
  publishArticle,
  offlineArticle,
  listCategories
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
  coverUrl: ''
})

const rules = {
  title: [{ required: true, message: t('cms.article.titleRequired'), trigger: 'blur' }],
  categoryId: [{ required: true, message: t('cms.article.categoryRequired'), trigger: 'change' }],
  content: [{ required: true, message: t('cms.article.contentRequired'), trigger: 'blur' }]
}

const isEdit = computed(() => form.id != null)
const dialogTitle = computed(() => (isEdit.value ? t('cms.article.editTitle') : t('cms.article.createTitle')))

function articleStatusTag(status) {
  if (status === 'DRAFT') return { type: 'info', label: t('cms.status.draft') }
  if (status === 'PUBLISHED') return statusTag('PUBLISHED')
  if (status === 'OFFLINE') return statusTag('OFFLINE')
  return { type: 'info', label: status || '-' }
}

function categoryName(categoryId) {
  const found = categories.value.find((item) => item.id === categoryId)
  return found ? found.categoryName : categoryId ?? '-'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
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
    coverUrl: detail.coverUrl || ''
  })
  dialogVisible.value = true
}

async function openDetail(row) {
  detailData.value = await getArticle(row.id)
  detailVisible.value = true
  // 打开详情即计一次浏览
  viewArticle(row.id).catch(() => {})
  row.viewCount = (row.viewCount ?? 0) + 1
}

function onSubmit() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const body = { ...form }
      if (isEdit.value) {
        await updateArticle(body)
        ElMessage.success(t('common.updateSuccess'))
      } else {
        delete body.id
        await createArticle(body)
        ElMessage.success(t('common.createSuccess'))
      }
      dialogVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function onPublish(row) {
  const confirmed = await ElMessageBox.confirm(t('cms.article.publishConfirm', { title: row.title }), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await publishArticle(row.id)
  ElMessage.success(t('cms.article.publishSuccess'))
  await load()
}

async function onOffline(row) {
  const confirmed = await ElMessageBox.confirm(t('cms.article.offlineConfirm', { title: row.title }), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await offlineArticle(row.id)
  ElMessage.success(t('cms.article.offlineSuccess'))
  await load()
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm(t('cms.article.deleteConfirm', { title: row.title }), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await deleteArticle(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await load()
}

onMounted(async () => {
  await loadCategories()
  await load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
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
</style>
