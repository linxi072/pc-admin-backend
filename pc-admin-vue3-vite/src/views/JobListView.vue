<template>
  <div>
    <h2 class="page-title">定时任务</h2>

    <!-- 调度引擎不可用提示 -->
    <el-alert
      v-if="metaLoaded && !meta.schedulerAvailable"
      class="mt-12"
      type="warning"
      show-icon
      :closable="false"
      title="调度引擎当前不可用：任务可正常配置，但「启动」将无法生效。请联系管理员检查 JobRunr 配置。"
    />

    <!-- 筛选工具栏：条件区（筛选控件分组）/ 操作区（查询动作与主操作分层） -->
    <el-card class="toolbar" shadow="never">
      <div class="toolbar-filters">
        <div class="filter-group">
          <span class="filter-label">任务名称</span>
          <el-input
            v-model="query.jobName"
            placeholder="任务名称"
            clearable
            style="width: 170px"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="filter-group">
          <span class="filter-label">状态</span>
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 170px" @change="onSearch">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">触发类型</span>
          <el-select v-model="query.triggerType" placeholder="全部触发类型" clearable style="width: 170px" @change="onSearch">
            <el-option label="cron 表达式" value="CRON" />
            <el-option label="固定间隔" value="SIMPLE" />
            <el-option label="单次执行" value="ONE_TIME" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">处理器</span>
          <el-select v-model="query.taskKey" placeholder="全部处理器" clearable style="width: 170px" @change="onSearch">
            <el-option v-for="h in handlers" :key="h.key" :label="h.name" :value="h.key" />
          </el-select>
        </div>
      </div>

      <div class="toolbar-actions">
        <div class="action-buttons">
          <el-button :loading="loading" @click="onSearch">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-switch v-model="autoRefresh" active-text="自动刷新" inline-prompt />
        </div>
        <div class="action-buttons">
          <el-button type="primary" @click="openCreate">新建任务</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card class="mt-12" shadow="never">
      <el-skeleton v-if="loading && !rows.length" :rows="6" animated />
      <template v-else>
        <el-table :data="rows" border stripe size="small" style="width: 100%">
          <el-table-column prop="jobName" label="任务名称" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="cell-name">{{ row.jobName }}</div>
              <div class="cell-sub">{{ row.jobGroup }}</div>
            </template>
          </el-table-column>
          <el-table-column label="处理器" min-width="130">
            <template #default="{ row }">
              <el-tooltip :content="row.taskKey" placement="top">
                <span>{{ row.taskKeyName || row.taskKey }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="触发规则" min-width="180">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" class="mr-6">{{ row.triggerTypeLabel || row.triggerType }}</el-tag>
              <span class="rule-text">{{ ruleText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="参数" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono">{{ row.jobParams || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusLabel || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上次执行" width="150">
            <template #default="{ row }">
              <div>{{ fmtTime(row.lastFireTime) }}</div>
              <div v-if="row.lastStatus" class="cell-sub">
                <el-tag :type="row.lastStatus === 'SUCCESS' ? 'success' : 'danger'" size="small" effect="plain">
                  {{ row.lastStatus === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
                <span v-if="row.lastDurationMs != null" class="ml-4">{{ row.lastDurationMs }}ms</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="下次执行" width="150">
            <template #default="{ row }">
              <span :class="{ 'text-strong': !!row.nextFireTime }">{{ fmtTime(row.nextFireTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="执行次数" width="120" align="center">
            <template #default="{ row }">
              <div>{{ row.totalRuns ?? 0 }}</div>
              <div class="cell-sub">成功 {{ row.successRuns ?? 0 }} / 失败 {{ row.failureRuns ?? 0 }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'RUNNING'"
                link
                type="success"
                size="small"
                @click="onStart(row)"
              >启动</el-button>
              <el-button
                v-else
                link
                type="warning"
                size="small"
                @click="onPause(row)"
              >暂停</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="info" size="small" @click="openLogs(row)">日志</el-button>
              <el-popconfirm title="确认删除该任务？" @confirm="onDelete(row)">
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!rows.length && !loading" description="暂无定时任务" />

        <div class="pager">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="query.page"
            :page-size="query.size"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </template>
    </el-card>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新建定时任务' : '编辑定时任务'" width="620px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.jobName" maxlength="128" placeholder="例如：每日数据同步" />
        </el-form-item>
        <el-form-item label="任务分组">
          <el-input v-model="form.jobGroup" maxlength="64" placeholder="默认 DEFAULT" />
        </el-form-item>
        <el-form-item label="处理器" required>
          <el-select v-model="form.taskKey" placeholder="请选择任务处理器" style="width: 100%">
            <el-option v-for="h in handlers" :key="h.key" :label="`${h.name}（${h.key}）`" :value="h.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发类型" required>
          <el-radio-group v-model="form.triggerType">
            <el-radio-button value="CRON">cron 表达式</el-radio-button>
            <el-radio-button value="SIMPLE">固定间隔</el-radio-button>
            <el-radio-button value="ONE_TIME">单次执行</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.triggerType === 'CRON'" label="cron" required>
          <el-input v-model="form.cronExpression" placeholder="例如：*/5 * * * *（分 时 日 月 周，支持 6 段秒精度）" />
          <div class="hint">支持 5 段（分钟精度）与 6 段（秒精度），例如「0 0 * * *」为每天 0 点。</div>
        </el-form-item>

        <el-form-item v-if="form.triggerType === 'SIMPLE'" label="执行间隔" required>
          <div class="interval-row">
            <el-input-number v-model="form.intervalValue" :min="1" :max="100000" controls-position="right" />
            <el-select v-model="form.intervalUnit" class="unit-select">
              <el-option label="秒" value="SECONDS" />
              <el-option label="分钟" value="MINUTES" />
              <el-option label="小时" value="HOURS" />
              <el-option label="天" value="DAYS" />
            </el-select>
          </div>
          <div class="hint">最小间隔 {{ meta.smallestIntervalSeconds || 5 }} 秒。</div>
        </el-form-item>

        <el-form-item v-if="form.triggerType === 'ONE_TIME'" label="执行时间" required>
          <el-date-picker
            v-model="form.oneTimeAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择执行时间（须晚于当前时间）"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="任务参数">
          <el-input
            v-model="form.jobParams"
            type="textarea"
            :rows="3"
            placeholder='可选，JSON 字符串，例如 {"biz":"sync"}'
          />
          <div class="hint">须为合法 JSON；将原样透传给处理器。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="onSubmit">
          {{ dialog.mode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 执行日志对话框 -->
    <el-dialog v-model="logDialog.visible" :title="`执行日志 · ${logDialog.jobName}`" width="760px">
      <el-skeleton v-if="logDialog.loading" :rows="5" animated />
      <template v-else>
        <el-empty v-if="!logDialog.rows.length" description="暂无执行记录" :image-size="80" />
        <template v-else>
          <el-card shadow="never" header="耗时趋势（近 30 次，按触发顺序）" class="mb-12">
            <BaseChart :option="logChartOption" height="200px" />
          </el-card>
          <el-table :data="logDialog.rows" border size="small" max-height="320">
            <el-table-column label="触发时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.fireTime) }}</template>
            </el-table-column>
            <el-table-column label="结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="90" align="right">
              <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + ' ms' : '—' }}</template>
            </el-table-column>
            <el-table-column label="traceId" width="160" show-overflow-tooltip>
              <template #default="{ row }"><span class="mono">{{ row.traceId || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="错误信息" min-width="200" show-overflow-tooltip>
              <template #default="{ row }"><span class="err-text">{{ row.errorMessage || '—' }}</span></template>
            </el-table-column>
          </el-table>
        </template>
      </template>
      <template #footer>
        <el-button @click="logDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import BaseChart from '@/components/BaseChart.vue'
import {
  jobMeta, jobHandlers, jobPage,
  jobCreate, jobUpdate, jobStart, jobPause, jobDelete, jobLogs
} from '@/api/job'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const handlers = ref([])
const metaLoaded = ref(false)
const meta = reactive({ schedulerAvailable: true, smallestIntervalSeconds: 5 })

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
  if (status === 'COMPLETED') return 'warning'
  return 'info'
}
function ruleText(row) {
  if (row.triggerType === 'CRON') return row.cronExpression || '—'
  if (row.triggerType === 'SIMPLE') return row.intervalLabel || '—'
  if (row.triggerType === 'ONE_TIME') return fmtTime(row.oneTimeAt)
  return '—'
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
  remark: ''
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
}

function openCreate() {
  resetForm()
  dialog.mode = 'create'
  dialog.id = null
  dialog.visible = true
}

/** ISO("2026-10-11T10:00:00") → "2026-10-11 10:00:00"（对齐选择器 value-format）。 */
function toPickerValue(iso) {
  if (!iso) return ''
  return iso.replace('T', ' ').slice(0, 19)
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
  dialog.mode = 'edit'
  dialog.id = row.id
  dialog.visible = true
}

function validateForm() {
  if (!form.jobName || !form.jobName.trim()) return '请填写任务名称'
  if (!form.taskKey) return '请选择任务处理器'
  if (form.triggerType === 'CRON') {
    if (!form.cronExpression || !form.cronExpression.trim()) return '请填写 cron 表达式'
  } else if (form.triggerType === 'SIMPLE') {
    if (!form.intervalValue || form.intervalValue <= 0) return '执行间隔必须为正整数'
    if (!form.intervalUnit) return '请选择执行间隔单位'
  } else if (form.triggerType === 'ONE_TIME') {
    if (!form.oneTimeAt) return '请选择单次执行时间'
  }
  if (form.jobParams && form.jobParams.trim()) {
    try {
      JSON.parse(form.jobParams)
    } catch (e) {
      return '任务参数必须是合法 JSON'
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
    remark: form.remark || null
  }
  dialog.saving = true
  try {
    if (dialog.mode === 'create') {
      await jobCreate(payload)
      ElMessage.success('创建成功（默认暂停，请手动启动）')
    } else {
      await jobUpdate(dialog.id, payload)
      ElMessage.success('保存成功')
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
    ElMessage.success('已启动，调度已即时生效')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onPause(row) {
  try {
    await jobPause(row.id)
    ElMessage.success('已暂停')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onDelete(row) {
  try {
    await jobDelete(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && query.page > 1) query.page -= 1
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
  await Promise.all([loadMeta(), loadHandlers()])
  load()
})
onBeforeUnmount(stopTimer)
</script>

<style scoped>
.page-title { margin: 0 0 12px; font-size: 20px; font-weight: 600; }
.ml-4 { margin-left: 4px; }
.ml-8 { margin-left: 8px; }
.mt-12 { margin-top: 12px; }
.mb-12 { margin-bottom: 12px; }
.mr-6 { margin-right: 6px; }
.cell-name { font-weight: 500; color: #303133; }
.cell-sub { color: #a8abb2; font-size: 12px; margin-top: 2px; }
.rule-text { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.err-text { color: #f56c6c; }
.text-strong { color: #409eff; font-weight: 500; }
.hint { color: #a8abb2; font-size: 12px; line-height: 1.6; margin-top: 4px; }
.interval-row { display: flex; gap: 10px; align-items: center; width: 100%; }
.unit-select { width: 120px; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
