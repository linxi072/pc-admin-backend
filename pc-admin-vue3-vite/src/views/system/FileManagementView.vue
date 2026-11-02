<template>
  <div>
    <h2 class="page-title">{{ t('route.file') }}</h2>

    <!-- 上传区：记录来源功能模块与上传人（上传人由后端取当前登录用户，前端不传） -->
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title">{{ t('system.uploadFile') }}</span>
      </template>
      <el-form :inline="true" class="upload-form" @submit.prevent>
        <el-form-item :label="t('system.sourceModule')" required>
          <el-select
            v-model="uploadForm.sourceModule"
            :placeholder="t('system.selectOrInputSourceModule')"
            filterable
            allow-create
            default-first-option
            style="width: 220px"
          >
            <el-option
              v-for="opt in sourceModuleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.bizRefId')">
          <el-input v-model="uploadForm.bizId" :placeholder="t('system.optionalWorkflowReportId')" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item :label="t('system.fileLabel')">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :show-file-list="true"
            :on-exceed="onExceed"
            accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar,.7z"
            style="width: 320px"
          >
            <template #trigger>
              <el-button :icon="Upload">{{ t('system.selectFile') }}</el-button>
            </template>
            <template #tip>
              <div class="el-upload__tip">
                大小上限 {{ maxSizeMB }}MB；支持类型：{{ allowedExtHint }}
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="UploadFilled" :loading="uploading" @click="onSubmit">{{ t('common.upload') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 存储统计：总量 / 占用空间 / 按来源模块拆分（V50） -->
    <el-card class="page-card" shadow="never" v-loading="statsLoading">
      <template #header>
        <span class="card-title">{{ t('system.storageStats') }}</span>
      </template>
      <div class="stats-row">
        <div class="stat-item">
          <div class="stat-value">{{ stats.totalCount }}</div>
          <div class="stat-label">{{ t('system.fileTotal') }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ fmtSize(stats.totalSize) }}</div>
          <div class="stat-label">{{ t('system.usedSpace') }}</div>
        </div>
        <div class="stat-item stat-modules">
          <el-tag
            v-for="m in stats.modules"
            :key="m.sourceModule"
            size="small"
            type="info"
            class="stat-tag"
          >
            {{ m.sourceModule }} · {{ m.fileCount }} 个 / {{ fmtSize(m.totalSize) }}
          </el-tag>
          <span v-if="!stats.modules.length" class="muted">{{ t('system.noFile') }}</span>
        </div>
      </div>
    </el-card>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.sourceModule') }}</span>
            <el-select v-model="query.sourceModule" :placeholder="t('common.all')" clearable filterable style="width: 180px">
              <el-option
                v-for="opt in sourceModuleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.uploader') }}</span>
            <el-input v-model="query.uploaderName" :placeholder="t('system.uploaderBlurred')" clearable style="width: 160px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.fileName') }}</span>
            <el-input v-model="query.originalName" :placeholder="t('system.fileNameBlurred')" clearable style="width: 180px" @keyup.enter="load" />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button
              type="danger"
              :icon="Delete"
              :disabled="!selectedRows.length"
              @click="onBatchDelete"
            >
              批量删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
            </el-button>
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="originalName" :label="t('system.fileName')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sourceModule" :label="t('system.sourceModule')" width="150">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.sourceModule || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.instantTransfer')" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.deduplicated" size="small" type="warning">{{ t('system.instantTransfer') }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="uploaderName" :label="t('system.uploader')" width="140" />
        <el-table-column prop="fileSize" :label="t('system.sizeSys')" width="120" sortable="custom">
          <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="contentType" :label="t('common.type')" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.contentType">{{ row.contentType }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="downloadCount" :label="t('system.downloadCount')" width="110" sortable="custom" />
        <el-table-column prop="createdAt" :label="t('system.uploadTime')" width="180" sortable="custom" />
        <el-table-column :label="t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Download" @click="onDownload(row)">{{ t('common.download') }}</el-button>
            <el-popconfirm :title="t('system.confirmDeleteFile')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页统一到全局形态：<div class="pager"> 包裹 + v-model + current/size 事件 + jumper -->
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
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft, Upload, UploadFilled, Download, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageFiles, uploadFile, downloadFile, deleteFile, checkDedup, fileStats, batchDeleteFiles } from '@/api/file'
// Blob 下载统一到 utils/download
import { saveBlobFromResponse } from '@/utils/download'

