<template>
  <div>
    <el-empty v-if="denied" :description="t('monitor.noMonPerm')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="card-title">{{ t('monitor.alertRuleDesc') }}</div>
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('monitor.addRule') }}</el-button>
            <el-button :icon="VideoPlay" @click="onEvaluate">{{ t('monitor.evaluateNow') }}</el-button>
            <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
          </div>
        </div>

        <el-table :data="rules" v-loading="loading" border stripe :empty-text="t('monitor.noAlertRule')">
          <el-table-column prop="ruleName" :label="t('monitor.ruleName')" min-width="160" show-overflow-tooltip />
          <el-table-column :label="t('monitor.metric')" width="150">
            <template #default="{ row }">{{ metricLabel(row.metricKey) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.triggerCondition')" width="160">
            <template #default="{ row }">
              {{ comparatorLabel(row.comparator) }} {{ row.threshold }}
            </template>
          </el-table-column>
          <el-table-column :label="t('monitor.severity')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'WARNING' ? 'warning' : 'info'" size="small">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('monitor.durationContinuous')" width="150">
            <template #default="{ row }">持续 ≥{{ row.forDurationSec }}s / 连续 {{ row.consecutiveCount }} 次</template>
          </el-table-column>
          <el-table-column prop="channelType" :label="t('common.channel')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.channelType === 'WEBHOOK' ? 'success' : 'info'" size="small">{{ row.channelType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.enabled')" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                @change="(val) => onToggle(row, val)"
                :disabled="saving"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.operation')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="danger" :icon="Delete" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="hint">
          规则默认不自动评估（需后端 <code>monitoring.alert.rule.enabled=true</code> 开启周期评估）。
          亦可通过「立即评估」按钮手动触发一次。命中规则后合成告警入「告警收件箱」Tab，WEBHOOK 渠道额外出站转发。
        </div>
      </el-card>

      <el-dialog
        v-model="dialogVisible"
        :title="editing ? t('monitor.editRuleTitle') : t('monitor.addRuleTitle')"
        width="560px"
        @closed="resetForm"
      >
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-form-item :label="t('monitor.ruleName')" prop="ruleName">
            <el-input v-model="form.ruleName" :placeholder="t('monitor.egCpu')" maxlength="128" />
          </el-form-item>
          <el-form-item :label="t('monitor.monitorMetric')" prop="metricKey">
            <el-select v-model="form.metricKey" :placeholder="t('monitor.selectMetric')" style="width: 100%">
              <el-option v-for="(label, key) in METRIC_LABELS" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('monitor.comparator')" prop="comparator">
            <el-select v-model="form.comparator" style="width: 100%">
              <el-option v-for="opt in COMPARATOR_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('monitor.threshold')" prop="threshold">
            <el-input-number v-model="form.threshold" :precision="2" :step="0.5" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="t('monitor.severity')" prop="severity">
            <el-select v-model="form.severity" style="width: 100%">
              <el-option v-for="s in SEVERITY_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('monitor.durationSec')" prop="forDurationSec">
            <el-input-number v-model="form.forDurationSec" :min="0" :step="10" style="width: 100%" />
            <div class="field-hint">{{ t('monitor.metricContinuousHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.consecutiveCount')" prop="consecutiveCount">
            <el-input-number v-model="form.consecutiveCount" :min="1" :step="1" style="width: 100%" />
            <div class="field-hint">{{ t('monitor.continuousNHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.channelType')" prop="channelType">
            <el-select v-model="form.channelType" style="width: 100%">
              <el-option v-for="c in CHANNEL_OPTIONS" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.channelType === 'WEBHOOK'" :label="t('monitor.webhookUrl')" prop="channelConfig">
            <el-input v-model="form.channelConfig" placeholder="https://hooks.example.com/alert" />
          </el-form-item>
          <el-form-item :label="t('common.enabled')" prop="enabled">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item :label="t('job.remark')" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submit">{{ t('common.save') }}</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh, VideoPlay } from '@element-plus/icons-vue'
import {
  listAlertRules,
  createAlertRule,
  updateAlertRule,
  deleteAlertRule,
  toggleAlertRule,
  evaluateAlertRules
} from '@/api/alertRule'
import { isForbidden } from './format'

const rules = ref([])
const loading = ref(false)
const saving = ref(false)
const denied = ref(false)

const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref(null)

const METRIC_LABELS = {
  CPU_USAGE: t('monitor.cpuUsage'),
  MEMORY_USAGE: t('monitor.memUsage'),
  DISK_USAGE: t('monitor.diskUsage'),
  THREAD_COUNT: t('monitor.threadCount'),
  NPLUS1_COUNT: t('monitor.n1SuspectCount'),
  ONLINE_USERS: t('monitor.onlineUsers'),
  ERROR_LOG_COUNT: t('monitor.errLogs10m')
}
const COMPARATOR_OPTIONS = [
  { value: 'GT', label: t('monitor.gt') },
  { value: 'GE', label: t('monitor.geq') },
  { value: 'LT', label: t('monitor.lt') },
  { value: 'LE', label: t('monitor.leq') },
  { value: 'EQ', label: t('monitor.eq') },
  { value: 'NE', label: t('monitor.neq') }
]
const SEVERITY_OPTIONS = ['CRITICAL', 'WARNING', 'INFO']
const CHANNEL_OPTIONS = ['INBOX', 'WEBHOOK']

const form = reactive({
  id: null,
  ruleName: '',
  metricKey: '',
  comparator: 'GT',
  threshold: 80,
  severity: 'WARNING',
  forDurationSec: 0,
  consecutiveCount: 1,
  channelType: 'INBOX',
  channelConfig: '',
  enabled: false,
  remark: ''
})

const rulesValidator = {
  ruleName: [{ required: true, message: t('monitor.plsRuleName'), trigger: 'blur' }],
  metricKey: [{ required: true, message: t('monitor.plsMetric'), trigger: 'change' }],
  comparator: [{ required: true, message: t('monitor.plsComparator'), trigger: 'change' }],
  threshold: [{ required: true, message: t('monitor.plsThreshold'), trigger: 'blur' }],
  severity: [{ required: true, message: t('monitor.plsSeverity'), trigger: 'change' }],
  channelType: [{ required: true, message: t('monitor.plsChannelType'), trigger: 'change' }],
  channelConfig: [
    {
      validator: (rule, value, cb) => {
        if (form.channelType === 'WEBHOOK' && !value) {
          cb(new Error(t('monitor.webhookCallbackRequired')))
        } else {
          cb()
        }
      },
      trigger: 'blur'
    }
  ]
}

function metricLabel(key) {
  return METRIC_LABELS[key] || key || '--'
}
function comparatorLabel(c) {
  return COMPARATOR_OPTIONS.find((o) => o.value === c)?.label?.charAt(0) || c || ''
}

async function load() {
  loading.value = true
  try {
    const data = await listAlertRules()
    rules.value = data || []
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    rules.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    ruleName: row.ruleName,
    metricKey: row.metricKey,
    comparator: row.comparator,
    threshold: row.threshold,
    severity: row.severity,
    forDurationSec: row.forDurationSec,
    consecutiveCount: row.consecutiveCount,
    channelType: row.channelType,
    channelConfig: row.channelConfig,
    enabled: row.enabled,
    remark: row.remark
  })
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: null,
    ruleName: '',
    metricKey: '',
    comparator: 'GT',
    threshold: 80,
    severity: 'WARNING',
    forDurationSec: 0,
    consecutiveCount: 1,
    channelType: 'INBOX',
    channelConfig: '',
    enabled: false,
    remark: ''
  })
  formRef.value?.clearValidate()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      ruleName: form.ruleName,
      metricKey: form.metricKey,
      comparator: form.comparator,
      threshold: form.threshold,
      severity: form.severity,
      forDurationSec: form.forDurationSec,
      consecutiveCount: form.consecutiveCount,
      channelType: form.channelType,
      channelConfig: form.channelType === 'WEBHOOK' ? form.channelConfig : '',
      enabled: form.enabled,
      remark: form.remark
    }
    if (editing.value) {
      await updateAlertRule(form.id, payload)
      ElMessage.success(t('monitor.ruleUpdated'))
    } else {
      await createAlertRule(payload)
      ElMessage.success(t('monitor.ruleAdded'))
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    // 拦截器已提示；校验 400 也由后端返回 message
  } finally {
    saving.value = false
  }
}

async function onToggle(row, val) {
  try {
    await toggleAlertRule(row.id, val)
    row.enabled = val
    ElMessage.success(val ? t('monitor.enabled') : t('monitor.disabled'))
  } catch (e) {
    load()
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(t('monitor.confirmDeleteRule', { ruleName: row.ruleName }), t('monitor.deleteConfirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAlertRule(row.id)
    ElMessage.success(t('common.deleted'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onEvaluate() {
  try {
    const fired = await evaluateAlertRules()
    ElMessage.success(t('monitor.evalDone', { fired: fired ?? 0 }))
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.field-hint {
  color: #a8abb2;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 2px;
}
.hint {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.hint code {
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
}
</style>
