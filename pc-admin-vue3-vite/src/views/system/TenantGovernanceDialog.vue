<template>
  <el-dialog v-model="visible" :title="t('system.tenantGovernance')" width="960px" @open="loadOverview">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.governanceDestructiveWarning')"
      class="mb-12"
    />

    <el-tabs v-model="active">
      <el-tab-pane :label="t('system.governanceDataOverview')" name="overview">
        <div v-loading="loading">
          <el-table :data="overview" border stripe default-expand-all :empty-text="t('system.empty')">
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-table :data="toPairs(row.tableCounts)" border size="small" style="margin: 8px 0">
                  <el-table-column prop="name" :label="t('system.businessTable')" min-width="240" />
                  <el-table-column prop="count" :label="t('formDesigner.rows')" width="140" align="right" />
                </el-table>
              </template>
            </el-table-column>
            <el-table-column prop="tenantName" :label="t('system.tenantName')" min-width="200" />
            <el-table-column prop="tenantId" :label="t('system.tenantId')" width="120" />
            <el-table-column prop="totalRows" :label="t('system.totalBusinessData')" width="160" align="right" />
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('system.backfill')" name="backfill">
        <el-form :model="backfillForm" label-width="160px">
          <el-form-item :label="t('system.backfillTargetTenantId')" required>
            <el-input
              v-model.number="backfillForm.targetTenantId"
              type="number"
              :placeholder="t('system.enterTargetTenantId')"
            />
          </el-form-item>
          <el-popconfirm
            :title="t('system.confirmBackfill', { id: backfillForm.targetTenantId || '?' })"
            width="320"
            @confirm="onBackfill"
          >
            <template #reference>
              <el-button type="danger" :loading="backfillLoading">{{ t('system.backfill') }}</el-button>
            </template>
          </el-popconfirm>
        </el-form>
      </el-tab-pane>

      <el-tab-pane :label="t('system.reassign')" name="reassign">
        <el-form :model="reassignForm" label-width="160px">
          <el-form-item :label="t('system.reassignFromTenantId')">
            <el-input
              v-model.number="reassignForm.fromTenantId"
              type="number"
              :placeholder="t('system.optional')"
            />
          </el-form-item>
          <el-form-item :label="t('system.reassignToTenantId')" required>
            <el-input
              v-model.number="reassignForm.toTenantId"
              type="number"
              :placeholder="t('system.enterTargetTenantId')"
            />
          </el-form-item>
          <el-form-item :label="t('system.tablesOptional')">
            <el-select
              v-model="reassignForm.tables"
              multiple
              filterable
              :placeholder="t('system.leaveEmptyForAllTables')"
              style="width: 100%"
            >
              <el-option v-for="name in tableOptions" :key="name" :label="name" :value="name" />
            </el-select>
          </el-form-item>
          <el-popconfirm
            :title="t('system.confirmReassign', { from: reassignForm.fromTenantId || '?', to: reassignForm.toTenantId || '?' })"
            width="320"
            @confirm="onReassign"
          >
            <template #reference>
              <el-button type="danger" :loading="reassignLoading">{{ t('system.reassign') }}</el-button>
            </template>
          </el-popconfirm>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTenantDataOverview, backfillTenantData, reassignTenantData } from '@/api/tenantGovernance'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const active = ref('overview')
const loading = ref(false)
const overview = ref([])

const backfillForm = reactive({ targetTenantId: null })
const backfillLoading = ref(false)
const reassignForm = reactive({ fromTenantId: null, toTenantId: null, tables: [] })
const reassignLoading = ref(false)

// 业务表名候选：从总览中各租户 tableCounts 的 key 并集推导，驱动迁移表的下拉多选。
const tableOptions = computed(() => {
  const set = new Set()
  for (const o of overview.value || []) {
    if (o.tableCounts) Object.keys(o.tableCounts).forEach((k) => set.add(k))
  }
  return Array.from(set).sort()
})

function toPairs(map) {
  if (!map) return []
  return Object.entries(map)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await fetchTenantDataOverview()
  } catch (e) {
    overview.value = []
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

async function onBackfill() {
  if (!backfillForm.targetTenantId) {
    ElMessage.warning(t('system.enterTargetTenantId'))
    return
  }
  backfillLoading.value = true
  try {
    const res = await backfillTenantData({ targetTenantId: backfillForm.targetTenantId })
    ElMessage.success(t('system.backfillDone', { n: res.affectedRows }))
    await loadOverview()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    backfillLoading.value = false
  }
}

async function onReassign() {
  if (!reassignForm.toTenantId) {
    ElMessage.warning(t('system.enterTargetTenantId'))
    return
  }
  reassignLoading.value = true
  try {
    const res = await reassignTenantData({
      fromTenantId: reassignForm.fromTenantId || null,
      toTenantId: reassignForm.toTenantId,
      tables: reassignForm.tables && reassignForm.tables.length ? reassignForm.tables : null
    })
    ElMessage.success(t('system.reassignDone', { n: res.affectedRows }))
    await loadOverview()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    reassignLoading.value = false
  }
}
</script>

<style scoped>
.mb-12 {
  margin-bottom: 12px;
}
</style>
