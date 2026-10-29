<template>
  <div>
    <h2 class="page-title">表单定义管理</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <span class="filter-label">共 {{ definitions.length }} 个表单定义</span>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">新建表单</el-button>
            <el-button :icon="RefreshLeft" @click="loadDefinitions">刷新</el-button>
          </div>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="definitions"
        border
        size="small"
        empty-text="暂无表单定义"
      >
        <el-table-column prop="formCode" label="表单编码" width="180" />
        <el-table-column prop="formName" label="表单名称" min-width="160" />
        <el-table-column label="绑定目标" width="150">
          <template #default="{ row }">
            <el-tag :type="targetTagType(row.targetType)" size="small">
              {{ targetLabel(row.targetType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="hint">
        表单以 JSON Schema 描述（version + fields[]），前端零依赖渲染；字段类型白名单
        INPUT / TEXTAREA / NUMBER / SELECT / RADIO / SWITCH / DATE。
        保存时后端按 schema 结构校验（版本 / 字段键 / 类型 / 选项 / 长度），校验失败整体拒绝。
      </div>
    </el-card>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑表单定义' : '新建表单定义'"
      width="1080px"
      top="5vh"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-row :gutter="16">
        <!-- 左：基本信息 + Schema 编辑器 -->
        <el-col :span="13">
          <el-form :model="form" label-width="92px" :disabled="saving">
            <el-form-item label="表单编码" required>
              <el-input v-model="form.formCode" placeholder="如 contact-feedback（驼峰/连字符）" :disabled="isEdit" />
            </el-form-item>
            <el-form-item label="表单名称" required>
              <el-input v-model="form.formName" placeholder="如 联系反馈" />
            </el-form-item>
            <el-form-item label="绑定目标">
              <el-select v-model="form.targetType" style="width: 100%">
                <el-option label="无（仅定义）" value="NONE" />
                <el-option label="业务表" value="BUSINESS_TABLE" />
                <el-option label="提交表（FORM_SUBMISSION）" value="FORM_SUBMISSION" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
            </el-form-item>
          </el-form>

          <div class="schema-section">
            <div class="schema-editor-head">
              <span class="schema-editor-tip">表单 Schema（version + fields[]）</span>
              <el-radio-group v-model="designMode" size="small">
                <el-radio-button value="design">{{ $t('formDesigner.visualDesign') }}</el-radio-button>
                <el-radio-button value="json">{{ $t('formDesigner.jsonSource') }}</el-radio-button>
              </el-radio-group>
            </div>

            <FormDesigner v-if="designMode === 'design'" v-model="form.schemaJson" />

            <template v-else>
              <div class="schema-editor-head" style="margin-top: 8px">
                <span class="schema-editor-tip">JSON Schema（version + fields[]）</span>
                <el-button link type="primary" :icon="MagicStick" @click="formatSchema">格式化</el-button>
              </div>
              <el-input
                v-model="form.schemaJson"
                type="textarea"
                :rows="14"
                class="schema-textarea"
                placeholder='{"version":1,"title":"表单标题","fields":[{"key":"name","label":"姓名","type":"INPUT","required":true}]}'
                @input="onSchemaInput"
              />
              <div v-if="schemaError" class="schema-error">
                <el-icon><WarningFilled /></el-icon>
                <span>{{ schemaError }}</span>
              </div>
              <div v-else class="schema-ok">
                <el-icon><CircleCheckFilled /></el-icon>
                <span>Schema 可解析，右侧为实时预览</span>
              </div>
            </template>
          </div>
        </el-col>

        <!-- 右：实时预览 -->
        <el-col :span="11">
          <div class="preview-title">实时预览（DynamicForm 渲染）</div>
          <div class="preview-body">
            <DynamicForm
              v-if="previewSchema"
              ref="previewFormRef"
              :schema="previewSchema"
              v-model="previewModel"
            />
            <el-empty v-else description="Schema 解析失败，无可预览内容" :image-size="80" />
          </div>
        </el-col>
      </el-row>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshLeft, Edit, Delete, MagicStick, WarningFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import {
  listFormDefinitions,
  getFormDefinition,
  createFormDefinition,
  updateFormDefinition,
  deleteFormDefinition
} from '@/api/formDefinition'
import DynamicForm from '@/components/DynamicForm.vue'
import FormDesigner from '@/components/FormDesigner.vue'

const definitions = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editingId = ref(null)
const previewFormRef = ref()
const designMode = ref('design')

const form = reactive({
  formCode: '',
  formName: '',
  targetType: 'NONE',
  enabled: true,
  remark: '',
  schemaJson: ''
})

const previewModel = ref({})
const previewSchema = computed(() => {
  if (!form.schemaJson) return null
  try {
    const obj = JSON.parse(form.schemaJson)
    return obj && Array.isArray(obj.fields) ? obj : null
  } catch {
    return null
  }
})
const schemaError = computed(() => {
  if (!form.schemaJson) return 'Schema 不能为空'
  try {
    JSON.parse(form.schemaJson)
    return ''
  } catch (e) {
    return 'JSON 语法错误：' + e.message
  }
})

function targetLabel(t) {
  return { NONE: '无', BUSINESS_TABLE: '业务表', FORM_SUBMISSION: '提交表' }[t] || t || '无'
}
function targetTagType(t) {
  return { NONE: 'info', BUSINESS_TABLE: 'warning', FORM_SUBMISSION: 'success' }[t] || 'info'
}

async function loadDefinitions() {
  loading.value = true
  try {
    definitions.value = await listFormDefinitions()
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    formCode: '',
    formName: '',
    targetType: 'NONE',
    enabled: true,
    remark: '',
    schemaJson: ''
  })
  previewModel.value = {}
  designMode.value = 'design'
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  isEdit.value = true
  editingId.value = row.id
  try {
    const def = await getFormDefinition(row.id)
    Object.assign(form, {
      formCode: def.formCode,
      formName: def.formName,
      targetType: def.targetType || 'NONE',
      enabled: def.enabled,
      remark: def.remark || '',
      schemaJson: def.schemaJson || ''
    })
    previewModel.value = {}
    dialogVisible.value = true
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

function onSchemaInput() {
  /* 仅触发 computed 重算（previewSchema / schemaError），无需额外处理 */
}

function formatSchema() {
  if (schemaError.value && schemaError.value.includes('JSON')) {
    ElMessage.warning('当前 JSON 语法有误，无法格式化')
    return
  }
  try {
    form.schemaJson = JSON.stringify(JSON.parse(form.schemaJson), null, 2)
  } catch {
    ElMessage.warning('当前 JSON 语法有误，无法格式化')
  }
}

async function onSave() {
  if (!form.formCode.trim()) {
    ElMessage.warning('请填写表单编码')
    return
  }
  if (!form.formName.trim()) {
    ElMessage.warning('请填写表单名称')
    return
  }
  if (schemaError.value) {
    ElMessage.warning('请先修正 Schema：' + schemaError.value)
    return
  }
  const payload = {
    formCode: form.formCode.trim(),
    formName: form.formName.trim(),
    schemaJson: form.schemaJson,
    targetType: form.targetType,
    enabled: form.enabled,
    remark: form.remark || ''
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateFormDefinition(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createFormDefinition(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadDefinitions()
  } catch (e) {
    /* 拦截器已统一提示（含后端校验失败信息） */
  } finally {
    saving.value = false
  }
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`确认删除表单「${row.formName}（${row.formCode}）」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteFormDefinition(row.id)
    ElMessage.success('删除成功')
    await loadDefinitions()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

function onDialogClosed() {
  resetForm()
  isEdit.value = false
  editingId.value = null
}

onMounted(loadDefinitions)
</script>

<style scoped>
.schema-editor-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-bottom: 4px;
}
.schema-editor-tip {
  font-size: 12px;
  color: #909399;
}
.schema-textarea :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.schema-error {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: #f56c6c;
  font-size: 12px;
}
.schema-ok {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: #67c23a;
  font-size: 12px;
}
.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.preview-body {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  min-height: 320px;
  background: #fafafa;
}
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
.hint code {
  background: #f4f4f5;
  padding: 1px 4px;
  border-radius: 4px;
  color: #606266;
}
</style>
