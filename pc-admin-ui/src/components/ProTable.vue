<template>
  <div class="pro-table">
    <el-table
      v-loading="loading"
      :data="data"
      :row-key="rowKey"
      :default-sort="defaultSort"
      v-bind="$attrs"
      @sort-change="onSortChange"
      @selection-change="onSelectionChange"
    >
      <slot name="prepend" />
      <el-table-column
        v-for="col in normalizedColumns"
        :key="col.prop || col.type || col.label"
        v-bind="col"
      >
        <template v-if="col.slots?.default" #default="scope">
          <slot :name="col.slots.default" v-bind="scope" />
        </template>
        <template v-if="col.slots?.header" #header="scope">
          <slot :name="col.slots.header" v-bind="scope" />
        </template>
      </el-table-column>
      <slot />
    </el-table>
    <div v-if="pagination" class="pro-table__pagination">
      <el-pagination
        v-model:current-page="innerPage"
        v-model:page-size="innerSize"
        :total="safeTotal"
        :page-sizes="pageSizes"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

/**
 * ProTable：通用数据表格（零业务依赖）。
 * 输入：data(行数据) / columns(列定义) / loading / pagination(是否分页) / total / page / pageSize / rowKey / defaultSort / pageSizes
 * 输出事件：update:page / update:pageSize / sort-change / selection-change
 * 边界：columns 为 null 时按空数组处理；pageSize 钳制在 pageSizes 内；total<0 按 0。
 * 异常：不主动抛错，数据异常由 el-table 自身兜底。
 */
const props = defineProps({
  data: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  pagination: { type: Boolean, default: true },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] },
  rowKey: { type: [String, Function], default: 'id' },
  defaultSort: { type: Object, default: undefined }
})

const emit = defineEmits([
  'update:page', 'update:pageSize', 'page-change', 'size-change',
  'sort-change', 'selection-change'
])

const innerPage = ref(Math.max(1, props.page))
const innerSize = ref(props.pageSizes.includes(props.pageSize) ? props.pageSize : (props.pageSizes[0] || 20))

watch(() => props.page, (v) => { innerPage.value = Math.max(1, v) })
watch(() => props.pageSize, (v) => {
  innerSize.value = props.pageSizes.includes(v) ? v : (props.pageSizes[0] || 20)
})

const normalizedColumns = computed(() =>
  (props.columns || []).map((c) => ({ ...c, label: c.label ?? '' }))
)
const safeTotal = computed(() => Math.max(0, props.total))

function onPageChange(p) {
  innerPage.value = p
  emit('update:page', p)
  emit('page-change', p)
}
function onSizeChange(s) {
  innerSize.value = s
  innerPage.value = 1
  emit('update:pageSize', s)
  emit('size-change', s)
  emit('update:page', 1)
  emit('page-change', 1)
}
function onSortChange(val) { emit('sort-change', val) }
function onSelectionChange(rows) { emit('selection-change', rows) }
</script>

<style scoped>
.pro-table { width: 100%; }
.pro-table__pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
