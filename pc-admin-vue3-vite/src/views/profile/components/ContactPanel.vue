<template>
  <div class="panel">
    <h3 class="panel-title">{{ t('profile.bindSecurityTitle') }}</h3>
    <p class="panel-desc">{{ t('profile.bindSecurityDesc') }}</p>

    <el-descriptions :column="1" border class="bind-list">
      <el-descriptions-item :label="t('common.phone')">
        <div class="row">
          <template v-if="profile?.mobileBound">
            <span class="value">{{ maskedMobile }}</span>
            <el-tag type="success" size="small" effect="plain">{{ t('profile.bound') }}</el-tag>
          </template>
          <el-tag v-else type="info" size="small" effect="plain">{{ t('profile.unbound') }}</el-tag>
          <span class="spacer" />
          <el-button
            v-if="profile?.mobileBound"
            text
            type="danger"
            :loading="submitting === 'MOBILE'"
            @click="onUnbind('MOBILE')"
          >
            解绑
          </el-button>
          <el-button
            v-else
            text
            type="primary"
            @click="openBind('MOBILE')"
          >
            绑定
          </el-button>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('common.email')">
        <div class="row">
          <template v-if="profile?.emailBound">
            <span class="value">{{ profile.email }}</span>
            <el-tag type="success" size="small" effect="plain">{{ t('profile.bound') }}</el-tag>
          </template>
          <el-tag v-else type="info" size="small" effect="plain">{{ t('profile.unbound') }}</el-tag>
          <span class="spacer" />
          <el-button
            v-if="profile?.emailBound"
            text
            type="danger"
            :loading="submitting === 'EMAIL'"
            @click="onUnbind('EMAIL')"
          >
            解绑
          </el-button>
          <el-button
            v-else
            text
            type="primary"
            @click="openBind('EMAIL')"
          >
            绑定
          </el-button>
        </div>
      </el-descriptions-item>
    </el-descriptions>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="bind-tip"
      :title="t('profile.demoSmsMailDesc')"
    />

    <!-- 绑定弹窗 -->
    <el-dialog
      v-model="bindVisible"
      :title="t('profile.bindingChannel', { channel: channelLabel })"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="80px">
        <el-form-item :label="channelLabel" prop="contact">
          <el-input
            v-model="bindForm.contact"
            :placeholder="channel === 'MOBILE' ? t('profile.pleaseEnterMobile') : t('profile.pleaseEnterEmail')"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('common.verificationCode')">
          <el-input v-model="bindForm.code" :placeholder="t('profile.demoNoVerify')" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting === 'BIND'" @click="onBind">{{ t('profile.confirmBind') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bindContact } from '@/api/profile'

const props = defineProps({
  profile: { type: Object, default: null }
})
const emit = defineEmits(['updated'])

const bindVisible = ref(false)
const bindFormRef = ref()
const submitting = ref('')
const channel = ref('MOBILE')
const bindForm = reactive({ contact: '', code: '' })

const channelLabel = computed(() => (channel.value === 'MOBILE' ? t('common.phone') : t('common.email')))

// 个人中心默认脱敏展示完整手机号，避免旁观者窥屏
const maskedMobile = computed(() => {
  const m = props.profile?.mobile
  if (!m) return ''
  return m.length === 11 ? `${m.slice(0, 3)}****${m.slice(7)}` : m
})

const bindRules = computed(() => ({
  contact: [
    { required: true, message: t('profile.pleaseEnterChannel', { channel: channelLabel.value }), trigger: 'blur' },
    channel.value === 'MOBILE'
      ? { pattern: /^1[3-9]\d{9}$/, message: t('profile.mobileFormatInvalid'), trigger: 'blur' }
      : { pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: t('profile.emailFormatInvalid'), trigger: 'blur' }
  ]
}))

function openBind(ch) {
  channel.value = ch
  bindForm.contact = ''
  bindForm.code = ''
  bindVisible.value = true
}

async function onBind() {
  const valid = await bindFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = 'BIND'
  try {
    const next = await bindContact({ channel: channel.value, contact: bindForm.contact.trim() })
    bindVisible.value = false
    emit('updated', next)
    ElMessage.success(t('profile.channelBound', { channel: channelLabel.value }))
  } catch (e) {
    // 冲突/格式错误提示已由 http 层弹出
  } finally {
    submitting.value = ''
  }
}

async function onUnbind(ch) {
  const label = ch === 'MOBILE' ? t('common.phone') : t('common.email')
  try {
    await ElMessageBox.confirm(
      t('profile.unbindConfirm', { label, channel: ch === 'MOBILE' ? t('common.sms') : t('common.mail') }),
      t('profile.confirmUnbind'),
      { confirmButtonText: t('profile.confirmUnbind'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }

  submitting.value = ch
  try {
    const next = await bindContact({ channel: ch, contact: '' })
    emit('updated', next)
    ElMessage.success(t('profile.channelUnbound', { label }))
  } catch (e) {
    // 错误提示已统一处理
  } finally {
    submitting.value = ''
  }
}
</script>

<style scoped>
.panel-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 500;
}
.panel-desc {
  margin: 0 0 16px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.value {
  word-break: break-all;
}
.spacer {
  flex: 1;
}
.bind-tip {
  margin-top: 12px;
}
</style>
