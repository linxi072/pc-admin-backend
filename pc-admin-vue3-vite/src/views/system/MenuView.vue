<template>
  <div>
    <h2 class="page-title">菜单管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input
              v-model="keyword"
              placeholder="搜索菜单名称 / 编码"
              clearable
              :prefix-icon="Search"
              style="width: 240px"
              @clear="keyword = ''"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增菜单</el-button>
          </div>
        </div>
      </div>

      <el-table
        :data="treeData"
        v-loading="loading"
        row-key="id"
        :row-class-name="rowClassName"
        :tree-props="{ children: 'children' }"
        border
        stripe
        default-expand-all
        empty-text="暂无菜单数据（点击右上角「新增菜单」创建）"
      >
        <el-table-column label="菜单名称" min-width="200">
          <template #default="{ row }">
            <span
              class="drag-handle"
              :class="{ 'drag-handle--disabled': !!keyword }"
              :title="keyword ? '搜索状态下不可拖拽，请清空关键字后调整顺序' : '拖拽调整同级菜单顺序'"
              draggable="true"
              @dragstart="onDragStart($event, row)"
              @dragend="onDragEnd"
              @dragover.prevent="onDragOver($event, row)"
              @dragleave="onDragLeave"
              @drop.prevent="onDrop($event, row)"
            >
              <el-icon><More /></el-icon>
            </span>
            <span class="menu-name">{{ row.menuName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="menuCode" label="菜单编码" width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.menuType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="routePath" label="路由地址" min-width="170" show-overflow-tooltip />
        <el-table-column prop="permissionCode" label="权限标识" min-width="170" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Top"
              :disabled="!!keyword || isFirstSibling(row)"
              title="上移"
              @click="moveMenu(row, -1)"
            />
            <el-button
              link
              type="primary"
              :icon="Bottom"
              :disabled="!!keyword || isLastSibling(row)"
              title="下移"
              @click="moveMenu(row, 1)"
            />
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该菜单？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑菜单' : '新增菜单'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" clearable placeholder="顶级菜单" style="width: 100%">
            <el-option v-for="m in parentOptions" :key="m.id" :label="m.label" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单编码" prop="menuCode">
          <el-input v-model="form.menuCode" placeholder="如 sys:user" />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option label="目录 (C)" value="C" />
            <el-option label="菜单 (M)" value="M" />
            <el-option label="按钮 (B)" value="B" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由地址">
          <el-input v-model="form.routePath" placeholder="/system/user" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="form.componentPath" placeholder="views/system/User.vue" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.permissionCode" placeholder="system:menu:read" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="如 Setting（Element Plus 图标名）" />
        </el-form-item>
        <el-form-item label="是否显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, Edit, Top, Bottom, More } from '@element-plus/icons-vue'
import { menuTree, createMenu, updateMenu, deleteMenu, sortMenus } from '@/api/menu'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rawTree = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

/** 拖拽排序状态：dragId=被拖行、overId=悬停目标行、position=落点（before/after 目标行）。 */
const dragState = ref(null)
/** 排序提交中（禁用重复操作与按钮）。 */
const sorting = ref(false)

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
  menuCode: [{ required: true, message: '请输入菜单编码', trigger: 'blur' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

function typeLabel(t) {
  return t === 'C' ? '目录' : t === 'B' ? '按钮' : '菜单'
}

// 将嵌套菜单树拍平为「上级菜单」下拉选项，带层级缩进
const flatMenus = computed(() => {
  const out = []
  const walk = (nodes, depth) => {
    for (const n of nodes) {
      out.push({ id: n.id, label: (depth > 0 ? '　'.repeat(depth) : '') + n.menuName })
      if (n.children?.length) walk(n.children, depth + 1)
    }
  }
  walk(rawTree.value, 0)
  return out
})

// 编辑态时，从「上级菜单」下拉中排除当前节点及其全部子孙，避免选到会造成环路的父级
const parentOptions = computed(() => {
  if (editingId.value == null) return flatMenus.value
  const banned = subtreeIds(rawTree.value, editingId.value)
  return flatMenus.value.filter((m) => !banned.has(m.id))
})

// 收集节点及其全部子孙的 id 集合（在父级下拉中排除，防止层级环路）
function subtreeIds(nodes, id) {
  const set = new Set()
  const walk = (list) => {
    for (const n of list) {
      if (n.id === id) {
        set.add(n.id)
        const collect = (node) => {
          for (const c of node.children || []) {
            set.add(c.id)
            collect(c)
          }
        }
        collect(n)
        return true
      }
      if (n.children?.length && walk(n.children)) return true
    }
    return false
  }
  walk(nodes)
  return set
}

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
      if (match || kids.length) res.push({ ...n, children: kids })
    }
    return res
  }
  return filter(rawTree.value)
})

