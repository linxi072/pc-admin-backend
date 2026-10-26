<template>
  <div>
    <h2 class="page-title">低代码表单</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">表单</span>
            <el-select
              v-model="selectedCode"
              placeholder="请选择表单"
              style="width: 240px"
              :loading="listLoading"
              @change="onFormChange"
            >
              <el-option
                v-for="f in forms"
                :key="f.formCode"
                :label="`${f.formName}（${f.formCode}）`"
                :value="f.formCode"
                :disabled="!f.enabled"
              />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button :icon="RefreshLeft" @click="loadForms">刷新列表</el-button>
          </div>
        </div>
      </div>

      <el-alert
        v-if="!selectedCode"
        type="info"
        :closable="false"
        show-icon
        title="请选择表单查看 Schema 渲染效果"
      />

      <template v-else>
        <div class="pilot-body">
          <div class="pilot-form">
            <h3 class="pilot-title">{{ schemaTitle }}</h3>
            <DynamicForm
              v-if="schema"
              ref="dynamicFormRef"
              :schema="schema"
              v-model="formData"
            />
            <div class="pilot-actions">
              <el-button type="primary" :loading="validating" @click="onValidate">校验并查看数据</el-button>
              <el-button type="success" :loading="submitting" :disabled="!schema || submitting" @click="onSubmit">提交</el-button>
              <el-button @click="onReset">重置</el-button>
            </div>
          </div>
          <div class="pilot-json">
            <div class="pilot-json-title">实时模型（model）</div>
            <pre class="pilot-json-body">{{ formattedModel }}</pre>
            <div class="pilot-json-title">Schema 原文</div>
            <pre class="pilot-json-body">{{ schemaText }}</pre>
          </div>
        </div>

        <el-divider />

        <div class="submission-block">
          <h3 class="pilot-title">
            提交记录
            <el-button text type="primary" :loading="submissionsLoading" @click="loadSubmissions">刷新</el-button>
          </h3>
          <el-table
            v-loading="submissionsLoading"
            :data="submissions"
            border
            size="small"
            empty-text="暂无提交记录"
          >
            <el-table-column prop="submittedBy" label="提交人" width="140" />
            <el-table-column label="提交内容">
              <template #default="{ row }">
                <pre class="submission-json">{{ pretty(row.dataJson) }}</pre>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="提交时间" width="190" />
          </el-table>
          <el-pagination
            v-if="submissionTotal > submissionPageSize"
            layout="prev, pager, next"
            :total="submissionTotal"
            :page-size="submissionPageSize"
            :current-page="submissionPage"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="onSubmissionPageChange"
          />
        </div>
      </template>

      <div class="hint">
        本页为 <b>M22 正式立项</b>：Schema 契约 + 零依赖渲染 + 提交落库（绑定目标 FORM_SUBMISSION）。
        字段类型白名单 INPUT / TEXTAREA / NUMBER / SELECT / RADIO / SWITCH / DATE；
        提交值经后端按 schema 校验（必填 / 类型 / 长度 / 选项白名单 / 未知字段拒绝）后写入独立
        <code>sys_form_submission</code> 表，不侵入业务表、不做运行时 DDL。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import { listForms, getFormSchema, submitForm, listSubmissions } from '@/api/formDefinition'
import DynamicForm from '@/components/DynamicForm.vue'

const forms = ref([])
const listLoading = ref(false)
const selectedCode = ref('')
const schema = ref(null)
const schemaText = ref('')
const formData = ref({})
const dynamicFormRef = ref()
const validating = ref(false)
const submitting = ref(false)

const submissions = ref([])
const submissionsLoading = ref(false)
const submissionTotal = ref(0)
const submissionPage = ref(1)
const submissionPageSize = ref(10)

const schemaTitle = computed(() => schema.value?.title || schema.value?.formName || selectedCode.value)
const formattedModel = computed(() => JSON.stringify(formData.value, null, 2))

async function loadForms() {
  listLoading.value = true
  try {
    forms.value = await listForms()
    // 默认选中首个启用中表单（如试点表单 contact-feedback）
    if (!selectedCode.value) {
      const first = forms.value.find((f) => f.enabled)
      if (first) await onFormChange(first.formCode)
    }
  } finally {
    listLoading.value = false
  }
}

async function onFormChange(code) {
  selectedCode.value = code
  schema.value = null
  schemaText.value = ''
  formData.value = {}
  submissionPage.value = 1
  try {
    const view = await getFormSchema(code)
    schema.value = JSON.parse(view.schemaJson)
    schemaText.value = JSON.stringify(schema.value, null, 2)
    await loadSubmissions()
  } catch (e) {
    /* 拦截器已统一提示；停用/不存在的编码后端返回 NOT_FOUND */
  }
}

async function onValidate() {
  if (!dynamicFormRef.value) return
  validating.value = true
  try {
    const ok = await dynamicFormRef.value.validate()
    if (ok) {
      ElMessage.success('校验通过，数据见右侧实时模型')
    } else {
      ElMessage.warning('存在校验未通过的字段')
    }
  } finally {
    validating.value = false
  }
}

async function onSubmit() {
  if (!dynamicFormRef.value || !selectedCode.value) return
  const ok = await dynamicFormRef.value.validate()
  if (!ok) {
    ElMessage.warning('存在校验未通过的字段')
    return
  }
  submitting.value = true
  try {
    await submitForm(selectedCode.value, formData.value)
    ElMessage.success('提交成功，记录已保存')
    submissionPage.value = 1
    await loadSubmissions()
  } catch (e) {
    /* 拦截器已统一提示（含后端校验失败信息） */
  } finally {
    submitting.value = false
  }
}

async function loadSubmissions() {
  if (!selectedCode.value) return
  submissionsLoading.value = true
  try {
    const res = await listSubmissions(selectedCode.value, {
      page: submissionPage.value,
      size: submissionPageSize.value
    })
    submissions.value = res.records || []
    submissionTotal.value = res.total || 0
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    submissionsLoading.value = false
  }
}

function onSubmissionPageChange(p) {
  submissionPage.value = p
  loadSubmissions()
}

function onReset() {
  dynamicFormRef.value?.resetFields()
}

function pretty(json) {
  if (!json) return ''
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

onMounted(loadForms)
</script>

<style scoped>
.pilot-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 8px;
}
.pilot-form {
  flex: 1 1 55%;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
}
.pilot-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.pilot-actions {
  margin-top: 8px;
}
.pilot-json {
  flex: 1 1 45%;
  min-width: 0;
}
.pilot-json-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}
.pilot-json-title + .pilot-json-body {
  margin-bottom: 16px;
}
.pilot-json-body {
  background: #f4f4f5;
  border-radius: 6px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #303133;
  margin: 0;
  max-height: 320px;
  overflow: auto;
}
.submission-block {
  margin-top: 4px;
}
.submission-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #303133;
  max-height: 200px;
  overflow: auto;
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
