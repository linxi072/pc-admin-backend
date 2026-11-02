<template>
  <div>
    <div class="designer-header">
      <el-button :icon="ArrowLeft" @click="goBack">{{ t('common.back') }}</el-button>
      <h2 class="page-title" style="margin: 0 12px">{{ def.processName || t('workflow.workflowDesigner') }}</h2>
      <el-tag :type="def.status === 'PUBLISHED' ? 'success' : 'info'">
        {{ def.status === 'PUBLISHED' ? t('workflow.publishedV') + def.version : t('common.draft') }}
      </el-tag>
      <span class="spacer" />
      <el-button :icon="View" @click="previewBpmn">{{ t('workflow.previewBpmn') }}</el-button>
      <el-button type="primary" :icon="Document" :loading="saving" @click="saveDraft">{{ t('workflow.saveDraft') }}</el-button>
      <el-button
        v-if="def.status !== 'PUBLISHED'"
        type="success" :icon="Upload" :loading="publishing" @click="doPublish">{{ t('workflow.publishAndActivate') }}</el-button>
      <el-button
        v-else type="warning" :icon="Download" :loading="publishing" @click="doUnpublish">{{ t('workflow.unpublish') }}</el-button>
    </div>

    <el-row :gutter="16">
      <!-- 节点列表（可增删/排序） -->
      <el-col :span="10">
        <el-card class="page-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>{{ t('workflow.nodesOrdered') }}</span>
              <el-dropdown @command="addNode">
                <el-button size="small" type="primary" :icon="Plus">{{ t('workflow.addNode') }}</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="APPROVAL">{{ t('workflow.approvalNode') }}</el-dropdown-item>
                    <el-dropdown-item command="SERVICE">{{ t('workflow.executeStep') }}</el-dropdown-item>
                    <el-dropdown-item command="CC">{{ t('common.cc') }}</el-dropdown-item>
                    <el-dropdown-item command="GATEWAY">{{ t('workflow.conditionBranch') }}</el-dropdown-item>
                    <el-dropdown-item command="END">{{ t('common.end') }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
          <div v-for="(n, idx) in nodes" :key="n.id" class="node-row" :class="{ active: idx === selectedIndex }">
            <el-tag :type="nodeTagType(n.type)" size="small" style="width: 64px; text-align: center">
              {{ nodeTypeLabel(n.type) }}
            </el-tag>
            <span class="node-name">{{ n.name || t('common.unnamed') }}</span>
            <span class="spacer" />
            <el-button link :icon="Top" :disabled="idx === 0" @click="move(idx, -1)" :title="t('common.moveUp')" />
            <el-button link :icon="Bottom" :disabled="idx === nodes.length - 1" @click="move(idx, 1)" :title="t('common.moveDown')" />
            <el-button link :icon="Edit" @click="selectNode(idx)" :title="t('common.edit')" />
            <el-button link type="danger" :icon="Delete" :disabled="n.type === 'START'" @click="removeNode(idx)" :title="t('common.delete')" />
          </div>
          <el-empty v-if="nodes.length === 0" :description="t('workflow.pleaseAddNode')" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 节点属性 + 连线 -->
      <el-col :span="14">
        <el-card v-if="current" class="page-card" shadow="never">
          <template #header><span>节点属性（{{ nodeTypeLabel(current.type) }}）</span></template>
          <el-form label-width="110px" :model="current">
            <el-form-item :label="t('workflow.nodeName')">
              <el-input v-model="current.name" :placeholder="t('workflow.exDirectManager')" />
            </el-form-item>

            <template v-if="current.type === 'APPROVAL'">
              <el-form-item :label="t('workflow.approvalMode')">
                <el-select v-model="current.approvalMode" style="width: 100%">
                  <el-option :label="t('workflow.orSign')" value="ANY" />
                  <el-option :label="t('workflow.countersign')" value="ALL" />
                  <el-option :label="t('workflow.passByRatio')" value="RATIO" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="current.approvalMode === 'RATIO'" :label="t('workflow.passRatio')">
                <el-input-number v-model="current.approvalRatio" :min="0.01" :max="1" :step="0.1" :precision="2" />
              </el-form-item>
              <el-form-item :label="t('workflow.assigneeType')">
                <el-select v-model="current.assigneeType" style="width: 100%">
                  <el-option :label="t('workflow.specifiedPerson')" value="USER" />
                  <el-option :label="t('common.byRole')" value="ROLE" />
                  <el-option :label="t('common.byDept')" value="ORG" />
                  <el-option :label="t('workflow.initiatorSelf')" value="INITIATOR" />
                  <el-option :label="t('workflow.initiatorManager')" value="INITIATOR_MANAGER" />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="current.assigneeType === 'USER'"
                :label="t('workflow.personId')">
                <el-input v-model="current.assigneeExpression" :placeholder="t('workflow.commaExample1')" />
              </el-form-item>
              <el-form-item
                v-else-if="current.assigneeType === 'ROLE' || current.assigneeType === 'ORG'"
                :label="current.assigneeType === 'ROLE' ? t('workflow.roleCode') : t('workflow.deptId')">
                <el-input v-model="current.assigneeExpression" :placeholder="current.assigneeType === 'ROLE' ? t('workflow.exRoleManager') : t('workflow.exDeptId')" />
              </el-form-item>
              <el-form-item :label="t('workflow.rejectStrategy')">
                <el-select v-model="current.rejectPolicy" style="width: 100%">
                  <el-option :label="t('workflow.rejectNotAllowed')" value="NONE" />
                  <el-option :label="t('workflow.rejectToPrev')" value="PREVIOUS" />
                  <el-option :label="t('workflow.rejectToEnd')" value="END" />
                  <el-option :label="t('workflow.rejectToNode')" value="SPECIFIC" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="current.rejectPolicy === 'SPECIFIC'" :label="t('workflow.rejectTargetNode')">
                <el-select v-model="current.rejectTargetNodeId" style="width: 100%" :placeholder="t('workflow.selectNode')">
                  <el-option v-for="o in nodeIdOptions" :key="o" :label="nodeName(o)" :value="o" />
                </el-select>
              </el-form-item>
            </template>

            <template v-if="current.type === 'SERVICE'">
              <el-form-item :label="t('common.stepType')">
                <el-input v-model="current.stepType" :placeholder="t('workflow.exSyncErp')" />
              </el-form-item>
              <el-form-item :label="t('workflow.stepConfig')">
                <el-input v-model="current.stepConfig" type="textarea" :rows="2" :placeholder="t('workflow.jsonOrFreeText')" />
              </el-form-item>
            </template>

            <template v-if="current.type === 'CC'">
              <el-form-item :label="t('workflow.ccPersonId')">
                <el-input v-model="current.assigneeExpression" :placeholder="t('workflow.commaExample2')" />
              </el-form-item>
            </template>

            <!-- 节点受让人预览：接已发布判定 -->
            <el-divider content-position="left">{{ t('workflow.assigneePreview') }}</el-divider>
            <el-alert
              v-if="isPublished"
              type="success" :closable="false" show-icon
              :title="t('workflow.publishedDraftPreviewNote', { publishedVersion })"
            />
            <el-alert
              v-else type="info" :closable="false" show-icon
              :title="t('workflow.unpublishedDraftPreview')"
            />
            <el-descriptions :column="1" border size="small" style="margin-top: 8px">
              <el-descriptions-item v-if="current.type === 'APPROVAL'" :label="t('workflow.approvalMode')">
                {{ approvalModeText(current) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('workflow.assigneeDraft')">
                {{ assigneePreviewText(current) }}
              </el-descriptions-item>
              <el-descriptions-item v-if="isPublished" :label="t('workflow.assigneeActive')">
                {{ assigneePreviewText(publishedNode) || t('workflow.nodeNotActive') }}
              </el-descriptions-item>
              <el-descriptions-item v-if="current.type === 'APPROVAL'" :label="t('workflow.rejectStrategy')">
                {{ rejectPolicyText(current) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-form>
        </el-card>

        <el-card class="page-card" shadow="never">
          <template #header><span>{{ t('workflow.edgesAndBranches') }}</span></template>
          <el-table :data="edges" border size="small">
            <el-table-column :label="t('common.source')" width="120">
              <template #default="{ row }">
                <el-select v-model="row.sourceNodeId" style="width: 100%">
                  <el-option v-for="o in nodeIdOptions" :key="o" :label="nodeName(o)" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.target')" width="120">
              <template #default="{ row }">
                <el-select v-model="row.targetNodeId" style="width: 100%">
                  <el-option v-for="o in nodeIdOptions" :key="o" :label="nodeName(o)" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.conditionExpr')">
              <template #default="{ row }">
                <el-input v-model="row.conditionExpression" :placeholder="t('workflow.exAmountCond')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.defaultBranch')" width="90" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.isDefault" :true-value="true" :false-value="false" />
              </template>
            </el-table-column>
            <el-table-column label="" width="50" align="center">
              <template #default="{ row }">
                <el-button link type="danger" :icon="Delete" @click="removeEdge(row)" />
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 10px">
            <el-button size="small" :icon="Plus" @click="addEdge">{{ t('workflow.addEdge') }}</el-button>
          </div>
          <el-alert
            style="margin-top: 10px"
            type="info" :closable="false"
            :title="t('workflow.branchHint')" />
        </el-card>

        <el-card class="page-card" shadow="never" style="margin-top: 16px">
          <template #header><span>{{ t('workflow.businessFormFields') }}</span></template>
          <el-table :data="formFields" border size="small">
            <el-table-column :label="t('workflow.fieldKey')" width="140">
              <template #default="{ row }"><el-input v-model="row.field" :placeholder="t('workflow.exAmount')" /></template>
            </el-table-column>
            <el-table-column :label="t('common.label')" width="140">
              <template #default="{ row }"><el-input v-model="row.label" :placeholder="t('workflow.exExpenseAmount')" /></template>
            </el-table-column>
            <el-table-column :label="t('common.type')" width="110">
              <template #default="{ row }">
                <el-select v-model="row.type">
                  <el-option :label="t('common.text')" value="text" />
                  <el-option :label="t('common.multiline')" value="textarea" />
                  <el-option :label="t('common.number')" value="number" />
                  <el-option :label="t('common.date')" value="date" />
                  <el-option :label="t('common.dropdown')" value="select" />
                  <el-option :label="t('common.switch')" value="switch" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.required')" width="64" align="center">
              <template #default="{ row }"><el-checkbox v-model="row.required" :true-value="true" :false-value="false" /></template>
            </el-table-column>
            <el-table-column :label="t('workflow.placeholderOrOption')" min-width="180">
              <template #default="{ row }">
                <el-input v-if="row.type === 'select'" v-model="row.optionsText" :placeholder="t('workflow.commaExample3')" />
                <el-input v-else v-model="row.placeholder" :placeholder="t('workflow.placeholderHint')" />
              </template>
            </el-table-column>
            <el-table-column label="" width="50" align="center">
              <template #default="{ row }"><el-button link type="danger" :icon="Delete" @click="removeFormField(row)" /></template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 10px">
            <el-button size="small" :icon="Plus" @click="addFormField">{{ t('workflow.addField') }}</el-button>
            <span style="margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px">
              字段将在发起流程时按类型动态渲染，提交值随流程变量下发。
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="bpmnVisible" :title="t('workflow.bpmnPreview')" width="760px">
      <pre class="bpmn-pre">{{ bpmnXml }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, View, Document, Upload, Download, Plus, Edit, Delete, Top, Bottom
} from '@element-plus/icons-vue'
import {
  getDefinition, saveDefinition, publishDefinition, unpublishDefinition, getDefinitionBpmn, getPublishedDefinition
} from '@/api/workflow'
import { listRoles } from '@/api/role'
import { pageUsers } from '@/api/user'

const route = useRoute()
const router = useRouter()
// 雪花 ID 以字符串透传：后端已按字符串序列化下发，Number() 转换会超出 2^53 精度损失
const id = route.params.id == null ? null : String(route.params.id)

const def = reactive({ id, processName: '', description: '', status: 'DRAFT', version: 0 })
const nodes = ref([])
const edges = ref([])
const selectedIndex = ref(-1)
const saving = ref(false)
const publishing = ref(false)
const bpmnVisible = ref(false)
const bpmnXml = ref('')
const publishedDesign = ref(null)
const formFields = ref([])
// 用户/角色反查映射：{ [userId]: displayName } 与 { [roleCode]: roleName }
const userMap = ref({})
const roleMap = ref({})

const current = computed(() => (selectedIndex.value >= 0 ? nodes.value[selectedIndex.value] : null))
const nodeIdOptions = computed(() => nodes.value.map((n) => n.id))
function nodeName(nid) {
  return nodes.value.find((n) => n.id === nid)?.name || nid
}

// ---- 已发布判定（与发起表单一致，统一以 getPublishedDefinition 为权威来源） ----
const isPublished = computed(() => !!publishedDesign.value)
const publishedVersion = computed(() => publishedDesign.value?.version || 0)
const publishedNode = computed(() => {
  if (!publishedDesign.value || !current.value) return null
  return (publishedDesign.value.nodes || []).find((n) => n.id === current.value.id) || null
})

const ASSIGNEE_TYPE_LABEL = {
  USER: t('workflow.specifiedPerson'), ROLE: t('common.role'), ORG: t('common.department'),
  INITIATOR: t('workflow.initiatorSelf'), INITIATOR_MANAGER: t('workflow.initiatorManager')
}
const APPROVAL_MODE_LABEL = { ANY: t('workflow.orSign'), ALL: t('workflow.countersign'), RATIO: t('workflow.passByRatio') }
const REJECT_POLICY_LABEL = { NONE: t('workflow.rejectNotAllowed'), PREVIOUS: t('workflow.rejectToPrev'), END: t('workflow.rejectToEnd'), SPECIFIC: t('workflow.rejectToNode') }

function approvalModeText(node) {
  return APPROVAL_MODE_LABEL[node?.approvalMode] || node?.approvalMode || '—'
}
function rejectPolicyText(node) {
  if (!node) return '—'
  const t = REJECT_POLICY_LABEL[node.rejectPolicy] || node.rejectPolicy || 'NONE'
  if (node.rejectPolicy === 'SPECIFIC' && node.rejectTargetNodeId) {
    return t + '（→ ' + nodeName(node.rejectTargetNodeId) + '）'
  }
  return t
}
// 受让人标识反查：将用户ID / 角色编码解析为真实姓名 / 角色名
function resolveAssignees(expr, kind) {
  if (!expr) return ''
  const tokens = String(expr).split(',').map((s) => s.trim()).filter(Boolean)
  if (tokens.length === 0) return ''
  if (kind === 'USER' || kind === 'CC') {
    return tokens.map((id) => userMap.value[String(id)] || `t('workflow.userHash', { id })`).join('、')
  }
  if (kind === 'ROLE') {
    return tokens.map((code) => roleMap.value[code] || code).join('、')
  }
  // ORG 等未提供反查接口的场景，回退展示原始标识
  return tokens.join('、')
}

function assigneePreviewText(node) {
  if (!node) return '—'
  if (node.type === 'CC') {
    const names = resolveAssignees(node.assigneeExpression, 'CC')
    return names ? (t('workflow.ccPerson') + names) : t('workflow.ccNotSet')
  }
  if (node.type !== 'APPROVAL') return '—'
  const t = node.assigneeType
  if (t === 'INITIATOR') return t('workflow.initiatorSelf')
  if (t === 'INITIATOR_MANAGER') return t('workflow.initiatorManager')
  const typeLabel = ASSIGNEE_TYPE_LABEL[t] || t || t('workflow.notSet')
  const names = resolveAssignees(node.assigneeExpression, t)
  return names ? `${typeLabel}：${names}` : t('workflow.typeLabelUnspecified', { typeLabel })
}

let seq = 0
function genId(prefix) {
  return `${prefix.toLowerCase()}_${++seq}`
}

function nodeTypeLabel(t) {
  return { START: t('common.initiate'), END: t('common.end'), APPROVAL: t('workflow.approval'), SERVICE: t('workflow.execute'), CC: t('common.cc'), GATEWAY: t('common.branch') }[t] || t
}
function nodeTagType(t) {
  return { START: 'success', END: 'info', APPROVAL: 'warning', SERVICE: 'primary', CC: 'info', GATEWAY: 'danger' }[t] || ''
}

function addNode(type) {
  if (type === 'START') {
    ElMessage.warning(t('workflow.startNodeBuiltin'))
    return
  }
  const n = { id: genId(type), type, name: nodeTypeLabel(type) }
  if (type === 'APPROVAL') {
    n.approvalMode = 'ANY'; n.assigneeType = 'USER'; n.assigneeExpression = ''; n.rejectPolicy = 'NONE'
  } else if (type === 'SERVICE') {
    n.stepType = ''; n.stepConfig = ''
  } else if (type === 'CC') {
    n.assigneeExpression = ''
  }
  nodes.value.push(n)
  selectedIndex.value = nodes.value.length - 1
}

function removeNode(idx) {
  const n = nodes.value[idx]
  nodes.value.splice(idx, 1)
  edges.value = edges.value.filter((e) => e.sourceNodeId !== n.id && e.targetNodeId !== n.id)
  if (selectedIndex.value >= nodes.value.length) selectedIndex.value = nodes.value.length - 1
}

function selectNode(idx) {
  selectedIndex.value = idx
}

function move(idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= nodes.value.length) return
  const arr = nodes.value
  const tmp = arr[idx]
  arr[idx] = arr[target]
  arr[target] = tmp
  selectedIndex.value = target
}

function addEdge() {
  edges.value.push({ id: genId('edge'), sourceNodeId: '', targetNodeId: '', conditionExpression: '', isDefault: false })
}

function removeEdge(row) {
  const i = edges.value.indexOf(row)
  if (i >= 0) edges.value.splice(i, 1)
}

// ---- 业务表单字段（formSchema）编辑 ----
function parseFormSchema(json) {
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
      optionsText: Array.isArray(f.options) ? f.options.join(',') : (f.optionsText || '')
    }))
  } catch {
    return []
  }
}

