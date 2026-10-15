<template>
  <div>
    <h2 class="page-title">文件管理</h2>

    <!-- 上传区：记录来源功能模块与上传人（上传人由后端取当前登录用户，前端不传） -->
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title">上传文件</span>
      </template>
      <el-form :inline="true" class="upload-form" @submit.prevent>
        <el-form-item label="来源功能模块" required>
          <el-select
            v-model="uploadForm.sourceModule"
            placeholder="选择或输入来源模块"
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
        <el-form-item label="业务关联 ID">
          <el-input v-model="uploadForm.bizId" placeholder="可选，如流程实例/报表 ID" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="文件">
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
              <el-button :icon="Upload">选择文件</el-button>
            </template>
            <template #tip>
              <div class="el-upload__tip">
                大小上限 {{ maxSizeMB }}MB；支持类型：{{ allowedExtHint }}
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="UploadFilled" :loading="uploading" @click="onSubmit">上传</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">来源功能模块</span>
            <el-select v-model="query.sourceModule" placeholder="全部" clearable filterable style="width: 180px">
              <el-option
                v-for="opt in sourceModuleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">上传人</span>
            <el-input v-model="query.uploaderName" placeholder="上传人（模糊）" clearable style="width: 160px" @keyup.enter="load" />
          </div>
          <div class="filter-group">
            <span class="filter-label">文件名</span>
            <el-input v-model="query.originalName" placeholder="文件名（模糊）" clearable style="width: 180px" @keyup.enter="load" />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
          </div>
        </div>
      </div>

      <!-- 明细表格 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sourceModule" label="来源功能模块" width="150">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.sourceModule || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="140" />
        <el-table-column prop="fileSize" label="大小" width="120" sortable="custom">
          <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.contentType">{{ row.contentType }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="downloadCount" label="下载次数" width="110" sortable="custom" />
        <el-table-column prop="createdAt" label="上传时间" width="180" sortable="custom" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Download" @click="onDownload(row)">下载</el-button>
            <el-popconfirm title="确认删除该文件？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50]"
        @change="load"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft, Upload, UploadFilled, Download, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageFiles, uploadFile, downloadFile, deleteFile } from '../../api/file'

// 来源功能模块候选（业务模块调用时也可自由输入，allow-create 兜底）
const sourceModuleOptions = [
  { value: 'file-management', label: '文件管理' },
  { value: 'workflow', label: '工作流' },
  { value: 'report', label: '报表管理' },
  { value: 'user', label: '用户管理' },
  { value: 'role', label: '角色管理' },
  { value: 'system', label: '系统配置' }
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

const uploadForm = reactive({ sourceModule: '', bizId: '' })
const query = reactive({
  page: 1,
  size: 20,
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

/** 表头排序：走服务端白名单（fileSize/downloadCount/createdAt），不拼 SQL。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function onExceed() {
  ElMessage.warning('一次仅可上传一个文件，请先移除已选文件')
}

/** 上传：先做客户端预校验（模块必填 / 大小 / 扩展名），再调用后端公共上传接口。 */
async function onSubmit() {
  if (!uploadForm.sourceModule) {
    ElMessage.warning('请选择或输入来源功能模块')
    return
  }
  const fileList = uploadRef.value?.uploadFiles || []
  const picked = fileList[0]?.raw
  if (!picked) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (picked.size > maxBytes) {
    ElMessage.error(`文件大小不能超过 ${maxSizeMB}MB`)
    return
  }
  const ext = (picked.name.split('.').pop() || '').toLowerCase()
  if (!allowedExts.includes(ext)) {
    ElMessage.error(`不支持的文件类型：.${ext}`)
    return
  }
  uploading.value = true
  try {
    await uploadFile(picked, uploadForm.sourceModule, uploadForm.bizId || undefined)
    ElMessage.success('上传成功')
    uploadRef.value?.clearFiles()
    uploadForm.bizId = ''
    query.page = 1
    load()
  } catch (e) {
    // 响应拦截器已弹错误提示（含后端 FILE_* 业务码）
  } finally {
    uploading.value = false
  }
}

/** 下载：沿用 CodegenView 防御式处理——区分文件 Blob 与业务错误 JSON。 */
async function onDownload(row) {
  try {
    const resp = await downloadFile(row.id)
    const data = resp?.data
    const blob = data instanceof Blob ? data : new Blob([data])
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      let msg = '下载失败'
      try {
        const obj = JSON.parse(text)
        msg = obj?.message || msg
      } catch (_) { /* ignore */ }
      ElMessage.error(msg)
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.originalName || `file-${row.id}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    // 下载计数由后端自增，刷新列表以展示最新下载次数
    load()
  } catch (e) {
    // 响应拦截器已弹错误提示
  }
}

async function onDelete(row) {
  try {
    await deleteFile(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    // 响应拦截器已弹错误提示
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

onMounted(load)
</script>

<style scoped>
.card-title { font-weight: 600; }
.upload-form :deep(.el-form-item) { margin-bottom: 0; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.toolbar-filters { display: flex; flex-wrap: wrap; gap: 12px; }
.filter-group { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 13px; color: #606266; white-space: nowrap; }
.toolbar-actions { display: flex; align-items: center; }
.action-buttons { display: flex; gap: 8px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.muted { color: #c0c4cc; }
.el-upload__tip { line-height: 1.4; color: #a8abb2; font-size: 12px; }
</style>
