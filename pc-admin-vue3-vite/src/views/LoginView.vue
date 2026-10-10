<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">
        <el-icon size="28" color="#409eff"><Setting /></el-icon>
        <h2>运营管理后台</h2>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaAnswer">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaAnswer"
              placeholder="请输入计算结果"
              :prefix-icon="Key"
              @keyup.enter="onSubmit"
            />
            <div class="captcha-box" @click="fetchCaptcha" title="点击刷新验证码">
              <span class="captcha-question">{{ captcha.question || '—' }}</span>
              <el-icon class="captcha-refresh"><Refresh /></el-icon>
            </div>
          </div>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="onSubmit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, Key, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, getCaptcha } from '@/api/auth'
import { setAuth } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123', captchaAnswer: '' })
const captcha = reactive({ token: '', question: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaAnswer: [{ required: true, message: '请输入验证码结果', trigger: 'blur' }]
}

async function fetchCaptcha() {
  try {
    const c = await getCaptcha()
    captcha.token = c.token
    captcha.question = c.question
    form.captchaAnswer = ''
  } catch (e) {
    // 验证码为可选安全增强：拉取失败时仅记录，不阻断登录页渲染
    captcha.token = ''
    captcha.question = ''
  }
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!captcha.token) {
    ElMessage.warning('验证码加载失败，请稍后重试')
    await fetchCaptcha()
    return
  }
  loading.value = true
  try {
    const tokenView = await login(form.username, form.password, captcha.token, form.captchaAnswer)
    setAuth(tokenView.accessToken, form.username)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/dashboard')
  } catch (e) {
    // 登录失败（含验证码错误/过期）后刷新验证码，原 token 已单次失效需重置
    await fetchCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(fetchCaptcha)
</script>

<style scoped>
.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.captcha-box {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 110px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}
.captcha-question {
  font-weight: 600;
  color: #409eff;
  letter-spacing: 1px;
}
.captcha-refresh {
  color: #909399;
}
</style>