function serializeFormSchema() {
  const arr = formFields.value
    .filter((f) => f.field && f.label)
    .map((f) => {
      const o = { field: f.field, label: f.label, type: f.type, required: !!f.required }
      if (f.placeholder) o.placeholder = f.placeholder
      if (f.type === 'select') {
        o.options = (f.optionsText || '').split(',').map((s) => s.trim()).filter(Boolean)
      }
      return o
    })
  return JSON.stringify(arr)
}

function addFormField() {
  formFields.value.push({ field: '', label: '', type: 'text', required: false, placeholder: '', optionsText: '' })
}

function removeFormField(row) {
  const i = formFields.value.indexOf(row)
  if (i >= 0) formFields.value.splice(i, 1)
}

// 拉取用户/角色清单，构建「标识 -> 名称」映射，供受让人预览反查真实姓名 / 角色名
async function loadRefData() {
  try {
    const [roles, usersPage] = await Promise.all([listRoles(), pageUsers({ page: 1, size: 1000 })])
    const rMap = {}
    for (const r of (roles || [])) rMap[r.roleCode] = r.roleName
    roleMap.value = rMap
    const uMap = {}
    const recs = (usersPage && usersPage.records) || []
    for (const u of recs) uMap[String(u.id)] = u.displayName
    userMap.value = uMap
  } catch {
    // 反查失败不影响设计器其余功能，预览回退为原始标识
    userMap.value = {}
    roleMap.value = {}
  }
}

