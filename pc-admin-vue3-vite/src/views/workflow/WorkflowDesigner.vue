<template>
  <div>
    <div class="designer-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
      <h2 class="page-title" style="margin: 0 12px">{{ def.processName || '工作流设计器' }}</h2>
      <el-tag :type="def.status === 'PUBLISHED' ? 'success' : 'info'">
        {{ def.status === 'PUBLISHED' ? '已发布 v' + def.version : '草稿' }}
      </el-tag>
      <span class="spacer" />
      <el-button :icon="View" @click="previewBpmn">预览 BPMN</el-button>
      <el-button type="primary" :icon="Document" :loading="saving" @click="saveDraft">保存草稿</el-button>
      <el-button
        v-if="def.status !== 'PUBLISHED'"
        type="success" :icon="Upload" :loading="publishing" @click="doPublish">发布并生效</el-button>
      <el-button
        v-else type="warning" :icon="Download" :loading="publishing" @click="doUnpublish">取消发布</el-button>
    </div>

    <el-row :gutter="16">
      <!-- 节点列表（可增删/排序） -->
      <el-col :span="10">
        <el-card class="page-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>节点（按顺序流转）</span>
              <el-dropdown @command="addNode">
                <el-button size="small" type="primary" :icon="Plus">新增节点</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="APPROVAL">审批节点</el-dropdown-item>
                    <el-dropdown-item command="SERVICE">执行步骤</el-dropdown-item>
                    <el-dropdown-item command="CC">抄送</el-dropdown-item>
                    <el-dropdown-item command="GATEWAY">条件分支</el-dropdown-item>
                    <el-dropdown-item command="END">结束</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
          <div v-for="(n, idx) in nodes" :key="n.id" class="node-row" :class="{ active: idx === selectedIndex }">
            <el-tag :type="nodeTagType(n.type)" size="small" style="width: 64px; text-align: center">
              {{ nodeTypeLabel(n.type) }}
            </el-tag>
            <span class="node-name">{{ n.name || '(未命名)' }}</span>
            <span class="spacer" />
            <el-button link :icon="Top" :disabled="idx === 0" @click="move(idx, -1)" title="上移" />
            <el-button link :icon="Bottom" :disabled="idx === nodes.length - 1" @click="move(idx, 1)" title="下移" />
            <el-button link :icon="Edit" @click="selectNode(idx)" title="编辑" />
            <el-button link type="danger" :icon="Delete" :disabled="n.type === 'START'" @click="removeNode(idx)" title="删除" />
          </div>
          <el-empty v-if="nodes.length === 0" description="请新增节点" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 节点属性 + 连线 -->
      <el-col :span="14">
        <el-card v-if="current" class="page-card" shadow="never">
          <template #header><span>节点属性（{{ nodeTypeLabel(current.type) }}）</span></template>
          <el-form label-width="110px" :model="current">
            <el-form-item label="节点名称">
              <el-input v-model="current.name" placeholder="如 直属主管审批" />
            </el-form-item>

            <template v-if="current.type === 'APPROVAL'">
              <el-form-item label="审批模式">
                <el-select v-model="current.approvalMode" style="width: 100%">
                  <el-option label="或签（任一通过）" value="ANY" />
                  <el-option label="会签（全部通过）" value="ALL" />
                  <el-option label="按比例通过" value="RATIO" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="current.approvalMode === 'RATIO'" label="通过比例">
                <el-input-number v-model="current.approvalRatio" :min="0.01" :max="1" :step="0.1" :precision="2" />
              </el-form-item>
              <el-form-item label="受让人类型">
                <el-select v-model="current.assigneeType" style="width: 100%">
                  <el-option label="指定人员" value="USER" />
                  <el-option label="按角色" value="ROLE" />
                  <el-option label="按部门" value="ORG" />
                  <el-option label="发起人本人" value="INITIATOR" />
                  <el-option label="发起人主管" value="INITIATOR_MANAGER" />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="current.assigneeType === 'USER'"
                label="人员ID">
                <el-input v-model="current.assigneeExpression" placeholder="逗号分隔，如 10,11" />
              </el-form-item>
              <el-form-item
                v-else-if="current.assigneeType === 'ROLE' || current.assigneeType === 'ORG'"
                :label="current.assigneeType === 'ROLE' ? '角色编码' : '部门ID'">
                <el-input v-model="current.assigneeExpression" :placeholder="current.assigneeType === 'ROLE' ? '如 MANAGER' : '如 3'" />
              </el-form-item>
              <el-form-item label="驳回策略">
                <el-select v-model="current.rejectPolicy" style="width: 100%">
                  <el-option label="不允许驳回" value="NONE" />
                  <el-option label="驳回到上一审批" value="PREVIOUS" />
                  <el-option label="驳回到结束" value="END" />
                  <el-option label="驳回到指定节点" value="SPECIFIC" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="current.rejectPolicy === 'SPECIFIC'" label="驳回目标节点">
                <el-select v-model="current.rejectTargetNodeId" style="width: 100%" placeholder="选择节点">
                  <el-option v-for="o in nodeIdOptions" :key="o" :label="nodeName(o)" :value="o" />
                </el-select>
              </el-form-item>
            </template>

            <template v-if="current.type === 'SERVICE'">
              <el-form-item label="步骤类型">
                <el-input v-model="current.stepType" placeholder="如 SYNC_ERP" />
              </el-form-item>
              <el-form-item label="步骤配置">
                <el-input v-model="current.stepConfig" type="textarea" :rows="2" placeholder="JSON 或自由文本" />
              </el-form-item>
            </template>

            <template v-if="current.type === 'CC'">
              <el-form-item label="抄送人ID">
                <el-input v-model="current.assigneeExpression" placeholder="逗号分隔，如 20,21" />
              </el-form-item>
            </template>
          </el-form>
        </el-card>

        <el-card class="page-card" shadow="never">
          <template #header><span>连线与条件分支</span></template>
          <el-table :data="edges" border size="small">
            <el-table-column label="来源" width="120">
              <template #default="{ row }">
                <el-select v-model="row.sourceNodeId" style="width: 100%">
                  <el-option v-for="o in nodeIdOptions" :key="o" :label="nodeName(o)" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="去向" width="120">
              <template #default="{ row }">
                <el-select v-model="row.targetNodeId" style="width: 100%">
                  <el-option v-for="o in nodeIdOptions" :key="o" :label="nodeName(o)" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="条件表达式 (EL)">
              <template #default="{ row }">
                <el-input v-model="row.conditionExpression" placeholder="如 amount <= 1000" />
              </template>
            </el-table-column>
            <el-table-column label="默认分支" width="90" align="center">
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
            <el-button size="small" :icon="Plus" @click="addEdge">添加连线</el-button>
          </div>
          <el-alert
            style="margin-top: 10px"
            type="info" :closable="false"
            title="提示：条件分支节点(网关)可有多条出边；每条出边可配置条件表达式，或指定一条为默认分支（无符合条件时走默认）。" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="bpmnVisible" title="BPMN 预览" width="760px">
      <pre class="bpmn-pre">{{ bpmnXml }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, View, Document, Upload, Download, Plus, Edit, Delete, Top, Bottom
} from '@element-plus/icons-vue'
import {
  getDefinition, saveDefinition, publishDefinition, unpublishDefinition, getDefinitionBpmn
} from '@/api/workflow'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const def = reactive({ id, processName: '', description: '', status: 'DRAFT', version: 0 })
const nodes = ref([])
const edges = ref([])
const selectedIndex = ref(-1)
const saving = ref(false)
const publishing = ref(false)
const bpmnVisible = ref(false)
const bpmnXml = ref('')

