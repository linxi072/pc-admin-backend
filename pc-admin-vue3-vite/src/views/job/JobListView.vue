<template>
  <div>
    <h2 class="page-title">{{ t('job.title') }}</h2>

    <!-- 调度引擎不可用提示 -->
    <el-alert
      v-if="metaLoaded && !meta.schedulerAvailable"
      class="mt-12"
      type="warning"
      show-icon
      :closable="false"
      :title="t('job.engineUnavailable')"
    />

    <!-- 筛选工具栏：条件区（筛选控件分组）/ 操作区（查询动作与主操作分层） -->
    <el-card class="toolbar" shadow="never">
      <div class="toolbar-filters">
        <div class="filter-group">
          <span class="filter-label">{{ t('job.taskName') }}</span>
          <el-input
            v-model="query.jobName"
            :placeholder="t('job.taskName')"
            clearable
            style="width: 170px"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="filter-group">
          <span class="filter-label">{{ t('common.status') }}</span>
          <el-select v-model="query.status" :placeholder="t('job.allStatus')" clearable style="width: 170px" @change="onSearch">
            <el-option :label="t('job.running')" value="RUNNING" />
            <el-option :label="t('job.paused')" value="PAUSED" />
            <el-option :label="t('job.completed')" value="COMPLETED" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">{{ t('job.triggerType') }}</span>
          <el-select v-model="query.triggerType" :placeholder="t('job.allTriggerType')" clearable style="width: 170px" @change="onSearch">
            <el-option :label="t('job.cronExpr')" value="CRON" />
            <el-option :label="t('job.fixedInterval')" value="SIMPLE" />
            <el-option :label="t('job.singleRun')" value="ONE_TIME" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">{{ t('job.processor') }}</span>
          <el-select v-model="query.taskKey" :placeholder="t('job.allProcessor')" clearable style="width: 170px" @change="onSearch">
            <el-option v-for="h in handlers" :key="h.key" :label="h.name" :value="h.key" />
          </el-select>
        </div>
      </div>

      <div class="toolbar-actions">
        <div class="action-buttons">
          <el-button :loading="loading" @click="onSearch">{{ t('common.query') }}</el-button>
          <el-button @click="reset">{{ t('common.reset') }}</el-button>
          <el-button :icon="Download" @click="onExportCsv">{{ t('common.exportCurrentPageCsv') }}</el-button>
          <el-switch v-model="autoRefresh" :active-text="t('job.autoRefresh')" inline-prompt />
        </div>
        <div class="action-buttons">
          <el-button type="primary" @click="openCreate">{{ t('job.newTask') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card class="mt-12" shadow="never">
      <el-skeleton v-if="loading && !rows.length" :rows="6" animated />
      <template v-else>
        <el-table :data="rows" border stripe size="small" style="width: 100%">
          <el-table-column prop="jobName" :label="t('job.taskName')" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="cell-name">{{ row.jobName }}</div>
              <div class="cell-sub">{{ row.jobGroup }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.processor')" min-width="130">
            <template #default="{ row }">
              <el-tooltip :content="row.taskKey" placement="top">
                <span>{{ row.taskKeyName || row.taskKey }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.triggerRule')" min-width="180">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" class="mr-6">{{ row.triggerTypeLabel || row.triggerType }}</el-tag>
              <span class="rule-text">{{ ruleText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.dependencyOrchestration')" min-width="200">
            <template #default="{ row }">
              <div class="dag-line">
                <span class="dag-label">{{ t('job.dependency') }}</span>
                <template v-if="row.dependsOn && row.dependsOn.length">
                  <el-tag v-for="d in row.dependsOn" :key="'up'+d" size="small" type="info" effect="plain" class="mr-4">{{ jobNameOf(d) }}</el-tag>
                </template>
                <span v-else class="cell-sub">{{ t('common.none') }}</span>
              </div>
              <div class="dag-line">
                <span class="dag-label">{{ t('job.downstream') }}</span>
                <template v-if="row.dependents && row.dependents.length">
                  <el-tag v-for="d in row.dependents" :key="'down'+d" size="small" type="success" effect="plain" class="mr-4">{{ jobNameOf(d) }}</el-tag>
                </template>
                <span v-else class="cell-sub">{{ t('common.none') }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.params')" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono">{{ row.jobParams || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusLabel || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.lastExec')" width="150">
            <template #default="{ row }">
              <div>{{ fmtTime(row.lastFireTime) }}</div>
              <div v-if="row.lastStatus" class="cell-sub">
                <el-tag :type="row.lastStatus === 'SUCCESS' ? 'success' : 'danger'" size="small" effect="plain">
                  {{ row.lastStatus === 'SUCCESS' ? t('common.success') : t('common.failed') }}
                </el-tag>
                <span v-if="row.lastDurationMs != null" class="ml-4">{{ row.lastDurationMs }}ms</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.nextExec')" width="150">
            <template #default="{ row }">
              <span :class="{ 'text-strong': !!row.nextFireTime }">{{ fmtTime(row.nextFireTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('job.execCount')" width="120" align="center">
            <template #default="{ row }">
              <div>{{ row.totalRuns ?? 0 }}</div>
              <div class="cell-sub">成功 {{ row.successRuns ?? 0 }} / 失败 {{ row.failureRuns ?? 0 }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.operation')" width="260" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'RUNNING'"
                link
                type="success"
                size="small"
                @click="onStart(row)"
              >{{ t('job.start') }}</el-button>
              <el-button
                v-else
                link
                type="warning"
                size="small"
                @click="onPause(row)"
              >{{ t('job.pause') }}</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
              <el-button
                v-if="row.dependents && row.dependents.length"
                link
                type="warning"
                size="small"
                @click="onTriggerDownstream(row)"
              >{{ t('job.triggerDownstream') }}</el-button>
              <el-button link type="info" size="small" @click="openLogs(row)">{{ t('job.log') }}</el-button>
              <el-popconfirm :title="t('job.confirmDelete')" @confirm="onDelete(row)">
                <template #reference>
                  <el-button link type="danger" size="small">{{ t('common.delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!rows.length && !loading" :description="t('job.empty')" />

        <div class="pager">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </template>
    </el-card>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? t('job.addTitle') : t('job.editTitle')" width="620px">
      <el-form :model="form" label-width="96px">
        <el-form-item :label="t('job.taskName')" required>
          <el-input v-model="form.jobName" maxlength="128" :placeholder="t('job.exampleName')" />
        </el-form-item>
        <el-form-item :label="t('job.taskGroup')">
          <el-input v-model="form.jobGroup" maxlength="64" :placeholder="t('job.defaultGroup')" />
        </el-form-item>
        <el-form-item :label="t('job.processor')" required>
          <el-select v-model="form.taskKey" :placeholder="t('job.pleaseSelectProcessor')" style="width: 100%">
            <el-option v-for="h in handlers" :key="h.key" :label="`${h.name}（${h.key}）`" :value="h.key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('job.upstreamDependency')">
          <el-select
            v-model="form.dependsOn"
            multiple
            filterable
            :placeholder="t('job.dependencyHint')"
            style="width: 100%"
          >
            <el-option
              v-for="j in dependencyOptions"
              :key="j.id"
              :label="j.jobName"
              :value="Number(j.id)"
            />
          </el-select>
          <div class="hint">{{ t('job.waitDependencyHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('job.triggerType')" required>
          <el-radio-group v-model="form.triggerType">
            <el-radio-button value="CRON">{{ t('job.cronExpr') }}</el-radio-button>
            <el-radio-button value="SIMPLE">{{ t('job.fixedInterval') }}</el-radio-button>
            <el-radio-button value="ONE_TIME">{{ t('job.singleRun') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.triggerType === 'CRON'" label="cron" required>
          <el-input v-model="form.cronExpression" :placeholder="t('job.cronExample')" />
          <div class="hint">{{ t('job.cronHint') }}</div>
        </el-form-item>

        <el-form-item v-if="form.triggerType === 'SIMPLE'" :label="t('job.interval')" required>
          <div class="interval-row">
            <el-input-number v-model="form.intervalValue" :min="1" :max="100000" controls-position="right" />
            <el-select v-model="form.intervalUnit" class="unit-select">
              <el-option :label="t('job.second')" value="SECONDS" />
              <el-option :label="t('job.minute')" value="MINUTES" />
              <el-option :label="t('job.hour')" value="HOURS" />
              <el-option :label="t('job.day')" value="DAYS" />
            </el-select>
          </div>
          <div class="hint">最小间隔 {{ meta.smallestIntervalSeconds || 5 }} 秒。</div>
        </el-form-item>

        <el-form-item v-if="form.triggerType === 'ONE_TIME'" :label="t('job.execTime')" required>
          <el-date-picker
            v-model="form.oneTimeAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :placeholder="t('job.execTimeHint')"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item :label="t('job.taskParams')">
          <el-input
            v-model="form.jobParams"
            type="textarea"
            :rows="3"
            :placeholder="t('job.paramsExample')"
          />
          <div class="hint">{{ t('job.paramsJsonHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('job.remark')">
          <el-input v-model="form.remark" maxlength="255" :placeholder="t('common.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="onSubmit">
          {{ dialog.mode === 'create' ? t('job.create') : t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 执行日志对话框 -->
    <el-dialog v-model="logDialog.visible" :title="t('job.logTitle') + ' · ' + logDialog.jobName" width="760px">
      <el-skeleton v-if="logDialog.loading" :rows="5" animated />
      <template v-else>
        <el-empty v-if="!logDialog.rows.length" :description="t('job.noExecRecord')" :image-size="80" />
        <template v-else>
          <el-card shadow="never" :header="t('job.durationTrend')" class="mb-12">
            <BaseChart :option="logChartOption" height="200px" />
          </el-card>
          <el-table :data="logDialog.rows" border size="small" max-height="320">
            <el-table-column :label="t('job.triggerTime')" width="160">
              <template #default="{ row }">{{ fmtTime(row.fireTime) }}</template>
            </el-table-column>
            <el-table-column :label="t('job.result')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'SUCCESS' ? t('common.success') : t('common.failed') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('job.duration')" width="90" align="right">
              <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + ' ms' : '—' }}</template>
            </el-table-column>
            <el-table-column label="traceId" width="160" show-overflow-tooltip>
              <template #default="{ row }"><span class="mono">{{ row.traceId || '—' }}</span></template>
            </el-table-column>
            <el-table-column :label="t('job.errorMsg')" min-width="200" show-overflow-tooltip>
              <template #default="{ row }"><span class="err-text">{{ row.errorMessage || '—' }}</span></template>
            </el-table-column>
          </el-table>
        </template>
      </template>
      <template #footer>
        <el-button @click="logDialog.visible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import BaseChart from '@/components/BaseChart.vue'
import {
  jobMeta, jobHandlers, jobPage,
  jobCreate, jobUpdate, jobStart, jobPause, jobDelete, jobLogs,
  jobTriggerDownstream
} from '@/api/job'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const handlers = ref([])
const metaLoaded = ref(false)
const meta = reactive({ schedulerAvailable: true, smallestIntervalSeconds: 5 })

// 依赖编排：全部任务清单（用于依赖选择器与 ID→名称反查）
const allJobs = ref([])
const jobNameMap = ref({}) // { [id:string]: jobName }
const selfId = ref(null) // 编辑时的当前任务 ID（依赖选择器排除自身，防自依赖）
const dependencyOptions = computed(() =>
  allJobs.value.filter((j) => String(j.id) !== String(selfId.value))
)
// 依赖 ID → 名称（dependsOn 来自后端是 number，row.id 为字符串，统一按字符串键查）
function jobNameOf(id) {
  return jobNameMap.value[String(id)] || ('#' + id)
}

const query = reactive({
  jobName: '',
  status: '',
  triggerType: '',
  taskKey: '',
  page: 1,
  size: 10
})

// 自动刷新：开启后按固定周期仅刷新列表（不打扰正在编辑的对话框）
const autoRefresh = ref(false)
let refreshTimer = null
const REFRESH_MS = 10000

// ---------------- 展示辅助 ----------------
function fmtTime(v) {
  if (!v) return '—'
  const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/.exec(v)
  if (!m) return v
  return `${m[2]}-${m[3]} ${m[4]}:${m[5]}:${m[6]}`
}
function statusTagType(status) {
  if (status === 'RUNNING') return 'success'
  if (status === 'WAITING') return 'warning'
  if (status === 'COMPLETED') return 'warning'
  return 'info'
}
function ruleText(row) {
  if (row.triggerType === 'CRON') return row.cronExpression || '—'
  if (row.triggerType === 'SIMPLE') return row.intervalLabel || '—'
  if (row.triggerType === 'ONE_TIME') return fmtTime(row.oneTimeAt)
  return '—'
}

// ---------------- 导出当前页 CSV ----------------
const exportColumns = [
  { label: t('job.taskName'), get: (r) => r.jobName },
  { label: t('job.taskGroup'), get: (r) => r.jobGroup || '' },
  { label: t('job.processor'), get: (r) => r.taskKeyName || r.taskKey },
  {
    label: t('job.triggerRule'),
    get: (r) => {
      const trig = r.triggerTypeLabel || r.triggerType
      const rule = ruleText(r)
      return rule && rule !== '—' ? `${trig} ${rule}` : trig
    }
  },
  { label: t('common.status'), get: (r) => r.statusLabel || r.status },
  { label: t('job.lastExec'), get: (r) => fmtTime(r.lastFireTime) },
  {
    label: t('job.result'),
    get: (r) =>
      r.lastStatus
        ? (r.lastStatus === 'SUCCESS' ? t('common.success') : t('common.failed'))
        : ''
  },
  { label: t('job.nextExec'), get: (r) => fmtTime(r.nextFireTime) },
  {
    label: t('job.execCount'),
    get: (r) =>
      `${r.totalRuns ?? 0}（${t('common.success')} ${r.successRuns ?? 0} / ${t('common.failed')} ${r.failureRuns ?? 0}）`
  }
]

function onExportCsv() {
  if (!rows.value.length) {
    ElMessage.warning(t('common.exportEmpty'))
    return
  }
  const headers = exportColumns.map((c) => c.label)
  const data = rows.value.map((r) => exportColumns.map((c) => c.get(r)))
  downloadCsv(`job-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(t('common.exportSuccess', { n: data.length }))
}

// ---------------- 数据加载 ----------------
async function loadMeta() {
  try {
    const res = await jobMeta()
    meta.schedulerAvailable = !!res?.schedulerAvailable
    meta.smallestIntervalSeconds = res?.smallestIntervalSeconds || 5
  } catch (e) {
    // 元信息为可选增强，失败不阻塞列表
  } finally {
    metaLoaded.value = true
  }
}

async function loadHandlers() {
  try {
    handlers.value = (await jobHandlers()) || []
  } catch (e) {
    handlers.value = []
  }
}

// 加载全部任务（依赖编排：选择器选项 + ID→名称反查）。失败时降级为空，不阻塞主列表。
async function loadAllJobs() {
  try {
    const res = await jobPage({ page: 1, size: 1000 })
    const list = res?.records || []
    allJobs.value = list
    const map = {}
    for (const j of list) map[String(j.id)] = j.jobName
    jobNameMap.value = map
  } catch (e) {
    allJobs.value = []
    jobNameMap.value = {}
  }
}

async function load() {
  loading.value = true
  try {
    const res = await jobPage({
      jobName: query.jobName || undefined,
      status: query.status || undefined,
      triggerType: query.triggerType || undefined,
      taskKey: query.taskKey || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}
function reset() {
  query.jobName = ''
  query.status = ''
  query.triggerType = ''
  query.taskKey = ''
  query.page = 1
  load()
}
function onPageChange(p) {
  query.page = p
  load()
}
function onSizeChange(s) {
  query.size = s
  query.page = 1
  load()
}

// ---------------- 自动刷新 ----------------
function startTimer() {
  stopTimer()
  refreshTimer = setInterval(() => {
    if (!dialog.visible && !logDialog.visible) load()
  }, REFRESH_MS)
}
function stopTimer() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}
watch(autoRefresh, (v) => (v ? startTimer() : stopTimer()))

// ---------------- 新建 / 编辑 ----------------
const dialog = reactive({ visible: false, mode: 'create', id: null, saving: false })
const form = reactive({
  jobName: '',
  jobGroup: 'DEFAULT',
  taskKey: '',
  triggerType: 'CRON',
  cronExpression: '*/5 * * * *',
  intervalValue: 5,
  intervalUnit: 'MINUTES',
  oneTimeAt: '',
  jobParams: '',
  remark: '',
  dependsOn: []
})

function resetForm() {
  form.jobName = ''
  form.jobGroup = 'DEFAULT'
  form.taskKey = handlers.value[0]?.key || ''
  form.triggerType = 'CRON'
  form.cronExpression = '*/5 * * * *'
  form.intervalValue = 5
  form.intervalUnit = 'MINUTES'
  form.oneTimeAt = ''
  form.jobParams = ''
  form.remark = ''
  form.dependsOn = []
  selfId.value = null
}

function openCreate() {
  resetForm()
  dialog.mode = 'create'
  dialog.id = null
  dialog.visible = true
}

/** ISO("2026-10-11T10:00:00") → 截断到秒（对齐选择器 value-format，保留 T 分隔符）。 */
function toPickerValue(iso) {
  if (!iso) return ''
  return String(iso).slice(0, 19)
}

function openEdit(row) {
  resetForm()
  form.jobName = row.jobName || ''
  form.jobGroup = row.jobGroup || 'DEFAULT'
  form.taskKey = row.taskKey || ''
  form.triggerType = row.triggerType || 'CRON'
  form.cronExpression = row.cronExpression || ''
  form.intervalValue = row.intervalValue ?? 5
  form.intervalUnit = row.intervalUnit || 'MINUTES'
  form.oneTimeAt = toPickerValue(row.oneTimeAt)
  form.jobParams = row.jobParams || ''
  form.remark = row.remark || ''
  form.dependsOn = row.dependsOn ? [...row.dependsOn] : []
  selfId.value = row.id
  dialog.mode = 'edit'
  dialog.id = row.id
  dialog.visible = true
}

function validateForm() {
  if (!form.jobName || !form.jobName.trim()) return t('job.pleaseEnterName')
  if (!form.taskKey) return t('job.pleaseSelectProcessor')
  if (form.triggerType === 'CRON') {
    if (!form.cronExpression || !form.cronExpression.trim()) return t('job.pleaseEnterCron')
  } else if (form.triggerType === 'SIMPLE') {
    if (!form.intervalValue || form.intervalValue <= 0) return t('job.intervalMustBePositive')
    if (!form.intervalUnit) return t('job.pleaseSelectIntervalUnit')
  } else if (form.triggerType === 'ONE_TIME') {
    if (!form.oneTimeAt) return t('job.pleaseSelectExecTime')
  }
  if (form.jobParams && form.jobParams.trim()) {
    try {
      JSON.parse(form.jobParams)
    } catch (e) {
      return t('job.paramsMustBeJson')
    }
  }
  return null
}

async function onSubmit() {
  const err = validateForm()
  if (err) {
    ElMessage.warning(err)
    return
  }
  const payload = {
    jobName: form.jobName.trim(),
    jobGroup: form.jobGroup || null,
    taskKey: form.taskKey,
    triggerType: form.triggerType,
    cronExpression: form.triggerType === 'CRON' ? form.cronExpression.trim() : null,
    intervalValue: form.triggerType === 'SIMPLE' ? form.intervalValue : null,
    intervalUnit: form.triggerType === 'SIMPLE' ? form.intervalUnit : null,
    oneTimeAt: form.triggerType === 'ONE_TIME' ? form.oneTimeAt : null,
    jobParams: form.jobParams && form.jobParams.trim() ? form.jobParams : null,
    remark: form.remark || null,
    dependsOn: form.dependsOn && form.dependsOn.length ? form.dependsOn : null
  }
  dialog.saving = true
  try {
    if (dialog.mode === 'create') {
      await jobCreate(payload)
      ElMessage.success(t('job.createSuccessHint'))
    } else {
      await jobUpdate(dialog.id, payload)
      ElMessage.success(t('common.saveSuccess'))
    }
    dialog.visible = false
    load()
  } catch (e) {
    // 错误消息由 http 拦截器统一提示
  } finally {
    dialog.saving = false
  }
}

// ---------------- 启动 / 暂停 / 删除 ----------------
async function onStart(row) {
  try {
    await jobStart(row.id)
    ElMessage.success(t('job.started'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onPause(row) {
  try {
    await jobPause(row.id)
    ElMessage.success(t('job.paused'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onDelete(row) {
  try {
    await jobDelete(row.id)
    ElMessage.success(t('job.deleted'))
    if (rows.value.length === 1 && query.page > 1) query.page -= 1
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

// 手动触发下游：立即驱动所有依赖本任务且已就绪的 WAITING 任务
async function onTriggerDownstream(row) {
  try {
    const count = await jobTriggerDownstream(row.id)
    if (count && count > 0) {
      ElMessage.success(t('job.triggeredDownstream', { count }))
    } else {
      ElMessage.info(t('job.noReadyDownstream'))
    }
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------------- 执行日志 ----------------
const logDialog = reactive({ visible: false, loading: false, jobName: '', rows: [] })

async function openLogs(row) {
  logDialog.visible = true
  logDialog.loading = true
  logDialog.jobName = row.jobName
  logDialog.rows = []
  try {
    const res = await jobLogs(row.id, { page: 1, size: 30 })
    logDialog.rows = res?.records || []
  } catch (e) {
    logDialog.rows = []
  } finally {
    logDialog.loading = false
  }
}

const logChartOption = computed(() => {
  // 接口按触发时间倒序返回，图表按时间正序展示
  const list = [...logDialog.rows].reverse()
  const labels = list.map((r) => fmtTime(r.fireTime).slice(6))
  const durations = list.map((r) => r.durationMs ?? 0)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 20, bottom: 28 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', name: 'ms' },
    series: [
      {
        type: 'bar',
        data: durations.map((v, i) => ({
          value: v,
          itemStyle: { color: list[i].status === 'SUCCESS' ? '#67c23a' : '#f56c6c', borderRadius: [4, 4, 0, 0] }
        })),
        barMaxWidth: 22
      }
    ]
  }
})

onMounted(async () => {
  await Promise.all([loadMeta(), loadHandlers(), loadAllJobs()])
  load()
})
onBeforeUnmount(stopTimer)
</script>

<style scoped>
/* .page-title 已统一到全局 styles/base.css */
.ml-4 { margin-left: 4px; }
.ml-8 { margin-left: 8px; }
.mt-12 { margin-top: 12px; }
.mb-12 { margin-bottom: 12px; }
.mr-6 { margin-right: 6px; }
.mr-4 { margin-right: 4px; }
.dag-line { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; margin: 2px 0; }
.dag-label { color: #a8abb2; font-size: 12px; margin-right: 6px; flex: none; }
.cell-name { font-weight: 500; color: var(--text); }
.cell-sub { color: #a8abb2; font-size: 12px; margin-top: 2px; }
.rule-text { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.err-text { color: #f56c6c; }
.text-strong { color: var(--brand); font-weight: 500; }
.hint { color: #a8abb2; font-size: 12px; line-height: 1.6; margin-top: 4px; }
.interval-row { display: flex; gap: 10px; align-items: center; width: 100%; }
.unit-select { width: 120px; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
