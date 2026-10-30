<template>
  <div>
    <h2 class="page-title">实时推送</h2>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="toolbar-filters">
        <span class="filter-label">在线会话与手动推送运维：只读查看在线连接，必要时向在线用户广播或定向推送实时消息</span>
      </div>
      <div class="toolbar-actions">
        <div class="action-buttons">
          <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      :title="error"
      show-icon
      style="margin-bottom: 12px"
    />

    <!-- 实时网关未启用提示：此时在线数为 0、推送全部触达 0（与离线用户语义一致）。 -->
    <el-alert
      v-if="!error && onlineCount === 0 && !loading"
      type="info"
      :closable="false"
      title="当前未查询到在线会话（实时网关 realtime.gateway.enabled=false 或未启用时属正常状态；前端进入页面时已通过 REST 拉取全量待办/未读）。"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-card class="page-card section" header="在线总览">
      <div class="stat-grid">
        <div class="stat-card">
          <div class="label">在线会话总数</div>
          <div class="value">{{ onlineCount }}</div>
        </div>
        <div class="stat-card">
          <div class="label">在线用户数</div>
          <div class="value">{{ sessions.length }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="page-card section" header="在线会话列表">
      <el-table :data="sessions" v-loading="loading" empty-text="暂无在线会话" style="width: 100%">
        <el-table-column prop="userId" label="用户 ID" min-width="160" />
        <el-table-column prop="sessionCount" label="活跃会话数" min-width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.sessionCount }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="page-card section" header="广播推送（向所有在线会话）">
      <el-form :model="broadcastForm" label-width="96px" @submit.prevent>
        <el-form-item label="使用模板">
          <el-switch v-model="broadcastForm.useTemplate" @change="onUseTemplateChange(broadcastForm)" />
          <span class="hint">开启后从通知模板中心（WEBSOCKET 渠道）选取模板，自动渲染标题/正文，无需手填负载</span>
        </el-form-item>

        <template v-if="broadcastForm.useTemplate">
          <el-form-item label="模板" required>
            <el-select
              v-model="broadcastForm.template"
              value-key="templateCode"
              filterable
              placeholder="选择 WEBSOCKET 模板"
              style="width: 360px"
              @change="onTemplateChange(broadcastForm)"
            >
              <el-option
                v-for="t in templates"
                :key="t.templateCode"
                :value="t"
                :label="`${t.templateName}（${t.templateCode}）`"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-for="v in broadcastForm.template?.variableNames || []" :key="v" :label="`变量 ${v}`">
            <el-input
              v-model="broadcastForm.variables[v]"
              :placeholder="`请输入 ${v}`"
              style="max-width: 520px"
              @input="refreshPreview(broadcastForm)"
            />
          </el-form-item>
          <el-form-item label="渲染预览" v-if="broadcastForm.preview">
            <pre class="tpl-preview">{{ broadcastForm.preview }}</pre>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="推送类型" required>
            <el-select v-model="broadcastForm.type" placeholder="请选择推送类型" style="width: 260px">
              <el-option v-for="t in pushTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务负载">
            <el-input
              v-model="broadcastForm.payload"
              type="textarea"
              :rows="3"
              placeholder="可选：纯文本或 JSON；留空则仅推送类型通知"
              style="max-width: 520px"
            />
          </el-form-item>
        </template>

        <el-form-item>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="broadcastLoading"
            @click="doBroadcast"
          >广播推送</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="page-card section" header="定向推送（向指定用户）">
      <el-form :model="userForm" label-width="96px" @submit.prevent>
        <el-form-item label="目标用户 ID" required>
          <el-input
            v-model="userForm.userIdsText"
            type="textarea"
            :rows="2"
            placeholder="多个 ID 以逗号 / 空格 / 换行分隔，例如：1001, 1002, 1003"
            style="max-width: 520px"
          />
        </el-form-item>

        <el-form-item label="使用模板">
          <el-switch v-model="userForm.useTemplate" @change="onUseTemplateChange(userForm)" />
          <span class="hint">开启后从通知模板中心（WEBSOCKET 渠道）选取模板，自动渲染标题/正文，无需手填负载</span>
        </el-form-item>

        <template v-if="userForm.useTemplate">
          <el-form-item label="模板" required>
            <el-select
              v-model="userForm.template"
              value-key="templateCode"
              filterable
              placeholder="选择 WEBSOCKET 模板"
              style="width: 360px"
              @change="onTemplateChange(userForm)"
            >
              <el-option
                v-for="t in templates"
                :key="t.templateCode"
                :value="t"
                :label="`${t.templateName}（${t.templateCode}）`"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-for="v in userForm.template?.variableNames || []" :key="v" :label="`变量 ${v}`">
            <el-input
              v-model="userForm.variables[v]"
              :placeholder="`请输入 ${v}`"
              style="max-width: 520px"
              @input="refreshPreview(userForm)"
            />
          </el-form-item>
          <el-form-item label="渲染预览" v-if="userForm.preview">
            <pre class="tpl-preview">{{ userForm.preview }}</pre>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="推送类型" required>
            <el-select v-model="userForm.type" placeholder="请选择推送类型" style="width: 260px">
              <el-option v-for="t in pushTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务负载">
            <el-input
              v-model="userForm.payload"
              type="textarea"
              :rows="3"
              placeholder="可选：纯文本或 JSON；留空则仅推送类型通知"
              style="max-width: 520px"
            />
          </el-form-item>
        </template>

        <el-form-item>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="userLoading"
            @click="doPushToUser"
          >定向推送</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="pushResult"
      :type="pushResult.ok ? 'success' : 'error'"
      :closable="false"
      :title="pushResult.title"
      show-icon
      style="margin-top: 12px"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Promotion } from '@element-plus/icons-vue'
