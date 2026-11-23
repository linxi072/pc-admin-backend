<template>
  <el-select
    v-model="innerValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :multiple="multiple"
    :filterable="filterable"
    :loading="loading"
    v-bind="$attrs"
    class="dict-select"
  >
    <el-option
      v-for="opt in normalizedOptions"
      :key="String(opt.value)"
      :label="opt.label"
      :value="opt.value"
      :disabled="!!opt.disabled"
    />
  </el-select>
</template>

<script setup>
import { computed } from 'vue'

/**
 * DictSelect：通用字典选择器（零业务依赖）。
 * 输入：modelValue(v-model) / options([{label,value,disabled}]) / placeholder / clearable / disabled / multiple / filterable / loading
 * 输出事件：update:modelValue / change
 * 边界：options 为 null 按空数组；value 支持单值与数组(multiple)；选项 value 统一按字符串比较兜底。
 * 异常：不主动抛错。
 */
const props = defineProps({
  modelValue: { type: [String, Number, Boolean, Array], default: undefined },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  multiple: { type: Boolean, default: false },
  filterable: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])

const innerValue = computed({
  get: () => props.modelValue,
  set: (v) => { emit('update:modelValue', v); emit('change', v) }
})

const normalizedOptions = computed(() =>
  (props.options || []).map((o) => ({
    label: o.label ?? String(o.value ?? ''),
    value: o.value,
    disabled: !!o.disabled
  }))
)
</script>

<style scoped>
.dict-select { width: 100%; }
</style>
