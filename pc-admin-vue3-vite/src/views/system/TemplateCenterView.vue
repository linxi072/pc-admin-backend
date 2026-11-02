<template>
  <div>
    <h2 class="page-title">{{ $t('templateCenter.title') }}</h2>

    <el-tabs v-model="activeTab" class="tpl-tabs">
      <!-- ============ 导出模板 ============ -->
      <el-tab-pane name="export" :label="$t('templateCenter.tabExport')">
        <el-card class="page-card" shadow="never">
          <template #header>
            <span class="card-title">{{ $t('templateCenter.tabExport') }}</span>
          </template>

          <!-- 文件管理列表导出：附筛选条件 -->
          <div v-if="filesTemplate" class="filter-bar">
            <span class="filter-label">{{ t('system.sourceModule') }}</span>
            <el-select v-model="query.sourceModule" :placeholder="t('common.all')" clearable filterable style="width: 180px">
              <el-option
                v-for="opt in sourceModuleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="filter-label">{{ t('system.uploader') }}</span>
            <el-input
              v-model="query.uploaderName"
              :placeholder="t('system.uploaderBlurred')"
              clearable
              style="width: 160px"
              @keyup.enter="onExport(filesTemplate)"
            />
            <span class="filter-label">{{ t('system.fileName') }}</span>
            <el-input
              v-model="query.originalName"
              :placeholder="t('system.fileNameBlurred')"
              clearable
              style="width: 180px"
              @keyup.enter="onExport(filesTemplate)"
            />
          </div>

          <!-- 报表管理导出：附报表类型 / 统计窗口 / 关键字（与报表管理页同一数据与口径） -->
          <div v-else-if="reportTemplate" class="filter-bar">
            <span class="filter-label">{{ t('system.reportType') }}</span>
            <el-select v-model="query.reportType" style="width: 180px" @keyup.enter="onExport(reportTemplate)">
              <el-option
                v-for="opt in reportTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="filter-label">{{ t('report.windowHint') }}</span>
            <el-select v-model="query.reportRange" style="width: 140px" @keyup.enter="onExport(reportTemplate)">
              <el-option :label="t('reportDashboard.last7Days')" value="LAST_7D" />
              <el-option :label="t('reportDashboard.last30Days')" value="LAST_30D" />
            </el-select>
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input
              v-model="query.reportKeyword"
              :placeholder="reportKeywordPlaceholder"
              clearable
              style="width: 180px"
              @keyup.enter="onExport(reportTemplate)"
            />
          </div>

          <el-table :data="exportRows" v-loading="exportLoading" border stripe>
            <el-table-column prop="displayName" :label="t('system.templateName')" min-width="200" />
            <el-table-column prop="fileName" :label="t('system.fileName')" width="200" />
            <el-table-column prop="code" :label="t('system.code')" width="160">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="260" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  :icon="Download"
                  :loading="exporting === row.code + ':CSV'"
                  @click="onExport(row, 'CSV')"
                >{{ t('report.exportCsv') }}</el-button>
                <!-- 按模板声明渲染双格式按钮（formats 由后端 /templates 返回，单表与多段模板均可声明 XLSX） -->
                <el-button
                  v-if="supportsXlsx(row)"
                  type="success"
                  :icon="Download"
                  :loading="exporting === row.code + ':XLSX'"
                  @click="onExport(row, 'XLSX')"
                >{{ t('system.exportXlsx') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ 导入模板 ============ -->
      <el-tab-pane name="import" :label="$t('templateCenter.tabImport')">
        <el-card class="page-card" shadow="never">
          <template #header>
            <span class="card-title">{{ $t('templateCenter.tabImport') }}</span>
          </template>

          <el-table :data="importRows" v-loading="importLoading" border stripe>
            <el-table-column prop="displayName" :label="t('system.templateName')" min-width="140" />
            <el-table-column prop="code" :label="t('system.code')" width="140">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('system.headerColumns')" min-width="280">
              <template #default="{ row }">
                <template v-if="row.multiSegment">
                  <div v-for="seg in row.segments" :key="seg.name" class="segment-block">
                    <el-tag size="small" type="warning" class="segment-name">{{ seg.name }}</el-tag>
                    <el-tag v-for="h in seg.headers" :key="h" size="small" class="header-tag">{{ h }}</el-tag>
                  </div>
                </template>
                <template v-else>
                  <el-tag v-for="h in row.headers" :key="h" size="small" class="header-tag">{{ h }}</el-tag>
                </template>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="360" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  :icon="Download"
                  :loading="downloading === row.code + ':XLSX'"
                  @click="onDownloadTemplate(row, 'XLSX')"
                >{{ t('system.downloadXlsxTemplate') }}</el-button>
                <el-tooltip v-if="row.multiSegment" :content="t('system.multiSegmentTemplatesSupportXlsxOnlyMultipleSheets')" placement="top">
                  <el-button :icon="Download" disabled>CSV</el-button>
                </el-tooltip>
                <el-button
                  v-else
                  :icon="Download"
                  :loading="downloading === row.code + ':CSV'"
                  @click="onDownloadTemplate(row, 'CSV')"
                >CSV</el-button>
                <el-button type="success" :icon="Upload" @click="openImport(row)">{{ t('common.import') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 导入对话框（跨 Tab 复用，仅导入面板触发） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="780px" @closed="resetDialog">
      <el-alert type="info" :closable="false" show-icon class="dialog-tip">
        <template #title>
          请先下载模板，严格按模板表头填写后上传（支持 CSV / XLSX）。建议先「预检」确认数据无误，再「确认导入」。
          <template v-if="current && current.multiSegment">
            <br />{{ t('system.thisTemplateIs') }}<b>{{ t('system.multiSegmentStructure') }}</b>，仅支持 XLSX：每个工作表对应一段数据，工作表名须与模板完全一致。
          </template>
        </template>
      </el-alert>

      <!-- auto-upload=false：文件由本页在用户点击「预检/确认导入」时统一提交，便于二次确认 -->
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        :file-list="fileList"
        accept=".csv,.xlsx"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        :on-exceed="onExceed"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ t('system.dragTheFileHereOr') }}<em>{{ t('system.clickToSelect') }}</em></div>
        <template #tip>
          <div class="el-upload__tip">{{ t('system.onlyASingleCsvXlsxFileHeaderMustExactlyMatchTheTemplate') }}</div>
        </template>
      </el-upload>

      <el-alert
        v-if="result"
        class="result-tip"
        :closable="false"
        show-icon
        :type="result.failureCount > 0 ? 'warning' : 'success'"
      >
        <template #title>
          {{ result.dryRun ? t('system.preCheckComplete') : t('system.importComplete') }}：
          <span v-if="result.totalRows != null">共 {{ result.totalRows }} 行，</span>
          <span v-if="result.successCount != null">成功 {{ result.successCount }} 行，</span>
          失败 {{ result.failureCount }} 行
          <span v-if="result.reportDownloaded">{{ t('system.detailsBelowFullReportDownloadedAsCsv') }}</span>
          <span v-else-if="result.errors.length < result.failureCount">
            （下方仅展示前 {{ result.errors.length }} 条失败明细）
          </span>
          <template v-if="result.segments && result.segments.length > 1">
            <div v-for="seg in result.segments" :key="seg.name" class="seg-summary">
              · {{ seg.name }}：{{ seg.totalRows }} 行 / 成功 {{ seg.successCount }} / 失败 {{ seg.failureCount }}
            </div>
          </template>
        </template>
      </el-alert>

      <el-table
        v-if="result && result.errors.length"
        :data="result.errors"
        border
        stripe
        size="small"
        max-height="260"
        class="error-table"
      >
        <el-table-column v-if="resultHasSegment" prop="segment" :label="t('system.dataSegment')" width="120" />
        <el-table-column prop="rowNo" :label="t('system.dataRowNumber')" width="110" />
        <el-table-column prop="message" :label="t('monitor.failReason')" />
      </el-table>

      <div class="submit-row">
        <el-checkbox v-model="withReport">{{ t('system.onFailureDownloadAnErrorReportCsvWithRawFailedRows') }}</el-checkbox>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.close') }}</el-button>
        <el-button :icon="View" :loading="submitting === 'dry'" @click="onSubmit(true)">{{ t('system.preCheck') }}</el-button>
        <el-button
          type="primary"
          :icon="Upload"
          :loading="submitting === 'real'"
          @click="onSubmit(false)"
        >{{ t('system.confirmImport') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Download, Upload, UploadFilled, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listExportTemplates, downloadExport } from '@/api/export'
import {
  listImportTemplates,
  downloadImportTemplate,
  uploadImportFile,
  uploadImportFileRaw
} from '@/api/import'
// Blob 下载统一到 utils/download
import { saveBlobFromResponse, saveBlob, parseFileName } from '@/utils/download'

const route = useRoute()
// 由路由 meta.defaultTab 决定默认激活的 Tab（system/export → export，system/import → import）
const activeTab = ref(route.meta.defaultTab === 'import' ? 'import' : 'export')

// ===================== 导出模板 =====================
const exportLoading = ref(false)
const exportRows = ref([])
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
  { value: 'file-management', label: t('route.file') },
  { value: 'workflow', label: t('system.workflow') },
  { value: 'report', label: t('dashboard.reportMgmt') },
  { value: 'user', label: t('route.user') },
  { value: 'role', label: t('route.role') },
  { value: 'system', label: t('system.systemConfig') }
]

// 报表类型候选（与后端 ReportType 枚举 label 对齐）
const reportTypeOptions = [
  { value: 'USAGE', label: t('system.usageStats') },
  { value: 'CATEGORY', label: t('system.categoryStats') },
  { value: 'STATUS', label: t('system.statusStats') },
  { value: 'JOB_EXEC', label: t('route.reportJobExec') },
  { value: 'LOGIN', label: t('route.reportLogin') },
  { value: 'API_CALL', label: t('route.reportApiCall') }
]

// 关键字占位提示（与后端按类型作用字段一致：任务名 / 用户名 / 请求路径；使用视角忽略）
const reportKeywordPlaceholder = computed(() => {
  if (query.reportType === 'JOB_EXEC') return t('report.taskNameKeyword')
  if (query.reportType === 'LOGIN') return t('report.usernameKeyword')
  if (query.reportType === 'API_CALL') return t('report.pathKeyword')
  return t('system.keywordNotFilteredForThisType')
})

const filesTemplate = computed(() => exportRows.value.find((r) => r.code === 'files'))
const reportTemplate = computed(() => exportRows.value.find((r) => r.code === 'report'))

async function loadExport() {
  exportLoading.value = true
  try {
    exportRows.value = await listExportTemplates()
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    exportLoading.value = false
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
      t('report.exportFailed')
    )
    if (ok) ElMessage.success(t('system.exported'))
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    exporting.value = null
  }
}

// ===================== 导入模板 =====================
const importLoading = ref(false)
const importRows = ref([])
const downloading = ref(null)

const dialogVisible = ref(false)
const current = ref(null)
const fileList = ref([])
const selectedFile = ref(null)
const submitting = ref(null)
const result = ref(null)
const withReport = ref(true)

const dialogTitle = computed(() =>
  current.value ? t('system.importDataOf', { name: current.value.displayName }) : t('system.importData')
)

// 失败明细是否含「数据段」列（多段导入的报告才有）
const resultHasSegment = computed(() =>
  !!(result.value && result.value.errors && result.value.errors.some((e) => e.segment))
)

async function loadImport() {
  importLoading.value = true
  try {
    importRows.value = await listImportTemplates()
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    importLoading.value = false
  }
}

/** 下载模板：Blob 处理统一走 utils/download（业务错误 JSON 已在其中提示）。 */
async function onDownloadTemplate(row, format) {
  downloading.value = `${row.code}:${format}`
  try {
    const resp = await downloadImportTemplate(row.code, format)
    const ok = await saveBlobFromResponse(
      resp,
      t('system.importTemplateOf', { name: row.displayName }),
      format === 'XLSX' ? 'xlsx' : 'csv',
      t('system.templateDownloadFailed')
    )
    if (ok) ElMessage.success(t('system.templateDownloaded'))
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    downloading.value = null
  }
}

function openImport(row) {
  current.value = row
  dialogVisible.value = true
}

function resetDialog() {
  current.value = null
  fileList.value = []
  selectedFile.value = null
  result.value = null
  submitting.value = null
}

function onFileChange(uploadFile) {
  // limit=1 下仅保留最新选择的文件
  fileList.value = [uploadFile]
  selectedFile.value = uploadFile.raw
  result.value = null
}

function onFileRemove() {
  fileList.value = []
  selectedFile.value = null
  result.value = null
}

function onExceed(files) {
  // 超出 limit 时用新文件替换旧文件
  const file = files[0]
  fileList.value = [{ name: file.name, raw: file }]
  selectedFile.value = file
  result.value = null
}

async function onSubmit(dryRun) {
  if (!selectedFile.value) {
    ElMessage.warning(t('system.selectTheFileToImportFirst'))
    return
  }
  submitting.value = dryRun ? 'dry' : 'real'
  try {
    if (withReport.value) {
      const handled = await submitWithReport(dryRun)
      if (handled) return
    }
    const res = await uploadImportFile(current.value.code, selectedFile.value, dryRun)
    handleResult(res, dryRun)
  } catch (e) {
    // 响应拦截器已弹错误提示（如表头不匹配、格式不支持）
  } finally {
    submitting.value = null
  }
}

/** 展示导入/预检结果（统计 + 内联错误明细表）。 */
function handleResult(res, dryRun) {
  result.value = res
  if (res.failureCount > 0) {
    ElMessage.warning(`${dryRun ? t('system.precheckNoun') : t('system.importNoun')}t('system.importDone', { successCount: res.successCount, failureCount: res.failureCount })`)
  } else {
    ElMessage.success(dryRun ? t('system.precheckPassed', { n: res.successCount }) : t('system.importSuccessN', { n: res.successCount }))
  }
}

/**
 * 勾选「失败时下载错误报告」时走 raw 通道：
 * 存在失败行 → 后端返回 CSV 附件（含失败行原始值），直接下载；
 * 无失败行 → 后端仍返回 JSON 统计（Blob 内为 Result 包装，需自行拆 data）。
 * 返回 true 表示本通道已处理完毕（含下载或错误提示），调用方无需再走常规通道。
 */
async function submitWithReport(dryRun) {
  const resp = await uploadImportFileRaw(current.value.code, selectedFile.value, dryRun, true)
  const data = resp?.data
  const blob = data instanceof Blob ? data : new Blob([data])
  if (blob.type && blob.type.includes('application/json')) {
    const text = await blob.text()
    let payload = null
    let errMsg = ''
    try {
      const obj = JSON.parse(text)
      payload = obj?.data
      errMsg = obj?.message || ''
    } catch (_) { /* ignore */ }
    if (payload) {
      handleResult(payload, dryRun)
      return true
    }
    if (errMsg) {
      ElMessage.error(errMsg)
      return true
    }
    // 无法识别的响应体：交由外层提示
    ElMessage.error(t('system.abnormalImportResponseFormat'))
    return true
  }
  // CSV 失败报告附件：触发下载，并解析报告内容回填结果区——
  // 早期该路径直接 return，对话框内的统计与失败明细表完全不展示，用户仅得一条 toast
  const fileName = parseFileName(resp, t('system.importFailReport', { name: current.value.displayName }), 'csv')
  saveBlob(blob, fileName)
  const { errors } = parseImportErrorReport(await blob.text())
  result.value = {
    dryRun,
    totalRows: null,
    successCount: null,
    failureCount: errors.length,
    errors,
    reportDownloaded: true
  }
  ElMessage.warning(t('system.importHasErrors', { n: errors.length }))
  return true
}

/**
 * 解析导入失败报告 CSV。最小 RFC4180 解析：支持引号内逗号/换行/双引号转义；跳过 UTF-8 BOM；
 * 失败原因去掉后端防公式注入的 ' 前缀。
 * 报告表头按列名定位（兼容单段「数据行号/失败原因/...」与多段「数据段/数据行号/失败原因/...」两种布局）。
 */
function parseImportErrorReport(text) {
  const src = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text
  const rows = []
  let row = []
  let field = ''
  let inQuotes = false
  for (let i = 0; i < src.length; i++) {
    const c = src[i]
    if (inQuotes) {
      if (c === '"') {
        if (src[i + 1] === '"') {
          field += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        field += c
      }
    } else if (c === '"') {
      inQuotes = true
    } else if (c === ',') {
      row.push(field)
      field = ''
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && src[i + 1] === '\n') i++
      row.push(field)
      field = ''
      if (row.some((x) => x !== '')) rows.push(row)
      row = []
    } else {
      field += c
    }
  }
  if (field !== '' || row.length) {
    row.push(field)
    if (row.some((x) => x !== '')) rows.push(row)
  }
  if (rows.length < 1) return { errors: [] }
  const header = rows[0].map((h) => (h || '').trim())
  const segIdx = header.indexOf(t('system.dataSegment'))
  const noIdx = header.indexOf(t('system.dataRowNumber'))
  const msgIdx = header.indexOf(t('monitor.failReason'))
  const data = rows.slice(1).filter((r) => r.some((x) => x !== ''))
  const errors = data.map((r) => ({
    segment: segIdx >= 0 ? (r[segIdx] || '') : '',
    rowNo: Number(r[noIdx]),
    message: (r[msgIdx] || '').replace(/^'/, '')
  })).filter((e) => !Number.isNaN(e.rowNo))
  return { errors }
}

onMounted(() => {
  loadExport()
  loadImport()
})
</script>

<style scoped>
/* .card-title 已统一到全局 styles.css */
.tpl-tabs { margin-top: 4px; }
.filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 14px; }
.filter-label { font-size: 13px; color: #606266; white-space: nowrap; }
.header-tag { margin: 2px 6px 2px 0; }
.dialog-tip { margin-bottom: 14px; }
.result-tip { margin-top: 14px; }
.error-table { margin-top: 10px; }
.submit-row { margin-top: 12px; }
</style>
