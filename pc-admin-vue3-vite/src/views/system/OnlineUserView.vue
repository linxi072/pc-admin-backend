<template>
  <div>
    <h2 class="page-title">{{ t('route.onlineUser') }}</h2>
    <el-card class="page-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ============ Tab1：在线会话 ============ -->
        <el-tab-pane :label="t('reportDashboard.onlineSession')" name="sessions">
          <!-- 搜索筛选 -->
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">{{ t('system.account') }}</span>
                <el-input
                  v-model="query.username"
                  :placeholder="t('system.accountBlurred')"
                  clearable
                  style="width: 180px"
                  @keyup.enter="load"
                />
              </div>
              <div class="filter-group">
                <span class="filter-label">{{ t('system.loginIp') }}</span>
                <el-input
                  v-model="query.ip"
                  :placeholder="t('system.ipBlurred')"
                  clearable
                  style="width: 160px"
                  @keyup.enter="load"
                />
              </div>
              <div class="filter-group">
                <span class="filter-label">{{ t('common.status') }}</span>
                <el-select v-model="query.status" :placeholder="t('common.status')" clearable style="width: 130px">
                  <el-option :label="t('system.online')" value="ONLINE" />
                  <el-option :label="t('system.idle')" value="IDLE" />
                  <el-option :label="t('system.restricted')" value="RESTRICTED" />
                </el-select>
              </div>
              <div class="filter-group">
                <span class="filter-label">{{ t('job.autoRefresh') }}</span>
                <el-select
                  v-model="refreshInterval"
                  :placeholder="t('common.close')"
                  style="width: 110px"
                  @change="onRefreshIntervalChange"
                >
                  <el-option :label="t('common.close')" :value="0" />
                  <el-option :label="t('system.k5Sec')" :value="5000" />
                  <el-option :label="t('system.k10Sec')" :value="10000" />
                  <el-option :label="t('system.k30Sec')" :value="30000" />
                  <el-option :label="t('system.k60Sec')" :value="60000" />
                </el-select>
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button type="primary" :icon="Search" @click="load">{{ t('common.query') }}</el-button>
                <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
                <el-button :icon="Download" @click="onExportSessionsCsv">{{ t('common.exportCurrentPageCsv') }}</el-button>
              </div>
              <div class="action-buttons">
                <!-- 批量强制下线：勾选行后可用；含当前设备时确认框额外警示 -->
                <el-button
                  type="danger"
                  :icon="SwitchButton"
                  :disabled="!selection.length"
                  @click="onBatchForceLogout"
                >{{ t('system.forceOffline') }}<template v-if="selection.length">（{{ selection.length }}）</template>
                </el-button>
              </div>
            </div>
          </div>

          <el-table
            :data="rows"
            v-loading="loading"
            border
            stripe
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="42" />
            <el-table-column prop="username" :label="t('system.account')" width="140" />
            <el-table-column :label="t('system.name')" width="110">
              <template #default="{ row }">{{ row.displayName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="loginIp" :label="t('system.loginIp')" width="140">
              <template #default="{ row }">{{ row.loginIp || '-' }}</template>
            </el-table-column>
            <el-table-column :label="t('common.loginTime')" width="165">
              <template #default="{ row }">{{ formatTime(row.loginAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('system.lastActive')" width="165">
              <template #default="{ row }">{{ formatTime(row.lastActiveAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('system.sessionStatus')" width="95">
              <template #default="{ row }">
                <!-- 状态标签统一走 utils/status 映射 -->
                <el-tag :type="statusTag(row.status).type" size="small" effect="dark">
                  {{ statusTag(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.device')" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="isCurrentDevice(row)" class="current-device">{{ t('profile.currentDevice') }}<el-tag type="success" size="small" style="margin-left: 6px">{{ t('system.thisMachine') }}</el-tag>
                </span>
                <span v-else class="text-muted">{{ row.userAgent || row.deviceId || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" :icon="SwitchButton" @click="onForceLogout([row])">{{ t('system.forceOffline') }}</el-button>
                <el-button link type="warning" :icon="Lock" @click="openRestrict(row)">{{ t('system.restrictLogin') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="query.page"
              v-model:page-size="query.size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="load"
              @size-change="load"
            />
          </div>
        </el-tab-pane>

        <!-- ============ Tab2：登录限制记录（结构化操作日志） ============ -->
        <el-tab-pane :label="t('system.restrictionRecords')" name="restrictions">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">{{ t('system.account') }}</span>
                <el-input
                  v-model="rQuery.username"
                  :placeholder="t('system.accountBlurred')"
                  clearable
                  style="width: 180px"
                  @keyup.enter="loadRestrictions"
                />
              </div>
              <div class="filter-group">
                <span class="filter-label">{{ t('common.status') }}</span>
                <el-select v-model="rQuery.status" :placeholder="t('common.status')" clearable style="width: 130px">
                  <el-option :label="t('system.active')" value="ACTIVE" />
                  <el-option :label="t('system.removed')" value="RELEASED" />
                </el-select>
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button type="primary" :icon="Search" @click="loadRestrictions">{{ t('common.query') }}</el-button>
                <el-button :icon="RefreshLeft" @click="resetRestrictionQuery">{{ t('common.reset') }}</el-button>
                <el-button :icon="Download" @click="onExportRestrictionsCsv">{{ t('common.exportCurrentPageCsv') }}</el-button>
              </div>
            </div>
          </div>

          <el-table :data="rRows" v-loading="rLoading" border stripe>
            <el-table-column prop="username" :label="t('system.account')" width="140" />
            <el-table-column :label="t('system.name')" width="100">
              <template #default="{ row }">{{ row.displayName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('system.restrictionReason')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="t('system.duration')" width="90">
              <template #default="{ row }">{{ durationLabel(row.durationMinutes) }}</template>
            </el-table-column>
            <el-table-column prop="restrictedByName" :label="t('system.restrictedBy')" width="100">
              <template #default="{ row }">{{ row.restrictedByName || '-' }}</template>
            </el-table-column>
            <el-table-column :label="t('system.restrictedAt')" width="165">
              <template #default="{ row }">{{ formatTime(row.restrictedAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('workflow.dueTime')" width="165">
              <template #default="{ row }">{{ row.expireAt ? formatTime(row.expireAt) : t('system.permanent') }}</template>
            </el-table-column>
            <el-table-column :label="t('system.removalInfo')" width="150">
              <template #default="{ row }">
                <template v-if="row.status === 'RELEASED'">
                  <div>{{ formatTime(row.releasedAt) }}</div>
                  <div class="text-muted">{{ row.releaseType === 'AUTO_EXPIRED' ? t('system.autoRemovedOnExpiry') : t('system.manualRemoval') }}</div>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.status')" width="90">
              <template #default="{ row }">
                <!-- 限制记录的 ACTIVE 意为「生效中」，与全局 STATUS_TAGS 的「启用」语义不同，保留本页判断 -->
                <el-tag :type="row.status === 'ACTIVE' ? 'danger' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? t('system.active') : t('system.removed') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  link
                  type="primary"
                  @click="onRelease(row)"
                >
                  解除
                </el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="rQuery.page"
              v-model:page-size="rQuery.size"
              :total="rTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadRestrictions"
              @size-change="loadRestrictions"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ============ 设置临时限制登录 ============ -->
    <el-dialog v-model="restrictVisible" :title="t('system.setTemporaryLoginRestriction')" width="480px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          限制后该用户全部在线会话将立即强制下线，且在限制期内无法登录；到期自动解除，也可手动提前解除。
        </template>
      </el-alert>
      <el-form :model="restrictForm" :rules="restrictRules" ref="restrictFormRef" label-width="90px">
        <el-form-item :label="t('system.account')">
          <span>{{ restrictTarget.username }}</span>
        </el-form-item>
        <el-form-item :label="t('system.restrictionDuration')" prop="durationMinutes">
          <el-select v-model="restrictForm.durationMinutes" style="width: 100%" :placeholder="t('system.selectDuration')">
            <el-option :label="t('system.k30Min')" :value="30" />
            <el-option :label="t('system.k1Hour')" :value="60" />
            <el-option :label="t('system.k12Hours')" :value="720" />
            <el-option :label="t('system.k1Day')" :value="1440" />
            <el-option :label="t('system.k3Days')" :value="4320" />
            <el-option :label="t('system.k7Days')" :value="10080" />
            <el-option :label="t('system.permanentManualRemovalOnly')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.restrictionReason')" prop="reason">
          <el-input
            v-model="restrictForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="t('system.fillTheRestrictionReasonRecordedInTheOperationLogRequired')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="restrictVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitRestrict">{{ t('system.confirmRestriction') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, SwitchButton, Lock, Download } from '@element-plus/icons-vue'
import {
  pageOnlineUsers,
  pageRestrictions,
  forceLogout,
  restrictLogin,
  releaseRestriction
} from '@/api/onlineUser'
// 状态标签映射统一到 utils
import { statusTag } from '@/utils/status'
import { toCsv, downloadCsv } from '@/utils/csv'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const selection = ref([])
const query = reactive({ page: 1, size: 10, username: '', ip: '', status: '' })

const activeTab = ref('sessions')

// 限制记录 Tab
const rLoading = ref(false)
const rRows = ref([])
const rTotal = ref(0)
const rQuery = reactive({ page: 1, size: 10, username: '', status: '' })

// 自动刷新：默认关闭；轮询期间不展示全屏 loading，避免列表闪烁
const refreshInterval = ref(0)
let refreshTimer = null

// 当前设备标识（与 http.js 的 X-Device-Id 同源）：用于标记「本机」会话并在批量下线时警示。
// 用 getter 惰性读取——该 key 由 http.js 在首个请求发出时才生成，setup 阶段同步读取
// 在首访（无历史 ID）时会取到空串，导致「本机」标记与自查警示失效
const currentDeviceId = () => localStorage.getItem('pc_admin_device_id') || ''

// 限制登录弹窗
const restrictVisible = ref(false)
const restrictFormRef = ref()
const restrictTarget = reactive({ userId: null, username: '' })
const restrictForm = reactive({ durationMinutes: 60, reason: '' })
const restrictRules = {
  durationMinutes: [{ required: true, message: t('system.selectRestrictionDuration'), trigger: 'change' }],
  reason: [
    { required: true, message: t('system.fillTheRestrictionReason'), trigger: 'blur' },
    { max: 255, message: t('system.restrictionReasonMustBe255Chars'), trigger: 'blur' }
  ]
}

// ------------------------------------------------------------------
// 工具
// ------------------------------------------------------------------

/** ISO LocalDateTime → 'YYYY-MM-DD HH:mm:ss'；空值回落 '-'。 */
function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

/** 时长（分钟）→ 展示文案；0 为长期。 */
function durationLabel(minutes) {
  const map = { 30: t('system.k30Min'), 60: t('system.k1Hour'), 720: t('system.k12Hours'), 1440: t('system.k1Day'), 4320: t('system.k3Days'), 10080: t('system.k7Days'), 0: t('system.permanent') }
  return map[minutes] || t('system.xMinutes', { minutes })
}

function isCurrentDevice(row) {
  return !!currentDeviceId() && row.deviceId === currentDeviceId()
}

// ------------------------------------------------------------------
// 在线会话列表
// ------------------------------------------------------------------

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const data = await pageOnlineUsers({ ...query })
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    // 401 由 http 拦截器统一跳登录；其余错误已提示
    if (e?.response?.status !== 401) rows.value = []
  } finally {
    if (!silent) loading.value = false
  }
}

function onSelectionChange(val) {
  selection.value = val
}

function resetQuery() {
  query.username = ''
  query.ip = ''
  query.status = ''
  query.page = 1
  load()
}

// ------------------------------------------------------------------
// 强制下线（单个 / 批量，二次确认）
// ------------------------------------------------------------------

async function onForceLogout(targets) {
  const list = Array.isArray(targets) ? targets : [targets]
  const selfIncluded = list.some(isCurrentDevice)
  let msg = t('system.confirmForceOffline', { n: list.length })
  if (selfIncluded) {
    msg += t('system.deviceSessionNote')
  }
  try {
    await ElMessageBox.confirm(msg, t('system.forceOffline'), {
      confirmButtonText: t('profile.confirmOffline'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      customClass: 'online-confirm'
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    const n = await forceLogout(list.map((r) => r.id))
    ElMessage.success(t('system.forcedOfflineSessions', { n }))
    selection.value = []
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function onBatchForceLogout() {
  if (!selection.value.length) return
  onForceLogout(selection.value)
}

// ------------------------------------------------------------------
// 设置临时限制登录（二次确认后提交）
// ------------------------------------------------------------------

function openRestrict(row) {
  restrictTarget.userId = row.userId
  restrictTarget.username = row.username
  restrictForm.durationMinutes = 60
  restrictForm.reason = ''
  restrictVisible.value = true
}

async function onSubmitRestrict() {
  const valid = await restrictFormRef.value.validate().catch(() => false)
  if (!valid) return
  const duration = durationLabel(restrictForm.durationMinutes)
  try {
    await ElMessageBox.confirm(
      t('system.confirmRestrictLogin', { username: restrictTarget.username, duration, reason: restrictForm.reason }),
      t('system.confirmLoginRestriction'),
      { confirmButtonText: t('system.confirmRestriction'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }
  saving.value = true
  try {
    await restrictLogin(restrictTarget.userId, {
      durationMinutes: restrictForm.durationMinutes,
      reason: restrictForm.reason
    })
    ElMessage.success(t('system.loginRestrictionSetRelatedSessionsForceSignedOff'))
    restrictVisible.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------------
// 限制记录
// ------------------------------------------------------------------

async function loadRestrictions() {
  rLoading.value = true
  try {
    const data = await pageRestrictions({ ...rQuery })
    rRows.value = data.records
    rTotal.value = data.total
  } catch (e) {
    if (e?.response?.status !== 401) rRows.value = []
  } finally {
    rLoading.value = false
  }
}

function resetRestrictionQuery() {
  rQuery.username = ''
  rQuery.status = ''
  rQuery.page = 1
  loadRestrictions()
}

async function onRelease(row) {
  try {
    await ElMessageBox.confirm(
      t('system.confirmRemoveRestriction', { username: row.username }),
      t('system.removeRestriction'),
      { confirmButtonText: t('system.confirmRemoval'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    const n = await releaseRestriction(row.userId)
    ElMessage.success(t('system.removedRestrictions', { n }))
    await loadRestrictions()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

// ------------------------------------------------------------------
// Tab 切换与自动刷新
// ------------------------------------------------------------------

function onTabChange(name) {
  if (name === 'restrictions' && !rRows.value.length) loadRestrictions()
}

/** 导出当前 Tab（在线会话）已加载数据为 CSV。 */
const sessionColumns = [
  { label: t('system.account'), get: (r) => r.username },
  { label: t('system.name'), get: (r) => r.displayName },
  { label: t('system.loginIp'), get: (r) => r.loginIp },
  { label: t('common.loginTime'), get: (r) => r.loginAt },
  { label: t('system.lastActive'), get: (r) => r.lastActiveAt },
  { label: t('system.sessionStatus'), get: (r) => statusTag(r.status).label },
  { label: t('common.device'), get: (r) => r.userAgent || r.deviceId || '' }
]
function onExportSessionsCsv() {
  if (!rows.value.length) {
    ElMessage.warning(t('common.exportEmpty'))
    return
  }
  const headers = sessionColumns.map((c) => c.label)
  const data = rows.value.map((r) => sessionColumns.map((c) => c.get(r)))
  downloadCsv(`online-sessions-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(t('common.exportSuccess', { n: data.length }))
}

/** 导出当前 Tab（登录限制记录）已加载数据为 CSV。 */
const restrictionColumns = [
  { label: t('system.account'), get: (r) => r.username },
  { label: t('system.name'), get: (r) => r.displayName },
  { label: t('system.restrictionReason'), get: (r) => r.reason },
  { label: t('system.duration'), get: (r) => durationLabel(r.durationMinutes) },
  { label: t('system.restrictedBy'), get: (r) => r.restrictedByName },
  { label: t('system.restrictedAt'), get: (r) => r.restrictedAt },
  { label: t('workflow.dueTime'), get: (r) => r.expireAt || '' },
  { label: t('common.status'), get: (r) => (r.status === 'ACTIVE' ? t('system.active') : t('system.removed')) }
]
function onExportRestrictionsCsv() {
  if (!rRows.value.length) {
    ElMessage.warning(t('common.exportEmpty'))
    return
  }
  const headers = restrictionColumns.map((c) => c.label)
  const data = rRows.value.map((r) => restrictionColumns.map((c) => c.get(r)))
  downloadCsv(`login-restrictions-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(headers, data))
  ElMessage.success(t('common.exportSuccess', { n: data.length }))
}

function startRefresh() {
  stopRefresh()
  if (refreshInterval.value > 0) {
    refreshTimer = setInterval(() => {
      // 自动刷新静默拉取：不触发 loading 闪烁；弹窗打开时跳过，避免打断操作
      if (!restrictVisible.value) load(true)
    }, refreshInterval.value)
  }
}

function stopRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function onRefreshIntervalChange() {
  startRefresh()
}

onMounted(load)
onUnmounted(stopRefresh)
</script>

<style scoped>
.current-device {
  font-size: 13px;
}
.online-confirm {
  white-space: pre-line;
  max-width: 420px;
}
</style>
