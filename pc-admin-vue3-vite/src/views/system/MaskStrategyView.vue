<template>
  <div>
    <h2 class="page-title">{{ t('system.maskingPolicyCenter') }}</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('formDesigner.key') }}</span>
            <el-input
              v-model="query.fieldKey"
              :placeholder="t('system.eGAuditIp')"
              clearable
              style="width: 200px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.maskingType') }}</span>
            <el-select v-model="query.sensitiveType" :placeholder="t('common.all')" clearable style="width: 140px">
              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.status') }}</span>
            <el-select v-model="query.enabled" :placeholder="t('common.all')" clearable style="width: 120px">
              <el-option :label="t('common.enabled')" :value="true" />
              <el-option :label="t('system.disable')" :value="false" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            <el-button type="success" :icon="Plus" @click="openCreate">{{ t('system.newPolicy') }}</el-button>
            <el-button :icon="Refresh" @click="onRefreshCache">{{ t('system.refreshCache') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 策略列表 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="strategyCode" :label="t('system.code')" width="180" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="strategyName" :label="t('common.name')" width="160" show-overflow-tooltip />
        <el-table-column prop="fieldKey" :label="t('formDesigner.key')" width="180" show-overflow-tooltip />
        <el-table-column :label="t('system.maskingType')" width="130">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.sensitiveType).type" size="small">{{ typeTag(row.sensitiveType).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.scope')" width="120">
          <template #default="{ row }">
            <el-tag :type="scopeTag(row.applyScope).type" size="small" effect="plain">
              {{ scopeTag(row.applyScope).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exemptRoles" :label="t('system.exemptRoles')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.exemptRoles">{{ row.exemptRoles }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('system.priority')" width="90" sortable="custom" />
        <el-table-column :label="t('common.enabled')" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="row.__toggling"
              @change="(val) => onToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('system.deleteThisPolicy')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
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
      <div class="hint">
        策略按 <b>{{ t('formDesigner.key') }}</b>{{ t('system.eG') }}<code>audit.ip</code>）在序列化/导出前由 MaskStrategyResolver 统一分派：
        未命中时按调用方声明的回落类型脱敏（绝不泄漏明文）。作用范围 <code>ROLE_EXEMPT</code> 表示命中豁免角色看明文；
        <code>NONE</code> 为显式放行（用于已脱敏存储字段，避免二次脱敏）。同字段键多条策略时优先级数值高者胜。
      </div>
    </el-card>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId !== null ? t('system.editPolicy') : t('system.newPolicy')"
      width="640px"
      @closed="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="92px">
        <el-form-item :label="t('system.policyCode')" prop="strategyCode">
          <el-input
            v-model="form.strategyCode"
            :placeholder="t('system.eGAuditIpMaskUniqueWithinTenant')"
            :disabled="editingId !== null"
          />
        </el-form-item>
        <el-form-item :label="t('system.policyName')" prop="strategyName">
          <el-input v-model="form.strategyName" :placeholder="t('system.eGAuditLogIpMasking')" />
        </el-form-item>
        <el-form-item :label="t('formDesigner.key')" prop="fieldKey">
          <el-input v-model="form.fieldKey" :placeholder="t('system.eGAuditIpLogicalFieldKeyForViewsExportJobs')" />
        </el-form-item>
        <el-form-item :label="t('system.maskingType')" prop="sensitiveType">
          <el-select v-model="form.sensitiveType" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.effectPreview')" v-if="form.sensitiveType">
          <div class="preview-box">
            <el-input
              v-model="previewSample"
              :placeholder="t('system.enterASampleValueToPreviewMaskingInRealTime')"
              style="width: 100%"
              @keyup.enter="runPreview"
            >
              <template #prepend>{{ t('system.sampleValue') }}</template>
              <template #append>
                <el-button :loading="previewing" :icon="View" @click="runPreview">{{ t('system.preview') }}</el-button>
              </template>
            </el-input>
            <div v-if="previewSample !== '' || previewResult !== '' || previewing" class="preview-result">
              <code class="preview-origin">{{ previewSample === '' ? t('system.empty') : previewSample }}</code>
              <el-icon class="preview-arrow"><Right /></el-icon>
              <code class="preview-masked">{{ previewResult === '' ? t('system.noResult') : previewResult }}</code>
            </div>
            <div class="preview-tip">{{ t('system.previewReusesTheBackendSensitivemaskerSingleSourceConsistentWithRuntimeMasking') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="t('system.scope')" prop="applyScope">
          <el-select v-model="form.applyScope" style="width: 100%">
            <el-option
              v-for="s in scopeOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.applyScope === 'ROLE_EXEMPT'" :label="t('system.exemptRoles')" prop="exemptRoles">
          <el-input
            v-model="form.exemptRoles"
            :placeholder="t('system.roleCodeCommaSemicolonSeparatedBlankMeansNoOneExempt')"
          />
        </el-form-item>
        <el-form-item :label="t('system.priority')" prop="priority">
          <el-input-number v-model="form.priority" :min="0" :max="9999" style="width: 160px" />
          <span class="field-hint">{{ t('system.whenMultiplePoliciesShareAFieldKeyTheHigherValueWins') }}</span>
        </el-form-item>
        <el-form-item :label="t('common.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item :label="t('job.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Edit, Search, RefreshLeft, Refresh, View, Right } from '@element-plus/icons-vue'
import {
  pageMaskStrategies,
  createMaskStrategy,
  updateMaskStrategy,
  deleteMaskStrategy,
  toggleMaskStrategy,
  refreshMaskStrategyCache,
  previewMask
} from '@/api/maskStrategy'
import { validateOrWarn } from '@/utils/validate'

// 与后端 SensitiveType 枚举一致（NONE 为显式放行标志，不在可选之列）
const typeOptions = [
  { value: 'MOBILE', label: t('common.phone') },
  { value: 'ID_CARD', label: t('system.idNumber') },
  { value: 'BANK_CARD', label: t('system.bankCardNumber') },
  { value: 'EMAIL', label: t('common.email') },
  { value: 'NAME', label: t('system.name') },
  { value: 'ADDRESS', label: t('system.contactAddress') },
  { value: 'IP', label: t('common.ipAddress') },
  { value: 'CREDENTIAL_TEXT', label: t('system.credentialSensitiveText') }
]
const typeTag = (t) => {
  const map = {
    MOBILE: { type: 'danger', label: t('common.phone') },
    ID_CARD: { type: 'danger', label: t('system.idNumber') },
    BANK_CARD: { type: 'warning', label: t('system.bankCardNumber') },
    EMAIL: { type: 'primary', label: t('common.email') },
    NAME: { type: 'success', label: t('system.name') },
    ADDRESS: { type: 'info', label: t('system.contactAddress') },
    IP: { type: 'primary', label: t('common.ipAddress') },
    CREDENTIAL_TEXT: { type: 'warning', label: t('system.credentialSensitiveText') },
    NONE: { type: 'info', label: t('system.noMasking') }
  }
  return map[t] || { type: 'info', label: t || '--' }
}

// 与后端 MaskStrategyRules 作用范围白名单一致
const scopeOptions = [
  { value: 'ALL', label: t('system.allMaskEverything') },
  { value: 'ROLE_EXEMPT', label: t('system.roleExemptRoleExempt') },
  { value: 'NONE', label: t('system.noneNeverMask') }
]
const scopeTag = (s) => {
  const map = {
    ALL: { type: 'danger', label: t('system.maskAll') },
    ROLE_EXEMPT: { type: 'warning', label: t('system.roleExempt') },
    NONE: { type: 'info', label: t('system.explicitAllow') }
  }
  return map[s] || { type: 'info', label: s || '--' }
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  fieldKey: '',
  sensitiveType: '',
  enabled: '',
  sortField: '',
  sortDirection: ''
})

/** 筛选条件映射为查询参数（空值统一 undefined，不传空串）。 */
function buildParams() {
  return {
    page: query.page,
    size: query.size,
    fieldKey: query.fieldKey || undefined,
    sensitiveType: query.sensitiveType || undefined,
    enabled: query.enabled === '' ? undefined : query.enabled,
    sortField: query.sortField || undefined,
    sortDirection: query.sortDirection || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const page = await pageMaskStrategies(buildParams())
    rows.value = (page.records || []).map((r) => ({ ...r, __toggling: false }))
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
  query.fieldKey = ''
  query.sensitiveType = ''
  query.enabled = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  load()
}

/** 服务端排序白名单：strategyCode/priority/enabled/createdAt。 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

async function onToggle(row, val) {
  row.__toggling = true
  try {
    await toggleMaskStrategy(row.id, val)
    ElMessage.success(val ? t('monitor.enabled') : t('monitor.disabled'))
  } catch (e) {
    row.enabled = !val // 失败时回滚开关
  } finally {
    row.__toggling = false
  }
}

async function onDelete(row) {
  try {
    await deleteMaskStrategy(row.id)
    ElMessage.success(t('common.deleted'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onRefreshCache() {
  try {
    const evicted = await refreshMaskStrategyCache()
    ElMessage.success(t('system.cacheRefreshed', { n: evicted ?? 0 }))
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

// ---- 新建 / 编辑 ----
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  strategyCode: '',
  strategyName: '',
  fieldKey: '',
  sensitiveType: 'MOBILE',
  applyScope: 'ROLE_EXEMPT',
  exemptRoles: '',
  priority: 0,
  enabled: true,
  remark: ''
})
const rules = {
  strategyCode: [{ required: true, message: t('system.enterPolicyCode'), trigger: 'blur' }],
  strategyName: [{ required: true, message: t('system.enterPolicyName'), trigger: 'blur' }],
  fieldKey: [{ required: true, message: t('system.enterFieldKey'), trigger: 'blur' }],
  sensitiveType: [{ required: true, message: t('system.selectMaskingType'), trigger: 'change' }],
  applyScope: [{ required: true, message: t('system.selectScope'), trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, {
    strategyCode: '',
    strategyName: '',
    fieldKey: '',
    sensitiveType: 'MOBILE',
    applyScope: 'ROLE_EXEMPT',
    exemptRoles: '',
    priority: 0,
    enabled: true,
    remark: ''
  })
  previewSample.value = ''
  previewResult.value = ''
  formRef.value?.clearValidate()
}

// ---- 脱敏效果预览（#752，复用后端 preview 端点） ----
const previewSample = ref('')
const previewResult = ref('')
const previewing = ref(false)

async function runPreview() {
  if (!form.sensitiveType) return
  previewing.value = true
  try {
    previewResult.value = await previewMask(form.sensitiveType, previewSample.value)
  } catch (e) {
    previewResult.value = ''
  } finally {
    previewing.value = false
  }
}

// 类型或示例值变化时自动预览（轻量防抖，避免每次按键都打接口）
let previewTimer = null
watch([() => form.sensitiveType, previewSample], () => {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(runPreview, 300)
})

function openCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    strategyCode: row.strategyCode,
    strategyName: row.strategyName,
    fieldKey: row.fieldKey,
    sensitiveType: row.sensitiveType,
    applyScope: row.applyScope,
    exemptRoles: row.exemptRoles || '',
    priority: row.priority ?? 0,
    enabled: !!row.enabled,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!(await validateOrWarn(formRef.value))) return
  saving.value = true
  try {
    const payload = { ...form }
    // 非角色豁免作用范围下豁免角色无意义，避免脏数据
    if (payload.applyScope !== 'ROLE_EXEMPT') payload.exemptRoles = ''
    if (editingId.value != null) {
      await updateMaskStrategy(editingId.value, payload)
      ElMessage.success(t('system.updated'))
    } else {
      await createMaskStrategy(payload)
      ElMessage.success(t('system.created'))
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: #c0c4cc; }
.field-hint {
  font-size: 12px;
  color: #a8abb2;
  margin-left: 12px;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
.hint code {
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
  color: #409eff;
}
.preview-box {
  width: 100%;
}
.preview-result {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  padding: 8px 10px;
  background: #f4f4f5;
  border-radius: 6px;
  font-size: 13px;
}
.preview-origin {
  color: #909399;
}
.preview-arrow {
  color: #c0c4cc;
}
.preview-masked {
  color: #f56c6c;
  font-weight: 600;
  word-break: break-all;
}
.preview-tip {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 6px;
}
</style>
