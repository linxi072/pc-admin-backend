<template>
  <el-form
    ref="formRef"
    :model="modelValue"
    :inline="inline"
    :label-width="labelWidth"
    class="query-form"
    @submit.prevent="onSubmit"
  >
    <slot />
    <el-form-item class="query-form__actions">
      <el-button type="primary" :loading="loading" @click="onSubmit">{{ searchText }}</el-button>
      <el-button @click="onReset">{{ resetText }}</el-button>
      <slot name="actions" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref } from 'vue'

/**
 * QueryForm：通用查询表单（零业务依赖）。
 * 输入：modelValue(查询条件对象，v-model) / inline / labelWidth / loading / searchText / resetText
 * 输出事件：submit(带当前条件副本) / reset(重置为空值副本) / update:modelValue
 * 边界：modelValue 为 null 时按空对象处理；重置将每个键置为 null（不删除键），保持表单结构稳定。
 * 异常：不主动抛错；校验由 el-form 负责。
 */
const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  inline: { type: Boolean, default: true },
  labelWidth: { type: String, default: 'auto' },
  loading: { type: Boolean, default: false },
  searchText: { type: String, default: '查询' },
  resetText: { type: String, default: '重置' }
})
const emit = defineEmits(['submit', 'reset', 'update:modelValue'])
const formRef = ref(null)

function onSubmit() {
  emit('submit', { ...(props.modelValue || {}) })
}
function onReset() {
  const cleared = {}
  Object.keys(props.modelValue || {}).forEach((k) => { cleared[k] = null })
  emit('update:modelValue', cleared)
  emit('reset', cleared)
}
function validate() {
  return formRef.value?.validate() ?? Promise.resolve(true)
}
function resetFields() {
  formRef.value?.resetFields()
}
defineExpose({ validate, resetFields })
</script>

<style scoped>
.query-form { width: 100%; }
.query-form__actions { margin-bottom: 0; }
</style>
