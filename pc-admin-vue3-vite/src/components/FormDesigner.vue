<template>
  <div class="fd">
    <!-- 字段类型面板（点击添加） -->
    <div class="fd-pane fd-palette">
      <div class="fd-pane-title">{{ '字段类型' }}</div>
      <el-button
        v-for="t in palette"
        :key="t.type"
        class="fd-palette-item"
        size="small"
        :icon="Plus"
        @click="addField(t.type)"
      >
        {{ tx(t.labelKey) }}
      </el-button>
      <div class="fd-palette-hint">{{ '点击添加字段到表单' }}</div>
    </div>

    <!-- 字段列表（选中编辑 / 上下移 / 删除） -->
    <div class="fd-pane fd-list">
      <div class="fd-pane-title">
        {{ '字段列表' }}（{{ fields.length }}）
      </div>
      <div v-if="!fields.length" class="fd-empty">{{ '从左侧点击添加字段' }}</div>
      <div
        v-for="(f, i) in fields"
        :key="i"
        class="fd-field"
        :class="{ active: selected === i }"
        @click="selected = i"
      >
        <span class="fd-field-idx">{{ i + 1 }}</span>
        <span class="fd-field-main">
          <span class="fd-field-label">{{ f.label || '（未命名）' }}</span>
          <span class="fd-field-meta">
            <el-tag size="small" effect="plain">{{ typeLabel(f.type) }}</el-tag>
            <span class="fd-field-key">{{ f.key }}</span>
            <el-tag v-if="f.required" size="small" type="danger" effect="plain">*</el-tag>
          </span>
        </span>
        <span class="fd-field-ops">
          <el-button link size="small" :disabled="i === 0" @click.stop="move(i, -1)">{{ '上移' }}</el-button>
          <el-button link size="small" :disabled="i === fields.length - 1" @click.stop="move(i, 1)">{{ '下移' }}</el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click.stop="remove(i)">{{ '删除' }}</el-button>
        </span>
      </div>
    </div>

    <!-- 选中字段属性面板 -->
    <div class="fd-pane fd-prop">
      <div class="fd-pane-title">{{ '字段属性' }}</div>
      <el-form v-if="current" label-width="78px" size="small" @submit.prevent>
        <el-form-item :label="'字段键'">
          <el-input v-model="current.key" @input="onKeyInput" />
          <div v-if="keyError" class="fd-field-error">{{ keyError }}</div>
        </el-form-item>
        <el-form-item :label="'标签'">
          <el-input v-model="current.label" />
        </el-form-item>
        <el-form-item :label="'类型'">
          <el-select v-model="current.type" style="width: 100%" @change="onTypeChange">
            <el-option v-for="t in palette" :key="t.type" :label="t.label" :value="t.type" />
          </el-select>
        </el-form-item>
        <el-form-item :label="'必填'">
          <el-switch v-model="current.required" />
        </el-form-item>
        <el-form-item v-if="hasPlaceholder(current.type)" :label="'占位提示'">
          <el-input v-model="current.placeholder" />
        </el-form-item>
        <template v-if="current.type === 'INPUT' || current.type === 'TEXTAREA'">
          <el-form-item :label="'最大长度'">
            <el-input-number v-model="current.maxLength" :min="0" :max="4000" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="'最小长度'">
            <el-input-number v-model="current.minLength" :min="0" :max="4000" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="'{label}格式不正确'">
            <el-input v-model="current.pattern" :placeholder="'^(?=.*[A-Za-z])(?=.*\\d).+$'" />
          </el-form-item>
        </template>
        <el-form-item v-if="current.type === 'TEXTAREA'" :label="'行数'">
          <el-input-number v-model="current.rows" :min="1" :max="20" controls-position="right" style="width: 100%" />
        </el-form-item>
        <template v-if="current.type === 'NUMBER'">
          <el-form-item :label="'{label}不能小于 {min}'">
            <el-input-number v-model="current.min" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="'{label}不能大于 {max}'">
            <el-input-number v-model="current.max" controls-position="right" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item v-if="current.type === 'SELECT' || current.type === 'RADIO'" :label="'选项'">
          <div v-for="(o, oi) in current.options" :key="oi" class="fd-opt-row">
            <el-input v-model="o.label" :placeholder="'标签'" size="small" />
            <el-input v-model="o.value" :placeholder="'值'" size="small" />
            <el-button link type="danger" :icon="Delete" @click="current.options.splice(oi, 1)" />
          </div>
          <el-button size="small" :icon="Plus" @click="current.options.push({ label: '', value: '' })">
            {{ '添加选项' }}
          </el-button>
        </el-form-item>
        <el-form-item :label="'显示条件'">
          <el-switch :model-value="!!current.visibleWhen" @change="toggleVisibleWhen" />
        </el-form-item>
        <template v-if="current.visibleWhen">
          <el-form-item :label="'依赖字段'">
            <el-select v-model="current.visibleWhen.field" style="width: 100%">
              <el-option v-for="o in otherFields" :key="o.key" :label="o.label" :value="o.key" />
            </el-select>
          </el-form-item>
          <el-form-item :label="'运算符'">
            <el-select v-model="current.visibleWhen.op" style="width: 100%">
              <el-option v-for="(lbl, k) in opOptions" :key="k" :label="tx(lbl)" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!['truthy', 'falsy'].includes(current.visibleWhen.op)" :label="'比较值'">
            <el-input v-model="current.visibleWhen.value" />
          </el-form-item>
        </template>
        <el-form-item :label="'栅格列宽'">
          <el-select v-model="current.colSpan" style="width: 100%">
            <el-option :label="'全宽'" :value="2" />
            <el-option :label="'半宽'" :value="1" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-empty v-else :description="'请选择左侧字段进行配置'" :image-size="60" />
    </div>

    <!-- 实时预览（复用 DynamicForm 渲染器，与运行时口径一致） -->
    <div class="fd-pane fd-preview">
      <div class="fd-pane-title">{{ '实时预览' }}</div>
      <div class="fd-preview-body">
        <DynamicForm v-if="schemaObject && schemaObject.fields.length" :schema="schemaObject" v-model="previewModel" />
        <el-empty v-else :description="'暂无字段可预览'" :image-size="60" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import DynamicForm from '@/components/DynamicForm.vue'
