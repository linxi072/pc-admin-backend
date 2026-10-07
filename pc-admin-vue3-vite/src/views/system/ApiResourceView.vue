<template>
  <div>
    <h2 class="page-title">接口资源管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <el-input
          v-model="filters.resourceName"
          placeholder="资源名称"
          clearable
          style="width: 180px"
          @clear="page = 1"
        />
        <el-input
          v-model="filters.permissionCode"
          placeholder="权限标识"
          clearable
          style="width: 180px"
          @clear="page = 1"
        />
        <el-select
          v-model="filters.httpMethod"
          placeholder="方法"
          clearable
          style="width: 120px"
          @change="page = 1"
        >
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <span class="spacer" />
        <el-button :icon="Refresh" :loading="scanning" @click="onScan">扫描对比</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">注册接口资源</el-button>
      </div>

      <el-table :data="pagedRows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="resourceName" label="资源名称" min-width="160" />
        <el-table-column prop="permissionCode" label="权限标识" min-width="170" show-overflow-tooltip />
        <el-table-column label="方法" width="100">
          <template #default="{ row }">
            <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pathPattern" label="路径模式" min-width="220" show-overflow-tooltip />
        <el-table-column prop="authMode" label="鉴权模式" width="110" />
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'MEDIUM' ? 'warning' : 'info'"
              size="small"
            >
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该接口资源？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="filtered.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑接口资源' : '注册接口资源'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="资源名称" prop="resourceName">
          <el-input v-model="form.resourceName" placeholder="如 用户-分页查询" />
        </el-form-item>
        <el-form-item label="权限标识" prop="permissionCode">
          <el-input v-model="form.permissionCode" placeholder="system:user:read" />
        </el-form-item>
        <el-form-item label="HTTP 方法" prop="httpMethod">
          <el-select v-model="form.httpMethod" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径模式" prop="pathPattern">
          <el-input v-model="form.pathPattern" placeholder="/api/system/users/**" />
        </el-form-item>
        <el-form-item label="鉴权模式">
          <el-select v-model="form.authMode" style="width: 100%">
            <el-option label="JWT" value="JWT" />
            <el-option label="REQUIRED" value="REQUIRED" />
            <el-option label="ANONYMOUS" value="ANONYMOUS" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.riskLevel" style="width: 100%">
            <el-option label="低 (LOW)" value="LOW" />
            <el-option label="中 (MEDIUM)" value="MEDIUM" />
            <el-option label="高 (HIGH)" value="HIGH" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="scanVisible" title="扫描对比结果" size="70%">
      <div class="scan-summary">
        <div class="scan-item">
          <span class="label">扫描接口总数</span>
          <span class="value">{{ scanResult.total }}</span>
        </div>
        <div class="scan-item">
          <span class="label">待新增</span>
          <span class="value warn">{{ scanResult.newResources.length }}</span>
        </div>
        <div class="scan-item">
          <span class="label">待更新</span>
          <span class="value warn">{{ scanResult.changedResources.length }}</span>
        </div>
        <div class="scan-item">
          <span class="label">无变化</span>
          <span class="value">{{ scanResult.unchangedCount }}</span>
        </div>
        <div class="scan-item">
          <span class="label">库中失效</span>
          <span class="value muted">{{ scanResult.orphanedResources.length }}</span>
        </div>
      </div>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="失效记录不会自动删除"
        description="库中存在但代码里已无对应接口的记录，仅供人工确认。删除前请先到「角色管理」确认没有角色引用它，否则会造成授权悬空。"
        style="margin-bottom: 12px"
      />

      <el-tabs v-model="scanTab">
        <el-tab-pane :label="`待新增 (${scanResult.newResources.length})`" name="new">
          <el-table :data="scanResult.newResources" border stripe max-height="460">
            <el-table-column label="方法" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" label="路径模式" min-width="230" show-overflow-tooltip />
            <el-table-column prop="permissionCode" label="权限标识" min-width="180" show-overflow-tooltip />
            <el-table-column prop="resourceName" label="资源名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="controllerMethod" label="源码位置" min-width="190" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`待更新 (${scanResult.changedResources.length})`" name="changed">
          <el-table :data="scanResult.changedResources" border stripe max-height="460">
            <el-table-column label="方法" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" label="路径模式" min-width="230" show-overflow-tooltip />
            <el-table-column prop="permissionCode" label="权限标识" min-width="180" show-overflow-tooltip />
            <el-table-column prop="resourceName" label="资源名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="controllerMethod" label="源码位置" min-width="190" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`库中失效 (${scanResult.orphanedResources.length})`" name="orphan">
          <el-table :data="scanResult.orphanedResources" border stripe max-height="460">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="resourceName" label="资源名称" min-width="160" />
            <el-table-column prop="permissionCode" label="权限标识" min-width="170" show-overflow-tooltip />
            <el-table-column label="方法" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" label="路径模式" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="scanVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="syncing"
          :disabled="pendingCount === 0"
          @click="onSync"
        >
          同步 {{ pendingCount }} 条到库
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Edit, Refresh } from '@element-plus/icons-vue'
import {
  listApiResources,
  createApiResource,
  updateApiResource,
  deleteApiResource,
  scanApiResources,
  syncApiResources
} from '@/api/apiResource'

