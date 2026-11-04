<template>
  <div>
    <h2 class="page-title">{{ t('route.dashboardTemplate') }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.templateCode"
            :placeholder="t('dashboardTemplate.codePlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-input
            v-model="query.templateName"
            :placeholder="t('dashboardTemplate.namePlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="query.status" :placeholder="t('dashboardTemplate.statusPlaceholder')" clearable style="width: 130px">
            <el-option :label="t('dashboardTemplate.status.draft')" value="DRAFT" />
            <el-option :label="t('dashboardTemplate.status.published')" value="PUBLISHED" />
            <el-option :label="t('dashboardTemplate.status.offline')" value="OFFLINE" />
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
        <el-table-column prop="templateCode" :label="t('dashboardTemplate.code')" width="160" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="templateName" :label="t('dashboardTemplate.name')" min-width="180" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" :label="t('dashboardTemplate.viewCount')" width="90" align="center" sortable="custom" />
        <el-table-column prop="updatedAt" :label="t('dashboardTemplate.updatedAt')" width="170" sortable="custom">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.action')" width="330" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openPreview(row)">{{ t('dashboardTemplate.preview') }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'"
              link
              type="success"
              @click="onPublish(row)"
            >{{ t('common.publish') }}</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="onUnpublish(row)">
              {{ t('dashboardTemplate.unpublish') }}
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

    <!-- 编辑弹窗：基础信息 + 布局配置器（拖拽排序卡片 / JSON 双向编辑） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" destroy-on-close top="5vh">
      <el-tabs v-model="editorTab">
        <el-tab-pane :label="t('dashboardTemplate.tab.basic')" name="basic">
          <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
            <el-form-item :label="t('dashboardTemplate.code')" prop="templateCode">
              <el-input v-model="form.templateCode" :placeholder="t('dashboardTemplate.codePlaceholder')" maxlength="64" />
            </el-form-item>
            <el-form-item :label="t('dashboardTemplate.name')" prop="templateName">
              <el-input v-model="form.templateName" :placeholder="t('dashboardTemplate.namePlaceholder')" maxlength="128" />
            </el-form-item>
            <el-form-item :label="t('common.description')" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="2" maxlength="512" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('common.status')" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio value="DRAFT">{{ t('dashboardTemplate.status.draft') }}</el-radio>
                <el-radio value="PUBLISHED">{{ t('dashboardTemplate.status.published') }}</el-radio>
                <el-radio value="OFFLINE">{{ t('dashboardTemplate.status.offline') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="t('dashboardTemplate.tab.layout')" name="layout">
          <el-alert :title="t('dashboardTemplate.layoutHint')" type="info" :closable="false" class="layout-alert" />
          <div class="layout-designer">
            <!-- 卡片列表（HTML5 原生拖拽排序） -->
            <div class="card-pool">
              <div
                v-for="(card, index) in layoutCards"
                :key="card.id"
                class="layout-card"
                :class="{ dragging: dragIndex === index }"
                draggable="true"
                @dragstart="onDragStart(index)"
                @dragover.prevent
                @drop="onDrop(index)"
              >
                <div class="layout-card-header">
                  <el-icon class="drag-handle"><Rank /></el-icon>
                  <el-input v-model="card.title" :placeholder="t('dashboardTemplate.cardTitle')" size="small" class="card-title-input" />
                  <el-button link type="danger" size="small" :icon="Delete" @click="removeCard(index)" />
                </div>
                <div class="layout-card-body">
                  <el-input v-model="card.component" :placeholder="t('dashboardTemplate.componentPlaceholder')" size="small" style="flex: 1" />
                  <el-select v-model="card.span" size="small" style="width: 110px">
                    <el-option v-for="n in 24" :key="n" :label="t('dashboardTemplate.spanN', { n })" :value="n" />
                  </el-select>
                  <el-button link size="small" @click="moveCard(index, -1)" :disabled="index === 0">↑</el-button>
                  <el-button link size="small" @click="moveCard(index, 1)" :disabled="index === layoutCards.length - 1">↓</el-button>
                </div>
              </div>
              <el-button type="primary" plain :icon="Plus" class="add-card-btn" @click="addCard">{{ t('dashboardTemplate.addCard') }}</el-button>
            </div>

            <!-- JSON 双向编辑 -->
            <div class="json-editor">
              <div class="json-editor-header">{{ t('dashboardTemplate.layoutJson') }}</div>
              <el-input
                v-model="layoutJsonText"
                type="textarea"
                :rows="14"
                class="json-textarea"
                @change="onJsonTextChange"
              />
              <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览抽屉：解析 layoutJson 渲染卡片网格 -->
    <el-drawer v-model="previewVisible" :title="t('dashboardTemplate.preview')" size="55%">
      <div v-loading="previewLoading">
        <template v-if="previewTemplate">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="t('dashboardTemplate.code')">{{ previewTemplate.templateCode }}</el-descriptions-item>
            <el-descriptions-item :label="t('dashboardTemplate.name')">{{ previewTemplate.templateName }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.description')" :span="2">{{ previewTemplate.description || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="preview-grid">
            <div
              v-for="card in previewCards"
              :key="card.id"
              class="preview-card"
              :style="{ gridColumn: `span ${card.span || 24}` }"
            >
              <div class="preview-card-title">{{ card.title || t('dashboardTemplate.unnamedCard') }}</div>
              <div class="preview-card-body">{{ card.component || t('dashboardTemplate.noComponent') }}</div>
            </div>
            <el-empty v-if="previewCards.length === 0" :description="t('dashboardTemplate.emptyLayout')" :image-size="70" />
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Refresh, View, Rank } from '@element-plus/icons-vue'
import {
  pageDashboardTemplates,
  createDashboardTemplate,
  updateDashboardTemplate,
  deleteDashboardTemplate,
  getDashboardTemplate,
  publishDashboardTemplate,
  unpublishDashboardTemplate,
  viewDashboardTemplate
} from '@/api/dashboardTemplate'
import { statusTag } from '@/utils/status'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const rows = ref([])
const total = ref(0)
const formRef = ref()
const editorTab = ref('basic')
const previewTemplate = ref(null)

const query = reactive({
  page: 1,
  size: 10,
  templateCode: '',
  templateName: '',
  status: '',
  sortField: '',
  sortDirection: ''
})

const form = reactive({
  id: null,
  templateCode: '',
  templateName: '',
  description: '',
  status: 'DRAFT'
})

// 布局配置器状态：卡片数组 ⇄ layoutJson 字符串双向同步
const layoutCards = ref([])
const layoutJsonText = ref('{}')
const jsonError = ref('')
const dragIndex = ref(null)

const rules = {
  templateCode: [{ required: true, message: t('dashboardTemplate.codeRequired'), trigger: 'blur' }],
  templateName: [{ required: true, message: t('dashboardTemplate.nameRequired'), trigger: 'blur' }]
}

const isEdit = computed(() => form.id != null)
const dialogTitle = computed(() => (isEdit.value ? t('dashboardTemplate.editTitle') : t('dashboardTemplate.createTitle')))

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

// ---------- layoutJson ⇄ 卡片数组 ----------

function cardsToJson(cards) {
  return JSON.stringify({ cards }, null, 2)
}

function jsonToCards(text) {
  const parsed = JSON.parse(text)
  const cards = Array.isArray(parsed) ? parsed : parsed.cards
  if (!Array.isArray(cards)) throw new Error(t('dashboardTemplate.jsonFormatError'))
  return cards.map((card, index) => ({
    id: card.id ?? index + 1,
    title: card.title || '',
    component: card.component || '',
    span: Math.min(Math.max(Number(card.span) || 24, 1), 24)
  }))
}

function syncCardsToJson() {
  layoutJsonText.value = cardsToJson(layoutCards.value)
  jsonError.value = ''
}

function onJsonTextChange() {
  try {
    layoutCards.value = jsonToCards(layoutJsonText.value)
    jsonError.value = ''
  } catch (e) {
    jsonError.value = t('dashboardTemplate.jsonInvalid', { msg: e.message })
  }
}

function addCard() {
  const maxId = layoutCards.value.reduce((max, card) => Math.max(max, Number(card.id) || 0), 0)
  layoutCards.value.push({ id: maxId + 1, title: '', component: '', span: 12 })
  syncCardsToJson()
}

function removeCard(index) {
  layoutCards.value.splice(index, 1)
  syncCardsToJson()
}

function moveCard(index, delta) {
  const target = index + delta
  if (target < 0 || target >= layoutCards.value.length) return
  const [card] = layoutCards.value.splice(index, 1)
  layoutCards.value.splice(target, 0, card)
  syncCardsToJson()
}

function onDragStart(index) {
  dragIndex.value = index
}

function onDrop(targetIndex) {
  if (dragIndex.value == null || dragIndex.value === targetIndex) return
  moveCard(dragIndex.value, targetIndex - dragIndex.value)
  dragIndex.value = null
}

// ---------- 列表与操作 ----------

async function load() {
  loading.value = true
  try {
    const data = await pageDashboardTemplates({ ...query })
    rows.value = data.records || []
    total.value = data.total ?? 0
  } finally {
    loading.value = false
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

function openCreate() {
  form.id = null
  form.templateCode = ''
  form.templateName = ''
  form.description = ''
  form.status = 'DRAFT'
  layoutCards.value = [{ id: 1, title: '', component: '', span: 24 }]
  syncCardsToJson()
  editorTab.value = 'basic'
  dialogVisible.value = true
}

async function openEdit(row) {
  const detail = await getDashboardTemplate(row.id)
  form.id = detail.id
  form.templateCode = detail.templateCode || ''
  form.templateName = detail.templateName || ''
  form.description = detail.description || ''
  form.status = detail.status || 'DRAFT'
  // 解析存量 layoutJson；空/坏数据回退为空卡片池
  const raw = detail.layoutJson && detail.layoutJson.trim() ? detail.layoutJson : '{}'
  layoutJsonText.value = raw
  try {
    layoutCards.value = jsonToCards(raw)
    jsonError.value = ''
  } catch {
    layoutCards.value = []
    jsonError.value = ''
  }
  editorTab.value = 'basic'
  dialogVisible.value = true
}

function onSubmit() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    // 布局 JSON 以 textarea 最新内容为准（允许直接手写 JSON）；非法 JSON 阻断提交
    if (jsonError.value) {
      ElMessage.warning(t('dashboardTemplate.jsonFixFirst'))
      editorTab.value = 'layout'
      return
    }
    try {
      JSON.parse(layoutJsonText.value)
    } catch (e) {
      jsonError.value = t('dashboardTemplate.jsonInvalid', { msg: e.message })
      editorTab.value = 'layout'
      return
    }
    submitting.value = true
    try {
      const body = {
        id: form.id,
        templateCode: form.templateCode,
        templateName: form.templateName,
        description: form.description,
        status: form.status,
        layoutJson: layoutJsonText.value
      }
      if (isEdit.value) {
        await updateDashboardTemplate(body)
        ElMessage.success(t('common.updateSuccess'))
      } else {
        delete body.id
        await createDashboardTemplate(body)
        ElMessage.success(t('common.createSuccess'))
      }
      dialogVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function openPreview(row) {
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewTemplate.value = await getDashboardTemplate(row.id)
    // 打开预览即计一次浏览
    viewDashboardTemplate(row.id).catch(() => {})
    row.viewCount = (row.viewCount ?? 0) + 1
  } finally {
    previewLoading.value = false
  }
}

const previewCards = computed(() => {
  const raw = previewTemplate.value?.layoutJson
  if (!raw) return []
  try {
    return jsonToCards(raw)
  } catch {
    return []
  }
})

async function onPublish(row) {
  const confirmed = await ElMessageBox.confirm(t('dashboardTemplate.publishConfirm', { name: row.templateName }), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await publishDashboardTemplate(row.id)
  ElMessage.success(t('dashboardTemplate.publishSuccess'))
  await load()
}

async function onUnpublish(row) {
  const confirmed = await ElMessageBox.confirm(t('dashboardTemplate.unpublishConfirm', { name: row.templateName }), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await unpublishDashboardTemplate(row.id)
  ElMessage.success(t('dashboardTemplate.unpublishSuccess'))
  await load()
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm(t('dashboardTemplate.deleteConfirm', { name: row.templateName }), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await deleteDashboardTemplate(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await load()
}

onMounted(load)
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
.layout-alert {
  margin-bottom: 12px;
}
.layout-designer {
  display: flex;
  gap: 16px;
}
.card-pool {
  flex: 1;
  min-width: 0;
}
.layout-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  cursor: grab;
  background: var(--el-fill-color-lighter);
  transition: box-shadow 0.2s;
}
.layout-card.dragging {
  opacity: 0.5;
}
.layout-card:hover {
  box-shadow: var(--el-box-shadow-light);
}
.layout-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.drag-handle {
  cursor: grab;
  color: var(--el-text-color-secondary);
}
.card-title-input {
  flex: 1;
}
.layout-card-body {
  display: flex;
  gap: 8px;
  align-items: center;
}
.add-card-btn {
  width: 100%;
}
.json-editor {
  width: 360px;
  flex-shrink: 0;
}
.json-editor-header {
  font-weight: 600;
  margin-bottom: 6px;
}
.json-textarea :deep(textarea) {
  font-family: monospace;
  font-size: 12px;
}
.json-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 6px;
}
.preview-grid {
  display: grid;
  grid-template-columns: repeat(24, 1fr);
  gap: 10px;
  margin-top: 16px;
}
.preview-card {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  min-height: 90px;
  padding: 10px;
  background: var(--el-fill-color-lighter);
}
.preview-card-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.preview-card-body {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}
</style>