import { tx } from '@/locales/zh-cn'

const props = defineProps({
  /** Schema JSON 字符串（与表单定义管理后端契约一致：{version,title,fields[]}）。 */
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/

const opOptions = {
  eq: 'form.opEq',
  ne: 'form.opNe',
  in: 'form.opIn',
  gt: 'form.opGt',
  lt: 'form.opLt',
  gte: 'form.opGte',
  lte: 'form.opLte',
  truthy: 'form.opTruthy',
  falsy: 'form.opFalsy'
}

const palette = [
  { type: 'INPUT', labelKey: 'formDesigner.typeInput' },
  { type: 'TEXTAREA', labelKey: 'formDesigner.typeTextarea' },
  { type: 'NUMBER', labelKey: 'formDesigner.typeNumber' },
  { type: 'SELECT', labelKey: 'formDesigner.typeSelect' },
  { type: 'RADIO', labelKey: 'formDesigner.typeRadio' },
  { type: 'SWITCH', labelKey: 'formDesigner.typeSwitch' },
  { type: 'DATE', labelKey: 'formDesigner.typeDate' }
]

const title = ref('')
const fields = reactive([])
const selected = ref(-1)
const previewModel = ref({})
const lastEmitted = ref('')

const current = computed(() => (selected.value >= 0 && selected.value < fields.length ? fields[selected.value] : null))

/** 可作为「条件可见性」依赖的其他字段（排除自身，避免自引用）。 */
const otherFields = computed(() =>
  fields
    .filter((_, i) => i !== selected.value)
    .map((f) => ({ key: f.key, label: f.label || f.key }))
)

const schemaObject = computed(() => ({ version: 1, title: title.value, fields: fields.map((f) => sanitize(f)) }))

const keyError = computed(() => {
  const f = current.value
  if (!f || !f.key) return tx('formDesigner.keyRequired')
  if (!KEY_PATTERN.test(f.key)) return tx('formDesigner.keyInvalid')
  const dup = fields.filter((x) => x.key === f.key).length > 1
  return dup ? tx('formDesigner.duplicateKey') : ''
})


function typeLabel(t) {
  const found = palette.find((p) => p.type === t)
  return found ? tx(found.labelKey) : t
}

function hasPlaceholder(t) {
  return t === 'INPUT' || t === 'TEXTAREA' || t === 'SELECT' || t === 'DATE'
}

function sanitize(f) {
  const out = { key: f.key, label: f.label, type: f.type, required: !!f.required }
  if (hasPlaceholder(f.type) && f.placeholder) out.placeholder = f.placeholder
  if (f.type === 'INPUT' || f.type === 'TEXTAREA') {
    if (Number.isInteger(f.maxLength)) out.maxLength = f.maxLength
    if (Number.isInteger(f.minLength)) out.minLength = f.minLength
    if (f.pattern) out.pattern = String(f.pattern)
  }
  if (f.type === 'TEXTAREA' && Number.isInteger(f.rows)) out.rows = f.rows
  if (f.type === 'NUMBER') {
    if (typeof f.min === 'number') out.min = f.min
    if (typeof f.max === 'number') out.max = f.max
  }
  if (f.type === 'SELECT' || f.type === 'RADIO') {
    const opts = (f.options || []).filter((o) => o && o.value !== '' && o.label !== '')
    if (opts.length) out.options = opts
  }
  if (f.visibleWhen && f.visibleWhen.field) {
    out.visibleWhen = { field: f.visibleWhen.field, op: f.visibleWhen.op || 'eq', value: f.visibleWhen.value }
  }
  if (f.colSpan === 1) out.colSpan = 1
  return out
}

function nextKey() {
  let i = fields.length + 1
  let k
  do {
    k = 'field_' + i++
  } while (fields.some((f) => f.key === k))
  return k
}

function addField(type) {
  const f = reactive({
    key: nextKey(),
    label: typeLabel(type),
    type,
    required: false,
    placeholder: '',
    maxLength: undefined,
    minLength: undefined,
    pattern: '',
    rows: 3,
    min: undefined,
    max: undefined,
    visibleWhen: undefined,
    colSpan: 2,
    options: type === 'SELECT' || type === 'RADIO' ? [{ label: '', value: '' }] : undefined
  })
  fields.push(f)
  selected.value = fields.length - 1
  sync()
}

function remove(i) {
  fields.splice(i, 1)
  if (selected.value >= fields.length) selected.value = fields.length - 1
  sync()
}

/** 开启/关闭当前字段的条件可见性：开启时初始化可见条件对象，关闭时移除。 */
function toggleVisibleWhen(on) {
  const f = current.value
  if (!f) return
  if (on) {
    if (!f.visibleWhen) f.visibleWhen = { field: '', op: 'eq', value: '' }
  } else {
    delete f.visibleWhen
  }
  sync()
}

function move(i, delta) {
  const j = i + delta
  if (j < 0 || j >= fields.length) return
  const tmp = fields[i]
  fields[i] = fields[j]
  fields[j] = tmp
  selected.value = j
  sync()
}

function onTypeChange() {
  const f = current.value
  if (!f) return
  if ((f.type === 'SELECT' || f.type === 'RADIO') && !Array.isArray(f.options)) {
    f.options = [{ label: '', value: '' }]
  }
  sync()
}

function onKeyInput() {
  sync()
}

// 内部变更 → 序列化并向上 emit（同时记录 lastEmitted 防止回环）
function sync() {
  const json = JSON.stringify(schemaObject.value, null, 2)
  lastEmitted.value = json
  emit('update:modelValue', json)
}

// 外部（JSON 源码 tab / 详情回填）变更 → 仅在与上次 emit 不一致时重新解析
watch(
  () => props.modelValue,
  (val) => {
    if (val === lastEmitted.value) return
    parse(val)
  }
)

function parse(val) {
  fields.splice(0, fields.length)
  title.value = ''
  previewModel.value = {}
  selected.value = -1
  if (!val) return
  try {
    const obj = JSON.parse(val)
    if (!obj || !Array.isArray(obj.fields)) return
    title.value = obj.title || ''
    for (const f of obj.fields) {
      fields.push(
        reactive({
          key: f.key || '',
          label: f.label || '',
          type: f.type || 'INPUT',
          required: !!f.required,
          placeholder: f.placeholder || '',
          maxLength: typeof f.maxLength === 'number' ? f.maxLength : undefined,
          minLength: typeof f.minLength === 'number' ? f.minLength : undefined,
          rows: typeof f.rows === 'number' ? f.rows : 3,
          options: Array.isArray(f.options) ? f.options.map((o) => ({ label: o.label || '', value: o.value ?? '' })) : undefined
        })
      )
    }
    lastEmitted.value = JSON.stringify(schemaObject.value, null, 2)
  } catch {
    /* 非法 JSON 时清空设计器，交由 JSON 源码 tab 修正 */
  }
}

parse(props.modelValue)
</script>

<style scoped>
.fd {
  display: flex;
  gap: 12px;
  align-items: stretch;
  min-height: 420px;
}
.fd-pane {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.fd-palette {
  flex: 0 0 150px;
}
.fd-list {
  flex: 1 1 240px;
  overflow: auto;
}
.fd-prop {
  flex: 0 0 290px;
  overflow: auto;
}
.fd-preview {
  flex: 1 1 280px;
  overflow: auto;
}
.fd-pane-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}
.fd-palette-item {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  justify-content: flex-start;
}
.fd-palette-hint {
  font-size: 11px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.5;
}
.fd-empty,
.fd-empty :deep(.el-empty__description) {
  font-size: 12px;
  color: #c0c4cc;
}
.fd-field {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 6px;
  background: var(--surface);
  cursor: pointer;
}
.fd-field.active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}
.fd-field-idx {
  flex: 0 0 18px;
  color: #909399;
  font-size: 12px;
}
.fd-field-main {
  flex: 1 1 auto;
  min-width: 0;
}
.fd-field-label {
  display: block;
  font-size: 13px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fd-field-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}
.fd-field-key {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: #909399;
}
.fd-field-ops {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fd-field-ops .el-button {
  margin-left: 0;
  padding: 2px 6px;
}
.fd-opt-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.fd-field-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}
.fd-preview-body {
  background: var(--surface);
  border-radius: 6px;
  padding: 12px;
}
</style>
