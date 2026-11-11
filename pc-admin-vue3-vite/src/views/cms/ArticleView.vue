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
          <el-select v-model="query.status" :placeholder="'全部状态'" clearable style="width: 130px">
            <el-option :label="'草稿'" value="DRAFT" />
            <el-option :label="'已发布'" value="PUBLISHED" />
            <el-option :label="'已下线'" value="OFFLINE" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="onSearch">{{ '搜索' }}</el-button>
        </div>
        <div class="actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ '创建' }}</el-button>
          <el-button :icon="Refresh" @click="load">{{ '刷新' }}</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
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
        <el-table-column prop="updatedAt" :label="'更新时间'" width="170" sortable="custom">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="'操作'" width="270" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">{{ '详情' }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ '编辑' }}</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" link type="success" @click="onPublish(row)">
              {{ '发布' }}
            </el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="onOffline(row)">
              {{ '下线' }}
            </el-button>
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
      </div>
      <el-divider />
      <p v-if="detailData.summary" class="detail-summary">{{ detailData.summary }}</p>
      <div class="detail-content">{{ detailData.content }}</div>
    </el-drawer>
  </div>
</template>

<script setup>

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
  title: [{ required: true, message: '请输入文章标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择所属栏目', trigger: 'change' }],
  content: [{ required: true, message: '请输入文章正文', trigger: 'blur' }]
}

const isEdit = computed(() => form.id != null)
const dialogTitle = computed(() => (isEdit.value ? '编辑文章' : '新增文章'))

function articleStatusTag(status) {
  if (status === 'DRAFT') return { type: 'info', label: '草稿' }
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
        ElMessage.success('更新成功')
      } else {
        delete body.id
        await createArticle(body)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await load()
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
  await publishArticle(row.id)
  ElMessage.success('发布成功')
  await load()
}

async function onOffline(row) {
  const confirmed = await ElMessageBox.confirm(`确认下线文章「${row.title}」吗？下线后前台不可见。`, '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  await offlineArticle(row.id)
  ElMessage.success('下线成功')
  await load()
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm(`确认删除文章「${row.title}」吗？`, '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  await deleteArticle(row.id)
  ElMessage.success('删除成功')
  await load()
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
</style>
