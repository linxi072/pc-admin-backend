<template>
  <div>
    <h2 class="page-title">{{ t('route.approvalPortal') }}</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="14" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card" @click="activeTab = 'todo'">
          <div class="stat-value">{{ stats.todoCount }}</div>
          <div class="stat-label">{{ t('approval.stats.todo') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card" @click="activeTab = 'done'">
          <div class="stat-value">{{ stats.doneCount }}</div>
          <div class="stat-label">{{ t('approval.stats.done') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card" @click="activeTab = 'instances'">
          <div class="stat-value">{{ stats.instanceCount }}</div>
          <div class="stat-label">{{ t('approval.stats.instances') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card" @click="activeTab = 'startable'">
          <div class="stat-value">{{ stats.startableCount }}</div>
          <div class="stat-label">{{ t('approval.stats.startable') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="page-card" shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 待办：可直接在门户内完成审批动作（委托 /api/approval/actions） -->
        <el-tab-pane :label="`${t('approval.tab.todo')} (${stats.todoCount})`" name="todo">
          <el-table :data="portal.todoTasks" v-loading="loading" border stripe>
            <el-table-column prop="name" :label="t('approval.task.node')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="processInstanceId" :label="t('approval.task.processInstanceId')" width="180" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('common.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PENDING' ? 'warning' : 'info'">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dueAt" :label="t('approval.task.dueAt')" width="160">
              <template #default="{ row }">{{ formatTime(row.dueAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.action')" width="320" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" :disabled="acting" @click="openApprove(row, false)">{{ t('approval.action.approve') }}</el-button>
                <el-button link type="danger" :disabled="acting" @click="openApprove(row, true)">{{ t('approval.action.reject') }}</el-button>
                <el-button link type="warning" :disabled="acting" @click="openAssign(row, 'transfer')">{{ t('approval.action.transfer') }}</el-button>
                <el-dropdown trigger="click" @command="(cmd) => onMore(cmd, row)">
                  <el-button link type="info" :disabled="acting">
                    {{ t('approval.action.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="delegate">{{ t('approval.action.delegate') }}</el-dropdown-item>
                      <el-dropdown-item command="resolve">{{ t('approval.action.resolveDelegate') }}</el-dropdown-item>
                      <el-dropdown-item command="claim">{{ t('approval.action.claim') }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button link type="primary" :icon="View" @click="openDetail(row.processInstanceId)">{{ t('common.detail') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 已办 -->
        <el-tab-pane :label="`${t('approval.tab.done')} (${stats.doneCount})`" name="done">
          <el-table :data="portal.doneTasks" v-loading="loading" border stripe>
            <el-table-column prop="name" :label="t('approval.task.node')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="processInstanceId" :label="t('approval.task.processInstanceId')" width="200" show-overflow-tooltip />
            <el-table-column prop="delegationState" :label="t('approval.task.delegationState')" width="130" align="center">
              <template #default="{ row }">{{ row.delegationState || '-' }}</template>
            </el-table-column>
            <el-table-column prop="dueAt" :label="t('approval.task.dueAt')" width="170">
              <template #default="{ row }">{{ formatTime(row.dueAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.action')" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openDetail(row.processInstanceId)">{{ t('common.detail') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 我发起 -->
        <el-tab-pane :label="`${t('approval.tab.instances')} (${stats.instanceCount})`" name="instances">
          <el-table :data="portal.myInstances" v-loading="loading" border stripe>
            <el-table-column prop="title" :label="t('approval.instance.title')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="businessType" :label="t('approval.instance.businessType')" width="120" align="center" />
            <el-table-column prop="status" :label="t('common.status')" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="instanceStatusType(row.status)">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" :label="t('approval.instance.startedAt')" width="170">
              <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column prop="finishedAt" :label="t('approval.instance.finishedAt')" width="170">
              <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.action')" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openDetail(row.processInstanceId)">{{ t('common.detail') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 可发起：门户内直接发起已发布流程 -->
        <el-tab-pane :label="`${t('approval.tab.startable')} (${stats.startableCount})`" name="startable">
          <el-table :data="portal.startable" v-loading="loading" border stripe>
            <el-table-column prop="processName" :label="t('approval.startable.processName')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="processKey" :label="t('approval.startable.processKey')" width="170" show-overflow-tooltip />
            <el-table-column prop="version" :label="t('approval.startable.version')" width="90" align="center">
              <template #default="{ row }">v{{ row.version ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('common.status')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="t('common.description')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="t('common.action')" width="170" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" :disabled="acting" @click="openStart(row)">{{ t('approval.action.start') }}</el-button>
                <el-button link type="primary" @click="goDefinition">{{ t('approval.startable.goStart') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 流程详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="t('approval.detail.title')" size="55%">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="t('approval.instance.title')" :span="2">{{ detail.title || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('approval.detail.processInstanceId')">{{ detail.processInstanceId }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.status')">
              <el-tag :type="instanceStatusType(detail.status)">{{ detail.status || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('approval.detail.starter')">{{ detail.starterUsername || detail.starterUserId || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('approval.instance.businessType')">{{ detail.businessType || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('approval.instance.startedAt')">{{ formatTime(detail.startedAt) }}</el-descriptions-item>
            <el-descriptions-item :label="t('approval.instance.finishedAt')">{{ formatTime(detail.finishedAt) }}</el-descriptions-item>
          </el-descriptions>

          <h4 class="section-title">{{ t('approval.detail.currentTasks') }}</h4>
          <el-table :data="detail.currentTasks" border size="small" :empty-text="t('common.noData')">
            <el-table-column prop="name" :label="t('approval.task.node')" min-width="140" />
            <el-table-column prop="assigneeUserId" :label="t('approval.task.assignee')" width="200" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('common.status')" width="110" align="center" />
          </el-table>

          <h4 class="section-title">{{ t('approval.detail.records') }}</h4>
          <el-timeline v-if="detail.records && detail.records.length > 0">
            <el-timeline-item
              v-for="record in detail.records"
              :key="record.operationId"
              :timestamp="formatTime(record.occurredAt)"
              :type="record.action === 'APPROVE' ? 'success' : record.action === 'REJECT' ? 'danger' : 'primary'"
            >
              <div class="record-item">
                <el-tag size="small" :type="recordActionType(record.action)">{{ record.action }}</el-tag>
                <span class="record-opinion">{{ record.opinion || '-' }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else :description="t('common.noData')" :image-size="60" />
        </template>
      </div>
    </el-drawer>

    <!-- 审批 / 驳回 -->
    <el-dialog
      v-model="approveVisible"
      :title="rejectMode ? t('approval.action.rejectTitle') : t('approval.action.approveTitle')"
      width="460px"
    >
      <el-form label-width="90px">
        <el-form-item :label="t('approval.task.node')">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item :label="t('approval.action.decision')">
          <el-tag :type="rejectMode ? 'danger' : 'success'">
            {{ rejectMode ? t('approval.action.reject') : t('approval.action.approve') }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="t('approval.action.opinion')">
          <el-input v-model="opinion" type="textarea" :rows="3" :placeholder="t('approval.action.opinionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="rejectMode ? 'danger' : 'primary'" :loading="acting" @click="submitApprove">
          {{ t('approval.action.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 转办 / 委派（共用目标用户与意见表单，仅动作不同） -->
    <el-dialog
      v-model="assignVisible"
      :title="assignMode === 'transfer' ? t('approval.action.transferTitle') : t('approval.action.delegateTitle')"
      width="460px"
    >
      <el-form label-width="100px">
        <el-form-item :label="t('approval.task.node')">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item :label="t('approval.action.targetUser')" required>
          <el-input v-model="targetUserId" :placeholder="t('approval.action.targetUserPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('approval.action.opinion')">
          <el-input v-model="opinion" type="textarea" :rows="3" :placeholder="t('approval.action.opinionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="acting" @click="submitAssign">{{ t('approval.action.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 发起流程 -->
    <el-dialog v-model="startVisible" :title="t('approval.action.startTitle')" width="480px">
      <el-form label-width="100px">
        <el-form-item :label="t('approval.action.processKey')">
          <span>{{ startForm.processKey }}</span>
        </el-form-item>
        <el-form-item :label="t('approval.action.businessType')" required>
          <el-input v-model="startForm.businessType" />
        </el-form-item>
        <el-form-item :label="t('approval.action.businessId')" required>
          <el-input v-model="startForm.businessId" />
        </el-form-item>
        <el-form-item :label="t('approval.action.title')" required>
          <el-input v-model="startForm.title" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="acting" @click="submitStart">{{ t('approval.action.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View, ArrowDown } from '@element-plus/icons-vue'
import { approvalDashboard, approvalDetail } from '@/api/approval'
import {
  approvalStart,
  approvalApprove,
  approvalTransfer,
  approvalDelegate,
  approvalResolveDelegate,
  approvalClaim
} from '@/api/approvalActions'
import { statusTag } from '@/utils/status'

const router = useRouter()
const loading = ref(false)
const acting = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const activeTab = ref('todo')
const detail = ref(null)

const approveVisible = ref(false)
const assignVisible = ref(false)
const startVisible = ref(false)
const rejectMode = ref(false)
const assignMode = ref('transfer')
const current = ref(null)
const opinion = ref('')
const targetUserId = ref('')
const startForm = reactive({ processKey: '', businessType: '', businessId: '', title: '' })

const portal = reactive({
  todoTasks: [],
  doneTasks: [],
  myInstances: [],
  startable: []
})
const stats = reactive({
  todoCount: 0,
  doneCount: 0,
  instanceCount: 0,
  startableCount: 0
})

// 与工作流任务页一致的幂等键格式（字母/数字/连字符），同时用作 body.operationId 与
// Idempotency-Key 请求头，保证后端双重去重语义对齐。
function genOpId() {
  return 'op-' + Date.now() + '-' + Math.floor(Math.random() * 1000000)
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function instanceStatusType(status) {
  if (status === 'RUNNING' || status === 'ACTIVE') return 'primary'
  if (status === 'COMPLETED') return 'success'
  if (status === 'TERMINATED' || status === 'CANCELLED') return 'danger'
  return 'info'
}

function recordActionType(action) {
  if (action === 'APPROVE') return 'success'
  if (action === 'REJECT') return 'danger'
  if (action === 'DELEGATE') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await approvalDashboard()
    portal.todoTasks = data.todoTasks || []
    portal.doneTasks = data.doneTasks || []
    portal.myInstances = data.myInstances || []
    portal.startable = data.startable || []
    if (data.stats) {
      stats.todoCount = data.stats.todoCount ?? 0
      stats.doneCount = data.stats.doneCount ?? 0
      stats.instanceCount = data.stats.instanceCount ?? 0
      stats.startableCount = data.stats.startableCount ?? 0
    }
  } finally {
    loading.value = false
  }
}

async function openDetail(processInstanceId) {
  if (!processInstanceId) return
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await approvalDetail(processInstanceId)
  } finally {
    detailLoading.value = false
  }
}

function goDefinition() {
  router.push('/workflow/definition')
}

function openApprove(row, reject) {
  current.value = row
  rejectMode.value = reject
  opinion.value = ''
  approveVisible.value = true
}

async function submitApprove() {
  acting.value = true
  try {
    await approvalApprove({
      taskId: current.value.taskId,
      action: rejectMode.value ? 'REJECT' : 'APPROVE',
      opinion: opinion.value,
      operationId: genOpId()
    })
    ElMessage.success(rejectMode.value ? t('common.rejected') : t('common.approved'))
    approveVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

function openAssign(row, mode) {
  current.value = row
  assignMode.value = mode
  opinion.value = ''
  targetUserId.value = ''
  assignVisible.value = true
}

async function submitAssign() {
  const toUserId = Number(targetUserId.value)
  if (!Number.isFinite(toUserId) || toUserId <= 0) {
    ElMessage.warning(t('approval.action.requiredFields'))
    return
  }
  acting.value = true
  try {
    const body = {
      taskId: current.value.taskId,
      toUserId,
      opinion: opinion.value,
      operationId: genOpId()
    }
    if (assignMode.value === 'transfer') {
      await approvalTransfer(body)
      ElMessage.success(t('approval.action.transferSuccess'))
    } else {
      await approvalDelegate(body)
      ElMessage.success(t('approval.action.delegateSuccess'))
    }
    assignVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

async function onMore(command, row) {
  if (command === 'delegate') {
    openAssign(row, 'delegate')
    return
  }
  acting.value = true
  try {
    if (command === 'claim') {
      await approvalClaim(row.taskId)
      ElMessage.success(t('approval.action.claimSuccess'))
    } else if (command === 'resolve') {
      await approvalResolveDelegate({ taskId: row.taskId, opinion: '', operationId: genOpId() })
      ElMessage.success(t('approval.action.resolveSuccess'))
    }
    await load()
  } finally {
    acting.value = false
  }
}

function openStart(row) {
  startForm.processKey = row.processKey || ''
  startForm.businessType = ''
  startForm.businessId = ''
  startForm.title = ''
  startVisible.value = true
}

async function submitStart() {
  if (!startForm.businessType.trim() || !startForm.businessId.trim() || !startForm.title.trim()) {
    ElMessage.warning(t('approval.action.requiredFields'))
    return
  }
  acting.value = true
  try {
    await approvalStart({
      processKey: startForm.processKey,
      businessType: startForm.businessType.trim(),
      businessId: startForm.businessId.trim(),
      title: startForm.title.trim(),
      assigneeUserIds: [],
      formFields: {}
    })
    ElMessage.success(t('approval.action.startSuccess'))
    startVisible.value = false
    activeTab.value = 'instances'
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stats-row {
  margin-bottom: 14px;
}
.stat-card {
  cursor: pointer;
  text-align: center;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-color-primary);
}
.stat-label {
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.section-title {
  margin: 18px 0 10px;
}
.record-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.record-opinion {
  color: var(--el-text-color-regular);
}
</style>
