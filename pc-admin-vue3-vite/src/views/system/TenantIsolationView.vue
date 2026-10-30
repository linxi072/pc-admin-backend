<template>
  <div>
    <h2 class="page-title">租户隔离态势</h2>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="toolbar-filters">
        <span class="filter-label">只读运维诊断：聚合隔离「配置态 + 运行态 + 当前作用域 + 配置校验」</span>
      </div>
      <div class="toolbar-actions">
        <div class="action-buttons">
          <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      :title="error"
      show-icon
      style="margin-bottom: 12px"
    />

    <template v-if="status">
      <el-card class="page-card section" header="隔离总览">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">多租户隔离总开关</div>
            <div class="value">
              <el-tag :type="boolTag(status.isolationEnabled).type" size="small">
                {{ boolTag(status.isolationEnabled).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">强制隔离运行态 (enforce)</div>
            <div class="value">
              <el-tag :type="boolTag(status.enforceEnabled).type" size="small">
                {{ boolTag(status.enforceEnabled).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">配置快照已注入</div>
            <div class="value">
              <el-tag :type="boolTag(status.configured).type" size="small">
                {{ boolTag(status.configured).label }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="page-card section" header="配置态">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">默认租户 ID</div>
            <div class="value">{{ status.defaultTenantId }}</div>
          </div>
          <div class="stat-card">
            <div class="label">平台管理员角色码</div>
            <div class="value">{{ status.platformAdminRoleCode }}</div>
          </div>
          <div class="stat-card">
            <div class="label">租户请求头</div>
            <div class="value">{{ status.headerName }}</div>
          </div>
          <div class="stat-card">
            <div class="label">Token 声明名</div>
            <div class="value">{{ status.claimName }}</div>
          </div>
        </div>
        <div style="margin-top: 14px">
          <span class="filter-label">平台共享维表（豁免隔离）</span>
          <div style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px">
            <el-tag v-for="t in status.platformTables || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
            <span v-if="!status.platformTables || !status.platformTables.length" class="filter-label">（空）</span>
          </div>
        </div>
      </el-card>

      <el-card class="page-card section" header="当前执行作用域">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">作用域已设置</div>
            <div class="value">
              <el-tag :type="boolTag(status.scopeSet).type" size="small">
                {{ boolTag(status.scopeSet).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">当前租户 ID</div>
            <div class="value">{{ status.currentTenantId != null ? status.currentTenantId : '（未设置）' }}</div>
          </div>
          <div class="stat-card">
            <div class="label">平台管理员作用域</div>
            <div class="value">
              <el-tag :type="boolTag(status.currentPlatformAdmin).type" size="small">
                {{ boolTag(status.currentPlatformAdmin).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">跨租户旁路</div>
            <div class="value">
              <el-tag :type="boolTag(status.currentBypass).type" size="small">
                {{ boolTag(status.currentBypass).label }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="page-card section" header="配置校验">
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 10px">
          <span class="filter-label">校验状态</span>
          <el-tag :type="validationTag(status.validationStatus).type" size="small">
            {{ validationTag(status.validationStatus).label }}
          </el-tag>
          <span class="filter-label">{{ status.validationStatus }}</span>
        </div>
        <el-alert
          :type="alertType(status.validationStatus)"
          :closable="false"
          :title="status.validationMessage"
          show-icon
        />
      </el-card>

      <el-card class="page-card section" header="存量隔离自检（安全开启 enforce 预检）">
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 10px">
          <el-button :icon="Refresh" :loading="auditLoading" @click="loadAudit">重新自检</el-button>
          <span class="filter-label">扫描全部业务表 tenant_id 归属，判定可否安全开启强制隔离</span>
        </div>
        <el-alert
          v-if="audit"
          :type="audit.safeToEnable ? 'success' : 'warning'"
          :closable="false"
          :title="audit.message"
          show-icon
          style="margin-bottom: 12px"
        />
        <div v-if="audit" class="stat-grid">
          <div class="stat-card">
            <div class="label">业务表总数</div>
            <div class="value">{{ audit.totalBusinessTables }}</div>
          </div>
          <div class="stat-card">
            <div class="label">问题表数</div>
            <div class="value">
              <el-tag :type="audit.violatedTables > 0 ? 'danger' : 'success'" size="small">
                {{ audit.violatedTables }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">未归属行数</div>
            <div class="value">{{ audit.totalViolatedRows }}</div>
          </div>
        </div>
        <el-table
          v-if="audit && audit.byTable && audit.byTable.length"
          :data="audit.byTable"
          size="small"
          style="margin-top: 12px"
        >
          <el-table-column prop="tableName" label="业务表" min-width="220" />
          <el-table-column label="未归属行数" width="160">
            <template #default="{ row }">
              <el-tag v-if="row.violatedRows < 0" type="warning" size="small">检查失败</el-tag>
              <span v-else>{{ row.violatedRows }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-card v-else-if="!loading" class="page-card section">
      <el-empty description="暂无租户隔离态势数据" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getTenantIsolationStatus, auditTenantIsolation } from '@/api/tenantIsolation'

const loading = ref(false)
const error = ref('')
const status = ref(null)

const audit = ref(null)
const auditLoading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    status.value = await getTenantIsolationStatus()
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadAudit() {
  auditLoading.value = true
  try {
    audit.value = await auditTenantIsolation()
  } catch (e) {
    // 自检失败不影响配置态势展示，仅清空上一次结果
    audit.value = null
    console.error('存量隔离自检失败', e)
  } finally {
    auditLoading.value = false
  }
}

/** 布尔值 → 标签（开启/关闭）。 */
function boolTag(v) {
  return v ? { type: 'success', label: '开启' } : { type: 'info', label: '关闭' }
}

/** 校验状态 → 标签（OK/INVALID/DISABLED）。 */
function validationTag(s) {
  if (s === 'OK') return { type: 'success', label: '配置自洽' }
  if (s === 'INVALID') return { type: 'danger', label: '配置非法' }
  return { type: 'info', label: '未开启校验' }
}

/** 校验状态 → el-alert 类型。 */
function alertType(s) {
  if (s === 'OK') return 'success'
  if (s === 'INVALID') return 'error'
  return 'info'
}

onMounted(() => {
  load()
  loadAudit()
})
</script>