async function load() {
  const d = await getDefinition(id)
  def.processName = d.processName
  def.description = d.description
  def.status = d.status
  def.version = d.version
  const loadedNodes = d.nodes || []
  const loadedEdges = d.edges || []
  // 用后台数据初始化序列号，避免新增 id 冲突
  loadedNodes.forEach((n) => {
    const m = String(n.id).match(/_(\d+)$/)
    if (m) seq = Math.max(seq, Number(m[1]))
  })
  nodes.value = loadedNodes.length ? loadedNodes : [
    { id: 'start', type: 'START', name: t('common.initiate') },
    { id: 'end', type: 'END', name: t('common.end') }
  ]
  edges.value = loadedEdges
  selectedIndex.value = -1
  // 解析已保存的表单字段 schema
  formFields.value = parseFormSchema(d.formSchema)
  // 已发布判定：拉取权威已发布版本，用于受让人预览对比
  try {
    publishedDesign.value = await getPublishedDefinition(d.processKey)
  } catch {
    publishedDesign.value = null
  }
  // 用户/角色反查：加载映射，使受让人预览展示真实姓名 / 角色名
  await loadRefData()
}

async function saveDraft() {
  if (!validate()) return
  saving.value = true
  try {
    await saveDefinition(id, {
      processName: def.processName,
      description: def.description,
      formSchema: serializeFormSchema(),
      nodes: nodes.value,
      edges: edges.value
    })
    ElMessage.success(t('workflow.draftSaved'))
  } finally {
    saving.value = false
  }
}

