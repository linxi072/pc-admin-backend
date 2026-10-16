<template>
  <div>
    <h2 class="page-title">导出中心</h2>

    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title">导出模板</span>
      </template>

      <!-- 文件管理列表导出：附筛选条件 -->
      <div v-if="filesTemplate" class="filter-bar">
        <span class="filter-label">来源功能模块</span>
        <el-select v-model="query.sourceModule" placeholder="全部" clearable filterable style="width: 180px">
          <el-option
            v-for="opt in sourceModuleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="filter-label">上传人</span>
        <el-input
          v-model="query.uploaderName"
          placeholder="上传人（模糊）"
          clearable
          style="width: 160px"
          @keyup.enter="onExport(filesTemplate)"
        />
        <span class="filter-label">文件名</span>
        <el-input
          v-model="query.originalName"
          placeholder="文件名（模糊）"
          clearable
          style="width: 180px"
          @keyup.enter="onExport(filesTemplate)"
        />
      </div>

      <!-- 报表管理导出：附报表类型 / 统计窗口 / 关键字（与报表管理页同一数据与口径） -->
      <div v-else-if="reportTemplate" class="filter-bar">
        <span class="filter-label">报表类型</span>
        <el-select v-model="query.reportType" style="width: 180px" @keyup.enter="onExport(reportTemplate)">
          <el-option
            v-for="opt in reportTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="filter-label">统计窗口</span>
        <el-select v-model="query.reportRange" style="width: 140px" @keyup.enter="onExport(reportTemplate)">
          <el-option label="近 7 天" value="LAST_7D" />
          <el-option label="近 30 天" value="LAST_30D" />
        </el-select>
        <span class="filter-label">关键字</span>
        <el-input
          v-model="query.reportKeyword"
          :placeholder="reportKeywordPlaceholder"
          clearable
          style="width: 180px"
          @keyup.enter="onExport(reportTemplate)"
        />
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="displayName" label="模板名称" min-width="200" />
        <el-table-column prop="fileName" label="文件名" width="200" />
        <el-table-column prop="code" label="编码" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting === row.code + ':CSV'"
              @click="onExport(row, 'CSV')"
            >导出 CSV</el-button>
            <!-- 按模板声明渲染双格式按钮（formats 由后端 /templates 返回，单表与多段模板均可声明 XLSX） -->
            <el-button
              v-if="supportsXlsx(row)"
              type="success"
              :icon="Download"
              :loading="exporting === row.code + ':XLSX'"
              @click="onExport(row, 'XLSX')"
            >导出 XLSX</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listExportTemplates, downloadExport } from '@/api/export'
// Blob 下载统一到 utils/download
import { saveBlobFromResponse } from '@/utils/download'

const loading = ref(false)
const rows = ref([])
const exporting = ref(null)

const query = reactive({
  sourceModule: '',
  uploaderName: '',
  originalName: '',
  reportType: 'USAGE',
  reportRange: 'LAST_7D',
  reportKeyword: ''
})

// 与后端 app.file.* 一致的来源模块候选（业务模块也可在上传时自由输入）
const sourceModuleOptions = [
  { value: 'file-management', label: '文件管理' },
  { value: 'workflow', label: '工作流' },
  { value: 'report', label: '报表管理' },
  { value: 'user', label: '用户管理' },
  { value: 'role', label: '角色管理' },
  { value: 'system', label: '系统配置' }
]

// 报表类型候选（与后端 ReportType 枚举 label 对齐）
const reportTypeOptions = [
  { value: 'USAGE', label: '使用统计' },
  { value: 'CATEGORY', label: '类别统计' },
  { value: 'STATUS', label: '状态统计' },
  { value: 'JOB_EXEC', label: '定时任务执行统计' },
  { value: 'LOGIN', label: '登录统计' },
  { value: 'API_CALL', label: '接口调用统计' }
]

// 关键字占位提示（与后端按类型作用字段一致：任务名 / 用户名 / 请求路径；使用视角忽略）
const reportKeywordPlaceholder = computed(() => {
  if (query.reportType === 'JOB_EXEC') return '任务名称关键字'
  if (query.reportType === 'LOGIN') return '用户名关键字'
  if (query.reportType === 'API_CALL') return '请求路径关键字'
  return '关键字（该类型不筛选）'
})

const filesTemplate = computed(() => rows.value.find((r) => r.code === 'files'))
const reportTemplate = computed(() => rows.value.find((r) => r.code === 'report'))

async function load() {
  loading.value = true
  try {
    rows.value = await listExportTemplates()
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

function buildParams(row, format) {
  if (row.code === 'files') {
    return {
      code: 'files',
      format,
      sourceModule: query.sourceModule || undefined,
      uploaderName: query.uploaderName || undefined,
      originalName: query.originalName || undefined
    }
  }
  if (row.code === 'report') {
    return {
      code: 'report',
      format,
      type: query.reportType || undefined,
      rangeType: query.reportRange || undefined,
      keyword: query.reportKeyword || undefined
    }
  }
  return { code: row.code, format }
}

/** 模板是否支持 XLSX（由后端 formats 声明：单表结构默认支持，多段结构提供 xlsxSheets 后支持）。 */
function supportsXlsx(row) {
  return Array.isArray(row.formats) ? row.formats.includes('XLSX') : false
}

/** 导出：Blob 处理统一走 utils/download（业务错误 JSON 已在其中提示）。 */
async function onExport(row, format = 'CSV') {
  exporting.value = `${row.code}:${format}`
  try {
    const resp = await downloadExport(buildParams(row, format))
    const ok = await saveBlobFromResponse(
      resp,
      row.fileName || 'export',
      format === 'XLSX' ? 'xlsx' : 'csv',
      '导出失败'
    )
    if (ok) ElMessage.success('导出成功')
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    exporting.value = null
  }
}

onMounted(load)
</script>

<style scoped>
/* .card-title 已统一到全局 styles.css */
.filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 14px; }
.filter-label { font-size: 13px; color: #606266; white-space: nowrap; }
</style>
