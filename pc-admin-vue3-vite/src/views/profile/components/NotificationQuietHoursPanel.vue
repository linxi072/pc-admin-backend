<template>
  <div class="panel">
    <h3 class="panel-title">免打扰时段</h3>
    <p class="panel-desc">
      设置在指定时间段内不接收通知（按服务器本地时间生效）：免打扰是时间维总闸，
      先于「通知订阅」的渠道/类型偏好生效；广播（角色/机构维度）与 Webhook 不过滤。
      结束时间早于开始时间表示跨午夜免打扰（如 22:00–08:00）。
    </p>

    <!-- 加载态 -->
    <el-skeleton v-if="loading && !loaded" :rows="4" animated />

    <!-- 错误态：允许重试，不阻断其他 Tab -->
    <el-alert
      v-else-if="error"
      type="warning"
      :closable="false"
      show-icon
      class="load-error"
    >
      <template #title>免打扰设置加载失败：{{ error }}</template>
      <template #default>
        <el-button size="small" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <template v-else>
      <el-form label-width="120px" class="form" :model="form">
        <el-form-item label="启用免打扰">
          <el-switch v-model="form.enabled" />
          <span v-if="form.quietNow" class="now-tag now-tag-on">当前处于免打扰中</span>
          <span v-else-if="loaded" class="now-tag">当前不在免打扰时段</span>
        </el-form-item>

        <el-form-item label="免打扰开始">
          <el-time-picker
            v-model="form.startTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="选择开始时间"
            :clearable="false"
          />
        </el-form-item>

        <el-form-item label="免打扰结束">
          <el-time-picker
            v-model="form.endTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="选择结束时间"
            :clearable="false"
          />
          <span v-if="crossMidnight" class="cross-tip">跨午夜免打扰（如 22:00–08:00）</span>
        </el-form-item>

        <el-form-item label="告警穿透">
          <el-switch v-model="form.alertBreakthrough" />
          <span class="item-tip">开启后，告警（ALERT）消息在免打扰时段内照常投递</span>
        </el-form-item>
      </el-form>

      <p class="form-hint">
        被免打扰抑制的通知不会报错：系统会记一条「已跳过」（SKIPPED）通知记录，
        可在「系统管理 → 通知记录」中按「已跳过」筛选查看。
      </p>

      <div class="actions">
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="onSave">
          保存设置
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
import { getMyQuietHours, updateMyQuietHours } from '@/api/notificationQuietHours'

const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const error = ref('')

/** 表单模型：与后端视图字段一一对应（quietNow 仅展示，不提交）。 */
const form = ref({
  enabled: false,
  startTime: '22:00',
  endTime: '08:00',
  alertBreakthrough: true,
  quietNow: false
})
const snapshot = ref('')

const dirty = computed(() => loaded.value && snapshot.value !== JSON.stringify(form.value))

/** 跨午夜窗口：结束时间早于开始时间（HH:mm 字符串可直接字典序比较）。 */
const crossMidnight = computed(() =>
  form.value.startTime && form.value.endTime && form.value.endTime < form.value.startTime
)

function applyView(view) {
  form.value = {
    enabled: view?.enabled === true,
    startTime: view?.startTime || '22:00',
    endTime: view?.endTime || '08:00',
    alertBreakthrough: view?.alertBreakthrough !== false,
    quietNow: view?.quietNow === true
  }
  snapshot.value = JSON.stringify(form.value)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    applyView(await getMyQuietHours())
    loaded.value = true
  } catch (e) {
    // 401 已由 http 拦截器统一跳转登录页，此处仅处理非登录态错误
    if (e?.code !== 'COMMON_401' && e?.response?.status !== 401) {
      error.value = e?.message || '免打扰设置加载失败'
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  applyView(JSON.parse(snapshot.value))
}

async function onSave() {
  // 与服务端同口径的前置校验：起止相同即退化窗口（半开区间下语义歧义）
  if (form.value.startTime === form.value.endTime) {
    ElMessage.warning('免打扰开始时间与结束时间不能相同')
    return
  }
  saving.value = true
  try {
    // 全量快照提交；后端返回更新后的配置视图，直接刷新快照，无需再次回查
    applyView(await updateMyQuietHours({
      enabled: form.value.enabled,
      startTime: form.value.startTime,
      endTime: form.value.endTime,
      alertBreakthrough: form.value.alertBreakthrough
    }))
    ElMessage.success('免打扰设置已保存')
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
.form {
  max-width: 520px;
}
.now-tag {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.now-tag-on {
  color: #e6a23c;
  font-weight: 500;
}
.cross-tip,
.item-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.form-hint {
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
