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
        <!-- 待办 -->
        <el-tab-pane :label="`${t('approval.tab.todo')} (${stats.todoCount})`" name="todo">
          <el-table :data="portal.todoTasks" v-loading="loading" border stripe>
            <el-table-column prop="name" :label="t('approval.task.node')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="processInstanceId" :label="t('approval.task.processInstanceId')" width="200" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('common.status')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PENDING' ? 'warning' : 'info'">{{ row.status || '-' }}</el-tag>
              </template>
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

        <!-- 可发起 -->
        <el-tab-pane :label="`${t('approval.tab.startable')} (${stats.startableCount})`" name="startable">
          <el-table :data="portal.startable" v-loading="loading" border stripe>
            <el-table-column prop="processName" :label="t('approval.startable.processName')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="processKey" :label="t('approval.startable.processKey')" width="180" show-overflow-tooltip />
            <el-table-column prop="version" :label="t('approval.startable.version')" width="90" align="center">
              <template #default="{ row }">v{{ row.version ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('common.status')" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="t('common.action')" width="120" align="center" fixed="right">
              <template #default>
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
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { View } from '@element-plus/icons-vue'
import { approvalDashboard, approvalDetail } from '@/api/approval'
import { statusTag } from '@/utils/status'

const router = useRouter()
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const activeTab = ref('todo')
const detail = ref(null)

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
