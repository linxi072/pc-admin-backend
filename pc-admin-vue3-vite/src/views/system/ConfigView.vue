<template>
  <div>
    <h2 class="page-title">系统变量</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <span class="card-title">动态读取（按配置键）</span>
        <el-input v-model="readKey" placeholder="输入配置键，如 sys.title" clearable style="width: 260px" @keyup.enter="onRead" />
        <el-button type="primary" :icon="Search" @click="onRead">读取</el-button>
        <el-tag v-if="readResult" type="success" class="read-result">
          {{ readResult.configName }} = {{ readResult.configValue }}（{{ readResult.configType }}）
        </el-tag>
        <el-tag v-else-if="readMiss" type="info" class="read-result">未找到该配置键</el-tag>
      </div>
    </el-card>

    <el-card class="page-card">
      <div class="toolbar">
        <span class="card-title">参数配置列表</span>
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增配置</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="configKey" label="配置键" min-width="160" />
        <el-table-column prop="configName" label="配置名称" min-width="120" />
        <el-table-column prop="configValue" label="配置值" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.configType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该配置？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑配置' : '新增配置'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如 sys.title" :disabled="editingId !== null" />
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="类型" prop="configType">
          <el-select v-model="form.configType" style="width: 100%">
            <el-option label="STRING" value="STRING" />
            <el-option label="INT" value="INT" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
            <el-option label="JSON" value="JSON" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, Edit } from '@element-plus/icons-vue'
import { listConfigs, getConfigByKey, createConfig, updateConfig, deleteConfig } from '@/api/sysConfig'

const loading = ref(false)
const saving = ref(false)
const list = ref([])

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
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configType: [{ required: true, message: '请选择类型', trigger: 'change' }]
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
    list.value = await listConfigs()
  } finally {
    loading.value = false
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
      ElMessage.success('更新成功')
    } else {
      await createConfig({ ...form })
      ElMessage.success('创建成功')
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
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>
