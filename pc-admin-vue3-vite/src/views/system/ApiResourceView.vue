<template>
  <div>
    <h2 class="page-title">{{ t('route.apiResource') }}</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.resourceNameSys') }}</span>
            <el-input
              v-model="filters.resourceName"
              :placeholder="t('system.resourceNameSys')"
              clearable
              style="width: 180px"
              @clear="page = 1"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.permissionCodeSys') }}</span>
            <el-input
              v-model="filters.permissionCode"
              :placeholder="t('system.permissionCodeSys')"
              clearable
              style="width: 180px"
              @clear="page = 1"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('system.methodSys') }}</span>
            <el-select
              v-model="filters.httpMethod"
              :placeholder="t('system.methodSys')"
              clearable
              style="width: 120px"
              @change="page = 1"
            >
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button :icon="Refresh" :loading="scanning" @click="onScan">{{ t('system.scanCompare') }}</el-button>
          </div>
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.registerApiResource') }}</el-button>
          </div>
        </div>
      </div>

      <el-table :data="pagedRows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
        <el-table-column prop="resourceName" :label="t('system.resourceNameSys')" min-width="160" />
        <el-table-column prop="permissionCode" :label="t('system.permissionCodeSys')" min-width="170" show-overflow-tooltip />
        <el-table-column :label="t('system.methodSys')" width="100">
          <template #default="{ row }">
            <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pathPattern" :label="t('system.pathPattern')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="authMode" :label="t('system.authMode')" width="110" />
        <el-table-column :label="t('system.riskLevel')" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'MEDIUM' ? 'warning' : 'info'"
              size="small"
            >
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('system.confirmDeleteApi')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? t('system.editApiResource') : t('system.registerApiResource')" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.resourceNameSys')" prop="resourceName">
          <el-input v-model="form.resourceName" :placeholder="t('system.phUserPaginatedQuery')" />
        </el-form-item>
        <el-form-item :label="t('system.permissionCodeSys')" prop="permissionCode">
          <el-input v-model="form.permissionCode" placeholder="system:user:read" />
        </el-form-item>
        <el-form-item :label="t('system.httpMethod')" prop="httpMethod">
          <el-select v-model="form.httpMethod" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.pathPattern')" prop="pathPattern">
          <el-input v-model="form.pathPattern" placeholder="/api/system/users/**" />
        </el-form-item>
        <el-form-item :label="t('system.authMode')">
          <el-select v-model="form.authMode" style="width: 100%">
            <el-option label="JWT" value="JWT" />
            <el-option label="REQUIRED" value="REQUIRED" />
            <el-option label="ANONYMOUS" value="ANONYMOUS" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.riskLevel')">
          <el-select v-model="form.riskLevel" style="width: 100%">
            <el-option :label="t('system.riskLow')" value="LOW" />
            <el-option :label="t('system.riskMedium')" value="MEDIUM" />
            <el-option :label="t('system.riskHigh')" value="HIGH" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="scanVisible" :title="t('system.scanCompareResult')" size="70%">
      <div class="scan-summary">
        <div class="scan-item">
          <span class="label">{{ t('system.scanTotal') }}</span>
          <span class="value">{{ scanResult.total }}</span>
        </div>
        <div class="scan-item">
          <span class="label">{{ t('system.toAdd') }}</span>
          <span class="value warn">{{ scanResult.newResources.length }}</span>
        </div>
        <div class="scan-item">
          <span class="label">{{ t('system.toUpdate') }}</span>
          <span class="value warn">{{ scanResult.changedResources.length }}</span>
        </div>
        <div class="scan-item">
          <span class="label">{{ t('system.noChangeSys') }}</span>
          <span class="value">{{ scanResult.unchangedCount }}</span>
        </div>
        <div class="scan-item">
          <span class="label">{{ t('system.staleInDb') }}</span>
          <span class="value muted">{{ scanResult.orphanedResources.length }}</span>
        </div>
      </div>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        :title="t('system.staleNotDeleted')"
        :description="t('system.orphanApiHint')"
        style="margin-bottom: 12px"
      />

      <el-tabs v-model="scanTab">
        <el-tab-pane :label="t('system.toAddCount', { n: scanResult.newResources.length })" name="new">
          <el-table :data="scanResult.newResources" border stripe max-height="460">
            <el-table-column :label="t('system.methodSys')" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" :label="t('system.pathPattern')" min-width="230" show-overflow-tooltip />
            <el-table-column prop="permissionCode" :label="t('system.permissionCodeSys')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="resourceName" :label="t('system.resourceNameSys')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="controllerMethod" :label="t('system.sourceLocation')" min-width="190" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="t('system.toUpdateCount', { n: scanResult.changedResources.length })" name="changed">
          <el-table :data="scanResult.changedResources" border stripe max-height="460">
            <el-table-column :label="t('system.methodSys')" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" :label="t('system.pathPattern')" min-width="230" show-overflow-tooltip />
            <el-table-column prop="permissionCode" :label="t('system.permissionCodeSys')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="resourceName" :label="t('system.resourceNameSys')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="controllerMethod" :label="t('system.sourceLocation')" min-width="190" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="t('system.staleCount', { n: scanResult.orphanedResources.length })" name="orphan">
          <el-table :data="scanResult.orphanedResources" border stripe max-height="460">
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="resourceName" :label="t('system.resourceNameSys')" min-width="160" />
            <el-table-column prop="permissionCode" :label="t('system.permissionCodeSys')" min-width="170" show-overflow-tooltip />
            <el-table-column :label="t('system.methodSys')" width="100">
              <template #default="{ row }">
                <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" :label="t('system.pathPattern')" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="scanVisible = false">{{ t('common.close') }}</el-button>
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
import { t } from '@/i18n'
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
import { validateOrWarn } from '@/utils/validate'

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
  resourceName: [{ required: true, message: t('system.needResourceName'), trigger: 'blur' }],
  permissionCode: [{ required: true, message: t('system.needPermissionCode'), trigger: 'blur' }],
  httpMethod: [{ required: true, message: t('system.selectMethod'), trigger: 'change' }],
  pathPattern: [{ required: true, message: t('system.needPathPattern'), trigger: 'blur' }]
}