async function load() {
  loading.value = true
  try {
    rawTree.value = await menuTree()
  } finally {
    loading.value = false
  }
}

// ------------------------------------------------------------------
// 拖拽排序（同级上下移动）
// 排序规则：同级内按 sortNo 升序（同值按 id 升序，后端二级键保证顺序确定）。
// 交互：拖拽手柄 HTML5 DnD 或操作列上移/下移按钮；仅支持同级排序（跨父级请用「编辑」调整上级菜单）。
// 持久化：本地移动后按 10 步进重算该级全部 sortNo，整级提交 PUT /api/system/menus/sort，
//         失败即重新拉取列表回滚，保证界面与后端一致。
// ------------------------------------------------------------------

/** 行样式：拖拽源行半透明、目标行上下沿指示线（视觉反馈）。 */
function rowClassName({ row }) {
  const s = dragState.value
  if (!s) return ''
  if (s.dragId === row.id) return 'menu-row-dragging'
  if (s.overId === row.id) {
    return s.position === 'before' ? 'menu-row-over-top' : 'menu-row-over-bottom'
  }
  return ''
}

function onDragStart(e, row) {
  if (keyword.value) {
    // 搜索态拖的是过滤视图，顺序不代表真实同级集合，直接拒绝
    e.preventDefault()
    ElMessage.warning('搜索状态下不可拖拽，请清空关键字后调整顺序')
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

function onDrop(e, row) {
  const s = dragState.value
  dragState.value = null
  if (!s || s.dragId === row.id || !s.overId) return
  if (parentIdOf(row) !== s.dragParentId) {
    ElMessage.warning('仅支持同级菜单排序；调整上级请用「编辑」')
    return
  }
  applyReorder(s.dragId, s.overId, s.position)
}

/** 上移/下移按钮（触屏与无拖拽环境的等价操作）。 */
async function moveMenu(row, dir) {
  if (keyword.value) {
    ElMessage.warning('搜索状态下不可调整顺序，请清空关键字后操作')
    return
  }
  const list = siblingList(parentIdOf(row))
  const i = list.findIndex((n) => n.id === row.id)
  const j = i + dir
  if (i < 0 || j < 0 || j >= list.length) return // 边界：已在首位/末位
  applyReorder(row.id, list[j].id, dir < 0 ? 'before' : 'after')
}

function isFirstSibling(row) {
  const list = siblingList(parentIdOf(row))
  return list.length > 0 && list[0].id === row.id
}

function isLastSibling(row) {
  const list = siblingList(parentIdOf(row))
  return list.length > 0 && list[list.length - 1].id === row.id
}

/** 取指定父级（0=顶级）下的子菜单数组引用（原始树，非过滤视图）。 */
function siblingList(parentId) {
  if (!parentId) return rawTree.value
  const walk = (nodes) => {
    for (const n of nodes) {
      if (n.id === parentId) return n.children || (n.children = [])
      if (n.children?.length) {
        const found = walk(n.children)
        if (found) return found
      }
    }
    return null
  }
  return walk(rawTree.value) || []
}

/** 本地移动 dragId 到 targetId 前/后，重算该级 sortNo 并整级提交；失败回滚。 */
async function applyReorder(dragId, targetId, position) {
  const list = siblingList(currentParentOf(dragId))
  const from = list.findIndex((n) => n.id === dragId)
  const to = list.findIndex((n) => n.id === targetId)
  if (from < 0 || to < 0 || from === to) return
  const moved = list.splice(from, 1)[0]
  list.splice(position === 'before' ? to : to + 1, 0, moved)
  // 10 步进重算（留出中间值空间，便于手动编辑 sortNo 插队）
  list.forEach((n, i) => {
    n.sortNo = (i + 1) * 10
  })
  const payload = list.map((n) => ({ id: n.id, sortNo: n.sortNo }))
  sorting.value = true
  try {
    await sortMenus(payload)
    ElMessage.success('排序已保存')
  } catch (e) {
    // 拦截器已统一提示；本地可能已改动，重新拉取保证与后端一致
    load()
  } finally {
    sorting.value = false
  }
}

/** 被拖行的父级 id（0=顶级）。 */
function currentParentOf(id) {
  let pid = 0
  const walk = (nodes, parent) => {
    for (const n of nodes) {
      if (n.id === id) {
        pid = parent
        return true
      }
      if (n.children?.length && walk(n.children, n.id)) return true
    }
    return false
  }
  walk(rawTree.value, 0)
  return pid
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

function openCreate() {
  editingId.value = null
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
    sortNo: 0,
    status: 'ENABLED'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
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

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateMenu(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createMenu({ ...form })
      ElMessage.success('创建成功')
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
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
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

/* 搜索态下禁止拖拽 */
.drag-handle--disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.menu-name {
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
</style>
