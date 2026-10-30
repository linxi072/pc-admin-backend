<template>
    <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">
        <!-- 品牌色统一走全局 --brand 变量 -->
        <el-icon size="28" color="var(--brand)"><Setting /></el-icon>
        <h2>{{ t('login.title') }}</h2>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="form.username" :placeholder="t('login.pleaseEnterUsername')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item :label="t('login.password')" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="t('login.pleaseEnterPassword')"
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item :label="t('login.captcha')" prop="captchaAnswer">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaAnswer"
              :placeholder="t('login.captchaPlaceholder')"
              :prefix-icon="Key"
              @keyup.enter="onSubmit"
            />
            <div class="captcha-box" @click="fetchCaptcha" :title="t('login.captchaRefresh')">
              <span class="captcha-question">{{ captcha.question || '—' }}</span>
              <el-icon class="captcha-refresh"><Refresh /></el-icon>
            </div>
          </div>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="onSubmit">{{ t('login.submit') }}</el-button>
        <div class="login-extra">
          <el-link type="primary" :underline="false" @click="openForgot">{{ t('login.forgotPassword') }}</el-link>
          <el-link type="info" :underline="false" @click="openChange">{{ t('login.changePassword') }}</el-link>
        </div>
      </el-form>
    </el-card>

    <!-- 修改密码：知道原密码时的自助改密（会话无关，登录被 AUTH_014 拦截时自动弹出） -->
    <el-dialog v-model="changeDlg.visible" :title="t('login.changePassword')" width="420px" @closed="resetChangeDlg">
      <el-form :model="changeDlg.form" :rules="changeRules" ref="changeFormRef" label-position="top">
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="changeDlg.form.username" :placeholder="t('login.pleaseEnterUsername')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item :label="t('login.oldPassword')" prop="oldPassword">
          <el-input
            v-model="changeDlg.form.oldPassword"
            type="password"
            show-password
            :placeholder="t('login.pleaseEnterOldPassword')"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item :label="t('login.newPassword')" prop="newPassword">
          <el-input
            v-model="changeDlg.form.newPassword"
            type="password"
            show-password
            :placeholder="t('login.newPwdPlaceholder')"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item :label="t('login.confirmNewPassword')" prop="confirmPassword">
          <el-input
            v-model="changeDlg.form.confirmPassword"
            type="password"
            show-password
            :placeholder="t('login.pleaseEnterConfirmPwd')"
            :prefix-icon="Lock"
            @keyup.enter="submitChange"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeDlg.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="changeDlg.loading" @click="submitChange">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 忘记密码：忘记原密码时凭手机号/邮箱核验自助重置 -->
    <el-dialog v-model="forgotDlg.visible" :title="t('login.forgotPassword')" width="420px" @closed="resetForgotDlg">
      <el-alert type="info" :closable="false" show-icon
        :title="t('login.forgotDesc')" />
      <el-form :model="forgotDlg.form" :rules="forgotRules" ref="forgotFormRef" label-position="top" style="margin-top: 12px">
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="forgotDlg.form.username" :placeholder="t('login.pleaseEnterUsername')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item :label="t('login.mobile')" prop="mobile">
          <el-input v-model="forgotDlg.form.mobile" :placeholder="t('login.mobilePlaceholder')" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item :label="t('login.email')" prop="email">
          <el-input v-model="forgotDlg.form.email" :placeholder="t('login.emailPlaceholder')" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item :label="t('login.newPassword')" prop="newPassword">
          <el-input
            v-model="forgotDlg.form.newPassword"
            type="password"
            show-password
            :placeholder="t('login.newPwdPlaceholder')"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item :label="t('login.confirmNewPassword')" prop="confirmPassword">
          <el-input
            v-model="forgotDlg.form.confirmPassword"
            type="password"
            show-password
            :placeholder="t('login.pleaseEnterConfirmPwd')"
            :prefix-icon="Lock"
            @keyup.enter="submitForgot"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="forgotDlg.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="forgotDlg.loading" @click="submitForgot">{{ t('login.reset') }}</el-button>
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
import { t } from '@/i18n'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', captchaAnswer: '' })
const captcha = reactive({ token: '', question: '' })

// 语言在应用启动时已按浏览器/系统语言自动选定（见 src/i18n），登录页无需手动切换；
// 校验提示文案直接由 t() 按当前语言解析。
const rules = {
  username: [{ required: true, message: t('login.pleaseEnterUsername'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.pleaseEnterPassword'), trigger: 'blur' }],
  captchaAnswer: [{ required: true, message: t('login.pleaseEnterCaptcha'), trigger: 'blur' }]
}

// ---------- 修改密码弹窗 ----------
const changeFormRef = ref()
const changeDlg = reactive({
  visible: false,
  loading: false,
  form: { username: '', oldPassword: '', newPassword: '', confirmPassword: '' }
})

const changeRules = {
  username: [{ required: true, message: t('login.pleaseEnterUsername'), trigger: 'blur' }],
  oldPassword: [{ required: true, message: t('login.pleaseEnterOldPassword'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('login.pleaseEnterNewPassword'), trigger: 'blur' },
    { min: 8, max: 64, message: t('login.pwdLength'), trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: t('login.pleaseEnterConfirmPwd'), trigger: 'blur' }]
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
    ElMessage.warning(t('login.pwdMismatch'))
    return
  }
  changeDlg.loading = true
  try {
    await changePassword(changeDlg.form.username, changeDlg.form.oldPassword, changeDlg.form.newPassword)
    ElMessage.success(t('login.changePwdSuccess'))
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
  username: [{ required: true, message: t('login.pleaseEnterUsername'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('login.pleaseEnterNewPassword'), trigger: 'blur' },
    { min: 8, max: 64, message: t('login.pwdLength'), trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: t('login.pleaseEnterConfirmPwd'), trigger: 'blur' }]
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
    ElMessage.warning(t('login.pleaseEnterVerifier'))
    return
  }
  if (forgotDlg.form.newPassword !== forgotDlg.form.confirmPassword) {
    ElMessage.warning(t('login.pwdMismatch'))
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
    ElMessage.success(t('login.resetPwdSuccess'))
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
    ElMessage.warning(t('login.captchaLoadFailed'))
    await fetchCaptcha()
    return
  }
  loading.value = true
  try {
    const tokenView = await login(form.username, form.password, captcha.token, form.captchaAnswer)
    setAuth(tokenView.accessToken, form.username)
    ElMessage.success(t('login.loginSuccess'))
    router.replace(route.query.redirect || '/dashboard')
  } catch (e) {
    // 登录失败（含验证码错误/过期）后刷新验证码，原 token 已单次失效需重置
    await fetchCaptcha()
    // AUTH_014「请修改初始/重置密码后再登录」：被拦用户无自助入口会卡死，
    // 自动弹出修改密码弹窗并预填用户名，闭合「重置→被拦→改密→登录」环路。
    // 双兼容错误形状：http.js 对业务错误（HTTP 200 + code≠0）reject 的是带 code 属性的
    // Error（无 response 形状），只有 HTTP 层错误才带 response——少判一种形状弹窗就永远不出现
    if (e?.code === 'AUTH_014' || e?.response?.data?.code === 'AUTH_014') {
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
  color: var(--brand);
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