// 后端一次性返回全量列表（非分页），前端做客户端筛选
const filtered = computed(() => {
  const rn = filters.resourceName.trim().toLowerCase()
  const pc = filters.permissionCode.trim().toLowerCase()
  const hm = filters.httpMethod
  return all.value.filter(
    (a) =>
      // 历史脏数据可能缺 resourceName/permissionCode，空值兜底避免整表 computed 抛错白板
      (!rn || (a.resourceName || '').toLowerCase().includes(rn)) &&
      (!pc || (a.permissionCode || '').toLowerCase().includes(pc)) &&
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
  if (!(await validateOrWarn(formRef.value))) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateApiResource(editingId.value, { ...form })
      ElMessage.success(t('system.updated'))
    } else {
      await createApiResource({ ...form })
      ElMessage.success(t('system.registered'))
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
    ElMessage.success(t('common.deleted'))
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
      ElMessage.success(t('system.apiUpToDate'))
    }
  } finally {
    scanning.value = false
  }
}

/** 执行同步写库。无变更时按钮禁用，这里再兜一层确认。 */
async function onSync() {
  if (pendingCount.value === 0) {
    ElMessage.info(t('system.noApiToSync'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('system.apiSyncConfirm', { add: scanResult.newResources.length, update: scanResult.changedResources.length }),
      t('system.confirmSync'),
      { type: 'warning', confirmButtonText: t('system.syncSys'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  syncing.value = true
  try {
    const r = await syncApiResources()
    ElMessage.success(
      t('system.apiSyncDone', { inserted: r.insertedCount, updated: r.updatedCount, unchanged: r.unchangedCount })
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
