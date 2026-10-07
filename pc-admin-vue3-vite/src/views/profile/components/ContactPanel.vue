<template>
  <div class="panel">
    <h3 class="panel-title">绑定与安全验证</h3>
    <p class="panel-desc">用于接收通知与找回密码。解绑后对应的通知渠道会自动关闭。</p>

    <el-descriptions :column="1" border class="bind-list">
      <el-descriptions-item label="手机号">
        <div class="row">
          <template v-if="profile?.mobileBound">
            <span class="value">{{ maskedMobile }}</span>
            <el-tag type="success" size="small" effect="plain">已绑定</el-tag>
          </template>
          <el-tag v-else type="info" size="small" effect="plain">未绑定</el-tag>
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

      <el-descriptions-item label="邮箱">
        <div class="row">
          <template v-if="profile?.emailBound">
            <span class="value">{{ profile.email }}</span>
            <el-tag type="success" size="small" effect="plain">已绑定</el-tag>
          </template>
          <el-tag v-else type="info" size="small" effect="plain">未绑定</el-tag>
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
      title="演示环境未接入短信/邮件网关，绑定时仅校验格式与唯一性；接入后将在此发送验证码。"
    />

    <!-- 绑定弹窗 -->
    <el-dialog
      v-model="bindVisible"
      :title="`绑定${channelLabel}`"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="80px">
        <el-form-item :label="channelLabel" prop="contact">
          <el-input
            v-model="bindForm.contact"
            :placeholder="channel === 'MOBILE' ? '请输入 11 位手机号' : '请输入邮箱地址'"
            clearable
          />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="bindForm.code" placeholder="演示环境暂不校验" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting === 'BIND'" @click="onBind">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
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

const channelLabel = computed(() => (channel.value === 'MOBILE' ? '手机号' : '邮箱'))

// 个人中心默认脱敏展示完整手机号，避免旁观者窥屏
const maskedMobile = computed(() => {
  const m = props.profile?.mobile
  if (!m) return ''
  return m.length === 11 ? `${m.slice(0, 3)}****${m.slice(7)}` : m
})

const bindRules = computed(() => ({
  contact: [
    { required: true, message: `请输入${channelLabel.value}`, trigger: 'blur' },
    channel.value === 'MOBILE'
      ? { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
      : { pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: '邮箱格式不正确', trigger: 'blur' }
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
    ElMessage.success(`${channelLabel.value}绑定成功`)
  } catch (e) {
    // 冲突/格式错误提示已由 http 层弹出
  } finally {
    submitting.value = ''
  }
}

async function onUnbind(ch) {
  const label = ch === 'MOBILE' ? '手机号' : '邮箱'
  try {
    await ElMessageBox.confirm(
      `解绑后将无法通过${label}接收通知，且${ch === 'MOBILE' ? '短信' : '邮件'}通知渠道会自动关闭。确认解绑吗？`,
      '确认解绑',
      { confirmButtonText: '确认解绑', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }

  submitting.value = ch
  try {
    const next = await bindContact({ channel: ch, contact: '' })
    emit('updated', next)
    ElMessage.success(`${label}已解绑`)
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