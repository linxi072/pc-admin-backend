<template>
  <div>
    <h2 class="page-title">导出中心</h2>

    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title">导出模板</span>
      </template>

      <!-- 文件管理列表导出：附筛选条件（其他模板按需扩展） -->
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

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="displayName" label="模板名称" min-width="200" />
        <el-table-column prop="fileName" label="文件名" width="200" />
        <el-table-column prop="code" label="编码" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting === row.code"
              @click="onExport(row)"
            >导出 CSV</el-button>
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
import { listExportTemplates, downloadExport } from '../../api/export'

const loading = ref(false)
const rows = ref([])
const exporting = ref(null)

const query = reactive({ sourceModule: '', uploaderName: '', originalName: '' })

// 与后端 app.file.* 一致的来源模块候选（业务模块也可在上传时自由输入）
const sourceModuleOptions = [
  { value: 'file-management', label: '文件管理' },
  { value: 'workflow', label: '工作流' },
  { value: 'report', label: '报表管理' },
  { value: 'user', label: '用户管理' },
  { value: 'role', label: '角色管理' },
  { value: 'system', label: '系统配置' }
]

const filesTemplate = computed(() => rows.value.find((r) => r.code === 'files'))

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

function buildParams(row) {
  if (row.code === 'files') {
    return {
      code: 'files',
      sourceModule: query.sourceModule || undefined,
      uploaderName: query.uploaderName || undefined,
      originalName: query.originalName || undefined
    }
  }
  return { code: row.code }
}

/** 导出：沿用 FileManagementView 防御式处理——区分文件 Blob 与业务错误 JSON。 */
async function onExport(row) {
  exporting.value = row.code
  try {
    const resp = await downloadExport(buildParams(row))
    const data = resp?.data
    const blob = data instanceof Blob ? data : new Blob([data])
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      let msg = '导出失败'
      try {
        const obj = JSON.parse(text)
        msg = obj?.message || msg
      } catch (_) { /* ignore */ }
      ElMessage.error(msg)
      return
    }
    const fileName = parseFileName(resp, row.fileName)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    // 响应拦截器已弹错误提示
  } finally {
    exporting.value = null
  }
}

/** 优先解析 Content-Disposition 中的文件名，失败回退到模板文件名。 */
function parseFileName(resp, fallback) {
  const cd = resp?.headers?.['content-disposition'] || ''
  const m = cd.match(/filename\*=UTF-8''([^;]+)/i) || cd.match(/filename="?([^";]+)"?/i)
  if (m && m[1]) {
    try {
      return decodeURIComponent(m[1])
    } catch (_) {
      return m[1]
    }
  }
  return (fallback || 'export') + '.csv'
}

onMounted(load)
</script>

<style scoped>
.card-title { font-weight: 600; }
.filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 14px; }
.filter-label { font-size: 13px; color: #606266; white-space: nowrap; }
</style>
