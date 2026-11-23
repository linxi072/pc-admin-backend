<template>
  <el-descriptions :column="column" :border="border" :size="size">
    <el-descriptions-item
      v-for="item in normalizedItems"
      :key="item.key"
      :label="item.label"
      :span="item.span || 1"
    >
      <slot :name="item.key" :item="item" :value="item.value">
        {{ item.value ?? '-' }}
      </slot>
    </el-descriptions-item>
  </el-descriptions>
</template>

<script setup>
import { computed } from 'vue'

/**
 * DescriptionList：通用描述列表（零业务依赖）。
 * 输入：items([{key,label,value,span}]) / column / border / size
 * 输出：具名插槽（以 item.key 命名，可自定义渲染），默认显示 value 或 '-'
 * 边界：items 为 null 时按空数组；value 为 null/undefined 显示 '-'；span 缺省为 1。
 * 异常：不主动抛错。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  column: { type: Number, default: 2 },
  border: { type: Boolean, default: true },
  size: { type: String, default: 'default' }
})

const normalizedItems = computed(() =>
  (props.items || []).map((it) => ({ key: String(it.key ?? it.label ?? ''), span: 1, ...it }))
)
</script>
