<template>
  <div>
    <h2 class="page-title">{{ '流程监控' }}</h2>

    <!-- 统计摘要 -->
    <div class="stats-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.runningInstances }}</div>
        <div class="stat-label">{{ '运行中实例' }}</div>
      </el-card>
      <el-card class="stat-card stat-danger" shadow="never">
        <div class="stat-value">{{ stats.overdueTasks }}</div>
        <div class="stat-label">{{ '超时任务' }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.pendingTasks }}</div>
        <div class="stat-label">{{ '待办任务' }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.todayStarted }}</div>
        <div class="stat-label">{{ '今日发起' }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.todayFinished }}</div>
        <div class="stat-label">{{ '今日办结' }}</div>
      </el-card>
    </div>

    <el-card class="page-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 运行实例 -->
        <el-tab-pane :label="'运行实例'" name="instances">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">{{ '状态' }}</span>
                <el-select v-model="instanceQuery.status" :placeholder="'运行中'" clearable style="width: 130px" @change="onInstanceSearch">
                  <el-option :label="'运行中'" value="RUNNING" />
                  <el-option :label="'已通过'" value="APPROVED" />
                  <el-option :label="'已驳回'" value="REJECTED" />
                </el-select>
              </div>
              <div class="filter-group">
                <span class="filter-label">{{ '关键字' }}</span>
                <el-input
                  v-model="instanceQuery.keyword"
                  :placeholder="'标题 / 业务单号'"
                  clearable
                  style="width: 200px"
                  @keyup.enter="onInstanceSearch"
                />
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button type="primary" :icon="Search" @click="onInstanceSearch">{{ '查询' }}</el-button>
                <el-button :icon="RefreshLeft" @click="resetInstanceQuery">{{ '重置' }}</el-button>
              </div>
            </div>
          </div>
          <el-table :data="instanceRows" v-loading="instanceLoading" border stripe>
            <el-table-column prop="processInstanceId" :label="'流程实例'" width="280" show-overflow-tooltip />
            <el-table-column prop="title" :label="'标题'" min-width="180" show-overflow-tooltip />
            <el-table-column prop="businessType" :label="'业务类型'" width="110" />
            <el-table-column prop="businessId" :label="'业务单号'" width="110" show-overflow-tooltip />
            <el-table-column :label="'状态'" width="100">
              <template #default="{ row }">
                <el-tag :type="instanceStatusTag(row.status).type" size="small">
                  {{ instanceStatusTag(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="'当前节点'" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.currentActivityId">{{ row.currentActivityId }}</span>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
            <el-table-column :label="'发起人'" width="110" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.starterUsername">{{ row.starterUsername }}</span>
                <span v-else class="muted">#{{ row.starterUserId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" :label="'发起时间'" width="170" />
            <el-table-column :label="'停留'" width="100">
              <template #default="{ row }">
                <span :class="{ 'overdue-text': row.stayedMinutes >= 1440 }">{{ formatMinutes(row.stayedMinutes) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="instanceQuery.page"
              v-model:page-size="instanceQuery.size"
              :total="instanceTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadInstances"
              @size-change="onInstanceSizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- 超时任务 -->
        <el-tab-pane :label="'超时任务'" name="overdue">
          <div class="toolbar">
            <span class="toolbar-tip">{{ '已过到期时间仍未办结的任务，最紧急在前；可对办理人实时催办。' }}</span>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button :icon="Refresh" @click="loadOverdue">{{ '刷新' }}</el-button>
              </div>
            </div>
          </div>
          <el-table :data="overdueRows" v-loading="overdueLoading" border stripe>
            <el-table-column prop="taskName" :label="'任务'" width="140" show-overflow-tooltip />
            <el-table-column prop="instanceTitle" :label="'流程标题'" min-width="160" show-overflow-tooltip />
            <el-table-column :label="'办理人'" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.assigneeUsername">{{ row.assigneeUsername }}</span>
                <span v-else class="muted">#{{ row.assigneeUserId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="dueAt" :label="'到期时间'" width="170" />
            <el-table-column :label="'超时'" width="100">
              <template #default="{ row }">
                <span class="overdue-text">{{ formatMinutes(row.overdueMinutes) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="processInstanceId" :label="'流程实例'" min-width="200" show-overflow-tooltip />
            <el-table-column :label="'操作'" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Bell" @click="onRemind(row)">{{ '催办' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="overdueQuery.page"
              v-model:page-size="overdueQuery.size"
              :total="overdueTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadOverdue"
              @size-change="onOverdueSizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- 定义版本链 -->
        <el-tab-pane :label="'定义版本链'" name="versions">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">{{ '流程' }}</span>
                <el-select
                  v-model="versionKey"
                  :placeholder="'选择流程定义'"
                  style="width: 260px"
                  @change="loadVersions"
                >
                  <el-option
                    v-for="d in definitions"
                    :key="d.processKey"
                    :label="d.processName + '（' + d.processKey + '）'"
                    :value="d.processKey"
                  />
                </el-select>
              </div>
            </div>
          </div>
          <el-table :data="versionRows" v-loading="versionLoading" border stripe>
            <el-table-column prop="version" :label="'版本'" width="80" />
            <el-table-column prop="name" :label="'名称'" min-width="160" show-overflow-tooltip />
            <el-table-column prop="key" :label="'流程 Key'" width="180" show-overflow-tooltip />
            <el-table-column :label="'状态'" width="90">
              <template #default="{ row }">
                <el-tag :type="row.suspended ? 'warning' : 'success'" size="small">
                  {{ row.suspended ? '已挂起' : '活跃' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deployTime" :label="'部署时间'" width="170">
              <template #default="{ row }">
                <span v-if="row.deployTime">{{ row.deployTime }}</span>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
            <el-table-column prop="id" :label="'定义 ID'" min-width="220" show-overflow-tooltip />
            <el-table-column prop="deploymentId" :label="'部署 ID'" min-width="180" show-overflow-tooltip />
            <el-table-column :label="'操作'" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.suspended"
                  link
                  type="warning"
                  :loading="definitionActionId === row.id"
                  @click="onToggleDefinition(row, 'suspend')"
                >{{ '挂起' }}</el-button>
                <el-button
                  v-else
                  link
                  type="success"
                  :loading="definitionActionId === row.id"
                  @click="onToggleDefinition(row, 'activate')"
                >{{ '激活' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!versionLoading && versionRows.length === 0" :description="'选择流程定义查看版本链'" />
        </el-tab-pane>
      </el-tabs>

      <div class="hint">
        监控数据以引擎与业务扩展表为准：超时判定取任务到期时间（BPMN 配置），停留时长按发起时间累计；
        催办受服务端最小间隔约束（默认 30 分钟，{@code app.workflow.monitor.remind-min-interval-minutes} 可配），
        记录写入审批流水（action=REMIND）。定义版本链可挂起/激活流程定义（阻止或恢复新实例发起，不影响在途实例）。
      </div>
    </el-card>
  </div>
</template>

<script setup>

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Refresh, Bell } from '@element-plus/icons-vue'
import {
  runningInstances,
  overdueTasks,
  definitionVersions,
  monitorStats,
  remindTask,
  listDefinitions,
  suspendDefinition,
  activateDefinition
} from '@/api/workflow'

const activeTab = ref('instances')

// ---- 统计 ----
const stats = reactive({
  runningInstances: 0,
  overdueTasks: 0,
  pendingTasks: 0,
  todayStarted: 0,
  todayFinished: 0
})

async function loadStats() {
  try {
    const s = await monitorStats()
    Object.assign(stats, s)
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

// ---- 运行实例 ----
const instanceLoading = ref(false)
const instanceRows = ref([])
const instanceTotal = ref(0)
const instanceQuery = reactive({ page: 1, size: 10, status: '', keyword: '' })

function instanceStatusTag(s) {
  const map = {
    RUNNING: { type: 'primary', label: '运行中' },
    APPROVED: { type: 'success', label: '已通过' },
    REJECTED: { type: 'danger', label: '已驳回' }
  }
  return map[s] || { type: 'info', label: s || '--' }
}

async function loadInstances() {
  instanceLoading.value = true
  try {
    const page = await runningInstances({
      page: instanceQuery.page,
      size: instanceQuery.size,
      status: instanceQuery.status || undefined,
      keyword: instanceQuery.keyword || undefined
    })
    instanceRows.value = page.records || []
    instanceTotal.value = page.total || 0
  } finally {
    instanceLoading.value = false
  }
}

function onInstanceSearch() {
  instanceQuery.page = 1
  loadInstances()
}

function onInstanceSizeChange(size) {
  instanceQuery.size = size
  instanceQuery.page = 1
  loadInstances()
}

function resetInstanceQuery() {
  instanceQuery.status = ''
  instanceQuery.keyword = ''
  instanceQuery.page = 1
  loadInstances()
}

// ---- 超时任务 ----
const overdueLoading = ref(false)
const overdueRows = ref([])
const overdueTotal = ref(0)
const overdueQuery = reactive({ page: 1, size: 10 })

async function loadOverdue() {
  overdueLoading.value = true
  try {
    const page = await overdueTasks({ page: overdueQuery.page, size: overdueQuery.size })
    overdueRows.value = page.records || []
    overdueTotal.value = page.total || 0
  } finally {
    overdueLoading.value = false
  }
}

function onOverdueSizeChange(size) {
  overdueQuery.size = size
  overdueQuery.page = 1
  loadOverdue()
}

async function onRemind(row) {
  try {
    await remindTask(row.taskId)
    ElMessage.success('已催办' + ' ' + (row.assigneeUsername || ('#' + row.assigneeUserId)))
  } catch (e) {
    /* 拦截器已统一提示（含太频繁的 429） */
  }
}

// ---- 定义版本链 ----
const versionLoading = ref(false)
const versionRows = ref([])
const versionKey = ref('')
const definitions = ref([])
const definitionActionId = ref(null)

async function loadVersions() {
  if (!versionKey.value) {
    versionRows.value = []
    return
  }
  versionLoading.value = true
  try {
    versionRows.value = (await definitionVersions(versionKey.value)) || []
  } finally {
    versionLoading.value = false
  }
}

/** 挂起/激活流程定义：二次确认后调用端点，成功刷新版本链；404/409 由 http 拦截器统一提示。 */
async function onToggleDefinition(row, action) {
  if (definitionActionId.value != null) return
  const isSuspend = action === 'suspend'
  const confirmed = await ElMessageBox.confirm(
    isSuspend
      ? `确认挂起流程定义「${row.name || row.key}」v${row.version} 吗？挂起后无法发起新实例，在途实例不受影响。`
      : `确认激活流程定义「${row.name || row.key}」v${row.version} 吗？激活后恢复新实例发起能力。`,
    '提示',
    { type: isSuspend ? 'warning' : 'info', confirmButtonText: isSuspend ? '确认挂起' : '确认激活', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!confirmed) return
  definitionActionId.value = row.id
  try {
    if (isSuspend) {
      await suspendDefinition(row.id)
      ElMessage.success('流程定义已挂起')
    } else {
      await activateDefinition(row.id)
      ElMessage.success('流程定义已激活')
    }
    await loadVersions()
  } catch {
    // 错误提示由 http 响应拦截器统一处理（含不存在/状态冲突的 404/409）
  } finally {
    definitionActionId.value = null
  }
}

/** 分钟数格式化为「x 小时 y 分 / y 分」（0 分显示「0 分」）。 */
function formatMinutes(m) {
  const total = Number(m) || 0
  if (total < 60) return total + ' ' + '分'
  const h = Math.floor(total / 60)
  const rest = total % 60
  if (h < 24) return rest === 0 ? h + ' ' + '小时' : h + ' ' + '小时' + ' ' + rest + ' ' + '分'
  const d = Math.floor(h / 24)
  const rh = h % 24
  return rh === 0 ? d + ' ' + '天' : d + ' ' + '天' + ' ' + rh + ' ' + '小时'
}

function onTabChange(name) {
  if (name === 'overdue') loadOverdue()
  if (name === 'versions' && versionKey.value) loadVersions()
}

onMounted(async () => {
  await Promise.all([loadStats(), loadInstances(), loadOverdue()])
  try {
    definitions.value = (await listDefinitions()) || []
    if (definitions.value.length > 0) {
      versionKey.value = definitions.value[0].processKey
      await loadVersions()
    }
  } catch (e) {
    definitions.value = []
  }
})
</script>

<style scoped>
.stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.stat-card {
  flex: 1;
  min-width: 140px;
  text-align: center;
}
.stat-card :deep(.el-card__body) {
  padding: 14px 12px;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary);
}
.stat-danger .stat-value {
  color: var(--el-color-danger);
}
.stat-label {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 4px;
}
.muted { color: #c0c4cc; }
.overdue-text { color: var(--el-color-danger); font-weight: 600; }
.toolbar-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 32px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
</style>
