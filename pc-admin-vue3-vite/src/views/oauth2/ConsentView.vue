<template>
  <div class="consent-wrap">
    <el-card class="consent-card" shadow="never">
      <!-- 加载态 -->
      <div v-if="!ready" class="state-box">
        <el-icon class="state-icon is-loading"><Loading /></el-icon>
        <p>{{ $t('oauth2Consent.loading') }}</p>
      </div>

      <!-- 异常态（令牌缺失/无效） -->
      <div v-else-if="error" class="state-box">
        <el-icon class="state-icon state-error"><WarningFilled /></el-icon>
        <p class="state-msg">{{ error }}</p>
        <el-button type="primary" @click="goHome">{{ $t('oauth2Consent.backToHome') }}</el-button>
      </div>

      <!-- 同意页主体 -->
      <template v-else>
        <h1 class="consent-title">{{ $t('oauth2Consent.title') }}</h1>
        <p class="consent-subtitle">{{ $t('oauth2Consent.subtitle') }}</p>

        <div class="app-block">
          <div class="app-row">
            <span class="app-key">{{ $t('oauth2Consent.appLabel') }}</span>
            <span class="app-val">{{ clientId || $t('oauth2Consent.unknownApp') }}</span>
          </div>
          <div v-if="redirectUri" class="app-row">
            <span class="app-key">{{ $t('oauth2Consent.redirectLabel') }}</span>
            <span class="app-val app-link" :title="redirectUri">{{ redirectUri }}</span>
          </div>
        </div>

        <div class="scopes-block">
          <div class="block-title">{{ $t('oauth2Consent.scopesTitle') }}</div>
          <ul v-if="scopes.length" class="scope-list">
            <li v-for="s in scopes" :key="s">
              <el-icon class="scope-icon"><Select /></el-icon>
              <div class="scope-text">
                <div class="scope-name">{{ s }}</div>
                <div v-if="scopeDesc(s)" class="scope-desc">{{ scopeDesc(s) }}</div>
              </div>
            </li>
          </ul>
          <p v-else class="scope-empty">{{ $t('oauth2Consent.noScope') }}</p>
        </div>

        <el-checkbox v-model="remember" class="remember-box">
          {{ $t('oauth2Consent.remember') }}
        </el-checkbox>

        <div class="actions">
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            @click="onAllow"
          >
            {{ submitting ? $t('oauth2Consent.submitting') : $t('oauth2Consent.allow') }}
          </el-button>
          <el-button size="large" :disabled="submitting" @click="onDeny">
            {{ $t('oauth2Consent.deny') }}
          </el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { decodeJwtPayload, splitScopes, submitConsent, buildCodeRedirectUrl } from '@/api/oauth2'
import { t } from '@/i18n'

const route = useRoute()
const router = useRouter()

const ready = ref(false)
const error = ref('')
const submitting = ref(false)
const consentToken = ref('')
const clientId = ref('')
const redirectUri = ref('')
const scopes = ref([])
const state = ref('')
const remember = ref(true)

onMounted(() => {
  const token = route.query.consent_token
  if (!token || typeof token !== 'string') {
    error.value = t('oauth2Consent.invalidToken')
    ready.value = true
    return
  }
  consentToken.value = token
  const payload = decodeJwtPayload(token)
  if (!payload) {
    error.value = t('oauth2Consent.invalidToken')
    ready.value = true
    return
  }
  // consent_token 内已含 client_id / scope / redirect_uri / state；query 中的同名参数仅作兜底
  clientId.value = payload.client_id || (typeof route.query.client_id === 'string' ? route.query.client_id : '')
  redirectUri.value = payload.redirect_uri || ''
  state.value = payload.state || (typeof route.query.state === 'string' ? route.query.state : '')
  const scopeStr = payload.scope || (typeof route.query.scopes === 'string' ? route.query.scopes : '')
  scopes.value = splitScopes(scopeStr)
  ready.value = true
})

// scope 描述：命中多语言字典则展示，否则留空（仅显示原始 scope 名）
function scopeDesc(s) {
  const key = 'oauth2Consent.scopeDescs.' + s
  const d = t(key)
  return d === key ? '' : d
}

function goHome() {
  router.replace('/dashboard')
}

async function onAllow() {
  if (submitting.value) return
  submitting.value = true
  try {
    const data = await submitConsent({
      consent_token: consentToken.value,
      decision: 'allow',
      remember: remember.value
    })
    const target = buildCodeRedirectUrl(redirectUri.value, data.code, state.value)
    if (!target) {
      ElMessage.error(t('oauth2Consent.invalidRedirect'))
      submitting.value = false
      return
    }
    // 全页跳转回客户端 redirect_uri，带上授权码与 state 完成授权委托
    window.location.href = target
  } catch (e) {
    // http 拦截器已统一提示（401/业务错误如 consent_token 过期等）；此处仅复位按钮态
    submitting.value = false
  }
}

function onDeny() {
  if (submitting.value) return
  // 拒绝：按 OAuth2 规范带 error=access_denied 回跳客户端 redirect_uri（由客户端处理）
  if (redirectUri.value) {
    const sep = redirectUri.value.includes('?') ? '&' : '?'
    let url = `${redirectUri.value}${sep}error=access_denied`
    if (state.value) url += `&state=${encodeURIComponent(state.value)}`
    window.location.href = url
  } else {
    error.value = t('oauth2Consent.deniedHint')
    ready.value = true
  }
}
</script>

<style scoped>
.consent-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color-page, #f2f3f5);
  padding: 24px;
}
.consent-card {
  width: 100%;
  max-width: 520px;
  border-radius: 12px;
}
.state-box {
  text-align: center;
  padding: 32px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.state-icon {
  font-size: 40px;
  color: var(--el-color-primary);
}
.state-icon.state-error {
  color: var(--el-color-danger);
}
.state-msg {
  margin: 0;
  color: var(--el-text-color-regular);
}
.consent-title {
  font-size: 22px;
  font-weight: 600;
  margin: 8px 0 4px;
  color: var(--el-text-color-primary);
}
.consent-subtitle {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin: 0 0 20px;
}
.app-block {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
}
.app-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 4px 0;
  font-size: 14px;
}
.app-key {
  flex: 0 0 84px;
  color: var(--el-text-color-secondary);
}
.app-val {
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.app-link {
  font-weight: 500;
  color: var(--el-color-primary);
}
.block-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}
.scope-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.scope-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.scope-icon {
  color: var(--el-color-success);
  font-size: 18px;
  margin-top: 2px;
}
.scope-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.scope-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.scope-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
.remember-box {
  margin: 20px 0;
}
.actions {
  display: flex;
  gap: 12px;
}
.actions .el-button {
  flex: 1;
}
</style>
