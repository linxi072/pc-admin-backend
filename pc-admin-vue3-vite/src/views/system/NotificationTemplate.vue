<template>
  <div>
    <h2 class="page-title">通知模板</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">渠道</span>
            <el-select v-model="query.channel" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="c in channelOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input
              v-model="query.keyword"
              placeholder="编码/名称/正文"
              clearable
              style="width: 220px"
              @keyup.enter="load"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
            <el-button type="success" :icon="Plus" @click="openCreate">新建模板</el-button>
          </div>
        </div>
      </div>

      <!-- 模板列表 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="templateCode" label="编码" width="180" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="templateName" label="名称" width="160" show-overflow-tooltip />
        <el-table-column label="渠道" width="120">
          <template #default="{ row }">
            <el-tag :type="channelTag(row.channel).type" size="small">{{ channelTag(row.channel).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.title">{{ row.title }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="正文（含 ${var} 占位）" min-width="240" show-overflow-tooltip />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="row.__toggling"
              @change="(val) => onToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openPreview(row)">预览</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该模板？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

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
      <div class="hint">
        模板为 M6 多渠道通知提供可复用文案：正文使用 <code>${变量名}</code> 占位，发送时由调用方渲染后投递。
        「预览」可按变量渲染并提示缺失变量；编码需为全大写字母/数字/下划线（如 ORDER_SHIPPED）。
      </div>
    </el-card>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId !== null ? '编辑模板' : '新建模板'"
      width="640px"
      @closed="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="92px">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input
            v-model="form.templateCode"
            placeholder="如 ORDER_SHIPPED（全大写，租户内唯一）"
            :disabled="editingId !== null"
          />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="如 订单发货通知" />
        </el-form-item>
        <el-form-item label="渠道" prop="channel">
          <el-select v-model="form.channel" style="width: 100%" @change="onChannelChange">
            <el-option v-for="c in channelOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="短信渠道忽略标题" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="支持 ${orderNo} / ${amount} 等占位变量"
          />
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="form.msgType" style="width: 100%">
            <el-option label="通知 NOTICE" value="NOTICE" />
            <el-option label="告警 ALERT" value="ALERT" />
            <el-option label="系统 SYSTEM" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 渲染预览弹窗 -->
    <el-dialog v-model="previewVisible" title="渲染预览" width="640px" @closed="closePreview">
      <template v-if="previewRow">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="编码">{{ previewRow.templateCode }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ channelTag(previewRow.channel).label }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="previewVariables.length" class="preview-vars">
          <div class="preview-vars-title">变量赋值</div>
          <el-form label-width="120px">
            <el-form-item v-for="v in previewVariables" :key="v" :label="v">
              <el-input v-model="previewVarValues[v]" :placeholder="`请填写 ${v} 的取值`" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="previewing" @click="renderPreview">渲染预览</el-button>
        </div>
        <el-empty v-else description="该模板未声明变量" :image-size="60" />

        <el-alert
          v-if="previewMissing.length"
          type="warning"
          :closable="false"
          show-icon
          :title="`以下变量尚未赋值：${previewMissing.join('、')}`"
          style="margin: 12px 0"
        />
        <div v-if="renderedText !== null" class="preview-result">
          <div class="preview-result-title">渲染结果</div>
          <pre class="preview-result-body">{{ renderedText }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Edit, Search, RefreshLeft, View } from '@element-plus/icons-vue'
import {
  pageNotificationTemplates,
  getTemplateVariables,
  previewNotificationTemplate,
  createNotificationTemplate,
  updateNotificationTemplate,
  deleteNotificationTemplate,
  toggleNotificationTemplate
} from '@/api/notificationTemplate'

// 与后端 Channel 枚举一致
const channelOptions = [
  { value: 'IN_APP', label: '站内信' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'SMS', label: '短信' },
  { value: 'WEBHOOK', label: 'Webhook' },
  { value: 'WEBSOCKET', label: 'WebSocket' }
]
const channelTag = (ch) => {
  const map = {
    IN_APP: { type: 'info', label: '站内信' },
    EMAIL: { type: 'primary', label: '邮件' },
    SMS: { type: 'success', label: '短信' },
    WEBHOOK: { type: 'warning', label: 'Webhook' },
    WEBSOCKET: { type: 'danger', label: 'WebSocket' }
  }
  return map[ch] || { type: 'info', label: ch || '--' }
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  channel: '',
  enabled: '',
  keyword: '',
  sortField: '',
  sortDirection: ''
})

/** 筛选条件映射为查询参数（空值统一 undefined，不传空串）。 */
function buildParams() {
  return {
    page: query.page,
    size: query.size,
    channel: query.channel || undefined,
    enabled: query.enabled === '' ? undefined : query.enabled,
    keyword: query.keyword || undefined,
    sortField: query.sortField || undefined,
    sortDirection: query.sortDirection || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const page = await pageNotificationTemplates(buildParams())
    rows.value = (page.records || []).map((r) => ({ ...r, __toggling: false }))
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

function resetQuery() {
  query.channel = ''
  query.enabled = ''
  query.keyword = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  load()
}

/** 服务端排序白名单：templateCode/templateName/channel/enabled/createdAt。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

async function onToggle(row, val) {
  row.__toggling = true
  try {
    await toggleNotificationTemplate(row.id, val)
    ElMessage.success(val ? '已启用' : '已停用')
  } catch (e) {
    row.enabled = !val // 失败时回滚开关
  } finally {
    row.__toggling = false
  }
}

async function onDelete(row) {
  try {
    await deleteNotificationTemplate(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

// ---- 新建 / 编辑 ----
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  templateCode: '',
  templateName: '',
  channel: 'IN_APP',
  title: '',
  content: '',
  msgType: 'NOTICE',
  enabled: true,
  remark: ''
})
const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  channel: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, {
    templateCode: '',
    templateName: '',
    channel: 'IN_APP',
    title: '',
    content: '',
    msgType: 'NOTICE',
    enabled: true,
    remark: ''
  })
  formRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    channel: row.channel,
    title: row.title || '',
    content: row.content,
    msgType: row.msgType || 'NOTICE',
    enabled: !!row.enabled,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

function onChannelChange() {
  // 短信渠道忽略标题，保持语义一致（仅 UI 提示，不强制清空）
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value != null) {
      await updateNotificationTemplate(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createNotificationTemplate(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- 渲染预览 ----
const previewVisible = ref(false)
const previewRow = ref(null)
const previewVariables = ref([])
const previewVarValues = reactive({})
const previewMissing = ref([])
const renderedText = ref(null)
const previewing = ref(false)

async function openPreview(row) {
  previewRow.value = row
  previewVisible.value = true
  previewMissing.value = []
  renderedText.value = null
  try {
    previewVariables.value = await getTemplateVariables(row.id)
    previewVariables.value.forEach((v) => {
      if (!(v in previewVarValues)) previewVarValues[v] = ''
    })
  } catch (e) {
    previewVariables.value = []
  }
}

function closePreview() {
  previewRow.value = null
  previewVariables.value = []
  previewMissing.value = []
  renderedText.value = null
  Object.keys(previewVarValues).forEach((k) => delete previewVarValues[k])
}

async function renderPreview() {
  if (!previewRow.value) return
  previewing.value = true
  try {
    const res = await previewNotificationTemplate(previewRow.value.id, { ...previewVarValues })
    renderedText.value = res.rendered
    previewMissing.value = res.missingVariables || []
  } finally {
    previewing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: #c0c4cc; }
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
.hint code {
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
  color: #409eff;
}
.preview-vars-title,
.preview-result-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.preview-result {
  margin-top: 12px;
}
.preview-result-body {
  background: #f4f4f5;
  border-radius: 6px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #303133;
  margin: 0;
}
</style>
