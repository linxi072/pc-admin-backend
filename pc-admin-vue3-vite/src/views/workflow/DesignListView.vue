<template>
  <div>
    <h2 class="page-title">{{ t('workflow.workflowDesign') }}</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input v-model="keyword" :placeholder="t('workflow.searchByProcessNameCode')" style="width: 220px" clearable />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('workflow.newWorkflow') }}</el-button>
          </div>
        </div>
      </div>
      <el-table :data="filteredRows" v-loading="loading" border stripe>
        <el-table-column prop="processKey" :label="t('workflow.processCode')" width="140" />
        <el-table-column prop="processName" :label="t('workflow.processName')" min-width="160" />
        <el-table-column prop="version" :label="t('common.version')" width="70" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? t('common.published') : t('common.draft') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" :label="t('workflow.publishTime')" width="170" />
        <el-table-column :label="t('common.operation')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openDesigner(row)">{{ t('workflow.design') }}</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link type="success" :icon="Upload"
              :loading="row._publishing" @click="doPublish(row)">{{ t('common.publish') }}</el-button>
            <el-button
              v-else link type="warning" :icon="Download"
              :loading="row._publishing" @click="doUnpublish(row)">{{ t('workflow.unpublish') }}</el-button>
            <el-tooltip :content="row.status === 'PUBLISHED' ? '' : t('workflow.publishToViewBpmn')" :disabled="row.status === 'PUBLISHED'">
              <span>
                <el-button link type="primary" :icon="View" :disabled="row.status !== 'PUBLISHED'" @click="previewBpmn(row)">BPMN</el-button>
              </span>
            </el-tooltip>
            <el-button link type="danger" :icon="Delete" @click="doDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredRows.length === 0" :description="t('workflow.noWorkflowDef')" />
    </el-card>

    <!-- 新建草稿 -->
    <el-dialog v-model="createVisible" :title="t('workflow.newWorkflowDraft')" width="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item :label="t('workflow.processCode')" required>
          <el-input v-model="createForm.processKey" :placeholder="t('workflow.codeRuleHint')" />
        </el-form-item>
        <el-form-item :label="t('workflow.processName')" required>
          <el-input v-model="createForm.processName" :placeholder="t('workflow.exPurchase')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- BPMN 预览 -->
    <el-dialog v-model="bpmnVisible" :title="t('workflow.bpmnPreview')" width="760px">
      <pre class="bpmn-pre">{{ bpmnXml }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Upload, Download, View, Delete } from '@element-plus/icons-vue'
import {
  listDefinitions, createDefinition, deleteDefinition,
  publishDefinition, unpublishDefinition, getDefinitionBpmn
} from '@/api/workflow'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const keyword = ref('')

const filteredRows = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter(r =>
    (r.processName || '').toLowerCase().includes(k) ||
    (r.processKey || '').toLowerCase().includes(k))
})

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ processKey: '', processName: '', description: '' })

const bpmnVisible = ref(false)
const bpmnXml = ref('')

async function load() {
  loading.value = true
  try {
    rows.value = await listDefinitions()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.processKey = ''
  createForm.processName = ''
  createForm.description = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(createForm.processKey)) {
    ElMessage.error(t('workflow.processCodeRule'))
    return
  }
  creating.value = true
  try {
    const def = await createDefinition({ ...createForm })
    ElMessage.success(t('workflow.draftCreated'))
    createVisible.value = false
    router.push(`/workflow/designer/${def.id}`)
  } finally {
    creating.value = false
  }
}

function openDesigner(row) {
  router.push(`/workflow/designer/${row.id}`)
}

async function doPublish(row) {
  row._publishing = true
  try {
    await publishDefinition(row.id)
    ElMessage.success(t('workflow.publishedUsesLatest'))
    load()
  } finally {
    row._publishing = false
  }
}

async function doUnpublish(row) {
  row._publishing = true
  try {
    await unpublishDefinition(row.id)
    ElMessage.success(t('workflow.unpublished'))
    load()
  } finally {
    row._publishing = false
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`t('workflow.confirmDeleteProcess', { name: row.processName })`, t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  await deleteDefinition(row.id)
  ElMessage.success(t('common.deleted'))
  load()
}

async function previewBpmn(row) {
  try {
    const res = await getDefinitionBpmn(row.id)
    bpmnXml.value = res.bpmnXml || t('workflow.noneParen')
    bpmnVisible.value = true
  } catch (e) {
    ElMessage.error(t('workflow.loadFailedX'))
  }
}

onMounted(load)
</script>

<style scoped>
.bpmn-pre {
  max-height: 60vh;
  overflow: auto;
  background: #0d1117;
  color: #c9d1d9;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
