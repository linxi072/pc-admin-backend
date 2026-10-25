<template>
  <div>
    <h2 class="page-title">脱敏策略中心</h2>

    <el-card class="page-card" shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">字段键</span>
            <el-input
              v-model="query.fieldKey"
              placeholder="如 audit.ip"
              clearable
              style="width: 200px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">脱敏类型</span>
            <el-select v-model="query.sensitiveType" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
            <el-button type="success" :icon="Plus" @click="openCreate">新建策略</el-button>
            <el-button :icon="Refresh" @click="onRefreshCache">刷新缓存</el-button>
          </div>
        </div>
      </div>

      <!-- 策略列表 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="strategyCode" label="编码" width="180" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="strategyName" label="名称" width="160" show-overflow-tooltip />
        <el-table-column prop="fieldKey" label="字段键" width="180" show-overflow-tooltip />
        <el-table-column label="脱敏类型" width="130">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.sensitiveType).type" size="small">{{ typeTag(row.sensitiveType).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="作用范围" width="120">
          <template #default="{ row }">
            <el-tag :type="scopeTag(row.applyScope).type" size="small" effect="plain">
              {{ scopeTag(row.applyScope).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exemptRoles" label="豁免角色" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.exemptRoles">{{ row.exemptRoles }}</span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" sortable="custom" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="row.__toggling"
              @change="(val) => onToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该策略？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
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
        策略按 <b>字段键</b>（如 <code>audit.ip</code>）在序列化/导出前由 MaskStrategyResolver 统一分派：
        未命中时按调用方声明的回落类型脱敏（绝不泄漏明文）。作用范围 <code>ROLE_EXEMPT</code> 表示命中豁免角色看明文；
        <code>NONE</code> 为显式放行（用于已脱敏存储字段，避免二次脱敏）。同字段键多条策略时优先级数值高者胜。
      </div>
    </el-card>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId !== null ? '编辑策略' : '新建策略'"
      width="640px"
      @closed="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="92px">
        <el-form-item label="策略编码" prop="strategyCode">
          <el-input
            v-model="form.strategyCode"
            placeholder="如 AUDIT_IP_MASK（租户内唯一）"
            :disabled="editingId !== null"
          />
        </el-form-item>
        <el-form-item label="策略名称" prop="strategyName">
          <el-input v-model="form.strategyName" placeholder="如 审计日志 IP 脱敏" />
        </el-form-item>
        <el-form-item label="字段键" prop="fieldKey">
          <el-input v-model="form.fieldKey" placeholder="如 audit.ip（视图/导出任务的逻辑字段键）" />
        </el-form-item>
        <el-form-item label="脱敏类型" prop="sensitiveType">
          <el-select v-model="form.sensitiveType" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用范围" prop="applyScope">
          <el-select v-model="form.applyScope" style="width: 100%">
            <el-option
              v-for="s in scopeOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.applyScope === 'ROLE_EXEMPT'" label="豁免角色" prop="exemptRoles">
          <el-input
            v-model="form.exemptRoles"
            placeholder="role_code，多个用逗号/分号分隔；留空表示无人豁免"
          />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="form.priority" :min="0" :max="9999" style="width: 160px" />
          <span class="field-hint">同字段键多条策略时，数值高者生效</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Edit, Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import {
  pageMaskStrategies,
  createMaskStrategy,
  updateMaskStrategy,
  deleteMaskStrategy,
  toggleMaskStrategy,
  refreshMaskStrategyCache
} from '@/api/maskStrategy'

// 与后端 SensitiveType 枚举一致（NONE 为显式放行标志，不在可选之列）
const typeOptions = [
  { value: 'MOBILE', label: '手机号' },
  { value: 'ID_CARD', label: '身份证号' },
  { value: 'BANK_CARD', label: '银行卡号' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'NAME', label: '姓名' },
  { value: 'ADDRESS', label: '联系地址' },
  { value: 'IP', label: 'IP 地址' },
  { value: 'CREDENTIAL_TEXT', label: '凭证/敏感文本' }
]
const typeTag = (t) => {
  const map = {
    MOBILE: { type: 'danger', label: '手机号' },
    ID_CARD: { type: 'danger', label: '身份证号' },
    BANK_CARD: { type: 'warning', label: '银行卡号' },
    EMAIL: { type: 'primary', label: '邮箱' },
    NAME: { type: 'success', label: '姓名' },
    ADDRESS: { type: 'info', label: '联系地址' },
    IP: { type: 'primary', label: 'IP 地址' },
    CREDENTIAL_TEXT: { type: 'warning', label: '凭证/敏感文本' },
    NONE: { type: 'info', label: '不脱敏' }
  }
  return map[t] || { type: 'info', label: t || '--' }
}

// 与后端 MaskStrategyRules 作用范围白名单一致
const scopeOptions = [
  { value: 'ALL', label: 'ALL（一律脱敏）' },
  { value: 'ROLE_EXEMPT', label: 'ROLE_EXEMPT（角色豁免）' },
  { value: 'NONE', label: 'NONE（永不脱敏）' }
]
const scopeTag = (s) => {
  const map = {
    ALL: { type: 'danger', label: '一律脱敏' },
    ROLE_EXEMPT: { type: 'warning', label: '角色豁免' },
    NONE: { type: 'info', label: '显式放行' }
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
    ElMessage.success(val ? '已启用' : '已停用')
  } catch (e) {
    row.enabled = !val // 失败时回滚开关
  } finally {
    row.__toggling = false
  }
}

async function onDelete(row) {
  try {
    await deleteMaskStrategy(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onRefreshCache() {
  try {
    const evicted = await refreshMaskStrategyCache()
    ElMessage.success(`缓存已刷新，失效 ${evicted ?? 0} 条`)
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
  strategyCode: [{ required: true, message: '请输入策略编码', trigger: 'blur' }],
  strategyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  fieldKey: [{ required: true, message: '请输入字段键', trigger: 'blur' }],
  sensitiveType: [{ required: true, message: '请选择脱敏类型', trigger: 'change' }],
  applyScope: [{ required: true, message: '请选择作用范围', trigger: 'change' }]
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
  formRef.value?.clearValidate()
}

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
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form }
    // 非角色豁免作用范围下豁免角色无意义，避免脏数据
    if (payload.applyScope !== 'ROLE_EXEMPT') payload.exemptRoles = ''
    if (editingId.value != null) {
      await updateMaskStrategy(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createMaskStrategy(payload)
      ElMessage.success('创建成功')
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
</style>
