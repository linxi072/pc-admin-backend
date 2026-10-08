<template>
  <div>
    <h2 class="page-title">我发起的流程</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="startVisible = true">发起流程</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="processInstanceId" label="流程实例" width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="businessType" label="业务类型" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RUNNING' ? 'primary' : row.status === 'COMPLETED' ? 'success' : 'info'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="发起时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Document" @click="openRecords(row)">审批记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="startVisible" title="发起审批流程" width="520px">
      <el-form :model="startForm" label-width="90px">
        <el-form-item label="流程定义">
          <el-select v-model="startForm.processKey" style="width: 100%" @change="onProcessChange">
            <el-option
              v-for="p in processOptions"
              :key="p.processKey"
              :label="p.processName + (p.status === 'PUBLISHED' ? '（已发布·自动审批人）' : '（草稿·需手动选审批人）')"
              :value="p.processKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="startForm.businessType" placeholder="如 LEAVE" />
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="startForm.businessId" placeholder="如 L001" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="startForm.title" />
        </el-form-item>

        <template v-if="hasDynamicFields">
          <el-divider content-position="left">业务表单（依据设计动态渲染）</el-divider>
          <el-form-item v-for="f in formSchemaFields" :key="f.field" :label="f.label">
            <el-input v-if="f.type === 'text'" v-model="formValues[f.field]" :placeholder="f.placeholder" />
            <el-input v-else-if="f.type === 'textarea'" v-model="formValues[f.field]" type="textarea" :rows="2" :placeholder="f.placeholder" />
            <el-input-number v-else-if="f.type === 'number'" v-model="formValues[f.field]" :min="0" controls-position="right" style="width: 100%" />
            <el-date-picker v-else-if="f.type === 'date'" v-model="formValues[f.field]" type="date" value-format="YYYY-MM-DD" :placeholder="f.placeholder" style="width: 100%" />
            <el-select v-else-if="f.type === 'select'" v-model="formValues[f.field]" :placeholder="f.placeholder || '请选择'" style="width: 100%">
              <el-option v-for="opt in f.options" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-switch v-else-if="f.type === 'switch'" v-model="formValues[f.field]" />
            <span v-if="f.required" style="color: var(--el-color-danger); margin-left: 4px">*</span>
            <div v-if="showErrors && missingFields.has(f.field)" class="field-error">该字段为必填项，请填写后提交</div>
          </el-form-item>
        </template>

        <el-form-item v-if="!designPublished" label="审批人">
          <el-select v-model="startForm.assigneeUserIds" multiple style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!designPublished" label="主管">
          <el-select v-model="startForm.managerUserId" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="designPublished"
          type="success"
          :closable="false"
          show-icon
          title="该流程已发布设计，审批人/主管由设计自动解析，无需手动选择。"
        />
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="submitStart">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="recordsVisible" title="审批记录" size="420px">
      <el-timeline v-loading="recordsLoading">
        <el-timeline-item
          v-for="r in records"
          :key="r.operationId"
          :timestamp="r.occurredAt"
          :type="r.action === 'APPROVE' ? 'success' : r.action === 'REJECT' ? 'danger' : 'primary'"
        >
          <div><b>{{ actionLabel(r.action) }}</b> · 操作人 #{{ r.operatorUserId }}</div>
          <div v-if="r.opinion">意见：{{ r.opinion }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!recordsLoading && records.length === 0" description="暂无审批记录" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Document } from '@element-plus/icons-vue'
import { myInstances, instanceRecords, startProcess, listDefinitions, getPublishedDefinition } from '@/api/workflow'
import { pageUsers } from '@/api/user'

const loading = ref(false)
const rows = ref([])
const userOptions = ref([])
const processOptions = ref([])
const designPublished = ref(false)
const formSchemaFields = ref([])
const formValues = reactive({})
const hasDynamicFields = computed(() => designPublished.value && formSchemaFields.value.length > 0)

// 必填校验状态：提交前若必填字段为空则拦截；showErrors 控制内联错误展示
const showErrors = ref(false)
const missingFields = computed(() => {
  const s = new Set()
  for (const f of formSchemaFields.value) {
    if (!f.required) continue
    const v = formValues[f.field]
    const empty = f.type === 'switch' ? v !== true : (v === undefined || v === null || v === '')
    if (empty) s.add(f.field)
  }
  return s
})
function fieldLabelOf(field) {
  return formSchemaFields.value.find((f) => f.field === field)?.label || field
}

const startVisible = ref(false)
const starting = ref(false)
const startForm = reactive({
  processKey: '', businessType: '', businessId: '', title: '', assigneeUserIds: [], managerUserId: null
})

const recordsVisible = ref(false)
const recordsLoading = ref(false)
const records = ref([])

function statusLabel(s) {
  return s === 'RUNNING' ? '运行中' : s === 'COMPLETED' ? '已完成' : s === 'TERMINATED' ? '已终止' : s
}
function actionLabel(a) {
  return a === 'APPROVE' ? '通过' : a === 'REJECT' ? '驳回' : a === 'START' ? '发起' : a === 'TRANSFER' ? '转办' : a
}

/** 业务类型默认取自 processKey 大写下划线转驼峰缩写（如 leaveApproval -> LEAVE）。 */
function defaultBusinessType(processKey) {
  if (!processKey) return ''
  return processKey.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase().split('_')[0]
}

/** 将后端 formSchema（JSON 字符串）解析为前端表单字段描述（select 的 options 展开为数组）。 */
function parseSchema(json) {
  if (!json) return []
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    if (!Array.isArray(arr)) return []
    return arr.map((f) => ({
      field: f.field || '',
      label: f.label || '',
      type: f.type || 'text',
      required: !!f.required,
      placeholder: f.placeholder || '',
      options: Array.isArray(f.options) ? f.options : []
    }))
  } catch {
    return []
  }
}

