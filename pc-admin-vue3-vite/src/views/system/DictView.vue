<template>
  <div>
    <h2 class="page-title">字典管理</h2>

    <el-row :gutter="16">
      <!-- 字典类型 -->
      <el-col :span="9">
        <el-card class="page-card" v-loading="typeLoading">
          <div class="toolbar">
            <span class="card-title">字典类型</span>
            <span class="spacer" />
            <el-button type="primary" :icon="Plus" @click="openCreateType">新增类型</el-button>
          </div>
          <el-table
            :data="types"
            highlight-current-row
            border
            stripe
            @current-change="onSelectType"
            :row-class-name="typeRowClass"
          >
            <el-table-column prop="dictCode" label="编码" min-width="130" />
            <el-table-column prop="dictName" label="名称" min-width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click.stop="openEditType(row)">编辑</el-button>
                <el-popconfirm title="确认删除该类型？" @confirm="onDeleteType(row)">
                  <template #reference>
                    <el-button link type="danger" :icon="Delete" @click.stop>删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 字典数据 -->
      <el-col :span="15">
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
            >新增数据</el-button>
          </div>
          <el-alert
            v-if="!selectedType"
            title="请先在左侧选择一个字典类型"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-table v-else :data="dataList" border stripe>
            <el-table-column prop="dictLabel" label="标签" min-width="120" />
            <el-table-column prop="dictValue" label="值" min-width="120" />
            <el-table-column prop="dictSort" label="排序" width="80" />
            <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openEditData(row)">编辑</el-button>
                <el-popconfirm title="确认删除该数据？" @confirm="onDeleteData(row)">
                  <template #reference>
                    <el-button link type="danger" :icon="Delete">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型弹窗 -->
    <el-dialog v-model="typeDialogVisible" :title="typeEditingId !== null ? '编辑类型' : '新增类型'" width="520px">
      <el-form :model="typeForm" :rules="typeRules" ref="typeFormRef" label-width="90px">
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="typeForm.dictCode" placeholder="如 sys_normal_disable" :disabled="typeEditingId !== null" />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="typeForm.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据弹窗 -->
    <el-dialog v-model="dataDialogVisible" :title="dataEditingId !== null ? '编辑数据' : '新增数据'" width="520px">
      <el-form :model="dataForm" :rules="dataRules" ref="dataFormRef" label-width="90px">
        <el-form-item label="所属类型">
          <el-input :model-value="selectedType?.dictName" disabled />
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="如 正常" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="如 0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.dictSort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dataForm.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitData">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Edit } from '@element-plus/icons-vue'
import { listDictTypes, createDictType, updateDictType, deleteDictType } from '@/api/dictType'
import { listDictData, createDictData, updateDictData, deleteDictData } from '@/api/dictData'

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
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }]
}

const dataDialogVisible = ref(false)
const dataEditingId = ref(null)
const dataFormRef = ref()
const dataForm = reactive({ dictLabel: '', dictValue: '', dictSort: 0, status: 'ACTIVE', remark: '' })
const dataRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
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
  const valid = await typeFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (typeEditingId.value != null) {
      await updateDictType(typeEditingId.value, { ...typeForm })
      ElMessage.success('更新成功')
    } else {
      await createDictType({ ...typeForm })
      ElMessage.success('创建成功')
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
    ElMessage.success('已删除')
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
  const valid = await dataFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...dataForm, dictTypeCode: selectedType.value.dictCode }
    if (dataEditingId.value != null) {
      await updateDictData(dataEditingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createDictData(payload)
      ElMessage.success('创建成功')
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
    ElMessage.success('已删除')
    loadData(selectedType.value.dictCode)
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(loadTypes)
</script>
