<template>
  <div class="panel">
    <h3 class="panel-title">修改密码</h3>
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
      <el-form-item label="原密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          placeholder="请输入当前密码"
          :prefix-icon="Lock"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="8~64 位，建议包含字母与数字"
          :prefix-icon="Lock"
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="请再次输入新密码"
          :prefix-icon="Lock"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          确认修改
        </el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { changePassword } from '@/api/profile'

const emit = defineEmits(['success'])

const formRef = ref()
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8~64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
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
    ElMessage.success('密码修改成功')
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