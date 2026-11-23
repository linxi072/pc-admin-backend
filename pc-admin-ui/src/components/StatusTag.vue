<template>
  <el-tag :type="resolved.type" :size="size" :effect="effect" :disable-transitions="disableTransitions">
    {{ resolved.text }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

/**
 * StatusTag：通用状态标签（零业务依赖）。
 * 输入：value(状态值) / mapping(状态→{text,type} 映射) / size / effect / disableTransitions / defaultType
 * 输出：无事件（纯展示）
 * 边界：未命中映射时显示原始 value（stringify），type 用 defaultType；value 为 null/undefined 显示 '-'。
 * 异常：mapping 非对象时按空映射处理，不抛错。
 */
const props = defineProps({
  value: { type: [String, Number, Boolean], default: null },
  mapping: { type: Object, default: () => ({}) },
  size: { type: String, default: 'small' },
  effect: { type: String, default: 'light' },
  disableTransitions: { type: Boolean, default: false },
  defaultType: { type: String, default: 'info' }
})

const resolved = computed(() => {
  if (props.value === null || props.value === undefined) return { text: '-', type: props.defaultType }
  const map = props.mapping && typeof props.mapping === 'object' ? props.mapping : {}
  const hit = map[String(props.value)]
  if (hit && typeof hit === 'object') {
    return { text: hit.text ?? String(props.value), type: hit.type ?? props.defaultType }
  }
  return { text: hit != null ? String(hit) : String(props.value), type: props.defaultType }
})
</script>
