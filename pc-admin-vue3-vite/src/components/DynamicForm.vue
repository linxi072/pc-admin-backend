<template>
    <el-form
      ref="formRef"
      class="df-grid"
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
        :style="f.colSpan === 1 ? 'grid-column: span 1;' : 'grid-column: 1 / -1;'"
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
        :min="f.min"
        :max="f.max"
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
import { ref, computed, watch } from 'vue'

import { buildRules, visibleFields, defaultValueFor } from '@/utils/formSchema'

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

/**
 * 可见字段清单：仅渲染通过条件可见性（visibleWhen）的字段，非法类型由模板显式降级提示。
 * 依赖 modelValue，因此条件字段变化会响应式刷新可见集合。
 */
const fields = computed(() => visibleFields(props.schema, props.modelValue))

/** 受控模型：写回时经 update:modelValue 通知父级，保持单一数据源。 */
const model = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** Schema 变化时补齐缺失字段初始值（不覆盖已有值，支持父级预填）。 */
watch(() => props.schema, (s) => {
  if (!s || !Array.isArray(s.fields)) return
  const patch = {}
  for (const f of s.fields) {
    if (!(f.key in props.modelValue)) patch[f.key] = defaultValueFor(f.type)
  }
  if (Object.keys(patch).length) emit('update:modelValue', { ...props.modelValue, ...patch })
}, { immediate: true })

/** Schema → element-plus 校验规则（required/长度/数字范围/正则；trigger 与控件交互对齐）。 */
const rules = computed(() => buildRules(fields.value, t))

/** 执行校验：resolve(true) 表示通过；调用方负责 catch 兜底提示。 */
async function validate() {
  if (!formRef.value) return true
  return formRef.value.validate().then(() => true).catch(() => false)
}

/** 重置校验态与初始值。 */
function resetFields() {
  formRef.value?.clearValidate()
  const patch = {}
  for (const f of fields.value) patch[f.key] = defaultValueFor(f.type)
  emit('update:modelValue', patch)
}

defineExpose({ validate, resetFields })
</script>

<style scoped>
/* 响应式双列栅格：colSpan=1 占半列，其余（默认）占满整行，保持历史全宽布局不变 */
.df-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.df-grid :deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
