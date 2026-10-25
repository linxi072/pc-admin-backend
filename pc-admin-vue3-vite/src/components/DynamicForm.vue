<template>
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    label-width="110px"
    :disabled="disabled"
    @submit.prevent
  >
    <el-form-item
      v-for="f in fields"
      :key="f.key"
      :label="f.label"
      :prop="f.key"
    >
      <!-- 单行文本 -->
      <el-input
        v-if="f.type === 'INPUT'"
        v-model="model[f.key]"
        :placeholder="f.placeholder || `请输入${f.label}`"
        :maxlength="f.maxLength || undefined"
        clearable
      />
      <!-- 多行文本 -->
      <el-input
        v-else-if="f.type === 'TEXTAREA'"
        v-model="model[f.key]"
        type="textarea"
        :rows="f.rows || 3"
        :placeholder="f.placeholder || `请输入${f.label}`"
        :maxlength="f.maxLength || undefined"
        show-word-limit
      />
      <!-- 数字 -->
      <el-input-number
        v-else-if="f.type === 'NUMBER'"
        v-model="model[f.key]"
        :placeholder="f.placeholder"
        controls-position="right"
        style="width: 100%"
      />
      <!-- 下拉选择 -->
      <el-select
        v-else-if="f.type === 'SELECT'"
        v-model="model[f.key]"
        :placeholder="f.placeholder || `请选择${f.label}`"
        style="width: 100%"
        clearable
      >
        <el-option
          v-for="o in f.options || []"
          :key="String(o.value)"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <!-- 单选组 -->
      <el-radio-group
        v-else-if="f.type === 'RADIO'"
        v-model="model[f.key]"
      >
        <el-radio
          v-for="o in f.options || []"
          :key="String(o.value)"
          :value="o.value"
        >{{ o.label }}</el-radio>
      </el-radio-group>
      <!-- 开关 -->
      <el-switch
        v-else-if="f.type === 'SWITCH'"
        v-model="model[f.key]"
      />
      <!-- 日期 -->
      <el-date-picker
        v-else-if="f.type === 'DATE'"
        v-model="model[f.key]"
        type="date"
        value-format="YYYY-MM-DD"
        :placeholder="f.placeholder || '请选择日期'"
        style="width: 100%"
      />
      <!-- 未支持类型：显式降级提示，避免静默丢失字段 -->
      <el-alert
        v-else
        type="warning"
        :closable="false"
        show-icon
        :title="`不支持的字段类型：${f.type}`"
      />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'

const props = defineProps({
  /** 表单 Schema（已解析对象：{ version, title, fields[] }），字段契约见后端 FormSchemaRules。 */
  schema: { type: Object, required: true },
  /** 表单数据（受控双向绑定）。 */
  modelValue: { type: Object, default: () => ({}) },
  /** 全局禁用（如提交中）。 */
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const formRef = ref()

/** 字段清单：仅渲染后端白名单内的类型，非法类型由模板显式降级提示。 */
const fields = computed(() => (props.schema && Array.isArray(props.schema.fields)) ? props.schema.fields : [])

/** 受控模型：写回时经 update:modelValue 通知父级，保持单一数据源。 */
const model = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 字段类型 → 初始值（SWITCH 默认 false，其余默认空串；SELECT/RADIO/NUMBER 不预设值）。 */
function defaultValue(type) {
  if (type === 'SWITCH') return false
  if (type === 'SELECT' || type === 'RADIO' || type === 'NUMBER') return undefined
  return ''
}

/** Schema 变化时补齐缺失字段初始值（不覆盖已有值，支持父级预填）。 */
watch(() => props.schema, (s) => {
  if (!s || !Array.isArray(s.fields)) return
  const patch = {}
  for (const f of s.fields) {
    if (!(f.key in props.modelValue)) patch[f.key] = defaultValue(f.type)
  }
  if (Object.keys(patch).length) emit('update:modelValue', { ...props.modelValue, ...patch })
}, { immediate: true })

/** Schema → element-plus 校验规则（required/长度/数字范围；trigger 与控件交互对齐）。 */
const rules = computed(() => {
  const out = {}
  for (const f of fields.value) {
    const list = []
    if (f.required) {
      const msg = f.type === 'SWITCH' ? `请开启${f.label}` : `${f.label}不能为空`
      list.push({ required: true, message: msg, trigger: f.type === 'SWITCH' ? 'change' : 'blur' })
    }
    const min = Number.isInteger(f.minLength) ? f.minLength : undefined
    const max = Number.isInteger(f.maxLength) ? f.maxLength : undefined
    if ((min !== undefined || max !== undefined) && (f.type === 'INPUT' || f.type === 'TEXTAREA')) {
      list.push({
        validator: (rule, value, callback) => {
          const len = value == null ? 0 : String(value).length
          if (min !== undefined && len < min) return callback(new Error(`${f.label}至少 ${min} 个字符`))
          if (max !== undefined && len > max) return callback(new Error(`${f.label}不超过 ${max} 个字符`))
          callback()
        },
        trigger: 'blur'
      })
    }
    if (list.length) out[f.key] = list
  }
  return out
})

/** 执行校验：resolve(true) 表示通过；调用方负责 catch 兜底提示。 */
async function validate() {
  if (!formRef.value) return true
  return formRef.value.validate().then(() => true).catch(() => false)
}

/** 重置校验态与初始值。 */
function resetFields() {
  formRef.value?.clearValidate()
  const patch = {}
  for (const f of fields.value) patch[f.key] = defaultValue(f.type)
  emit('update:modelValue', patch)
}

defineExpose({ validate, resetFields })
</script>
