<template>
  <div>
    <h2 class="page-title">低代码表单试点</h2>

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
      </template>

      <div class="hint">
        本页为 <b>M22 spike</b>：仅验证「Schema 契约 + 零依赖渲染」可行性——字段类型白名单
        INPUT / TEXTAREA / NUMBER / SELECT / RADIO / SWITCH / DATE，SELECT/RADIO 须声明 options；
        <b>提交落库与绑定目标</b>（绑定既有表 vs 独立 form_submission 表）为正式立项决策点，本期不实现。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import { listForms, getFormSchema } from '@/api/formDefinition'
import DynamicForm from '@/components/DynamicForm.vue'

const forms = ref([])
const listLoading = ref(false)
const selectedCode = ref('')
const schema = ref(null)
const schemaText = ref('')
const formData = ref({})
const dynamicFormRef = ref()
const validating = ref(false)

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
  try {
    const view = await getFormSchema(code)
    schema.value = JSON.parse(view.schemaJson)
    schemaText.value = JSON.stringify(schema.value, null, 2)
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

function onReset() {
  dynamicFormRef.value?.resetFields()
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
.hint {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 8px;
  line-height: 1.6;
}
</style>
