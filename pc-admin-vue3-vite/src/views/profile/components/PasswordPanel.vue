<template>
  <div class="panel">
    <h3 class="panel-title">{{ t('profile.changePwd') }}</h3>
    <p class="panel-desc">
      修改成功后，当前账号在所有设备上的登录状态都会失效，需使用新密码重新登录。
    </p>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="96px"
      class="pwd-form"
      @submit.prevent
    >
      <el-form-item :label="t('profile.oldPwd')" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          :placeholder="t('profile.enterCurrentPwd')"
          :prefix-icon="Lock"
        />
      </el-form-item>
      <el-form-item :label="t('profile.newPwd')" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          :placeholder="t('profile.pwdRule')"
          :prefix-icon="Lock"
        />
      </el-form-item>
      <el-form-item :label="t('profile.confirmNewPwd')" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          :placeholder="t('profile.reenterNewPwd')"
          :prefix-icon="Lock"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          确认修改
        </el-button>
        <el-button @click="reset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { changePassword } from '@/api/profile'

const emit = defineEmits(['success'])

const formRef = ref()
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: t('profile.enterOldPwd'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('profile.enterNewPwd'), trigger: 'blur' },
    { min: 8, max: 64, message: t('profile.pwdLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('profile.reenterNewPwd'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error(t('profile.pwdMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function reset() {
  formRef.value?.clearValidate()
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    reset()
    ElMessage.success(t('profile.pwdChanged'))
    emit('success')
  } catch (e) {
    // 错误提示已由 http 层统一弹出，此处仅结束 loading
  } finally {
    submitting.value = false
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
.pwd-form {
  max-width: 480px;
}
@media (max-width: 480px) {
  .pwd-form :deep(.el-form-item__label) {
    width: 76px !important;
  }
}
</style>