// 来源功能模块候选（业务模块调用时也可自由输入，allow-create 兜底）
const sourceModuleOptions = [
  { value: 'file-management', label: t('route.file') },
  { value: 'workflow', label: t('system.workflow') },
  { value: 'report', label: t('dashboard.reportMgmt') },
  { value: 'user', label: t('route.user') },
  { value: 'role', label: t('route.role') },
  { value: 'system', label: t('system.systemConfig') }
]

// 与后端 app.file.* 一致的客户端提示与预校验（权威校验仍在后端）
const maxSizeMB = 10
const maxBytes = maxSizeMB * 1024 * 1024
const allowedExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'zip', 'rar', '7z']
const allowedExtHint = allowedExts.join(' / ')

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const uploading = ref(false)
const uploadRef = ref()
const selectedRows = ref([])

// 存储统计（V50）：总量 / 占用空间 / 按来源模块拆分
const statsLoading = ref(false)
const stats = reactive({ totalCount: 0, totalSize: 0, modules: [] })

async function loadStats() {
  statsLoading.value = true
  try {
    const s = await fileStats()
    stats.totalCount = s.totalCount || 0
    stats.totalSize = s.totalSize || 0
    stats.modules = s.modules || []
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    statsLoading.value = false
  }
}

const uploadForm = reactive({ sourceModule: '', bizId: '' })
const query = reactive({
  page: 1,
  size: 10, // 默认每页条数统一为 10
  sourceModule: '',
  uploaderName: '',
  originalName: '',
  sortField: '',
  sortDirection: ''
})

/** 统一把筛选条件映射为查询参数（空值一律 undefined，不传空串）。 */
function buildParams() {
  return {
    page: query.page,
    size: query.size,
    sourceModule: query.sourceModule || undefined,
    uploaderName: query.uploaderName || undefined,
    originalName: query.originalName || undefined,
    sortField: query.sortField || undefined,
    sortDirection: query.sortDirection || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const page = await pageFiles(buildParams())
    rows.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function resetQuery() {
  query.sourceModule = ''
  query.uploaderName = ''
  query.originalName = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  load()
}

/** 每页条数变化后回到第一页。 */
function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

/** 表头排序：走服务端白名单（fileSize/downloadCount/createdAt），不拼 SQL。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function onExceed() {
  ElMessage.warning(t('system.oneFileOnly'))
}

/** 上传：先做客户端预校验（模块必填 / 大小 / 扩展名），再秒传预检，最后调用后端公共上传接口。 */
async function onSubmit() {
  if (!uploadForm.sourceModule) {
    ElMessage.warning(t('system.selectOrInputModule'))
    return
  }
  const fileList = uploadRef.value?.uploadFiles || []
  const picked = fileList[0]?.raw
  if (!picked) {
    ElMessage.warning(t('system.selectFileFirst'))
    return
  }
  if (picked.size > maxBytes) {
    ElMessage.error(t('system.fileSizeExceeded', { maxSizeMB }))
    return
  }
  const ext = (picked.name.split('.').pop() || '').toLowerCase()
  if (!allowedExts.includes(ext)) {
    ElMessage.error(t('system.unsupportedType', { ext }))
    return
  }
  await precheckDedup(picked)
  uploading.value = true
  try {
    const view = await uploadFile(picked, uploadForm.sourceModule, uploadForm.bizId || undefined)
    ElMessage.success(view?.deduplicated ? t('system.uploadSuccessDedup') : t('system.uploadSuccess'))
    uploadRef.value?.clearFiles()
    uploadForm.bizId = ''
    query.page = 1
    load()
    loadStats()
  } catch (e) {
    // 响应拦截器已弹错误提示（含后端 FILE_* 业务码）
  } finally {
    uploading.value = false
  }
}

/**
 * 秒传预检（V50）：安全上下文（https/localhost）下用 Web Crypto 计算 SHA-256 回传服务端预检，
 * 命中存量文件时提示将复用（后端上传时仍会计算并去重，预检仅改善体验）。
 * 非安全上下文 crypto.subtle 不可用，静默降级跳过。
 */
async function precheckDedup(picked) {
  if (!window.crypto?.subtle) return
  try {
    const buf = await picked.arrayBuffer()
    const digest = await window.crypto.subtle.digest('SHA-256', buf)
    const hex = Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, '0')).join('')
    const res = await checkDedup(hex)
    if (res?.exists) {
      ElMessage.info(t('system.sameFileInstant', { name: res.originalName || '' }))
    }
  } catch (e) {
    // 预检失败不阻断上传（后端仍会计算哈希去重）
  }
}