import {
  listRealtimeSessions,
  realtimeOnlineCount,
  realtimeBroadcast,
  realtimePushToUser
} from '@/api/realtime'
import { pageNotificationTemplates, previewNotificationTemplate } from '@/api/notificationTemplate'

const pushTypes = [
  { value: 'UNREAD_MESSAGE', label: '未读消息 (UNREAD_MESSAGE)' },
  { value: 'TODO_REMINDER', label: '待办提醒 (TODO_REMINDER)' },
  { value: 'NOTIFICATION', label: '系统通知 (NOTIFICATION)' },
  { value: 'PONG', label: '心跳探测 (PONG)' }
]

const loading = ref(false)
const error = ref('')
const onlineCount = ref(0)
const sessions = ref([])

// M30 模板化：通知模板中心（WEBSOCKET 渠道）下拉数据源
const templates = ref([])

const broadcastForm = reactive({
  type: 'NOTIFICATION',
  payload: '',
  useTemplate: false,
  template: null,
  variables: {},
  preview: ''
})
const broadcastLoading = ref(false)

const userForm = reactive({
  userIdsText: '',
  type: 'NOTIFICATION',
  payload: '',
  useTemplate: false,
  template: null,
  variables: {},
  preview: ''
})
const userLoading = ref(false)

const pushResult = ref(null)

/** 业务负载：尝试解析为 JSON 对象，失败则作为纯文本透传；空串返回 null（仅推类型通知）。 */
function buildPayload(text) {
  const t = (text || '').trim()
  if (!t) return null
  try {
    return JSON.parse(t)
  } catch (e) {
    return t
  }
}