async function doPublish() {
  if (!validate()) return
  try {
    await ElMessageBox.confirm(t('workflow.publishConfirm'), t('workflow.publishWorkflow'), { type: 'warning' })
  } catch {
    return
  }
  publishing.value = true
  try {
    // 先保存再发布，保证部署的是最新设计
    await saveDefinition(id, {
      processName: def.processName,
      description: def.description,
      formSchema: serializeFormSchema(),
      nodes: nodes.value,
      edges: edges.value
    })
    const updated = await publishDefinition(id)
    def.status = updated.status
    def.version = updated.version
    ElMessage.success(t('workflow.publishedAndActive'))
  } finally {
    publishing.value = false
  }
}

async function doUnpublish() {
  publishing.value = true
  try {
    const updated = await unpublishDefinition(id)
    def.status = updated.status
    ElMessage.success(t('workflow.unpublished'))
  } finally {
    publishing.value = false
  }
}

async function previewBpmn() {
  try {
    const res = await getDefinitionBpmn(id)
    bpmnXml.value = res.bpmnXml || t('workflow.emptySaveOrPublish')
    bpmnVisible.value = true
  } catch (e) {
    ElMessage.error(t('workflow.loadFailedX'))
  }
}

// 前端轻量校验：至少 1 个 START / 1 个 END，无悬空连线
function validate() {
  const types = nodes.value.map((n) => n.type)
  if (types.filter((t) => t === 'START').length !== 1) {
    ElMessage.error(t('workflow.exactlyOneStart'))
    return false
  }
  if (types.filter((t) => t === 'END').length < 1) {
    ElMessage.error(t('workflow.atLeastOneEnd'))
    return false
  }
  const ids = new Set(nodes.value.map((n) => n.id))
  for (const e of edges.value) {
    if (!e.sourceNodeId || !e.targetNodeId) {
      ElMessage.error(t('workflow.edgeMissingSourceTarget'))
      return false
    }
    if (!ids.has(e.sourceNodeId) || !ids.has(e.targetNodeId)) {
      ElMessage.error(t('workflow.edgeRefsMissingNode'))
      return false
    }
  }
  return true
}

function goBack() {
  router.push('/workflow/definition')
}

watch(() => route.params.id, () => location.reload())

onMounted(load)
</script>

<style scoped>
.designer-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.node-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 6px;
}
.node-row.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.node-name {
  font-weight: 500;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.bpmn-pre {
  max-height: 60vh;
  overflow: auto;
  background: #0d1117;
  color: #c9d1d9;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
