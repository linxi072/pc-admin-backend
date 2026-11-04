<template>
  <div>
    <h2 class="page-title">{{ t('workflow.instanceAdmin.title') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('common.status') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.running')" clearable style="width: 130px" @change="onSearch">
              <el-option :label="t('common.running')" value="RUNNING" />
              <el-option :label="t('common.approved')" value="APPROVED" />
              <el-option :label="t('common.rejected')" value="REJECTED" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input
              v-model="query.keyword"
              :placeholder="t('workflow.titleOrBusinessNo')"
              clearable
              style="width: 220px"
              @keyup.enter="onSearch"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            <el-button :icon="Download" @click="onExportCsv">{{ t('common.exportCurrentPageCsv') }}</el-button>
          </div>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe empty-text=" ">
        <el-table-column prop="processInstanceId" :label="t('common.processInstance')" width="280" show-overflow-tooltip />
        <el-table-column prop="title" :label="t('common.title')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="businessType" :label="t('common.businessType')" width="110" />
        <el-table-column prop="businessId" :label="t('workflow.businessNo')" width="120" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">
              {{ statusTag(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.instanceAdmin.suspended')" width="90">
          <template #default="{ row }">
            <el-tag v-if="isSuspended(row)" type="warning" size="small">{{ t('workflow.instanceAdmin.suspended') }}</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.currentNode')" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.currentActivityId">{{ row.currentActivityId }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.initiator')" width="120" show-overflow-tooltip>
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
        <el-table-column :label="t('common.operation')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!isSuspended(row)"
              link
              type="warning"
              :icon="VideoPause"
              :loading="acting === row.processInstanceId + ':suspend'"
              @click="onSuspend(row)"
            >{{ t('workflow.instanceAdmin.suspend') }}</el-button>
            <el-button
              v-else
              link
              type="success"
              :icon="VideoPlay"
              :loading="acting === row.processInstanceId + ':resume'"
              @click="onResume(row)"
            >{{ t('workflow.instanceAdmin.resume') }}</el-button>
            <el-button
              link
              type="primary"
              :icon="Switch"
              :loading="acting === row.processInstanceId + ':reassign'"
              @click="openReassign(row)"
            >{{ t('workflow.instanceAdmin.reassign') }}</el-button>
            <el-button
              link
              type="primary"
              :icon="Promotion"
              :loading="acting === row.processInstanceId + ':migrate'"
              @click="openMigrate(row)"
            >{{ t('workflow.instanceAdmin.migrate') }}</el-button>
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
      <div class="hint">{{ t('workflow.instanceAdmin.desc') }}</div>
    </el-card>

    <!-- 改派对话框 -->
    <el-dialog v-model="reassignVisible" :title="t('workflow.instanceAdmin.reassignTitle')" width="460px" @open="loadUsers">
      <el-form label-width="100px">
        <el-form-item :label="t('workflow.instanceAdmin.reassignTarget')">
          <el-select
            v-model="reassignForm.toUserId"
            :placeholder="t('workflow.instanceAdmin.reassignPlaceholder')"
            filterable
            style="width: 100%"
            :loading="userLoading"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="(u.nickname ? u.nickname + '（' + u.username + '）' : u.username)"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="dialog-hint">
        {{ t('workflow.instanceAdmin.reassignConfirm', { title: currentRow?.title || '--', name: selectedUserName || '--' }) }}
      </div>
      <template #footer>
        <el-button @click="reassignVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!reassignForm.toUserId" @click="submitReassign">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 迁移对话框 -->
    <el-dialog v-model="migrateVisible" :title="t('workflow.instanceAdmin.migrateTitle')" width="460px" @open="loadDefinitions">
      <el-form label-width="100px">
        <el-form-item :label="t('workflow.instanceAdmin.migrateTarget')">
          <el-select
            v-model="migrateForm.toProcessDefinitionId"
            :placeholder="t('workflow.instanceAdmin.migratePlaceholder')"
            filterable
            style="width: 100%"
            :loading="defLoading"
          >
            <el-option
              v-for="d in definitionOptions"
              :key="d.processDefinitionId"
              :label="d.processName + '（v' + d.version + ' · ' + d.processKey + '）'"
              :value="d.processDefinitionId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="dialog-hint">
        {{ t('workflow.instanceAdmin.migrateConfirm', { title: currentRow?.title || '--', name: selectedDefName || '--' }) }}
      </div>
      <template #footer>
        <el-button @click="migrateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!migrateForm.toProcessDefinitionId" @click="submitMigrate">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, VideoPause, VideoPlay, Switch, Promotion, Download } from '@element-plus/icons-vue'
import { runningInstances, suspendInstance, resumeInstance, reassignInstance, migrateInstance, listDefinitions } from '@/api/workflow'
import { pageUsers } from '@/api/user'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
// 挂起态为引擎态，RunningInstanceView 不返回 suspended 标志，故以本地集合跟踪用户本次会话内的操作结果
const suspended = ref({})
// 当前正在执行的治理动作令牌（processInstanceId + ':' + action），用于按钮 loading
const acting = ref('')
// 对话框确认提交中的加载态
const submitting = ref(false)

const query = reactive({ page: 1, size: 10, status: '', keyword: '' })

function setSuspended(pid, val) {
  suspended.value = { ...suspended.value, [pid]: val }
}
const isSuspended = (row) => !!suspended.value[row.processInstanceId]

function buildParams() {
  return {
    page: query.page,
    size: query.size,
    status: query.status || undefined,
    keyword: query.keyword || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const page = await runningInstances(buildParams())
    rows.value = page.records || []
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
  query.status = ''
  query.keyword = ''
  query.page = 1
  load()
}

function statusTag(s) {
  const map = {
    RUNNING: { type: 'primary', label: t('common.running') },
    APPROVED: { type: 'success', label: t('common.approved') },
    REJECTED: { type: 'danger', label: t('common.rejected') }
  }
  return map[s] || { type: 'info', label: s || '--' }
}

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

// ---- 挂起 / 恢复 ----
async function onSuspend(row) {
  try {
    await ElMessageBox.confirm(t('workflow.instanceAdmin.suspendConfirm'), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  acting.value = row.processInstanceId + ':suspend'
  try {
    await suspendInstance(row.processInstanceId)
    setSuspended(row.processInstanceId, true)
    ElMessage.success(t('workflow.instanceAdmin.suspendSuccess'))
  } catch (e) {
    // 拦截器已统一提示（含 409 已挂起）
  } finally {
    acting.value = ''
  }
}

async function onResume(row) {
  try {
    await ElMessageBox.confirm(t('workflow.instanceAdmin.resumeConfirm'), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  acting.value = row.processInstanceId + ':resume'
  try {
    await resumeInstance(row.processInstanceId)
    setSuspended(row.processInstanceId, false)
    ElMessage.success(t('workflow.instanceAdmin.resumeSuccess'))
  } catch (e) {
    // 拦截器已统一提示（含 409 未挂起）
  } finally {
    acting.value = ''
  }
}

// ---- 改派 ----
const reassignVisible = ref(false)
const userLoading = ref(false)
const userOptions = ref([])
const currentRow = ref(null)
const reassignForm = reactive({ toUserId: '' })

const selectedUserName = computed(() => {
  const u = userOptions.value.find((x) => x.id === reassignForm.toUserId)
  return u ? (u.nickname ? u.nickname + '（' + u.username + '）' : u.username) : ''
})

async function loadUsers() {
  userLoading.value = true
  try {
    const page = await pageUsers({ page: 1, size: 200 })
    userOptions.value = page.records || []
  } catch (e) {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

function openReassign(row) {
  currentRow.value = row
  reassignForm.toUserId = ''
  reassignVisible.value = true
}

async function submitReassign() {
  if (!reassignForm.toUserId) return
  submitting.value = true
  acting.value = currentRow.value.processInstanceId + ':reassign'
  try {
    await reassignInstance(currentRow.value.processInstanceId, reassignForm.toUserId)
    ElMessage.success(t('workflow.instanceAdmin.reassignSuccess'))
    reassignVisible.value = false
  } catch (e) {
    // 拦截器已统一提示（含 400/404/409）
  } finally {
    submitting.value = false
    acting.value = ''
  }
}

// ---- 迁移 ----
const migrateVisible = ref(false)
const defLoading = ref(false)
const definitionOptions = ref([])
const migrateForm = reactive({ toProcessDefinitionId: '' })

const selectedDefName = computed(() => {
  const d = definitionOptions.value.find((x) => x.processDefinitionId === migrateForm.toProcessDefinitionId)
  return d ? d.processName + '（v' + d.version + '）' : ''
})

async function loadDefinitions() {
  defLoading.value = true
  try {
    definitionOptions.value = (await listDefinitions()) || []
  } catch (e) {
    definitionOptions.value = []
  } finally {
    defLoading.value = false
  }
}

function openMigrate(row) {
  currentRow.value = row
  migrateForm.toProcessDefinitionId = ''
  migrateVisible.value = true
}

async function submitMigrate() {
  if (!migrateForm.toProcessDefinitionId) return
  submitting.value = true
  acting.value = currentRow.value.processInstanceId + ':migrate'
  try {
    await migrateInstance(currentRow.value.processInstanceId, migrateForm.toProcessDefinitionId)
    ElMessage.success(t('workflow.instanceAdmin.migrateSuccess'))
    migrateVisible.value = false
  } catch (e) {
    // 拦截器已统一提示（含 400/404 目标不存在或相同）
  } finally {
    submitting.value = false
    acting.value = ''
  }
}

const exportColumns = [
  { label: t('common.processInstance'), get: (r) => r.processInstanceId },
  { label: t('common.title'), get: (r) => r.title },
  { label: t('common.businessType'), get: (r) => r.businessType || '--' },
  { label: t('workflow.businessNo'), get: (r) => r.businessId || '--' },
  { label: t('common.status'), get: (r) => statusTag(r.status).label },
  { label: t('workflow.instanceAdmin.suspended'), get: (r) => (isSuspended(r) ? t('workflow.instanceAdmin.suspended') : '--') },
  { label: t('workflow.currentNode'), get: (r) => r.currentActivityId || '--' },
  { label: t('workflow.initiator'), get: (r) => r.starterUsername || `#${r.starterUserId}` },
  { label: t('workflow.startTime'), get: (r) => r.startedAt || '--' },
  { label: t('workflow.stay'), get: (r) => formatMinutes(r.stayedMinutes) }
]
function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning(t('common.exportEmpty'))
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`instance-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(t('common.exportSuccess', { n: data.length }))
}

onMounted(load)
</script>

<style scoped>
.muted { color: #c0c4cc; }
.overdue-text { color: var(--el-color-danger); font-weight: 600; }
.dialog-hint {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 4px;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
</style>
