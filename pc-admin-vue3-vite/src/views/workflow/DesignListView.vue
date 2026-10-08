<template>
  <div>
    <h2 class="page-title">工作流设计</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建工作流</el-button>
        <span class="spacer" />
        <el-input v-model="keyword" placeholder="按流程名称/编码搜索" style="width: 220px" clearable />
      </div>
      <el-table :data="filteredRows" v-loading="loading" border stripe>
        <el-table-column prop="processKey" label="流程编码" width="140" />
        <el-table-column prop="processName" label="流程名称" min-width="160" />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openDesigner(row)">设计</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link type="success" :icon="Upload"
              :loading="row._publishing" @click="doPublish(row)">发布</el-button>
            <el-button
              v-else link type="warning" :icon="Download"
              :loading="row._publishing" @click="doUnpublish(row)">取消发布</el-button>
            <el-button link type="primary" :icon="View" @click="previewBpmn(row)">BPMN</el-button>
            <el-button link type="danger" :icon="Delete" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredRows.length === 0" description="暂无工作流定义，点击「新建工作流」开始设计" />
    </el-card>

    <!-- 新建草稿 -->
    <el-dialog v-model="createVisible" title="新建工作流草稿" width="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="流程编码" required>
          <el-input v-model="createForm.processKey" placeholder="字母开头，仅含字母/数字/下划线，如 purchase" />
        </el-form-item>
        <el-form-item label="流程名称" required>
          <el-input v-model="createForm.processName" placeholder="如 采购审批" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- BPMN 预览 -->
    <el-dialog v-model="bpmnVisible" title="BPMN 预览" width="760px">
      <pre class="bpmn-pre">{{ bpmnXml }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
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
    ElMessage.error('流程编码需以字母开头且仅含字母/数字/下划线')
    return
  }
  creating.value = true
  try {
    const def = await createDefinition({ ...createForm })
    ElMessage.success('草稿已创建')
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
    ElMessage.success('已发布，新发起的实例将使用最新版本')
    load()
  } finally {
    row._publishing = false
  }
}

async function doUnpublish(row) {
  row._publishing = true
  try {
    await unpublishDefinition(row.id)
    ElMessage.success('已取消发布')
    load()
  } finally {
    row._publishing = false
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.processName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteDefinition(row.id)
  ElMessage.success('已删除')
  load()
}

async function previewBpmn(row) {
  const res = await getDefinitionBpmn(row.id)
  bpmnXml.value = res.bpmnXml || '(暂无)'
  bpmnVisible.value = true
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
