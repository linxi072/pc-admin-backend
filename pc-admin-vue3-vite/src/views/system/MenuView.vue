<template>
  <div>
    <h2 class="page-title">{{ t('route.menu') }}</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.common.keyword') }}</span>
            <el-input
              v-model="keyword"
              :placeholder="t('system.menu.searchPlaceholder')"
              clearable
              :prefix-icon="Search"
              style="width: 240px"
              @clear="keyword = ''"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.menu.addTitle') }}</el-button>
          </div>
        </div>
      </div>

      <el-table
        :data="treeData"
        v-loading="loading"
        element-:loading-text="t('system.common.loadingEllipsis')"
        row-key="id"
        :row-class-name="rowClassName"
        :tree-props="{ children: 'children' }"
        border
        stripe
        default-expand-all
      >
        <el-table-column :label="t('system.menu.name')" min-width="200">
          <template #default="{ row }">
            <span
              class="drag-handle"
              :class="{ 'drag-handle--disabled': !!keyword || sorting }"
              :title="dragHandleTitle"
              draggable="true"
              @dragstart="onDragStart($event, row)"
              @dragend="onDragEnd"
              @dragover.prevent="onDragOver($event, row)"
              @dragleave="onDragLeave"
              @drop.prevent="onDrop(row)"
            >
              <el-icon><More /></el-icon>
            </span>
            <span class="menu-name">{{ row.menuName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="menuCode" :label="t('system.menu.code')" width="150" />
        <el-table-column :label="t('system.common.type')" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.menuType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="routePath" :label="t('system.menu.routePath')" min-width="170" show-overflow-tooltip />
        <el-table-column prop="permissionCode" :label="t('system.role.permissionKey')" min-width="170" show-overflow-tooltip />
        <el-table-column :label="t('system.common.sort')" width="100">
          <template #header>
            <span>{{ t('system.common.sort') }}</span>
            <el-tooltip placement="top" raw-content>
              <template #content>
                同级菜单按排序值升序展示，值相同按创建先后（后端 sort_no ASC, id ASC 二级键）。<br />
                拖拽或上移/下移后自动按 10 步进重算整级排序值，留出中间值便于手动插队。
              </template>
              <el-icon class="col-tip"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span class="sort-no">{{ row.sortNo || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Top"
              :disabled="!!keyword || sorting || isFirstSibling(row)"
              :title="t('formDesigner.moveUp')"
              @click="moveMenu(row, -1)"
            />
            <el-button
              link
              type="primary"
              :icon="Bottom"
              :disabled="!!keyword || sorting || isLastSibling(row)"
              :title="t('formDesigner.moveDown')"
              @click="moveMenu(row, 1)"
            />
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('system.menu.confirmDelete')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>

        <!-- 空态细分：加载失败可重试 / 搜索无匹配可清空 / 无数据引导新增 -->
        <template #empty>
          <div class="table-empty">
            <template v-if="loadError">
              <el-icon class="empty-icon empty-icon--error"><WarningFilled /></el-icon>
              <p>{{ t('system.menu.loadFailed') }}</p>
              <el-button size="small" @click="load">{{ t('system.common.reload') }}</el-button>
            </template>
            <template v-else-if="keyword">
              <el-icon class="empty-icon"><Search /></el-icon>
              <p>未找到匹配「{{ keyword }}」的菜单</p>
              <el-button size="small" @click="keyword = ''">{{ t('system.common.clearKeyword') }}</el-button>
            </template>
            <template v-else>
              <el-icon class="empty-icon"><MenuIcon /></el-icon>
              <p>{{ t('system.menu.empty') }}</p>
              <el-button size="small" type="primary" :icon="Plus" @click="openCreate">{{ t('system.menu.addTitle') }}</el-button>
            </template>
          </div>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? t('system.menu.editTitle') : t('system.menu.addTitle')" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.menu.parent')">
          <el-select v-model="form.parentId" clearable :placeholder="t('system.menu.top')" style="width: 100%">
            <el-option v-for="m in parentOptions" :key="m.id" :label="m.label" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.menu.code')" prop="menuCode">
          <el-input v-model="form.menuCode" :placeholder="t('system.common.examplePerm')" />
        </el-form-item>
        <el-form-item :label="t('system.menu.name')" prop="menuName">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item :label="t('system.menu.type')" prop="menuType">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option :label="t('system.menu.typeCatalog')" value="C" />
            <el-option :label="t('system.menu.typeMenu')" value="M" />
            <el-option :label="t('system.menu.typeButton')" value="B" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.menu.routePath')">
          <el-input v-model="form.routePath" placeholder="/system/user" />
        </el-form-item>
        <el-form-item :label="t('system.menu.componentPath')">
          <el-input v-model="form.componentPath" placeholder="views/system/User.vue" />
        </el-form-item>
        <el-form-item :label="t('system.role.permissionKey')">
          <el-input v-model="form.permissionCode" placeholder="system:menu:read" />
        </el-form-item>
        <el-form-item :label="t('system.common.icon')">
          <IconPicker v-model="form.icon" />
        </el-form-item>
        <el-form-item :label="t('system.menu.visible')">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="t('system.common.sort')">
          <el-input-number v-model="form.sortNo" :min="0" @change="sortNoTouched = true" />
          <span class="sort-hint">{{ t('system.menu.sortDefaultHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ENABLED" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, Edit, Top, Bottom, More, QuestionFilled, WarningFilled, Menu as MenuIcon } from '@element-plus/icons-vue'
import IconPicker from '@/components/IconPicker.vue'
import { menuTree, createMenu, updateMenu, deleteMenu, sortMenus } from '@/api/menu'
// 树拍平 / 子树 id 收集统一到 utils/tree
import { flattenTree, collectSubtreeIds } from '@/utils/tree'
import { validateOrWarn } from '@/utils/validate'

const loading = ref(false)
/** 加载失败标记：驱动空态展示重试入口（与「无数据」「搜索无匹配」区分）。 */
const loadError = ref(false)
const saving = ref(false)
const keyword = ref('')
const rawTree = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

/** 拖拽排序状态：dragId=被拖行、dragParentId=被拖行父级、overId=悬停目标行、position=落点（before/after 目标行）。 */
const dragState = ref(null)
/** 排序提交中（禁用拖拽与上移/下移按钮，防止并发提交乱序）。 */
const sorting = ref(false)

/** 新建/更新成功后高亮的行 id（数秒后自动清除），便于在长列表中定位刚操作的菜单。 */
const highlightId = ref(null)
let highlightTimer = null
/** 用户在对话框内手动改过排序值时为 true，此时切换上级菜单不再自动预置 sortNo。 */
const sortNoTouched = ref(false)

const form = reactive({
  parentId: null,
  menuCode: '',
  menuName: '',
  menuType: 'M',
  routePath: '',
  componentPath: '',
  permissionCode: '',
  icon: '',
  visible: 1,
  sortNo: 0,
  status: 'ENABLED'
})
const rules = {
  menuCode: [{ required: true, message: t('system.menu.pleaseEnterCode'), trigger: 'blur' }],
  menuName: [{ required: true, message: t('system.menu.pleaseEnterName'), trigger: 'blur' }],
  menuType: [{ required: true, message: t('system.menu.pleaseSelectType'), trigger: 'change' }]
}

function typeLabel(t) {
  return t === 'C' ? t('system.common.directory') : t === 'B' ? t('system.common.button') : t('system.common.menu')
}

// ------------------------------------------------------------------
// 节点索引（性能核心）
//
// 旧实现中 isFirstSibling/isLastSibling 由模板逐行调用，每次 siblingList()
// 都从根节点全树遍历找父级（O(T)），N 行渲染即 O(N×T)；拖拽期间 dragover
// 高频触发 rowClassName 重算，整表反复全树遍历，菜单量大时明显卡顿。
//
// 现改为一次 O(T) 遍历同时产出两张表（computed 缓存，rawTree 深变更时自动
// 重建，splice 改动嵌套数组同样触发重建）：
//   byParent: 归一化父级 key（顶级为 0）→ children 数组（rawTree 中的真实
//             引用，splice 直接改动即响应式更新）
//   byId:     id → { parentId, index, count }（上移/下移的禁用态与位置 O(1) 判定）
// ------------------------------------------------------------------
const nodeIndex = computed(() => {
  const byParent = new Map()
  const byId = new Map()
  const keyOf = (p) => (p == null || p === '' || p === '0' || p === 0 ? 0 : p)
  const walk = (nodes, parentId) => {
    const kids = nodes || []
    byParent.set(keyOf(parentId), kids)
    kids.forEach((n, i) => {
      byId.set(n.id, { parentId: keyOf(parentId), index: i, count: kids.length })
      if (n.children?.length) walk(n.children, n.id)
    })
  }
  walk(rawTree.value, 0)
  return { byParent, byId }
})

/** 取指定父级（0=顶级）下的子菜单数组引用（原始树，非过滤视图）。 */
function siblingList(parentId) {
  const key = parentId == null || parentId === '' || parentId === '0' || parentId === 0 ? 0 : parentId
  return nodeIndex.value.byParent.get(key) || []
}

/** 建议排序值：同级最大值 + 10（10 步进与拖拽重算保持一致，留出手动插队空间）。 */
function suggestSortNo(parentId) {
  const kids = siblingList(parentId)
  return kids.reduce((m, n) => Math.max(m, n.sortNo || 0), 0) + 10
}

// 将嵌套菜单树拍平为「上级菜单」下拉选项，带层级缩进
const flatMenus = computed(() => flattenTree(rawTree.value, { nameKey: 'menuName' }))

// 编辑态时，从「上级菜单」下拉中排除当前节点及其全部子孙，避免选到会造成环路的父级
const parentOptions = computed(() => {
  if (editingId.value == null) return flatMenus.value
  const banned = collectSubtreeIds(rawTree.value, editingId.value)
  return flatMenus.value.filter((m) => !banned.has(m.id))
})

// 关键词过滤（保留命中或命中子孙的祖先），与后端 MenuTreeVO 结构对齐
const treeData = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rawTree.value
  const filter = (nodes) => {
    const res = []
    for (const n of nodes) {
      const match =
        (n.menuName || '').toLowerCase().includes(kw) ||
        (n.menuCode || '').toLowerCase().includes(kw)
      const kids = n.children ? filter(n.children) : []
      // 命中但无命中子孙时不挂空 children，避免展开箭头点开是空白
      if (match || kids.length) res.push({ ...n, children: kids.length ? kids : undefined })
    }
    return res
  }
  return filter(rawTree.value)
})

async function load() {
  loading.value = true
  loadError.value = false
  try {
    rawTree.value = await menuTree()
  } catch (e) {
    loadError.value = true
    ElMessage.error(t('system.menu.loadFailed'))
  } finally {
    loading.value = false
  }
}

// ------------------------------------------------------------------
// 拖拽排序（同级上下移动）
//
// 排序规则（与后端 MenuService.tree()/menusForCurrentUser() 一致）：
//   同级内 sort_no ASC，二级键 id ASC（同值按创建先后，顺序确定）。
// 交互约束：
//   仅支持同级排序（跨父级请用「编辑」调整上级菜单）；搜索态过滤视图的
//   顺序不代表真实同级集合，禁止拖拽/上下移；排序提交中禁止重复操作。
// 持久化策略：
//   本地先行移动并按 10 步进重算整级 sortNo，整级提交
//   PUT /api/system/menus/sort（事务内逐项更新，任一不存在整体回滚）；
//   成功即认为本地状态与后端一致（提交值即展示值，无需回查）；
//   失败提示并重新拉取列表回滚。
// ------------------------------------------------------------------

const dragHandleTitle = computed(() => {
  if (sorting.value) return t('system.common.sortSaving')
  if (keyword.value) return t('system.menu.noDragWhileSearch')
  return t('system.menu.dragHint')
})

/** 行样式：拖拽源行半透明、目标行上下沿指示线、新操作行高亮（视觉反馈）。 */
function rowClassName({ row }) {
  const s = dragState.value
  if (s) {
    if (s.dragId === row.id) return 'menu-row-dragging'
    if (s.overId === row.id) {
      return s.position === 'before' ? 'menu-row-over-top' : 'menu-row-over-bottom'
    }
  }
  if (highlightId.value != null && String(highlightId.value) === String(row.id)) {
    return 'menu-row-new'
  }
  return ''
}

function onDragStart(e, row) {
  if (sorting.value) {
    e.preventDefault()
    return
  }
  if (keyword.value) {
    // 搜索态拖的是过滤视图，顺序不代表真实同级集合，直接拒绝
    e.preventDefault()
    ElMessage.warning(t('system.menu.noDragWhileSearch'))
    return
  }
  dragState.value = { dragId: row.id, dragParentId: parentIdOf(row), overId: null, position: null }
  // 部分浏览器需要 setData 才会持续触发拖拽
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(row.id))
}

function onDragOver(e, row) {
  const s = dragState.value
  if (!s || s.dragId === row.id) return
  // 同级判定：parentId 不一致（跨父级/跨层）时不给放置反馈
  if (parentIdOf(row) !== s.dragParentId) return
  // 落点判定：鼠标位于目标行上半部 → before，下半部 → after
  const rect = e.currentTarget.getBoundingClientRect()
  const position = e.clientY < rect.top + rect.height / 2 ? 'before' : 'after'
  s.overId = row.id
  s.position = position
}

function onDragLeave() {
  // 仅清除目标行反馈，保留拖拽源状态（拖拽仍在进行）
  if (dragState.value) {
    dragState.value.overId = null
    dragState.value.position = null
  }
}

function onDragEnd() {
  // 无论放置成功与否（含拖出边界/按 Esc 取消），统一清理拖拽态
  dragState.value = null
}

function onDrop(row) {
  const s = dragState.value
  dragState.value = null
  if (!s || s.dragId === row.id || !s.overId) return
  if (parentIdOf(row) !== s.dragParentId) {
    ElMessage.warning(t('system.menu.sortHint'))
    return
  }
  applyReorder(s.dragId, s.overId, s.position)
}

/** 上移/下移按钮（触屏与无拖拽环境的等价操作）。 */
async function moveMenu(row, dir) {
  if (sorting.value) return
  if (keyword.value) {
    ElMessage.warning(t('system.menu.noSortWhileSearch'))
    return
  }
  const entry = nodeIndex.value.byId.get(row.id)
  if (!entry) return
  const list = siblingList(entry.parentId)
  const j = entry.index + dir
  if (j < 0 || j >= list.length) return // 边界：已在首位/末位
  applyReorder(row.id, list[j].id, dir < 0 ? 'before' : 'after')
}

function isFirstSibling(row) {
  const e = nodeIndex.value.byId.get(row.id)
  return !!e && e.index === 0
}

function isLastSibling(row) {
  const e = nodeIndex.value.byId.get(row.id)
  return !!e && e.index === e.count - 1
}

/** 本地移动 dragId 到 targetId 前/后，重算该级 sortNo 并整级提交；失败回滚。 */
async function applyReorder(dragId, targetId, position) {
  if (sorting.value) return
  const entry = nodeIndex.value.byId.get(dragId)
  if (!entry) return
  const list = siblingList(entry.parentId)
  const from = list.findIndex((n) => n.id === dragId)
  const to = list.findIndex((n) => n.id === targetId)
  if (from < 0 || to < 0 || from === to) return
  const moved = list.splice(from, 1)[0]
  /*
   * 插入点修正：splice 移除 from 后，若 from < to，目标元素索引已前移 1
   * （to → to-1），此时「插到目标后」的落点也要相应减 1，否则被拖行会
   * 多跳一格（旧实现未修正：下移按钮一次跳两格、拖拽落点整体偏移）。
   * from >= to 时目标索引不变，插入点即 to / to+1。
   */
  const targetIdx = from < to ? to - 1 : to
  list.splice(position === 'before' ? targetIdx : targetIdx + 1, 0, moved)
  // 10 步进重算（留出中间值空间，便于手动编辑 sortNo 插队）
  list.forEach((n, i) => {
    n.sortNo = (i + 1) * 10
  })
  const payload = list.map((n) => ({ id: n.id, sortNo: n.sortNo }))
  sorting.value = true
  try {
    await sortMenus(payload)
    ElMessage.success(t('system.common.sortSaved'))
  } catch (e) {
    // 拦截器已统一提示错误原因；本地可能已改动，重新拉取保证与后端一致
    ElMessage.error(t('system.common.sortSaveFailed'))
    try {
      await load()
    } catch {
      /* load 内部已有提示 */
    }
  } finally {
    sorting.value = false
  }
}

/**
 * 归一化行的 parentId：顶级统一为数值 0，其余为字符串 id。
 *
 * <p>后端 Long 一律序列化为字符串（18 位雪花 ID 超出 JS Number.MAX_SAFE_INTEGER，
 * 防前端 round 后按 id 操作失配），顶层菜单的 parentId 上链后是字符串 "0"，
 * 不能直接用 ?? 0 / || null 判断（"0" 为 truthy），故统一走本函数归一化。
 */
function parentIdOf(row) {
  const p = row.parentId
  return p == null || p === '' || p === '0' || p === 0 ? 0 : p
}

/** 高亮指定行并滚动到可视区（新建/更新成功后定位刚操作的菜单）。 */
async function highlightRow(id) {
  if (id == null) return
  highlightId.value = id
  clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => {
    highlightId.value = null
  }, 3000)
  await nextTick()
  // 表格数据异步渲染，下一 tick 未必已生成 DOM；双保险：先立即试一次，再延后试一次
  scrollRowIntoView()
  setTimeout(scrollRowIntoView, 50)
}

function scrollRowIntoView() {
  const el = document.querySelector('.menu-row-new')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function openCreate() {
  editingId.value = null
  sortNoTouched.value = false
  Object.assign(form, {
    parentId: null,
    menuCode: '',
    menuName: '',
    menuType: 'M',
    routePath: '',
    componentPath: '',
    permissionCode: '',
    icon: '',
    visible: 1,
    // 预置为「顶级同级最大值 + 10」：新菜单排在同级末尾，避免 sortNo=0 时
    // 因 ASC 排序跳到同级最前面（切换上级后由 watch 重算）
    sortNo: suggestSortNo(null),
    status: 'ENABLED'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  sortNoTouched.value = false
  Object.assign(form, {
    parentId: parentIdOf(row) || null,
    menuCode: row.menuCode,
    menuName: row.menuName,
    menuType: row.menuType,
    routePath: row.routePath || '',
    componentPath: row.componentPath || '',
    permissionCode: row.permissionCode || '',
    icon: row.icon || '',
    visible: row.visible == null ? 1 : row.visible,
    sortNo: row.sortNo || 0,
    status: row.status || 'ENABLED'
  })
  dialogVisible.value = true
}

// 新增态切换上级菜单时重算建议排序值（排到新同级末尾）；
// 编辑态或用户已手动指定过排序值时不干扰
watch(() => form.parentId, (pid) => {
  if (editingId.value != null || sortNoTouched.value) return
  form.sortNo = suggestSortNo(pid)
})

async function onSubmit() {
  if (!(await validateOrWarn(formRef.value))) return
  saving.value = true
  try {
    let operatedId = editingId.value
    if (editingId.value != null) {
      await updateMenu(editingId.value, { ...form })
      ElMessage.success(t('system.common.updateSuccess'))
    } else {
      // 后端 create 返回新菜单 id（字符串化雪花 ID）
      operatedId = await createMenu({ ...form })
      ElMessage.success(t('system.common.createSuccess'))
    }
    dialogVisible.value = false
    editingId.value = null
    await load()
    await highlightRow(operatedId)
  } catch (e) {
    // 拦截器已统一提示；保持对话框开启，便于用户就地修正后重试
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await deleteMenu(row.id)
    ElMessage.success(t('system.department.deleted'))
    await load()
  } catch (e) {
    // 拦截器已统一提示（含存在子菜单等业务拒绝），列表保持原状
  }
}

onMounted(load)

onBeforeUnmount(() => {
  clearTimeout(highlightTimer)
})
</script>

<style scoped>
/* 拖拽手柄：默认低调可见，悬停/拖拽中高亮 */
.drag-handle {
  display: inline-flex;
  align-items: center;
  margin-right: 8px;
  color: #b4b8c4;
  cursor: grab;
  border-radius: 4px;
  vertical-align: middle;
  user-select: none;
}

.drag-handle:hover {
  color: var(--el-color-primary);
  background: #f0f5ff;
}

.drag-handle:active {
  cursor: grabbing;
}

/* 搜索态 / 排序提交中禁止拖拽 */
.drag-handle--disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.menu-name {
  vertical-align: middle;
}

/* 排序值列头提示图标 */
.col-tip {
  margin-left: 4px;
  color: #b4b8c4;
  cursor: help;
  vertical-align: middle;
}

/* 拖拽源行：半透明（浏览器原生拖拽影像之外的弱化） */
:deep(.menu-row-dragging) {
  opacity: 0.45;
}

/* 目标行反馈：上沿/下沿主色指示线（2px），标识落点 */
:deep(.menu-row-over-top > td) {
  box-shadow: inset 0 2px 0 0 var(--el-color-primary);
}

:deep(.menu-row-over-bottom > td) {
  box-shadow: inset 0 -2px 0 0 var(--el-color-primary);
}

/* 新建/更新成功行：绿色背景渐隐高亮，3 秒后自动清除 */
:deep(.menu-row-new > td) {
  animation: menu-row-flash 2.4s ease-out;
}

@keyframes menu-row-flash {
  0% {
    background: #f0f9eb;
  }
  70% {
    background: #f0f9eb;
  }
  100% {
    background: transparent;
  }
}

/* 空态细分 */
.table-empty {
  padding: 32px 0;
  color: #909399;
}

.table-empty .empty-icon {
  font-size: 40px;
  margin-bottom: 8px;
  color: #c0c4cc;
}

.table-empty .empty-icon--error {
  color: #f56c6c;
}

.table-empty p {
  margin: 0 0 12px;
  font-size: 13px;
}

/* 对话框排序字段旁的建议说明 */
.sort-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