/** 解析目标用户 ID 文本：按非数字切分，过滤空串与非法值。 */
function parseUserIds(text) {
  const ids = (text || '')
    .split(/[^\d]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .map((s) => Number(s))
    .filter((n) => Number.isInteger(n) && n > 0)
  return Array.from(new Set(ids))
}

/** 拉取 WEBSOCKET 渠道、启用中的通知模板，供广播/定向推送复用。 */
async function loadTemplates() {
  try {
    const data = await pageNotificationTemplates({ channel: 'WEBSOCKET', enabled: true, size: 100 })
    templates.value = Array.isArray(data?.records) ? data.records : []
  } catch (e) {
    // 无权限或模板模块未启用时静默降级为空列表，不影响原始推送路径
    templates.value = []
  }
}

/** 关闭模板开关时清理模板/变量/预览状态。 */
function onUseTemplateChange(form) {
  if (!form.useTemplate) {
    form.template = null
    form.variables = {}
    form.preview = ''
  }
}

/** 切换模板：重置变量与预览，并立即刷新一次预览。 */
function onTemplateChange(form) {
  form.variables = {}
  form.preview = ''
  if (form.template) refreshPreview(form)
}

/** 渲染预览：调用模板中心预览接口，用当前变量映射得到渲染后正文。 */
async function refreshPreview(form) {
  if (!form.template) {
    form.preview = ''
    return
  }
  try {
    const res = await previewNotificationTemplate(form.template.id, form.variables || {})
    form.preview = res?.rendered ?? ''
  } catch (e) {
    form.preview = ''
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [count, list] = await Promise.all([realtimeOnlineCount(), listRealtimeSessions()])
    onlineCount.value = typeof count === 'number' ? count : 0
    sessions.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function doBroadcast() {
  if (!broadcastForm.useTemplate && !broadcastForm.type) {
    ElMessage.warning('请选择推送类型')
    return
  }
  broadcastLoading.value = true
  pushResult.value = null
  try {
    let delivered
    if (broadcastForm.useTemplate) {
      if (!broadcastForm.template) {
        ElMessage.warning('请选择模板')
        broadcastLoading.value = false
        return
      }
      const res = await realtimeBroadcast(
        broadcastForm.type || 'NOTIFICATION',
        null,
        broadcastForm.template.templateCode,
        broadcastForm.variables
      )
      delivered = res?.delivered ?? 0
    } else {
      const res = await realtimeBroadcast(broadcastForm.type, buildPayload(broadcastForm.payload), null, null)
      delivered = res?.delivered ?? 0
    }
    pushResult.value = { ok: true, title: `广播已下发，成功触达 ${delivered} 个在线会话` }
    ElMessage.success(`广播成功触达 ${delivered} 个会话`)
  } catch (e) {
    // 响应拦截器已弹出错误提示（如 403 无权限），此处仅记录内联状态，避免重复提示
    pushResult.value = { ok: false, title: e?.message || '广播推送失败' }
  } finally {
    broadcastLoading.value = false
  }
}

async function doPushToUser() {
  if (!userForm.useTemplate && !userForm.type) {
    ElMessage.warning('请选择推送类型')
    return
  }
  const ids = parseUserIds(userForm.userIdsText)
  if (ids.length === 0) {
    ElMessage.warning('请填写至少一个有效的目标用户 ID')
    return
  }
  userLoading.value = true
  pushResult.value = null
  try {
    let delivered
    if (userForm.useTemplate) {
      if (!userForm.template) {
        ElMessage.warning('请选择模板')
        userLoading.value = false
        return
      }
      const res = await realtimePushToUser(
        ids,
        userForm.type || 'NOTIFICATION',
        null,
        userForm.template.templateCode,
        userForm.variables
      )
      delivered = res?.delivered ?? 0
    } else {
      const res = await realtimePushToUser(ids, userForm.type, buildPayload(userForm.payload), null, null)
      delivered = res?.delivered ?? 0
    }
    pushResult.value = {
      ok: true,
      title: `定向推送已下发，成功触达 ${delivered} / ${ids.length} 个用户`
    }
    ElMessage.success(`定向推送成功触达 ${delivered} 个用户`)
  } catch (e) {
    pushResult.value = { ok: false, title: e?.message || '定向推送失败' }
  } finally {
    userLoading.value = false
  }
}

onMounted(() => {
  load()
  loadTemplates()
})
</script>

<style scoped>
.hint {
  color: #909399;
  font-size: 12px;
  margin-left: 10px;
}
.tpl-preview {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  margin: 0;
  max-width: 520px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: #303133;
}
</style>
