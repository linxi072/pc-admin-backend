<template>
  <div>
    <h2 class="page-title">{{ t('workflow.processMonitor') }}</h2>

    <!-- 统计摘要 -->
    <div class="stats-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.runningInstances }}</div>
        <div class="stat-label">{{ t('workflow.runningInstances') }}</div>
      </el-card>
      <el-card class="stat-card stat-danger" shadow="never">
        <div class="stat-value">{{ stats.overdueTasks }}</div>
        <div class="stat-label">{{ t('workflow.overdueTasks') }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.pendingTasks }}</div>
        <div class="stat-label">{{ t('workflow.todoTasks') }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.todayStarted }}</div>
        <div class="stat-label">{{ t('workflow.todayInitiated') }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.todayFinished }}</div>
        <div class="stat-label">{{ t('workflow.todayCompleted') }}</div>
      </el-card>
    </div>

    <el-card class="page-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 运行实例 -->
        <el-tab-pane :label="t('workflow.runningInstance')" name="instances">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">{{ t('common.status') }}</span>
                <el-select v-model="instanceQuery.status" :placeholder="t('common.running')" clearable style="width: 130px" @change="onInstanceSearch">
                  <el-option :label="t('common.running')" value="RUNNING" />
                  <el-option :label="t('common.approved')" value="APPROVED" />
                  <el-option :label="t('common.rejected')" value="REJECTED" />
                </el-select>
              </div>
              <div class="filter-group">
                <span class="filter-label">{{ t('common.keyword') }}</span>
                <el-input
                  v-model="instanceQuery.keyword"
                  :placeholder="t('workflow.titleOrBusinessNo')"
                  clearable
                  style="width: 200px"
                  @keyup.enter="onInstanceSearch"
                />
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button type="primary" :icon="Search" @click="onInstanceSearch">{{ t('common.query') }}</el-button>
                <el-button :icon="RefreshLeft" @click="resetInstanceQuery">{{ t('common.reset') }}</el-button>
              </div>
            </div>
          </div>
          <el-table :data="instanceRows" v-loading="instanceLoading" border stripe>
            <el-table-column prop="processInstanceId" :label="t('common.processInstance')" width="280" show-overflow-tooltip />
            <el-table-column prop="title" :label="t('common.title')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="businessType" :label="t('common.businessType')" width="110" />
            <el-table-column prop="businessId" :label="t('workflow.businessNo')" width="110" show-overflow-tooltip />
            <el-table-column :label="t('common.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="instanceStatusTag(row.status).type" size="small">
                  {{ instanceStatusTag(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.currentNode')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.currentActivityId">{{ row.currentActivityId }}</span>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.initiator')" width="110" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.starterUsername">{{ row.starterUsername }}</span>
                <span v-else class="muted">#{{ row.starterUserId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" :label="t('workflow.startTime')" width="170" />
            <el-table-column :label="t('workflow.stay')" width="100">
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
        <el-tab-pane :label="t('workflow.overdueTasks')" name="overdue">
          <div class="toolbar">
            <span class="toolbar-tip">{{ t('workflow.overdueHint') }}</span>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button :icon="Refresh" @click="loadOverdue">{{ t('common.refresh') }}</el-button>
              </div>
            </div>
          </div>
          <el-table :data="overdueRows" v-loading="overdueLoading" border stripe>
            <el-table-column prop="taskName" :label="t('common.task')" width="140" show-overflow-tooltip />
            <el-table-column prop="instanceTitle" :label="t('workflow.processTitle')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="t('workflow.handler')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.assigneeUsername">{{ row.assigneeUsername }}</span>
                <span v-else class="muted">#{{ row.assigneeUserId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="dueAt" :label="t('workflow.dueTime')" width="170" />
            <el-table-column :label="t('workflow.overdue')" width="100">
              <template #default="{ row }">
                <span class="overdue-text">{{ formatMinutes(row.overdueMinutes) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="processInstanceId" :label="t('common.processInstance')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="t('common.operation')" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Bell" @click="onRemind(row)">{{ t('workflow.urge') }}</el-button>
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
        <el-tab-pane :label="t('workflow.definitionVersionChain')" name="versions">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">{{ t('workflow.process') }}</span>
                <el-select
                  v-model="versionKey"
                  :placeholder="t('workflow.selectProcessDefinition')"
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
            <el-table-column prop="version" :label="t('common.version')" width="80" />
            <el-table-column prop="name" :label="t('common.name')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="key" :label="t('workflow.processKey')" width="180" show-overflow-tooltip />
            <el-table-column :label="t('common.status')" width="90">
              <template #default="{ row }">
                <el-tag :type="row.suspended ? 'warning' : 'success'" size="small">
                  {{ row.suspended ? t('common.suspended') : t('common.active') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deployTime" :label="t('workflow.deployTime')" width="170">
              <template #default="{ row }">
                <span v-if="row.deployTime">{{ row.deployTime }}</span>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
            <el-table-column prop="id" :label="t('workflow.definitionId')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="deploymentId" :label="t('workflow.deploymentId')" min-width="180" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!versionLoading && versionRows.length === 0" :description="t('workflow.selectDefViewChain')" />
        </el-tab-pane>
      </el-tabs>

      <div class="hint">
        监控数据以引擎与业务扩展表为准：超时判定取任务到期时间（BPMN 配置），停留时长按发起时间累计；
        催办受服务端最小间隔约束（默认 30 分钟，{@code app.workflow.monitor.remind-min-interval-minutes} 可配），
        记录写入审批流水（action=REMIND）。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Refresh, Bell } from '@element-plus/icons-vue'
import {
  runningInstances,
  overdueTasks,
  definitionVersions,
  monitorStats,
  remindTask,
  listDefinitions
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
    RUNNING: { type: 'primary', label: t('common.running') },
    APPROVED: { type: 'success', label: t('common.approved') },
    REJECTED: { type: 'danger', label: t('common.rejected') }
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
    ElMessage.success(t('workflow.urged') + ' ' + (row.assigneeUsername || ('#' + row.assigneeUserId)))
  } catch (e) {
    /* 拦截器已统一提示（含太频繁的 429） */
  }
}

// ---- 定义版本链 ----
const versionLoading = ref(false)
const versionRows = ref([])
const versionKey = ref('')
const definitions = ref([])

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

/** 分钟数格式化为「x 小时 y 分 / y 分」（0 分显示「0 分」）。 */
function formatMinutes(m) {
  const total = Number(m) || 0
  if (total < 60) return total + ' ' + t('workflow.minuteUnit')
  const h = Math.floor(total / 60)
  const rest = total % 60
  if (h < 24) return rest === 0 ? h + ' ' + t('workflow.hourUnit') : h + ' ' + t('workflow.hourUnit') + ' ' + rest + ' ' + t('workflow.minuteUnit')
  const d = Math.floor(h / 24)
  const rh = h % 24
  return rh === 0 ? d + ' ' + t('workflow.dayUnit') : d + ' ' + t('workflow.dayUnit') + ' ' + rh + ' ' + t('workflow.hourUnit')
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
