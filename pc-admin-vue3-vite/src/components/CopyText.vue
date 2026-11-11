<template>
  <span class="copy-text" :class="{ 'is-block': block }">
    <span v-if="showText" class="copy-text__value" :title="displayText">{{ displayText }}</span>
    <el-button
      v-if="copyable"
      class="copy-text__btn"
      text
      size="small"
      :icon="DocumentCopy"
      :aria-label="'复制'"
      @click="onCopy"
    />
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'

const props = defineProps({
  // 待复制的原始文本（可为数字，内部转为字符串）
  text: { type: [String, Number], default: '' },
  // 展示文本（默认等于 text；可自定义脱敏展示）
  display: { type: [String, Number], default: '' },
  // 是否渲染复制按钮
  copyable: { type: Boolean, default: true },
  // 是否整行块级展示
  block: { type: Boolean, default: false },
  // 是否展示文本（仅要按钮时置 false）
  showText: { type: Boolean, default: true }
})

const source = computed(() => String(props.text ?? ''))
const displayText = computed(() => (props.display !== '' ? String(props.display) : source.value))

async function onCopy() {
  const text = source.value
  if (!text) {
    ElMessage.warning('暂无内容可复制')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
    ElMessage.success('已复制')
  } catch (e) {
    // 现代 API 失败时退回到 execCommand 兜底
    try {
      fallbackCopy(text)
      ElMessage.success('已复制')
    } catch (_) {
      ElMessage.error('复制失败')
    }
  }
}

// 非安全上下文（http / 沙箱）下使用临时 textarea + execCommand 兜底
function fallbackCopy(text) {
  if (typeof document === 'undefined') throw new Error('document unavailable')
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.top = '-9999px'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch (_) {
    ok = false
  }
  document.body.removeChild(ta)
  if (!ok) throw new Error('execCommand copy failed')
}
</script>

<style scoped>
.copy-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}
.copy-text.is-block {
  display: flex;
  width: 100%;
}
.copy-text__value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.copy-text__btn {
  flex: 0 0 auto;
  padding: 2px;
  height: auto;
}
</style>
