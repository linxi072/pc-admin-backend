<template>
  <el-card class="page-card profile-card" shadow="never">
    <!-- 加载态：骨架屏避免空白与布局跳动 -->
    <el-skeleton v-if="loading" :rows="6" animated />

    <!-- 错误态：给出原因与重试入口 -->
    <el-result
      v-else-if="error"
      icon="error"
      :title="t('profile.cardLoadFailed')"
      :sub-title="error"
    >
      <template #extra>
        <el-button type="primary" @click="$emit('retry')">{{ t('common.reload') }}</el-button>
      </template>
    </el-result>

    <!-- 空态：理论上不应出现（登录即有用户），仍做兜底 -->
    <el-empty v-else-if="!profile" :description="t('profile.noProfile')" />

    <template v-else>
      <div class="avatar-wrap">
        <el-avatar :size="72" :src="avatarUrl" class="avatar">
          {{ (profile.displayName || profile.username || 'U').charAt(0) }}
        </el-avatar>
        <el-upload
          class="avatar-upload"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="doUpload"
          accept="image/png,image/jpeg,image/gif,image/webp"
        >
          <el-button size="small" :icon="Camera" :loading="uploading">{{ t('profile.changeAvatar') }}</el-button>
        </el-upload>
        <p class="avatar-tip">{{ t('profile.avatarFormatDesc') }}</p>
      </div>

      <el-divider />

      <el-form label-width="72px" label-position="right" class="info-form">
        <el-form-item :label="t('common.username')">
          <span class="readonly">{{ profile.username }}</span>
        </el-form-item>
        <el-form-item :label="t('common.nickname')">
          <span class="readonly">{{ profile.displayName || '-' }}</span>
        </el-form-item>
        <el-form-item :label="t('common.role')">
          <span v-if="(profile.roleNames || []).length">{{ (profile.roleNames || []).join(' / ') }}</span>
          <span v-else class="text-muted">{{ t('profile.noRole') }}</span>
        </el-form-item>
        <el-form-item :label="t('common.department')">
          <span v-if="(profile.orgNames || []).length">{{ (profile.orgNames || []).join(' / ') }}</span>
          <span v-else class="text-muted">{{ t('profile.noDept') }}</span>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-tag :type="statusType(profile.status)" size="small">
            {{ statusText(profile.status) }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="t('common.lastLogin')">
          <span>{{ profile.lastLoginAt || t('common.noRecord') }}</span>
        </el-form-item>
        <el-form-item :label="t('common.registerTime')">
          <span>{{ profile.createdAt || '-' }}</span>
        </el-form-item>
      </el-form>
    </template>
  </el-card>
</template>

<script setup>
import { t } from '@/i18n'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { uploadAvatar } from '@/api/profile'

const props = defineProps({
  profile: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['retry', 'avatar-change'])

const uploading = ref(false)

// 头像为相对路径时拼接后端地址；绝对路径（http/https、data:）直接使用
const avatarUrl = computed(() => {
  const url = props.profile?.avatarUrl
  if (!url) return ''
  if (/^(https?:|data:|blob:)/.test(url)) return url
  const base = import.meta.env?.VITE_API_BASE || ''
  return `${base}${url}`
})

const statusText = (s) =>
  ({ ACTIVE: t('common.normal'), DISABLED: t('common.disabled'), LOCKED: t('common.locked') }[s] || s || '-')
const statusType = (s) =>
  ({ ACTIVE: 'success', DISABLED: 'info', LOCKED: 'danger' }[s] || 'info')

/** 前端先做一次大小与类型校验，减少无效请求（后端仍有魔数二次校验） */
function beforeUpload(file) {
  const isImage = /^image\/(png|jpeg|gif|webp)$/.test(file.type)
  if (!isImage) {
    ElMessage.error(t('profile.avatarFormat'))
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error(t('profile.avatarSize'))
    return false
  }
  return true
}

async function doUpload({ file }) {
  uploading.value = true
  try {
    const next = await uploadAvatar(file)
    emit('avatar-change', next)
    ElMessage.success(t('profile.avatarUpdated'))
  } catch (e) {
    ElMessage.error(e?.message || t('profile.avatarUploadFailed'))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.avatar {
  background: var(--brand);
  font-size: 28px;
}
.avatar-tip {
  margin: 0;
  font-size: 12px;
  color: #909399;
}
.info-form {
  margin-top: 4px;
}
.readonly {
  color: #606266;
}
/* 窄屏下压缩标签宽度，避免输入项被挤压 */
@media (max-width: 480px) {
  .info-form :deep(.el-form-item__label) {
    width: 60px !important;
  }
}
</style>
