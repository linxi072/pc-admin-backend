<template>
  <div>
    <h2 class="page-title">导入中心</h2>

    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title">导入模板</span>
      </template>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="displayName" label="模板名称" min-width="140" />
        <el-table-column prop="code" label="编码" width="140">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="表头列" min-width="280">
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
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              :icon="Download"
              :loading="downloading === row.code + ':XLSX'"
              @click="onDownloadTemplate(row, 'XLSX')"
            >下载模板 XLSX</el-button>
            <el-tooltip v-if="row.multiSegment" content="多段模板仅支持 XLSX（含多工作表）" placement="top">
              <el-button :icon="Download" disabled>CSV</el-button>
            </el-tooltip>
            <el-button
              v-else
              :icon="Download"
              :loading="downloading === row.code + ':CSV'"
              @click="onDownloadTemplate(row, 'CSV')"
            >CSV</el-button>
            <el-button type="success" :icon="Upload" @click="openImport(row)">导入</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="780px" @closed="resetDialog">
      <el-alert type="info" :closable="false" show-icon class="dialog-tip">
        <template #title>
          请先下载模板，严格按模板表头填写后上传（支持 CSV / XLSX）。建议先「预检」确认数据无误，再「确认导入」。
          <template v-if="current && current.multiSegment">
            <br />本模板为<b>多段结构</b>，仅支持 XLSX：每个工作表对应一段数据，工作表名须与模板完全一致。
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
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .csv / .xlsx 单文件；表头须与模板完全一致</div>
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
          {{ result.dryRun ? '预检完成' : '导入完成' }}：
          <span v-if="result.totalRows != null">共 {{ result.totalRows }} 行，</span>
          <span v-if="result.successCount != null">成功 {{ result.successCount }} 行，</span>
          失败 {{ result.failureCount }} 行
          <span v-if="result.reportDownloaded">（明细如下，完整报告已下载为 CSV）</span>
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
        <el-table-column v-if="resultHasSegment" prop="segment" label="数据段" width="120" />
        <el-table-column prop="rowNo" label="数据行号" width="110" />
        <el-table-column prop="message" label="失败原因" />
      </el-table>

      <div class="submit-row">
        <el-checkbox v-model="withReport">失败时下载错误报告（CSV，含失败行原始值）</el-checkbox>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button :icon="View" :loading="submitting === 'dry'" @click="onSubmit(true)">预检</el-button>
        <el-button
          type="primary"
          :icon="Upload"
          :loading="submitting === 'real'"
          @click="onSubmit(false)"
        >确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Download, Upload, UploadFilled, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listImportTemplates, downloadImportTemplate, uploadImportFile, uploadImportFileRaw } from '@/api/import'
// Blob 下载统一到 utils/download
import { saveBlobFromResponse, saveBlob, parseFileName } from '@/utils/download'

const loading = ref(false)
const rows = ref([])
const downloading = ref(null)

const dialogVisible = ref(false)
const current = ref(null)
const fileList = ref([])
const selectedFile = ref(null)
const submitting = ref(null)
const result = ref(null)
const withReport = ref(true)

const dialogTitle = computed(() =>
  current.value ? `导入数据 - ${current.value.displayName}` : '导入数据')

// 失败明细是否含「数据段」列（多段导入的报告才有）
const resultHasSegment = computed(() =>
  !!(result.value && result.value.errors && result.value.errors.some((e) => e.segment)))

async function load() {
  loading.value = true
  try {
    rows.value = await listImportTemplates()
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

/** 下载模板：Blob 处理统一走 utils/download（业务错误 JSON 已在其中提示）。 */
async function onDownloadTemplate(row, format) {
  downloading.value = `${row.code}:${format}`
  try {
    const resp = await downloadImportTemplate(row.code, format)
    const ok = await saveBlobFromResponse(
      resp,
      `导入模板-${row.displayName}`,
      format === 'XLSX' ? 'xlsx' : 'csv',
      '模板下载失败'
    )
    if (ok) ElMessage.success('模板下载成功')
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
    ElMessage.warning('请先选择要导入的文件')
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
    ElMessage.warning(`${dryRun ? '预检' : '导入'}完成：成功 ${res.successCount} 行，失败 ${res.failureCount} 行`)
  } else {
    ElMessage.success(dryRun ? `预检通过：${res.successCount} 行均可导入` : `导入成功：${res.successCount} 行`)
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
    ElMessage.error('导入响应格式异常')
    return true
  }
  // CSV 失败报告附件：触发下载，并解析报告内容回填结果区——
  // 早期该路径直接 return，对话框内的统计与失败明细表完全不展示，用户仅得一条 toast
  const fileName = parseFileName(resp, `导入失败报告-${current.value.displayName}`, 'csv')
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
  ElMessage.warning(`存在失败行（明细 ${errors.length} 条），错误报告已下载；请按报告修复后重新导入`)
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
  const segIdx = header.indexOf('数据段')
  const noIdx = header.indexOf('数据行号')
  const msgIdx = header.indexOf('失败原因')
  const data = rows.slice(1).filter((r) => r.some((x) => x !== ''))
  const errors = data.map((r) => ({
    segment: segIdx >= 0 ? (r[segIdx] || '') : '',
    rowNo: Number(r[noIdx]),
    message: (r[msgIdx] || '').replace(/^'/, '')
  })).filter((e) => !Number.isNaN(e.rowNo))
  return { errors }
}

onMounted(load)
</script>

<style scoped>
/* .card-title 已统一到全局 styles.css */
.header-tag { margin: 2px 6px 2px 0; }
.dialog-tip { margin-bottom: 14px; }
.result-tip { margin-top: 14px; }
.error-table { margin-top: 10px; }
.submit-row { margin-top: 12px; }
</style>
