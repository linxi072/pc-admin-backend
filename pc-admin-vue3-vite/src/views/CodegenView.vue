<template>
  <div>
    <h2 class="page-title">代码生成</h2>

    <el-alert class="mb-12" type="info" :closable="false" show-icon>
      <template #title>
        按数据库表结构生成 CRUD 骨架：后端六层（DO / VO / Query / Controller / Service / ServiceImpl）与前端两文件（Vue 页面 / api.js）。
        生成结果为<b>文本预览</b>，服务端不落盘——可单文件复制或打包 zip 下载后自行入库，业务规则需手工补齐。
      </template>
    </el-alert>

    <!-- 生成条件区 -->
    <el-card class="mb-12" shadow="never">
      <div class="toolbar-filters">
        <div class="filter-group">
          <span class="filter-label">数据表</span>
          <el-select
            v-model="form.tableName"
            placeholder="选择表（可搜索）"
            filterable
            clearable
            style="width: 260px"
            :loading="tablesLoading"
            @change="onTableChange"
          >
            <el-option v-for="t in tables" :key="t" :label="t" :value="t" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">模块名</span>
          <el-input v-model="form.moduleName" placeholder="如 user / order-item" style="width: 160px" />
        </div>
        <div class="filter-group">
          <span class="filter-label">功能名</span>
          <el-input v-model="form.functionName" placeholder="页面标题，如 用户管理" style="width: 160px" />
        </div>
        <div class="filter-group">
          <span class="filter-label">实体类名</span>
          <el-input v-model="form.className" placeholder="留空按表名推导" style="width: 140px" />
        </div>
        <div class="filter-group">
          <span class="filter-label">作者</span>
          <el-input v-model="form.author" placeholder="文件头注释作者" style="width: 120px" />
        </div>
        <div class="filter-group">
          <el-checkbox v-model="form.withFrontend">同时生成前端（Vue + api.js）</el-checkbox>
        </div>
      </div>

      <div class="toolbar-actions">
        <span class="window-hint">{{ tableHint }}</span>
        <div class="action-buttons">
          <el-button type="primary" :icon="MagicStick" :loading="previewing" :disabled="!form.tableName" @click="onPreview">
            生成预览
          </el-button>
          <el-button :icon="Download" :loading="downloading" :disabled="!form.tableName" @click="onDownload">
            打包下载 zip
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 字段元信息（选表后展示） -->
    <el-card v-if="meta" class="mb-12" shadow="never" header="字段元信息">
      <el-table :data="meta.columns" border stripe size="small" max-height="280">
        <el-table-column prop="columnName" label="列名" min-width="140" />
        <el-table-column prop="dataType" label="数据库类型" min-width="110" />
        <el-table-column prop="javaType" label="Java 类型" min-width="110" />
        <el-table-column prop="javaField" label="字段名" min-width="120" />
        <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
        <el-table-column label="主键" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.primaryKey" type="danger" size="small">PK</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="自增" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.autoIncrement" type="warning" size="small">AI</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="可空" width="70">
          <template #default="{ row }">
            <el-tag :type="row.nullable ? 'info' : 'success'" size="small">{{ row.nullable ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 生成结果预览 -->
    <el-card v-if="files.length" shadow="never">
      <template #header>
        <div class="preview-header">
          <span>生成预览（{{ files.length }} 个文件）</span>
          <el-button link type="primary" :icon="CopyDocument" @click="copyActive">复制当前文件</el-button>
        </div>
      </template>
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane v-for="f in files" :key="f.path" :name="f.path">
          <template #label>
            <span class="tab-label">
              <el-tag size="small" :type="langType(f.language)" effect="plain">{{ f.language }}</el-tag>
              {{ fileName(f.path) }}
            </span>
          </template>
          <div class="file-path">{{ f.path }}</div>
          <pre class="code-block"><code>{{ f.content }}</code></pre>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-empty v-else-if="!previewing" description="选择数据表并点击「生成预览」查看骨架代码" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Download, CopyDocument } from '@element-plus/icons-vue'
import { codegenTables, codegenTableMeta, codegenPreview, codegenDownload } from '@/api/codegen'

const tables = ref([])
const tablesLoading = ref(false)
const meta = ref(null)
const files = ref([])
const activeTab = ref('')
const previewing = ref(false)
const downloading = ref(false)

const form = reactive({
  tableName: '',
  moduleName: '',
  functionName: '',
  className: '',
  author: '',
  withFrontend: true
})

const tableHint = computed(() => {
  if (!meta.value) return '选择数据表后自动带出模块名 / 功能名 / 类名'
  return `表 ${meta.value.tableName}${meta.value.comment ? '（' + meta.value.comment + '）' : ''} · ${meta.value.columns.length} 个字段`
})

// 表名 → 模块名：去 sys_/t_ 前缀，下划线转连字符
const toModuleName = (tableName) =>
  tableName.replace(/^(sys_|t_)/, '').replace(/_/g, '-')

// 表名 → 类名：去 sys_/t_ 前缀，下划线转大驼峰
const toClassName = (tableName) => {
  const field = tableName.replace(/^(sys_|t_)/, '').replace(/_+/g, ' ').trim()
  if (!field) return ''
  return field.split(' ').map((s) => s.charAt(0).toUpperCase() + s.slice(1)).join('')
}

async function loadTables() {
  tablesLoading.value = true
  try {
    tables.value = await codegenTables()
  } catch (e) {
    // 响应拦截器已统一提示
  } finally {
    tablesLoading.value = false
  }
}

async function onTableChange(tableName) {
  meta.value = null
  if (!tableName) {
    form.moduleName = ''
    form.functionName = ''
    form.className = ''
    return
  }
  try {
    const m = await codegenTableMeta(tableName)
    meta.value = m
    // 自动带出：模块名 / 功能名（表注释）/ 类名（可覆盖）
    form.moduleName = toModuleName(tableName)
    form.functionName = m.comment || ''
    form.className = m.className || toClassName(tableName)
  } catch (e) { /* 已提示 */ }
}

function currentOption() {
  return {
    tableName: form.tableName,
    moduleName: form.moduleName,
    functionName: form.functionName,
    className: form.className,
    author: form.author,
    withFrontend: form.withFrontend
  }
}

async function onPreview() {
  previewing.value = true
  try {
    const list = await codegenPreview(currentOption())
    files.value = list || []
    activeTab.value = list && list.length ? list[0].path : ''
    if (!list || !list.length) ElMessage.warning('未生成任何文件')
  } catch (e) { /* 已提示 */ } finally {
    previewing.value = false
  }
}

async function onDownload() {
  downloading.value = true
  try {
    const resp = await codegenDownload(currentOption())
    // requestRaw 不拆包：业务错误时 content-type 为 application/json，需转文本提示
    const blob = resp.data instanceof Blob ? resp.data : new Blob([resp.data])
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      ElMessage.error(parseErrMsg(text))
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'codegen-' + form.tableName + '.zip'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已开始下载')
  } catch (e) { /* 已提示 */ } finally {
    downloading.value = false
  }
}

function parseErrMsg(text) {
  try {
    const j = JSON.parse(text)
    return j.message || '下载失败'
  } catch (e) {
    return '下载失败'
  }
}

async function copyActive() {
  const f = files.value.find((x) => x.path === activeTab.value)
  if (!f) return
  try {
    await navigator.clipboard.writeText(f.content)
    ElMessage.success('已复制 ' + fileName(f.path))
  } catch (e) {
    // 剪贴板不可用时的降级：临时 textarea
    const ta = document.createElement('textarea')
    ta.value = f.content
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制 ' + fileName(f.path))
  }
}

const fileName = (path) => path.substring(path.lastIndexOf('/') + 1)
const langType = (lang) => ({ JAVA: 'primary', VUE: 'success', JS: 'warning', SQL: 'info' }[lang] || 'info')

onMounted(loadTables)
</script>

<style scoped>
/* .page-title 已统一到全局 styles.css */
.mb-12 { margin-bottom: 12px; }
.window-hint { color: #8a919f; font-size: 13px; }
.preview-header { display: flex; align-items: center; justify-content: space-between; }
.tab-label { display: inline-flex; align-items: center; gap: 6px; }
.file-path { margin-bottom: 6px; color: #8a919f; font-size: 12px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.code-block {
  margin: 0;
  max-height: 560px;
  overflow: auto;
  padding: 12px 14px;
  background: #f6f8fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre;
}
</style>
