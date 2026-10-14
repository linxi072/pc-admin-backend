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
        <div class="login-extra">
          <el-link type="primary" :underline="false" @click="openForgot">忘记密码</el-link>
          <el-link type="info" :underline="false" @click="openChange">修改密码</el-link>
        </div>
      </el-form>
    </el-card>

    <!-- 修改密码：知道原密码时的自助改密（会话无关，登录被 AUTH_014 拦截时自动弹出） -->
    <el-dialog v-model="changeDlg.visible" title="修改密码" width="420px" @closed="resetChangeDlg">
      <el-form :model="changeDlg.form" :rules="changeRules" ref="changeFormRef" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="changeDlg.form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="changeDlg.form.oldPassword"
            type="password"
            show-password
            placeholder="请输入原密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="changeDlg.form.newPassword"
            type="password"
            show-password
            placeholder="8~64 位，至少包含字母和数字"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="changeDlg.form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
            :prefix-icon="Lock"
            @keyup.enter="submitChange"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeDlg.visible = false">取 消</el-button>
        <el-button type="primary" :loading="changeDlg.loading" @click="submitChange">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 忘记密码：忘记原密码时凭手机号/邮箱核验自助重置 -->
    <el-dialog v-model="forgotDlg.visible" title="忘记密码" width="420px" @closed="resetForgotDlg">
      <el-alert type="info" :closable="false" show-icon
        title="请填写账号登记的手机号或邮箱（二选一）完成身份核验，核验通过后可重置密码。" />
      <el-form :model="forgotDlg.form" :rules="forgotRules" ref="forgotFormRef" label-position="top" style="margin-top: 12px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="forgotDlg.form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="forgotDlg.form.mobile" placeholder="账号登记的手机号（与邮箱二选一）" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="forgotDlg.form.email" placeholder="账号登记的邮箱（与手机号二选一）" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="forgotDlg.form.newPassword"
            type="password"
            show-password
            placeholder="8~64 位，至少包含字母和数字"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="forgotDlg.form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
            :prefix-icon="Lock"
            @keyup.enter="submitForgot"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="forgotDlg.visible = false">取 消</el-button>
        <el-button type="primary" :loading="forgotDlg.loading" @click="submitForgot">重 置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, Key, Refresh, Iphone, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, getCaptcha, changePassword, forgotPassword } from '@/api/auth'
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

// ---------- 修改密码弹窗 ----------
const changeFormRef = ref()
const changeDlg = reactive({
  visible: false,
  loading: false,
  form: { username: '', oldPassword: '', newPassword: '', confirmPassword: '' }
})

const changeRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8~64 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }]
}

function openChange() {
  // 预填登录页已输入的用户名，减少重复输入
  changeDlg.form.username = form.username || ''
  changeDlg.visible = true
}

function resetChangeDlg() {
  changeFormRef.value?.resetFields()
  Object.assign(changeDlg.form, { username: '', oldPassword: '', newPassword: '', confirmPassword: '' })
}

async function submitChange() {
  const valid = await changeFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (changeDlg.form.newPassword !== changeDlg.form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changeDlg.loading = true
  try {
    await changePassword(changeDlg.form.username, changeDlg.form.oldPassword, changeDlg.form.newPassword)
    ElMessage.success('密码修改成功，请使用新密码登录')
    changeDlg.visible = false
    // 旧口令已失效：同步刷新登录页状态，引导用新密码重新登录
    form.password = ''
    form.captchaAnswer = ''
    await fetchCaptcha()
  } catch (e) {
    // 失败时刷新验证码，避免下次登录因验证码过期二次失败
    await fetchCaptcha()
  } finally {
    changeDlg.loading = false
  }
}

// ---------- 忘记密码弹窗 ----------
const forgotFormRef = ref()
const forgotDlg = reactive({
  visible: false,
  loading: false,
  form: { username: '', mobile: '', email: '', newPassword: '', confirmPassword: '' }
})

const forgotRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8~64 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }]
}

function openForgot() {
  forgotDlg.form.username = form.username || ''
  forgotDlg.visible = true
}

function resetForgotDlg() {
  forgotFormRef.value?.resetFields()
  Object.assign(forgotDlg.form, { username: '', mobile: '', email: '', newPassword: '', confirmPassword: '' })
}

async function submitForgot() {
  const valid = await forgotFormRef.value.validate().catch(() => false)
  if (!valid) return
  // 手机号/邮箱二选一：与后端 ForgotPasswordRules.hasVerifier 同效（服务端为权威）
  if (!forgotDlg.form.mobile.trim() && !forgotDlg.form.email.trim()) {
    ElMessage.warning('请填写手机号或邮箱中的至少一个用于身份核验')
    return
  }
  if (forgotDlg.form.newPassword !== forgotDlg.form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  forgotDlg.loading = true
  try {
    await forgotPassword(
      forgotDlg.form.username,
      forgotDlg.form.newPassword,
      forgotDlg.form.mobile.trim() || undefined,
      forgotDlg.form.email.trim() || undefined
    )
    ElMessage.success('密码重置成功，请使用新密码登录')
    forgotDlg.visible = false
    form.password = ''
    form.captchaAnswer = ''
    await fetchCaptcha()
  } catch (e) {
    // 防枚举：服务端对「用户不存在」与「验证信息不匹配」返回同一错误，此处不细分
    await fetchCaptcha()
  } finally {
    forgotDlg.loading = false
  }
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
    // AUTH_014「请修改初始/重置密码后再登录」：被拦用户无自助入口会卡死，
    // 自动弹出修改密码弹窗并预填用户名，闭合「重置→被拦→改密→登录」环路
    if (e?.response?.data?.code === 'AUTH_014') {
      changeDlg.form.username = form.username
      changeDlg.visible = true
    }
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
.login-extra {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
}
</style>
