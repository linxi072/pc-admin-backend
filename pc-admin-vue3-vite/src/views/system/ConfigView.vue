<template>
  <div>
    <h2 class="page-title">{{ t('route.config') }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="card-title">{{ t('system.config.dynamicRead') }}</span>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.config.key') }}</span>
            <el-input v-model="readKey" :placeholder="t('system.config.inputKeyHint')" clearable style="width: 260px" @keyup.enter="onRead" />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-tag v-if="readResult" type="success">
              {{ readResult.configName }} = {{ readResult.configValue }}（{{ readResult.configType }}）
            </el-tag>
            <el-tag v-else-if="readMiss" type="info">{{ t('system.config.keyNotFound') }}</el-tag>
          </div>
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onRead">{{ t('system.common.read') }}</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="page-card">
      <div class="toolbar">
        <span class="card-title">{{ t('system.config.listTitle') }}</span>
        <span class="spacer" />
        <el-button :icon="Refresh" :loading="refreshing" @click="onRefresh">{{ t('system.config.refreshCache') }}</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.config.addTitle') }}</el-button>
      </div>

      <el-collapse v-if="groups.length" class="config-groups">
        <el-collapse-item
          v-for="g in groups"
          :key="g.group"
          :name="g.group"
          :title="`${g.label}（${g.items.length}）`"
        >
          <el-table :data="g.items" v-loading="loading" border stripe>
            <el-table-column prop="configKey" :label="t('system.config.key')" min-width="160" />
            <el-table-column prop="configName" :label="t('system.config.name')" min-width="120" />
            <el-table-column prop="configValue" :label="t('system.config.value')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="t('system.common.type')" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.configType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('system.common.remark')" min-width="140" show-overflow-tooltip />
            <el-table-column :label="t('common.status')" width="90">
              <template #default="{ row }">
                <!-- 状态标签统一走 utils/status 映射 -->
                <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
                <el-popconfirm :title="t('system.config.confirmDelete')" @confirm="onDelete(row)">
                  <template #reference>
                    <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-else :description="t('system.config.empty')" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? t('system.config.editTitle') : t('system.config.addTitle')" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item :label="t('system.config.key')" prop="configKey">
          <el-input v-model="form.configKey" :placeholder="t('system.config.exampleKey')" :disabled="editingId !== null" />
        </el-form-item>
        <el-form-item :label="t('system.config.name')" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item :label="t('system.config.value')" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('system.common.type')" prop="configType">
          <el-select v-model="form.configType" style="width: 100%">
            <el-option label="STRING" value="STRING" />
            <el-option label="INT" value="INT" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
            <el-option label="JSON" value="JSON" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.common.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, Edit, Refresh } from '@element-plus/icons-vue'
import { listConfigGroups, getConfigByKey, createConfig, updateConfig, deleteConfig, refreshConfigs } from '@/api/sysConfig'
// 状态标签映射统一到 utils
import { statusTag } from '@/utils/status'

const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const groups = ref([])

const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  configKey: '',
  configName: '',
  configValue: '',
  configType: 'STRING',
  status: 'ACTIVE',
  remark: ''
})
const rules = {
  configKey: [{ required: true, message: t('system.config.pleaseEnterKey'), trigger: 'blur' }],
  configName: [{ required: true, message: t('system.config.pleaseEnterName'), trigger: 'blur' }],
  configType: [{ required: true, message: t('system.common.pleaseSelectType'), trigger: 'change' }]
}

// 动态读取演示
const readKey = ref('')
const readResult = ref(null)
const readMiss = ref(false)
async function onRead() {
  if (!readKey.value.trim()) return
  readResult.value = null
  readMiss.value = false
  try {
    const vo = await getConfigByKey(readKey.value.trim())
    if (vo) readResult.value = vo
    else readMiss.value = true
  } catch (e) {
    readMiss.value = true
  }
}

async function load() {
  loading.value = true
  try {
    groups.value = await listConfigGroups()
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    await refreshConfigs()
    ElMessage.success(t('system.config.cacheRefreshed'))
    await load()
  } finally {
    refreshing.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    configKey: '',
    configName: '',
    configValue: '',
    configType: 'STRING',
    status: 'ACTIVE',
    remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    configKey: row.configKey,
    configName: row.configName,
    configValue: row.configValue,
    configType: row.configType || 'STRING',
    status: row.status || 'ACTIVE',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateConfig(editingId.value, { ...form })
      ElMessage.success(t('system.common.updateSuccess'))
    } else {
      await createConfig({ ...form })
      ElMessage.success(t('system.common.createSuccess'))
    }
    dialogVisible.value = false
    editingId.value = null
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await deleteConfig(row.id)
    ElMessage.success(t('system.department.deleted'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>
