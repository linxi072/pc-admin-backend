<template>
  <div class="panel">
    <h3 class="panel-title">{{ t('profile.privacyPrefTitle') }}</h3>
    <p class="panel-desc">{{ t('profile.privacyPrefDesc') }}</p>

    <!-- 加载态 -->
    <el-skeleton v-if="loading && !loaded" :rows="5" animated />

    <!-- 错误态：仍展示开关默认值，允许重试，不阻断其他 Tab -->
    <el-alert
      v-else-if="error"
      type="warning"
      :closable="false"
      show-icon
      class="load-error"
    >
      <template #title>偏好设置加载失败：{{ error }}</template>
      <template #default>
        <el-button size="small" @click="load">{{ t('common.reload') }}</el-button>
      </template>
    </el-alert>

    <template v-else>
      <div class="group">
        <h4 class="group-title">{{ t('profile.notifyMethod') }}</h4>
        <el-form label-width="140px" label-position="left">
          <el-form-item :label="t('profile.stationMsgNotify')">
            <el-switch v-model="form.notifySiteMessage" />
            <span class="hint">{{ t('profile.importantNoticeDesc') }}</span>
          </el-form-item>
          <el-form-item :label="t('profile.mailNotify')">
            <el-switch v-model="form.notifyEmail" :disabled="!profile?.emailBound" />
            <span class="hint">
              {{ profile?.emailBound ? t('profile.sendToEmail') : t('profile.needBindEmail') }}
            </span>
          </el-form-item>
          <el-form-item :label="t('profile.smsNotify')">
            <el-switch v-model="form.notifyMobile" :disabled="!profile?.mobileBound" />
            <span class="hint">
              {{ profile?.mobileBound ? t('profile.sendToPhone') : t('profile.needBindPhone') }}
            </span>
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <div class="group">
        <h4 class="group-title">{{ t('profile.privacySettings') }}</h4>
        <el-form label-width="140px" label-position="left">
          <el-form-item :label="t('profile.showLoginHistory')">
            <el-switch v-model="form.showLoginLog" />
            <span class="hint">{{ t('profile.loginHistoryDesc') }}</span>
          </el-form-item>
          <el-form-item :label="t('profile.maskPhonePublic')">
            <el-switch v-model="form.maskMobile" />
            <span class="hint">{{ t('profile.maskPhoneDesc') }}</span>
          </el-form-item>
          <el-form-item :label="t('profile.allowSearch')">
            <el-switch v-model="form.discoverable" />
            <span class="hint">{{ t('profile.allowSearchDesc') }}</span>
          </el-form-item>
        </el-form>
      </div>

      <div class="actions">
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="onSave">
          保存设置
        </el-button>
        <el-button :disabled="!dirty" @click="reset">{{ t('common.restore') }}</el-button>
        <span v-if="!dirty && loaded" class="saved-tip">{{ t('common.saved') }}</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPreference, updatePreference } from '@/api/profile'

const props = defineProps({
  profile: { type: Object, default: null }
})

const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const error = ref('')

const form = reactive({
  notifySiteMessage: true,
  notifyEmail: true,
  notifyMobile: false,
  showLoginLog: true,
  maskMobile: true,
  discoverable: true
})
// 保存快照用于「是否有改动」判断，避免每次输入都变脏
const snapshot = ref(JSON.stringify(form))

const dirty = computed(() => loaded.value && JSON.stringify(form) !== snapshot.value)

/** 绑定被解除时同步关闭对应通知渠道，与后端行为保持一致 */
watch(
  () => props.profile?.emailBound,
  (bound) => {
    if (bound === false) form.notifyEmail = false
  }
)
watch(
  () => props.profile?.mobileBound,
  (bound) => {
    if (bound === false) form.notifyMobile = false
  }
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const pref = await getPreference()
    if (pref) {
      Object.keys(form).forEach((k) => {
        if (typeof pref[k] === 'boolean') form[k] = pref[k]
      })
      snapshot.value = JSON.stringify(form)
      loaded.value = true
    }
  } catch (e) {
    if (e?.code !== 'COMMON_401' && e?.response?.status !== 401) {
      error.value = e?.message || t('profile.prefLoadFailed')
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  const s = JSON.parse(snapshot.value)
  Object.keys(form).forEach((k) => {
    form[k] = s[k]
  })
}

async function onSave() {
  saving.value = true
  try {
    // 全量提交：开关语义简单，全量可避免部分更新带来的状态歧义
    await updatePreference({
      notifySiteMessage: form.notifySiteMessage ? 1 : 0,
      notifyEmail: form.notifyEmail ? 1 : 0,
      notifyMobile: form.notifyMobile ? 1 : 0,
      showLoginLog: form.showLoginLog ? 1 : 0,
      maskMobile: form.maskMobile ? 1 : 0,
      discoverable: form.discoverable ? 1 : 0
    })
    snapshot.value = JSON.stringify(form)
    ElMessage.success(t('profile.prefSaved'))
  } catch (e) {
    // 错误提示已统一处理
  } finally {
    saving.value = false
  }
}

onMounted(load)
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
.load-error {
  margin-bottom: 12px;
}
.group-title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.saved-tip {
  font-size: 12px;
  color: #67c23a;
}
/* 窄屏：标签与开关改为上下排列，避免横向溢出 */
@media (max-width: 768px) {
  .panel :deep(.el-form-item__label) {
    width: 100% !important;
    text-align: left;
    margin-bottom: 4px;
  }
  .panel :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
  .hint {
    display: block;
    margin: 2px 0 0;
  }
}
</style>
