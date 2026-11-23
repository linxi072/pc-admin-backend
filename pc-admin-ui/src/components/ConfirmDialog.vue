<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    :append-to-body="appendToBody"
    class="confirm-dialog"
    @closed="onClosed"
  >
    <div class="confirm-dialog__content">
      <el-icon v-if="icon" class="confirm-dialog__icon" :class="toneClass"><component :is="icon" /></el-icon>
      <div class="confirm-dialog__message">{{ messageText }}</div>
    </div>
    <template #footer>
      <el-button @click="onCancel">{{ cancelText }}</el-button>
      <el-button :type="confirmButtonType" :loading="loading" @click="onConfirm">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

/**
 * ConfirmDialog：通用确认对话框（零业务依赖，受控组件）。
 * 输入：modelValue(是否显示，v-model) / title / message / width / loading / danger / confirmText / cancelText / icon / appendToBody
 * 输出事件：update:modelValue / confirm / cancel / closed
 * 边界：message 可为字符串或函数（() => string），渲染时求值；danger=true 时确认按钮为 danger。
 * 异常：不主动抛错；确认后的异步操作由调用方处理 loading 与关闭。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: [String, Function], default: '' },
  width: { type: String, default: '420px' },
  loading: { type: Boolean, default: false },
  danger: { type: Boolean, default: false },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  icon: { type: [Object, Function], default: null },
  appendToBody: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const confirmButtonType = computed(() => (props.danger ? 'danger' : 'primary'))
const toneClass = computed(() => (props.danger ? 'is-danger' : 'is-primary'))
const messageText = computed(() =>
  typeof props.message === 'function' ? props.message() : String(props.message ?? '')
)

function onConfirm() { emit('confirm') }
function onCancel() { visible.value = false; emit('cancel') }
function onClosed() { emit('closed') }
</script>

<style scoped>
.confirm-dialog__content { display: flex; align-items: flex-start; gap: 10px; }
.confirm-dialog__icon { font-size: 20px; margin-top: 2px; }
.confirm-dialog__icon.is-danger { color: var(--el-color-danger); }
.confirm-dialog__icon.is-primary { color: var(--el-color-primary); }
.confirm-dialog__message { flex: 1; line-height: 1.5; }
</style>
