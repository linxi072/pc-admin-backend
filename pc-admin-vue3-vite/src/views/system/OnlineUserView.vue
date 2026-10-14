<template>
  <div>
    <h2 class="page-title">在线用户管理</h2>
    <el-card class="page-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ============ Tab1：在线会话 ============ -->
        <el-tab-pane label="在线会话" name="sessions">
          <!-- 搜索筛选 -->
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">账号</span>
                <el-input
                  v-model="query.username"
                  placeholder="账号（模糊）"
                  clearable
                  style="width: 180px"
                  @keyup.enter="load"
                />
              </div>
              <div class="filter-group">
                <span class="filter-label">登录IP</span>
                <el-input
                  v-model="query.ip"
                  placeholder="IP（模糊）"
                  clearable
                  style="width: 160px"
                  @keyup.enter="load"
                />
              </div>
              <div class="filter-group">
                <span class="filter-label">状态</span>
                <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
                  <el-option label="在线" value="ONLINE" />
                  <el-option label="空闲" value="IDLE" />
                  <el-option label="已限制" value="RESTRICTED" />
                </el-select>
              </div>
              <div class="filter-group">
                <span class="filter-label">自动刷新</span>
                <el-select
                  v-model="refreshInterval"
                  placeholder="关闭"
                  style="width: 110px"
                  @change="onRefreshIntervalChange"
                >
                  <el-option label="关闭" :value="0" />
                  <el-option label="5 秒" :value="5000" />
                  <el-option label="10 秒" :value="10000" />
                  <el-option label="30 秒" :value="30000" />
                  <el-option label="60 秒" :value="60000" />
                </el-select>
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button type="primary" :icon="Search" @click="load">查询</el-button>
                <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
              </div>
              <div class="action-buttons">
                <!-- 批量强制下线：勾选行后可用；含当前设备时确认框额外警示 -->
                <el-button
                  type="danger"
                  :icon="SwitchButton"
                  :disabled="!selection.length"
                  @click="onBatchForceLogout"
                >
                  强制下线<template v-if="selection.length">（{{ selection.length }}）</template>
                </el-button>
              </div>
            </div>
          </div>

          <el-table
            :data="rows"
            v-loading="loading"
            border
            stripe
            height="480"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="42" />
            <el-table-column prop="username" label="账号" width="140" />
            <el-table-column label="姓名" width="110">
              <template #default="{ row }">{{ row.displayName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="loginIp" label="登录IP" width="140">
              <template #default="{ row }">{{ row.loginIp || '-' }}</template>
            </el-table-column>
            <el-table-column label="登录时间" width="165">
              <template #default="{ row }">{{ formatTime(row.loginAt) }}</template>
            </el-table-column>
            <el-table-column label="最近活跃" width="165">
              <template #default="{ row }">{{ formatTime(row.lastActiveAt) }}</template>
            </el-table-column>
            <el-table-column label="会话状态" width="95">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small" effect="dark">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="设备" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="isCurrentDevice(row)" class="current-device">
                  当前设备<el-tag type="success" size="small" style="margin-left: 6px">本机</el-tag>
                </span>
                <span v-else class="text-muted">{{ row.userAgent || row.deviceId || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" :icon="SwitchButton" @click="onForceLogout([row])">
                  强制下线
                </el-button>
                <el-button link type="warning" :icon="Lock" @click="openRestrict(row)">限制登录</el-button>
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
        <el-tab-pane label="限制记录" name="restrictions">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <span class="filter-label">账号</span>
                <el-input
                  v-model="rQuery.username"
                  placeholder="账号（模糊）"
                  clearable
                  style="width: 180px"
                  @keyup.enter="loadRestrictions"
                />
              </div>
              <div class="filter-group">
                <span class="filter-label">状态</span>
                <el-select v-model="rQuery.status" placeholder="状态" clearable style="width: 130px">
                  <el-option label="生效中" value="ACTIVE" />
                  <el-option label="已解除" value="RELEASED" />
                </el-select>
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button type="primary" :icon="Search" @click="loadRestrictions">查询</el-button>
                <el-button :icon="RefreshLeft" @click="resetRestrictionQuery">重置</el-button>
              </div>
            </div>
          </div>

          <el-table :data="rRows" v-loading="rLoading" border stripe height="480">
            <el-table-column prop="username" label="账号" width="140" />
            <el-table-column label="姓名" width="100">
              <template #default="{ row }">{{ row.displayName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="限制原因" min-width="160" show-overflow-tooltip />
            <el-table-column label="时长" width="90">
              <template #default="{ row }">{{ durationLabel(row.durationMinutes) }}</template>
            </el-table-column>
            <el-table-column prop="restrictedByName" label="限制人" width="100">
              <template #default="{ row }">{{ row.restrictedByName || '-' }}</template>
            </el-table-column>
            <el-table-column label="限制时间" width="165">
              <template #default="{ row }">{{ formatTime(row.restrictedAt) }}</template>
            </el-table-column>
            <el-table-column label="到期时间" width="165">
              <template #default="{ row }">{{ row.expireAt ? formatTime(row.expireAt) : '长期' }}</template>
            </el-table-column>
            <el-table-column label="解除信息" width="150">
              <template #default="{ row }">
                <template v-if="row.status === 'RELEASED'">
                  <div>{{ formatTime(row.releasedAt) }}</div>
                  <div class="text-muted">{{ row.releaseType === 'AUTO_EXPIRED' ? '到期自动解除' : '手动解除' }}</div>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'danger' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '生效中' : '已解除' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
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
    <el-dialog v-model="restrictVisible" title="设置临时限制登录" width="480px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          限制后该用户全部在线会话将立即强制下线，且在限制期内无法登录；到期自动解除，也可手动提前解除。
        </template>
      </el-alert>
      <el-form :model="restrictForm" :rules="restrictRules" ref="restrictFormRef" label-width="90px">
        <el-form-item label="账号">
          <span>{{ restrictTarget.username }}</span>
        </el-form-item>
        <el-form-item label="限制时长" prop="durationMinutes">
          <el-select v-model="restrictForm.durationMinutes" style="width: 100%" placeholder="选择时长">
            <el-option label="30 分钟" :value="30" />
            <el-option label="1 小时" :value="60" />
            <el-option label="12 小时" :value="720" />
            <el-option label="1 天" :value="1440" />
            <el-option label="3 天" :value="4320" />
            <el-option label="7 天" :value="10080" />
            <el-option label="长期（仅手动解除）" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="限制原因" prop="reason">
          <el-input
            v-model="restrictForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请填写限制原因（将记入操作日志，必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="restrictVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitRestrict">确认限制</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, SwitchButton, Lock } from '@element-plus/icons-vue'
import {
  pageOnlineUsers,
  pageRestrictions,
  forceLogout,
  restrictLogin,
  releaseRestriction
} from '@/api/onlineUser'

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

// 当前设备标识（与 http.js 的 X-Device-Id 同源）：用于标记「本机」会话并在批量下线时警示
const currentDeviceId = localStorage.getItem('pc_admin_device_id') || ''

// 限制登录弹窗
const restrictVisible = ref(false)
const restrictFormRef = ref()
const restrictTarget = reactive({ userId: null, username: '' })
const restrictForm = reactive({ durationMinutes: 60, reason: '' })
const restrictRules = {
  durationMinutes: [{ required: true, message: '请选择限制时长', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写限制原因', trigger: 'blur' },
    { max: 255, message: '限制原因不能超过 255 字', trigger: 'blur' }
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

function statusLabel(s) {
  return s === 'IDLE' ? '空闲' : s === 'RESTRICTED' ? '已限制' : '在线'
}

function statusTagType(s) {
  return s === 'IDLE' ? 'info' : s === 'RESTRICTED' ? 'danger' : 'success'
}

/** 时长（分钟）→ 展示文案；0 为长期。 */
function durationLabel(minutes) {
  const map = { 30: '30 分钟', 60: '1 小时', 720: '12 小时', 1440: '1 天', 4320: '3 天', 10080: '7 天', 0: '长期' }
  return map[minutes] || `${minutes} 分钟`
}

function isCurrentDevice(row) {
  return !!currentDeviceId && row.deviceId === currentDeviceId
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
  let msg = `确认强制下线所选 ${list.length} 个会话吗？相关设备上的登录状态将立即失效，需重新登录。`
  if (selfIncluded) {
    msg += '\n注意：其中包含你当前设备的会话，下线后本页面将退出到登录页。'
  }
  try {
    await ElMessageBox.confirm(msg, '强制下线', {
      confirmButtonText: '确认下线',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'online-confirm'
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    const n = await forceLogout(list.map((r) => r.id))
    ElMessage.success(`已强制下线 ${n} 个会话`)
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
      `确认限制账号「${restrictTarget.username}」登录吗？\n时长：${duration}\n原因：${restrictForm.reason}`,
      '限制登录二次确认',
      { confirmButtonText: '确认限制', cancelButtonText: '取消', type: 'warning' }
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
    ElMessage.success('已设置限制登录，相关会话已强制下线')
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
      `确认解除账号「${row.username}」的登录限制吗？解除后该账号可立即重新登录。`,
      '解除限制',
      { confirmButtonText: '确认解除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    const n = await releaseRestriction(row.userId)
    ElMessage.success(`已解除 ${n} 条限制`)
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