const current = computed(() => (selectedIndex.value >= 0 ? nodes.value[selectedIndex.value] : null))
const nodeIdOptions = computed(() => nodes.value.map((n) => n.id))
function nodeName(nid) {
  return nodes.value.find((n) => n.id === nid)?.name || nid
}

let seq = 0
function genId(prefix) {
  return `${prefix.toLowerCase()}_${++seq}`
}

function nodeTypeLabel(t) {
  return { START: '发起', END: '结束', APPROVAL: '审批', SERVICE: '执行', CC: '抄送', GATEWAY: '分支' }[t] || t
}
function nodeTagType(t) {
  return { START: 'success', END: 'info', APPROVAL: 'warning', SERVICE: 'primary', CC: 'info', GATEWAY: 'danger' }[t] || ''
}

function addNode(type) {
  if (type === 'START') {
    ElMessage.warning('发起节点已内置，无需新增')
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
    { id: 'start', type: 'START', name: '发起' },
    { id: 'end', type: 'END', name: '结束' }
  ]
  edges.value = loadedEdges
  selectedIndex.value = -1
}

async function saveDraft() {
  if (!validate()) return
  saving.value = true
  try {
    await saveDefinition(id, {
      processName: def.processName,
      description: def.description,
      nodes: nodes.value,
      edges: edges.value
    })
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function doPublish() {
  if (!validate()) return
  try {
    await ElMessageBox.confirm('发布后将生成新版本并即时对新发起实例生效，确认发布？', '发布工作流', { type: 'warning' })
  } catch {
    return
  }
  publishing.value = true
  try {
    // 先保存再发布，保证部署的是最新设计
    await saveDefinition(id, {
      processName: def.processName,
      description: def.description,
      nodes: nodes.value,
      edges: edges.value
    })
    const updated = await publishDefinition(id)
    def.status = updated.status
    def.version = updated.version
    ElMessage.success('已发布并生效')
  } finally {
    publishing.value = false
  }
}

async function doUnpublish() {
  publishing.value = true
  try {
    const updated = await unpublishDefinition(id)
    def.status = updated.status
    ElMessage.success('已取消发布')
  } finally {
    publishing.value = false
  }
}

async function previewBpmn() {
  const res = await getDefinitionBpmn(id)
  bpmnXml.value = res.bpmnXml || '(暂无，请先保存或发布)'
  bpmnVisible.value = true
}

// 前端轻量校验：至少 1 个 START / 1 个 END，无悬空连线
function validate() {
  const types = nodes.value.map((n) => n.type)
  if (types.filter((t) => t === 'START').length !== 1) {
    ElMessage.error('必须有且仅有一个发起(START)节点')
    return false
  }
  if (types.filter((t) => t === 'END').length < 1) {
    ElMessage.error('至少需要一个结束(END)节点')
    return false
  }
  const ids = new Set(nodes.value.map((n) => n.id))
  for (const e of edges.value) {
    if (!e.sourceNodeId || !e.targetNodeId) {
      ElMessage.error('存在未填写来源/去向的连线')
      return false
    }
    if (!ids.has(e.sourceNodeId) || !ids.has(e.targetNodeId)) {
      ElMessage.error('连线引用了不存在的节点')
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