/** 下载：Blob 处理统一走 utils/download（业务错误 JSON 已在其中提示）。 */
async function onDownload(row) {
  try {
    const resp = await downloadFile(row.id)
    const ok = await saveBlobFromResponse(resp, row.originalName || `file-${row.id}`, '', t('codegen.downloadFailed'))
    if (!ok) return
    // 下载计数由后端自增，刷新列表以展示最新下载次数
    load()
  } catch (e) {
    // 响应拦截器已弹错误提示
  }
}

async function onDelete(row) {
  try {
    await deleteFile(row.id)
    // 文案统一为「已删除」，与其余列表页一致
    ElMessage.success(t('common.deleted'))
    load()
    loadStats()
  } catch (e) {
    // 响应拦截器已弹错误提示
  }
}

/** 表格选择变化（批量删除）。 */
function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

/**
 * 批量删除（V50，部分成功语义）：逐项失败不影响其余，按成功数 / 失败明细分别提示。
 * 单次上限 100（后端 FILE_007 拦截）。
 */
async function onBatchDelete() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(t('system.confirmDeleteFiles', { count: selectedRows.value.length }), t('system.batchDelete'), { type: 'warning' })
  } catch (e) {
    return // 用户取消
  }
  try {
    const res = await batchDeleteFiles(selectedRows.value.map((r) => r.id))
    const failed = res?.failures || []
    if (failed.length) {
      ElMessage.warning(t('system.deletedFilesSummary', { deletedCount: res.deletedCount, failedCount: failed.length, details: failed.map((f) => `#${f.id} ${f.reason}`).join('；') }))
    } else {
      ElMessage.success(t('system.deletedFiles', { count: res.deletedCount }))
    }
    load()
    loadStats()
  } catch (e) {
    // 响应拦截器已弹错误提示（含 FILE_006/007）
  }
}

const fmtSize = (bytes) => {
  if (bytes == null) return '--'
  const n = Number(bytes)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}
const fmtTime = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '--')

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
/* .card-title / toolbar 系列（.toolbar / .toolbar-filters / .filter-group /
   .filter-label / .toolbar-actions / .action-buttons / .pager）已统一到全局 styles.css，
   此处仅保留页面特有规则 */
.upload-form :deep(.el-form-item) { margin-bottom: 0; }
.muted { color: #c0c4cc; }
.el-upload__tip { line-height: 1.4; color: #a8abb2; font-size: 12px; }

/* 存储统计卡（V50） */
.stats-row { display: flex; flex-wrap: wrap; gap: 24px; align-items: center; }
.stat-item { min-width: 110px; }
.stat-value { font-size: 20px; font-weight: 600; color: var(--text); line-height: 1.2; }
.stat-label { font-size: 12px; color: #a8abb2; margin-top: 4px; }
.stat-modules { flex: 1; display: flex; flex-wrap: wrap; gap: 8px; }
</style>
