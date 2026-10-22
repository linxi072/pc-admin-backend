<template>
  <div class="panel">
    <h3 class="panel-title">通知订阅</h3>
    <p class="panel-desc">
      按渠道与消息类型控制是否接收通知：未单独设置的格子默认开通；
      渠道总开关（「通知与隐私」）关闭时，该渠道全部类型不再接收。
    </p>

    <!-- 加载态 -->
    <el-skeleton v-if="loading && !loaded" :rows="5" animated />

    <!-- 错误态：允许重试，不阻断其他 Tab -->
    <el-alert
      v-else-if="error"
      type="warning"
      :closable="false"
      show-icon
      class="load-error"
    >
      <template #title>订阅设置加载失败：{{ error }}</template>
      <template #default>
        <el-button size="small" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <template v-else>
      <el-table :data="rows" border size="small" class="matrix">
        <el-table-column label="渠道" width="170">
          <template #default="{ row }">
            <div class="channel-cell">
              <span class="channel-name">{{ CHANNEL_LABELS[row.channel] || row.channel }}</span>
              <el-tag v-if="!row.channelEnabled" type="info" size="small" effect="plain">
                {{ row.channel === 'EMAIL' && !profile?.emailBound ? '需先绑定邮箱' : row.channel === 'SMS' && !profile?.mobileBound ? '需先绑定手机号' : '总开关已关闭' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-for="t in MSG_TYPES"
          :key="t"
          :label="MSG_TYPE_LABELS[t]"
          min-width="120"
          align="center"
        >
          <template #default="{ row }">
            <div class="cell">
              <el-switch
                :model-value="cellOn(row, t)"
                :disabled="!row.channelEnabled"
                @change="(val) => onCellChange(row, t, val)"
              />
              <el-button
                v-if="row.states[t] !== 'DEFAULT'"
                class="cell-reset"
                link
                type="primary"
                size="small"
                @click="onResetCell(row, t)"
              >
                默认
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :disabled="rowAllDefault(row)"
              @click="onResetRow(row)"
            >
              恢复默认
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <p class="matrix-hint">
        「默认」= 恢复该格子的未设置状态（默认开通）；如需开启邮件/短信渠道，请先在「通知与隐私」绑定邮箱/手机号并打开对应总开关。
        全部定向接收者退订某类型通知时，系统会记一条「已跳过」通知记录，不会报错。
      </p>

      <div class="actions">
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="onSave">
          保存订阅
        </el-button>
        <el-button :disabled="!dirty" @click="reset">还原</el-button>
        <span v-if="!dirty && loaded" class="saved-tip">已保存</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyNotificationPreference, updateMyNotificationPreference } from '@/api/notificationPreference'

const props = defineProps({
  profile: { type: Object, default: null }
})

/** 矩阵列序与后端 MSG_TYPES 白名单一致（NOTICE/ALERT/SYSTEM/CC）。 */
const MSG_TYPES = ['NOTICE', 'ALERT', 'SYSTEM', 'CC']
const MSG_TYPE_LABELS = {
  NOTICE: '一般通知',
  ALERT: '告警',
  SYSTEM: '系统消息',
  CC: '抄送'
}
/** 渠道展示名（与通知记录页 CHANNEL_META 同口径）。 */
const CHANNEL_LABELS = {
  IN_APP: '站内信',
  EMAIL: '邮件',
  SMS: '短信',
  WEBSOCKET: '实时推送'
}

const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const error = ref('')

/** 行模型：{ channel, channelEnabled, states: { NOTICE: 'DEFAULT' | 'ON' | 'OFF' } } */
const rows = ref([])
const snapshot = ref('')

const dirty = computed(() => loaded.value && snapshot.value !== JSON.stringify(rows.value))

/** 格子开关展示值：总开关关闭时强制关（置灰），否则仅显式 OFF 关闭。 */
function cellOn(row, t) {
  return row.channelEnabled && row.states[t] !== 'OFF'
}

function rowAllDefault(row) {
  return MSG_TYPES.every((t) => row.states[t] === 'DEFAULT')
}

function onCellChange(row, t, val) {
  row.states[t] = val ? 'ON' : 'OFF'
}

/** 单格恢复默认：下次保存以 enabled=null 物理删行，回到「无行默认开通」。 */
function onResetCell(row, t) {
  row.states[t] = 'DEFAULT'
}

function onResetRow(row) {
  MSG_TYPES.forEach((t) => {
    row.states[t] = 'DEFAULT'
  })
}

/**
 * 把后端矩阵翻转为行模型。source 语义：
 * PREFERENCE = 显式设置过（取行值）；DEFAULT = 无行默认开通；
 * CHANNEL = 总开关关闭强制关——保留用户原设置值，仅由 channelEnabled 置灰展示，
 * 总开关恢复后格子按原值生效，不丢设置。
 */
function applyMatrix(matrix) {
  const channels = (matrix && matrix.channels) || []
  rows.value = channels.map((ch) => ({
    channel: ch.channel,
    channelEnabled: ch.channelEnabled !== false,
    states: Object.fromEntries(MSG_TYPES.map((t) => {
      const cell = (ch.cells || []).find((c) => c.msgType === t)
      if (!cell || cell.source !== 'PREFERENCE') return [t, 'DEFAULT']
      return [t, cell.enabled ? 'ON' : 'OFF']
    }))
  }))
  snapshot.value = JSON.stringify(rows.value)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    applyMatrix(await getMyNotificationPreference())
    loaded.value = true
  } catch (e) {
    // 401 已由 http 拦截器统一跳转登录页，此处仅处理非登录态错误
    if (e?.code !== 'COMMON_401' && e?.response?.status !== 401) {
      error.value = e?.message || '订阅设置加载失败'
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  applyMatrix(JSON.parse(snapshot.value))
}

async function onSave() {
  const before = JSON.parse(snapshot.value)
  const beforeMap = new Map(before.map((r) => [r.channel, r.states]))
  // 局部更新：仅提交发生变化的格子，未提交的保持原值
  const items = []
  for (const row of rows.value) {
    const old = beforeMap.get(row.channel) || {}
    for (const t of MSG_TYPES) {
      if (row.states[t] === old[t]) continue
      items.push({
        channel: row.channel,
        msgType: t,
        // 三态：DEFAULT → null（删行恢复默认）；ON → 1；OFF → 0
        enabled: row.states[t] === 'DEFAULT' ? null : row.states[t] === 'ON' ? 1 : 0
      })
    }
  }
  if (!items.length) return
  saving.value = true
  try {
    // 后端返回更新后的完整矩阵，直接刷新快照，无需再次回查
    applyMatrix(await updateMyNotificationPreference(items))
    ElMessage.success('订阅设置已保存')
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
.matrix {
  width: 100%;
}
.channel-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.channel-name {
  font-weight: 500;
}
.cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.cell-reset {
  font-size: 12px;
}
.matrix-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.saved-tip {
  font-size: 12px;
  color: #67c23a;
}
</style>
