<template>
  <div>
    <el-empty v-if="denied" description="无「系统监控读取」权限（system:monitor:read），请联系管理员授权后重试">
      <el-button type="primary" @click="load">重新加载</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="card-title">告警规则（规则引擎按周期评估，命中后入收件箱并可出站 Webhook）</div>
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
            <el-button :icon="VideoPlay" @click="onEvaluate">立即评估</el-button>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>

        <el-table :data="rules" v-loading="loading" border stripe empty-text="暂无告警规则">
          <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="指标" width="150">
            <template #default="{ row }">{{ metricLabel(row.metricKey) }}</template>
          </el-table-column>
          <el-table-column label="触发条件" width="160">
            <template #default="{ row }">
              {{ comparatorLabel(row.comparator) }} {{ row.threshold }}
            </template>
          </el-table-column>
          <el-table-column label="严重度" width="100">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'WARNING' ? 'warning' : 'info'" size="small">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="持续/连续" width="150">
            <template #default="{ row }">持续 ≥{{ row.forDurationSec }}s / 连续 {{ row.consecutiveCount }} 次</template>
          </el-table-column>
          <el-table-column prop="channelType" label="渠道" width="100">
            <template #default="{ row }">
              <el-tag :type="row.channelType === 'WEBHOOK' ? 'success' : 'info'" size="small">{{ row.channelType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                @change="(val) => onToggle(row, val)"
                :disabled="saving"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="onDelete(row)">删除</el-button>
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
        :title="editing ? '编辑告警规则' : '新增告警规则'"
        width="560px"
        @closed="resetForm"
      >
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" placeholder="如：CPU 持续高负载" maxlength="128" />
          </el-form-item>
          <el-form-item label="监控指标" prop="metricKey">
            <el-select v-model="form.metricKey" placeholder="选择指标" style="width: 100%">
              <el-option v-for="(label, key) in METRIC_LABELS" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="比较符" prop="comparator">
            <el-select v-model="form.comparator" style="width: 100%">
              <el-option v-for="opt in COMPARATOR_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="阈值" prop="threshold">
            <el-input-number v-model="form.threshold" :precision="2" :step="0.5" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="严重度" prop="severity">
            <el-select v-model="form.severity" style="width: 100%">
              <el-option v-for="s in SEVERITY_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="持续时长(s)" prop="forDurationSec">
            <el-input-number v-model="form.forDurationSec" :min="0" :step="10" style="width: 100%" />
            <div class="field-hint">指标连续超阈达到该时长才触发，0 表示立即。</div>
          </el-form-item>
          <el-form-item label="连续次数" prop="consecutiveCount">
            <el-input-number v-model="form.consecutiveCount" :min="1" :step="1" style="width: 100%" />
            <div class="field-hint">连续 N 个评估周期均超阈才触发，避免抖动误报。</div>
          </el-form-item>
          <el-form-item label="渠道类型" prop="channelType">
            <el-select v-model="form.channelType" style="width: 100%">
              <el-option v-for="c in CHANNEL_OPTIONS" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.channelType === 'WEBHOOK'" label="Webhook 地址" prop="channelConfig">
            <el-input v-model="form.channelConfig" placeholder="https://hooks.example.com/alert" />
          </el-form-item>
          <el-form-item label="启用" prop="enabled">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
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
  CPU_USAGE: 'CPU 使用率(%)',
  MEMORY_USAGE: '内存使用率(%)',
  DISK_USAGE: '磁盘使用率(%)',
  THREAD_COUNT: '线程数(个)',
  NPLUS1_COUNT: 'N+1 疑似数(个)',
  ONLINE_USERS: '在线用户数(人)',
  ERROR_LOG_COUNT: '近10分钟错误日志数(条)'
}
const COMPARATOR_OPTIONS = [
  { value: 'GT', label: '> 大于' },
  { value: 'GE', label: '≥ 大于等于' },
  { value: 'LT', label: '< 小于' },
  { value: 'LE', label: '≤ 小于等于' },
  { value: 'EQ', label: '= 等于' },
  { value: 'NE', label: '≠ 不等于' }
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
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  metricKey: [{ required: true, message: '请选择监控指标', trigger: 'change' }],
  comparator: [{ required: true, message: '请选择比较符', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重度', trigger: 'change' }],
  channelType: [{ required: true, message: '请选择渠道类型', trigger: 'change' }],
  channelConfig: [
    {
      validator: (rule, value, cb) => {
        if (form.channelType === 'WEBHOOK' && !value) {
          cb(new Error('WEBHOOK 渠道必须填写回调地址'))
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
      ElMessage.success('已更新规则')
    } else {
      await createAlertRule(payload)
      ElMessage.success('已新增规则')
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
    ElMessage.success(val ? '已启用' : '已停用')
  } catch (e) {
    load()
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAlertRule(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onEvaluate() {
  try {
    const fired = await evaluateAlertRules()
    ElMessage.success(`评估完成，本次新触发 ${fired ?? 0} 条告警（已入收件箱）`)
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
