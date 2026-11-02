<template>
  <div class="panel">
    <div class="panel-head">
      <div>
        <h3 class="panel-title">{{ t('profile.loginDevice') }}</h3>
        <p class="panel-desc">
          展示当前账号的活跃会话。发现陌生设备可立即下线；下线后该设备需重新登录。
        </p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
        <el-button
          type="danger"
          plain
          :icon="SwitchButton"
          :disabled="otherCount === 0"
          :loading="loggingOut"
          @click="onLogoutOthers"
        >{{ t('profile.logoutOthers') }}<template v-if="otherCount > 0">（{{ otherCount }}）</template>
        </el-button>
      </div>
    </div>

    <!-- 加载态 -->
    <el-skeleton v-if="loading && !devices.length" :rows="3" animated />

    <!-- 错误态 -->
    <el-result
      v-else-if="error"
      icon="error"
      :title="t('profile.deviceListLoadFailed')"
      :sub-title="error"
    >
      <template #extra>
        <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
      </template>
    </el-result>

    <!-- 空态 -->
    <el-empty
      v-else-if="!devices.length"
      :description="t('profile.noLoginDevice')"
    />

    <el-table v-else :data="devices" v-loading="loading" border stripe>
      <el-table-column :label="t('profile.device')" min-width="180">
        <template #default="{ row }">
          <div class="device-cell">
            <el-icon class="device-icon"><Monitor /></el-icon>
            <div class="device-text">
              <div class="device-name">
                {{ row.deviceName || t('profile.unknownDevice') }}
                <el-tag v-if="row.current" type="success" size="small" effect="dark">{{ t('common.current') }}</el-tag>
              </div>
              <div class="device-ua">{{ row.userAgent || '-' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" :label="t('profile.ipAddress')" width="130">
        <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('profile.lastUsed')" width="160">
        <template #default="{ row }">{{ formatTime(row.lastUsedAt || row.issuedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('profile.loginTime')" width="160">
        <template #default="{ row }">{{ formatTime(row.issuedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            text
            type="danger"
            :disabled="row.current"
            :loading="revoking === row.sessionId"
            @click="onRevoke(row)"
          >
            {{ row.current ? t('profile.currentDevice') : t('profile.offline') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { listDevices, logoutOtherDevices, revokeDevice } from '@/api/profile'

const props = defineProps({
  // profile 仅用于在资料尚未加载完成时避免重复请求，不参与设备渲染
  profile: { type: Object, default: null }
})
void props

const devices = ref([])
const loading = ref(false)
const error = ref('')
const revoking = ref('')
const loggingOut = ref(false)

const otherCount = computed(() => devices.value.filter((d) => !d.current).length)

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await listDevices()
    devices.value = Array.isArray(list) ? list : []
  } catch (e) {
    if (e?.code !== 'COMMON_401' && e?.response?.status !== 401) {
      error.value = e?.message || t('profile.deviceListLoadFailed')
    }
    devices.value = []
  } finally {
    loading.value = false
  }
}

async function onRevoke(row) {
  try {
    await ElMessageBox.confirm(
      t('profile.confirmOfflineDevice', { name: row.deviceName || t('profile.thisDevice') }),
      t('profile.offlineDevice'),
      { confirmButtonText: t('profile.confirmOffline'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }

  revoking.value = row.sessionId
  try {
    await revokeDevice(row.sessionId)
    ElMessage.success(t('profile.deviceOffline'))
    await load()
  } catch (e) {
    // 错误提示已统一处理
  } finally {
    revoking.value = ''
  }
}

async function onLogoutOthers() {
  try {
    await ElMessageBox.confirm(
      t('profile.confirmLogoutOthers', { count: otherCount.value }),
      t('profile.logoutOthers'),
      { confirmButtonText: t('profile.confirmLogout'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch (e) {
    return
  }

  loggingOut.value = true
  try {
    await logoutOtherDevices()
    ElMessage.success(t('profile.othersLoggedOut'))
    await load()
  } catch (e) {
    // 错误提示已统一处理
  } finally {
    loggingOut.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.panel-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 500;
}
.panel-desc {
  margin: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.device-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.device-icon {
  color: var(--brand);
  flex-shrink: 0;
}
.device-text {
  min-width: 0;
}
.device-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.device-ua {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
/* 窄屏：操作区换行，避免按钮被挤压溢出 */
@media (max-width: 768px) {
  .panel-head {
    flex-direction: column;
  }
  .device-ua {
    max-width: 180px;
  }
}
</style>
