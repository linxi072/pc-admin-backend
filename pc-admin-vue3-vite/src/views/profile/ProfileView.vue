<template>
  <div class="profile-page">
    <el-row :gutter="16">
      <!-- 左：资料卡 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="7">
        <ProfileCard
          :profile="profile"
          :loading="cardLoading"
          :error="cardError"
          @retry="loadProfile"
          @avatar-change="onAvatarChange"
        />
      </el-col>

      <!-- 右：账号设置 Tabs -->
      <el-col :xs="24" :sm="24" :md="16" :lg="17">
        <el-card class="page-card" shadow="never">
          <el-tabs v-model="activeTab">
            <el-tab-pane name="security">
              <template #label>
                <span class="tab-label"><el-icon><Lock /></el-icon>账号安全</span>
              </template>
              <PasswordPanel
                @success="onPasswordChanged"
              />
              <el-divider />
              <ContactPanel
                :profile="profile"
                @updated="onProfileUpdated"
              />
            </el-tab-pane>

            <el-tab-pane name="device">
              <template #label>
                <span class="tab-label"><el-icon><Monitor /></el-icon>登录设备</span>
              </template>
              <DevicePanel :profile="profile" />
            </el-tab-pane>

            <el-tab-pane name="preference">
              <template #label>
                <span class="tab-label"><el-icon><Setting /></el-icon>通知与隐私</span>
              </template>
              <PreferencePanel :profile="profile" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProfileCard from './components/ProfileCard.vue'
import PasswordPanel from './components/PasswordPanel.vue'
import ContactPanel from './components/ContactPanel.vue'
import DevicePanel from './components/DevicePanel.vue'
import PreferencePanel from './components/PreferencePanel.vue'
import { getProfile } from '@/api/profile'
import { clearAuth } from '@/store/auth'

const router = useRouter()
const route = useRoute()
// 支持从顶栏「账号设置」以 /profile#security 直达对应 Tab
const HASH_TAB = { '#security': 'security', '#device': 'device', '#preference': 'preference' }
const activeTab = ref(HASH_TAB[route.hash] || 'security')

// 资料为整页共享数据：左卡片展示，右各面板也依赖绑定状态
const profile = ref(null)
const cardLoading = ref(true)
const cardError = ref('')

async function loadProfile() {
  cardLoading.value = true
  cardError.value = ''
  try {
    profile.value = await getProfile()
  } catch (e) {
    // 401 已由 http 拦截器统一跳转登录页，此处仅处理非登录态错误
    if (e?.code !== 'COMMON_401' && e?.response?.status !== 401) {
      cardError.value = e?.message || '个人资料加载失败'
    }
  } finally {
    cardLoading.value = false
  }
}

function onProfileUpdated(next) {
  if (next) profile.value = next
}

async function onAvatarChange(next) {
  if (next) profile.value = next
}

/** 改密成功后强制重新登录：服务端已吊销全部令牌，本地凭证必然失效 */
async function onPasswordChanged() {
  try {
    await ElMessageBox.confirm(
      '密码修改成功，出于安全考虑需要重新登录。是否立即前往登录页？',
      '请重新登录',
      { confirmButtonText: '立即登录', cancelButtonText: '稍后再说', type: 'success' }
    )
  } catch (e) {
    // 用户选择稍后：给出提示后清理本地会话并跳转
  }
  ElMessage.warning('当前登录状态已失效，请重新登录')
  clearAuth()
  router.replace('/login')
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page {
  min-height: 100%;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.page-card {
  margin-bottom: 0;
}
</style>