<template>
  <div>
    <h2 class="page-title">{{ t('route.dict') }}</h2>

    <el-row :gutter="16">
      <!-- 字典类型：原固定 :span="9" 在 1440 宽度下只剩约 410px，
           4 列最小宽度合计 470px，列被压到极窄后中文逐字竖排（E2E F6）。
           改为响应式分栏 + 长文本省略，窄屏下单列堆叠。 -->
      <el-col :xs="24" :sm="24" :md="11" :lg="10" :xl="10">
        <el-card class="page-card" v-loading="typeLoading">
          <div class="toolbar">
            <span class="card-title">{{ t('system.dict.type') }}</span>
            <span class="spacer" />
            <el-button type="primary" :icon="Plus" @click="openCreateType">{{ t('system.dict.addType') }}</el-button>
          </div>
          <el-table
            :data="types"
            highlight-current-row
            border
            stripe
            @current-change="onSelectType"
            :row-class-name="typeRowClass"
          >
            <el-table-column prop="dictCode" :label="t('system.common.code')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="dictName" :label="t('system.common.name')" min-width="140" show-overflow-tooltip />
            <el-table-column :label="t('common.status')" width="90">
              <template #default="{ row }">
                <!-- 状态标签统一走 utils/status 映射 -->
                <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click.stop="openEditType(row)">{{ t('common.edit') }}</el-button>
                <el-popconfirm :title="t('system.dict.confirmDeleteType')" @confirm="onDeleteType(row)">
                  <template #reference>
                    <el-button link type="danger" :icon="Delete" @click.stop>{{ t('common.delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 字典数据 -->
      <el-col :xs="24" :sm="24" :md="13" :lg="14" :xl="14">
        <el-card class="page-card" v-loading="dataLoading">
          <div class="toolbar">
            <span class="card-title">
              字典数据{{ selectedType ? '：' + selectedType.dictName : '' }}
            </span>
            <span class="spacer" />
            <el-button
              type="primary"
              :icon="Plus"
              :disabled="!selectedType"
              @click="openCreateData"
            >{{ t('system.dict.addData') }}</el-button>
          </div>
          <el-alert
            v-if="!selectedType"
            :title="t('system.dict.pleaseSelectType')"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-table v-else :data="dataList" border stripe>
            <el-table-column prop="dictLabel" :label="t('system.common.label')" min-width="120" />
            <el-table-column prop="dictValue" :label="t('system.common.value')" min-width="120" />
            <el-table-column prop="dictSort" :label="t('system.common.sort')" width="80" />
            <el-table-column prop="remark" :label="t('system.common.remark')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="t('common.status')" width="90">
              <template #default="{ row }">
                <!-- 状态标签统一走 utils/status 映射 -->
                <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openEditData(row)">{{ t('common.edit') }}</el-button>
                <el-popconfirm :title="t('system.dict.confirmDeleteData')" @confirm="onDeleteData(row)">
                  <template #reference>
                    <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型弹窗 -->
    <el-dialog v-model="typeDialogVisible" :title="typeEditingId !== null ? t('system.dict.editType') : t('system.dict.addType')" width="520px">
      <el-form :model="typeForm" :rules="typeRules" ref="typeFormRef" label-width="90px">
        <el-form-item :label="t('system.dict.code')" prop="dictCode">
          <el-input v-model="typeForm.dictCode" :placeholder="t('system.dict.exampleCode')" :disabled="typeEditingId !== null" />
        </el-form-item>
        <el-form-item :label="t('system.dict.name')" prop="dictName">
          <el-input v-model="typeForm.dictName" />
        </el-form-item>
        <el-form-item :label="t('system.common.sort')">
          <el-input-number v-model="typeForm.sortNo" :min="0" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="typeForm.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.common.remark')">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitType">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据弹窗 -->
    <el-dialog v-model="dataDialogVisible" :title="dataEditingId !== null ? t('system.dict.editData') : t('system.dict.addData')" width="520px">
      <el-form :model="dataForm" :rules="dataRules" ref="dataFormRef" label-width="90px">
        <el-form-item :label="t('system.common.belongType')">
          <el-input :model-value="selectedType?.dictName" disabled />
        </el-form-item>
        <el-form-item :label="t('system.dict.label')" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" :placeholder="t('system.config.exampleValue')" />
        </el-form-item>
        <el-form-item :label="t('system.dict.value')" prop="dictValue">
          <el-input v-model="dataForm.dictValue" :placeholder="t('system.common.exampleZero')" />
        </el-form-item>
        <el-form-item :label="t('system.common.sort')">
          <el-input-number v-model="dataForm.dictSort" :min="0" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="dataForm.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.common.remark')">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitData">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Edit } from '@element-plus/icons-vue'
import { listDictTypes, createDictType, updateDictType, deleteDictType } from '@/api/dictType'
import { listDictData, createDictData, updateDictData, deleteDictData } from '@/api/dictData'
// 状态标签映射统一到 utils
import { statusTag } from '@/utils/status'
import { validateOrWarn } from '@/utils/validate'

const typeLoading = ref(false)
const dataLoading = ref(false)
const saving = ref(false)
const types = ref([])
const selectedType = ref(null)
const dataList = ref([])

const typeDialogVisible = ref(false)
const typeEditingId = ref(null)
const typeFormRef = ref()
const typeForm = reactive({ dictCode: '', dictName: '', sortNo: 0, status: 'ACTIVE', remark: '' })
const typeRules = {
  dictCode: [{ required: true, message: t('system.dict.pleaseEnterCode'), trigger: 'blur' }],
  dictName: [{ required: true, message: t('system.dict.pleaseEnterName'), trigger: 'blur' }]
}

const dataDialogVisible = ref(false)
const dataEditingId = ref(null)
const dataFormRef = ref()
const dataForm = reactive({ dictLabel: '', dictValue: '', dictSort: 0, status: 'ACTIVE', remark: '' })
const dataRules = {
  dictLabel: [{ required: true, message: t('system.dict.pleaseEnterLabel'), trigger: 'blur' }],
  dictValue: [{ required: true, message: t('system.dict.pleaseEnterValue'), trigger: 'blur' }]
}

async function loadTypes() {
  typeLoading.value = true
  try {
    types.value = await listDictTypes()
    if (selectedType.value) {
      const still = types.value.find((t) => t.dictCode === selectedType.value.dictCode)
      selectedType.value = still || null
      if (still) await loadData(still.dictCode)
      else dataList.value = []
    }
  } finally {
    typeLoading.value = false
  }
}

async function loadData(dictTypeCode) {
  if (!dictTypeCode) {
    dataList.value = []
    return
  }
  dataLoading.value = true
  try {
    dataList.value = await listDictData(dictTypeCode)
  } finally {
    dataLoading.value = false
  }
}

function onSelectType(row) {
  if (!row) return
  selectedType.value = row
  loadData(row.dictCode)
}

function typeRowClass({ row }) {
  return selectedType.value && row.dictCode === selectedType.value.dictCode ? 'current-row' : ''
}

// ---- 类型 CRUD ----
function openCreateType() {
  typeEditingId.value = null
  Object.assign(typeForm, { dictCode: '', dictName: '', sortNo: 0, status: 'ACTIVE', remark: '' })
  typeDialogVisible.value = true
}

function openEditType(row) {
  typeEditingId.value = row.id
  Object.assign(typeForm, {
    dictCode: row.dictCode,
    dictName: row.dictName,
    sortNo: row.sortNo || 0,
    status: row.status || 'ACTIVE',
    remark: row.remark || ''
  })
  typeDialogVisible.value = true
}

async function onSubmitType() {
  if (!(await validateOrWarn(typeFormRef.value))) return
  saving.value = true
  try {
    if (typeEditingId.value != null) {
      await updateDictType(typeEditingId.value, { ...typeForm })
      ElMessage.success(t('system.common.updateSuccess'))
    } else {
      await createDictType({ ...typeForm })
      ElMessage.success(t('system.common.createSuccess'))
    }
    typeDialogVisible.value = false
    typeEditingId.value = null
    loadTypes()
  } finally {
    saving.value = false
  }
}

async function onDeleteType(row) {
  try {
    await deleteDictType(row.id)
    ElMessage.success(t('system.department.deleted'))
    if (selectedType.value && selectedType.value.dictCode === row.dictCode) {
      selectedType.value = null
      dataList.value = []
    }
    loadTypes()
  } catch (e) {
    /* 拦截器已统一提示（如存在字典数据时拒绝） */
  }
}

// ---- 数据 CRUD ----
function openCreateData() {
  if (!selectedType.value) return
  dataEditingId.value = null
  Object.assign(dataForm, { dictLabel: '', dictValue: '', dictSort: 0, status: 'ACTIVE', remark: '' })
  dataDialogVisible.value = true
}

function openEditData(row) {
  dataEditingId.value = row.id
  Object.assign(dataForm, {
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    dictSort: row.dictSort || 0,
    status: row.status || 'ACTIVE',
    remark: row.remark || ''
  })
  dataDialogVisible.value = true
}

async function onSubmitData() {
  if (!(await validateOrWarn(dataFormRef.value))) return
  saving.value = true
  try {
    const payload = { ...dataForm, dictTypeCode: selectedType.value.dictCode }
    if (dataEditingId.value != null) {
      await updateDictData(dataEditingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    } else {
      await createDictData(payload)
      ElMessage.success(t('system.common.createSuccess'))
    }
    dataDialogVisible.value = false
    dataEditingId.value = null
    loadData(selectedType.value.dictCode)
  } finally {
    saving.value = false
  }
}

async function onDeleteData(row) {
  try {
    await deleteDictData(row.id)
    ElMessage.success(t('system.department.deleted'))
    loadData(selectedType.value.dictCode)
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(loadTypes)
</script>