/** 切换流程定义时，查询其是否已发布设计；已发布则隐藏审批人/主管选择框并加载动态业务字段。 */
async function onProcessChange(key) {
  designPublished.value = false
  formSchemaFields.value = []
  showErrors.value = false
  startForm.businessType = defaultBusinessType(key)
  // 清理上一次的动态字段值
  for (const k in formValues) delete formValues[k]
  try {
    const published = await getPublishedDefinition(key)
    designPublished.value = !!published
    if (published) {
      // 设计已发布：审批人/主管由设计解析，清空手动选择
      startForm.assigneeUserIds = []
      startForm.managerUserId = null
      // 依据已发布设计的表单 schema 动态渲染业务字段
      formSchemaFields.value = parseSchema(published.formSchema)
    }
  } catch {
    designPublished.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [instances, users] = await Promise.all([myInstances(), pageUsers({ page: 1, size: 50 })])
    rows.value = instances
    userOptions.value = users.records
  } finally {
    loading.value = false
  }
}

async function submitStart() {
  starting.value = true
  try {
    // 必填业务字段强制校验：未通过则拦截提交并提示具体缺失项，待全部通过方可继续
    if (missingFields.value.size > 0) {
      showErrors.value = true
      ElMessage.error('请先填写必填项：' + [...missingFields.value].map(fieldLabelOf).join('、'))
      return
    }
    const payload = { ...startForm }
    // 设计已发布时，审批人/主管由设计自动解析，不向后端传手动选择
    if (designPublished.value) {
      delete payload.assigneeUserIds
      delete payload.managerUserId
    }
    // 依据已发布设计的表单 schema 收集业务字段值，随流程变量下发
    const formFields = {}
    for (const f of formSchemaFields.value) {
      const v = formValues[f.field]
      if (f.type === 'switch') {
        formFields[f.field] = !!v
      } else if (v !== undefined && v !== null && v !== '') {
        formFields[f.field] = v
      }
    }
    payload.formFields = formFields
    const pid = await startProcess(payload)
    showErrors.value = false
    ElMessage.success('流程已发起：' + pid)
    startVisible.value = false
    load()
  } finally {
    starting.value = false
  }
}

async function openRecords(row) {
  recordsVisible.value = true
  recordsLoading.value = true
  records.value = []
  try {
    records.value = await instanceRecords(row.processInstanceId)
  } finally {
    recordsLoading.value = false
  }
}

async function loadProcessOptions() {
  try {
    const defs = await listDefinitions()
    processOptions.value = defs || []
    if (processOptions.value.length > 0) {
      const initial = processOptions.value.find((d) => d.processKey === 'leaveApproval')
          || processOptions.value[0]
      startForm.processKey = initial.processKey
      await onProcessChange(initial.processKey)
    }
  } catch {
    processOptions.value = []
  }
}

onMounted(async () => {
  await Promise.all([load(), loadProcessOptions()])
})
</script>

<style scoped>
.field-error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
  margin-top: 4px;
}
</style>