const loading = ref(false)
const saving = ref(false)
const all = ref([])
const filters = reactive({ resourceName: '', permissionCode: '', httpMethod: '' })
const page = ref(1)
const size = ref(10)
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

// ---- 扫描对比 ----
const scanning = ref(false)
const syncing = ref(false)
const scanVisible = ref(false)
const scanTab = ref('new')
const scanResult = reactive({
  newResources: [],
  changedResources: [],
  orphanedResources: [],
  unchangedCount: 0,
  total: 0
})
const pendingCount = computed(
  () => scanResult.newResources.length + scanResult.changedResources.length
)

const form = reactive({
  resourceName: '',
  permissionCode: '',
  httpMethod: 'GET',
  pathPattern: '',
  authMode: 'JWT',
  riskLevel: 'LOW'
})
const rules = {
  resourceName: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  permissionCode: [{ required: true, message: '请输入权限标识', trigger: 'blur' }],
  httpMethod: [{ required: true, message: '请选择方法', trigger: 'change' }],
  pathPattern: [{ required: true, message: '请输入路径模式', trigger: 'blur' }]
}

// 后端一次性返回全量列表（非分页），前端做客户端筛选
const filtered = computed(() => {
  const rn = filters.resourceName.trim().toLowerCase()
  const pc = filters.permissionCode.trim().toLowerCase()
  const hm = filters.httpMethod
  return all.value.filter(
    (a) =>
      (!rn || a.resourceName.toLowerCase().includes(rn)) &&
      (!pc || a.permissionCode.toLowerCase().includes(pc)) &&
      (!hm || a.httpMethod === hm)
  )
})
const pagedRows = computed(() => {
  const start = (page.value - 1) * size.value
  return filtered.value.slice(start, start + size.value)
})

function methodType(m) {
  return m === 'GET' ? 'success' : m === 'POST' ? 'warning' : m === 'PUT' ? 'primary' : 'danger'
}
function riskLabel(r) {
  return r === 'HIGH' ? '高' : r === 'MEDIUM' ? '中' : '低'
}

async function load() {
  loading.value = true
  try {
    all.value = await listApiResources()
    page.value = 1
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    resourceName: '',
    permissionCode: '',
    httpMethod: 'GET',
    pathPattern: '',
    authMode: 'JWT',
    riskLevel: 'LOW'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    resourceName: row.resourceName,
    permissionCode: row.permissionCode,
    httpMethod: row.httpMethod,
    pathPattern: row.pathPattern,
    authMode: row.authMode || 'JWT',
    riskLevel: row.riskLevel || 'LOW'
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateApiResource(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createApiResource({ ...form })
      ElMessage.success('注册成功')
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
    await deleteApiResource(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

/** 扫描对比：只读，不写库。 */
async function onScan() {
  scanning.value = true
  try {
    const data = await scanApiResources()
    // 防御性赋值：后端字段缺失时退化为空数组，避免模板渲染报错
    Object.assign(scanResult, {
      newResources: data.newResources || [],
      changedResources: data.changedResources || [],
      orphanedResources: data.orphanedResources || [],
      unchangedCount: data.unchangedCount || 0,
      total: data.total || 0
    })
    scanTab.value = scanResult.newResources.length ? 'new' : 'changed'
    scanVisible.value = true
    if (pendingCount.value === 0) {
      ElMessage.success('接口资源已是最新，无需同步')
    }
  } finally {
    scanning.value = false
  }
}

/** 执行同步写库。无变更时按钮禁用，这里再兜一层确认。 */
async function onSync() {
  if (pendingCount.value === 0) {
    ElMessage.info('没有需要同步的接口')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将新增 ${scanResult.newResources.length} 条、更新 ${scanResult.changedResources.length} 条接口资源。更新会覆盖权限标识与资源名称（状态与风险等级保留）。是否继续？`,
      '确认同步',
      { type: 'warning', confirmButtonText: '同步', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  syncing.value = true
  try {
    const r = await syncApiResources()
    ElMessage.success(
      `同步完成：新增 ${r.insertedCount} 条，更新 ${r.updatedCount} 条，无变化 ${r.unchangedCount} 条`
    )
    scanVisible.value = false
    load()
  } finally {
    syncing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.scan-summary {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.scan-item {
  flex: 1;
  min-width: 120px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.scan-item .label {
  font-size: 12px;
  color: #909399;
}
.scan-item .value {
  font-size: 20px;
  font-weight: 600;
}
.scan-item .value.warn {
  color: #e6a23c;
}
.scan-item .value.muted {
  color: #909399;
}
</style>
