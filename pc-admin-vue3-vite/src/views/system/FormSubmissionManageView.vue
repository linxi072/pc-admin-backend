<template>
  <div>
    <h2 class="page-title">{{ $t('route.formSubmission') }}</h2>
    <p class="scope-hint">{{ $t('formSubmission.scope') }}</p>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ $t('formSubmission.formCode') }}</span>
            <el-input v-model="query.formCode" :placeholder="$t('formSubmission.formCodePlaceholder')" clearable style="width: 160px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ $t('formSubmission.submittedBy') }}</span>
            <el-input v-model="query.submittedBy" :placeholder="$t('formSubmission.submittedByPlaceholder')" clearable style="width: 150px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ $t('formSubmission.keyword') }}</span>
            <el-input v-model="query.keyword" :placeholder="$t('formSubmission.keywordPlaceholder')" clearable style="width: 200px" @keyup.enter="onSearch" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ $t('formSubmission.sortField') }}</span>
            <el-select v-model="query.sortField" :placeholder="$t('formSubmission.sortField')" style="width: 140px" @change="onSearch">
              <el-option :label="$t('formSubmission.sortByCreatedAt')" value="created_at" />
              <el-option :label="$t('formSubmission.sortBySubmittedBy')" value="submitted_by" />
              <el-option :label="$t('formSubmission.sortByFormCode')" value="form_code" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ $t('formSubmission.sortDir') }}</span>
            <el-select v-model="query.sortDir" :placeholder="$t('formSubmission.sortDir')" style="width: 120px" @change="onSearch">
              <el-option :label="$t('formSubmission.desc')" value="DESC" />
              <el-option :label="$t('formSubmission.asc')" value="ASC" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ $t('formSubmission.search') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ $t('formSubmission.reset') }}</el-button>
            <el-button :icon="Download" :loading="exporting" @click="onExport">{{ $t('formSubmission.export') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 统计摘要（随筛选联动） -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="6">
          <div class="stat-card stat-total">
            <div class="stat-num">{{ stats.totalCount ?? 0 }}</div>
            <div class="stat-label">{{ $t('formSubmission.totalCount') }}</div>
          </div>
        </el-col>
        <el-col :span="18">
          <div class="stat-card stat-breakdown">
            <div class="stat-label stat-breakdown-title">{{ $t('formSubmission.byForm') }}</div>
            <div v-if="stats.byForm && stats.byForm.length" class="breakdown-list">
              <span v-for="item in topForms" :key="item.formCode" class="breakdown-item">
                <span class="breakdown-name">{{ item.formName || item.formCode }}</span>
                <span class="breakdown-count">{{ item.count }}</span>
              </span>
              <span v-if="stats.byForm.length > topForms.length" class="breakdown-more">
                +{{ stats.byForm.length - topForms.length }}
              </span>
            </div>
            <div v-else class="muted">{{ $t('formSubmission.noData') }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" :label="$t('formSubmission.id')" width="90" />
        <el-table-column prop="formCode" :label="$t('formSubmission.formCode')" width="150" show-overflow-tooltip />
        <el-table-column prop="formName" :label="$t('formSubmission.formName')" width="170" show-overflow-tooltip />
        <el-table-column prop="submittedBy" :label="$t('formSubmission.submittedBy')" width="140" />
        <el-table-column prop="dataJson" :label="$t('formSubmission.dataJson')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="$t('formSubmission.createdAt')" width="180" />
        <el-table-column :label="$t('formSubmission.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">{{ $t('formSubmission.detail') }}</el-button>
            <el-popconfirm :title="$t('formSubmission.deleteConfirm')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ $t('formSubmission.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页统一到全局形态 -->
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

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" :title="$t('formSubmission.detailTitle')" width="640px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item :label="$t('formSubmission.id')">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item :label="$t('formSubmission.formCode')">{{ detail.formCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('formSubmission.formName')">{{ detail.formName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('formSubmission.submittedBy')">{{ detail.submittedBy || '--' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('formSubmission.createdAt')">{{ detail.createdAt || '--' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('formSubmission.dataJson')">
          <pre class="data-json">{{ formatData(detail.dataJson) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('formSubmission.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Download, View, Delete } from '@element-plus/icons-vue'
import { t } from '@/i18n'
import {
  pageSubmissions,
  getSubmissionStats,
  getSubmissionDetail,
  removeSubmission,
  exportSubmissions
} from '@/api/formSubmission'

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const stats = ref({ totalCount: 0, byForm: [] })

const detailVisible = ref(false)
const detail = ref(null)

const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  formCode: '',
  submittedBy: '',
  keyword: '',
  sortField: 'created_at', // 后端白名单：created_at / submitted_by / form_code
  sortDir: 'DESC'
})

/** 统计卡仅展示按数量降序的前 5 个表单，避免卡片过长。 */
const topForms = computed(() => (stats.value.byForm || []).slice(0, 5))

/** 统一把筛选条件映射为查询参数（空值一律 undefined，不传空串）。 */
function buildParams() {
  return {
    page: query.page,
    size: query.size,
    formCode: query.formCode || undefined,
    submittedBy: query.submittedBy || undefined,
    keyword: query.keyword || undefined,
    sortField: query.sortField || undefined,
    sortDir: query.sortDir || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const [page, stat] = await Promise.all([
      pageSubmissions(buildParams()),
      getSubmissionStats()
    ])
    rows.value = page.records || []
    total.value = page.total || 0
    stats.value = stat || { totalCount: 0, byForm: [] }
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
  query.formCode = ''
  query.submittedBy = ''
  query.keyword = ''
  query.sortField = 'created_at'
  query.sortDir = 'DESC'
  query.page = 1
  load()
}

async function openDetail(row) {
  try {
    detail.value = await getSubmissionDetail(row.id)
    detailVisible.value = true
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onDelete(row) {
  try {
    await removeSubmission(row.id)
    ElMessage.success(t('formSubmission.deletedHint'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onExport() {
  exporting.value = true
  try {
    const ok = await exportSubmissions(buildParams())
    if (ok) ElMessage.success(t('formSubmission.exportedHint'))
  } finally {
    exporting.value = false
  }
}

/** 提交内容 JSON 美化；解析失败则原样回退，空值显示占位。 */
function formatData(json) {
  if (!json) return '--'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch (e) {
    return json
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
.summary-row {
  margin-bottom: 12px;
}
.stat-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  height: 100%;
}
.stat-total .stat-num {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}
.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.stat-breakdown-title {
  margin-bottom: 8px;
}
.breakdown-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}
.breakdown-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
}
.breakdown-name {
  color: #606266;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.breakdown-count {
  color: #409eff;
  font-weight: 600;
}
.breakdown-more {
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #c0c4cc;
}
.data-json {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
}
</style>
