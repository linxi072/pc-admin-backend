<template>
  <div>
    <h2 class="page-title">部门管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input
              v-model="keyword"
              placeholder="搜索部门名称 / 编码"
              clearable
              :prefix-icon="Search"
              style="width: 240px"
              @clear="keyword = ''"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增部门</el-button>
          </div>
        </div>
      </div>

      <el-table
        :data="treeData"
        v-loading="loading"
        row-key="id"
        :tree-props="{ children: 'children' }"
        border
        stripe
        default-expand-all
      >
        <el-table-column prop="orgName" label="部门名称" min-width="160" />
        <el-table-column prop="orgCode" label="部门编码" width="150" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.orgType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="负责人" min-width="140">
          <template #default="{ row }">
            <span v-if="row.leaderUserName">{{ row.leaderUserName }}</span>
            <span v-else class="text-muted">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该部门？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑部门' : '新增部门'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" clearable placeholder="顶级部门" style="width: 100%">
            <el-option v-for="m in parentOptions" :key="m.id" :label="m.label" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门编码" prop="orgCode">
          <el-input v-model="form.orgCode" placeholder="如 tech-dept" />
        </el-form-item>
        <el-form-item label="部门名称" prop="orgName">
          <el-input v-model="form.orgName" />
        </el-form-item>
        <el-form-item label="部门类型" prop="orgType">
          <el-select v-model="form.orgType" style="width: 100%">
            <el-option label="公司 (COMPANY)" value="COMPANY" />
            <el-option label="部门 (DEPARTMENT)" value="DEPARTMENT" />
            <el-option label="小组 (TEAM)" value="TEAM" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-model="form.leaderUserId"
            filterable
            remote
            clearable
            placeholder="输入用户名 / 昵称搜索"
            :remote-method="onLeaderSearch"
            :loading="leaderLoading"
            style="width: 100%"
            @visible-change="onLeaderDropdown"
          >
            <el-option
              v-for="u in leaderOptions"
              :key="u.id"
              :label="leaderOptionLabel(u)"
              :value="u.id"
            />
            <template #empty>
              <div class="leader-empty">
                {{ leaderLoading ? '搜索中…' : leaderSearched ? '未找到匹配用户' : '输入关键词搜索用户' }}
              </div>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
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
import { Plus, Delete, Search, Edit } from '@element-plus/icons-vue'
import { orgTree, createOrg, updateOrg, deleteOrg } from '@/api/org'
import { pageUsers } from '@/api/user'
// 树拍平 / 子树 id 收集统一到 utils/tree
import { flattenTree, collectSubtreeIds } from '@/utils/tree'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rawTree = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  parentId: null,
  orgCode: '',
  orgName: '',
  orgType: 'DEPARTMENT',
  sortNo: 0,
  leaderUserId: null, // 负责人用户 id（字符串：雪花 ID），null 表示未设置
  status: 'ACTIVE'
})

// ---- 负责人远程搜索下拉 ----
// 候选项来源：① remote search 结果 ② 编辑态回显的已选用户（保证 label 正确展示）
const leaderOptions = ref([])
const leaderLoading = ref(false)
const leaderSearched = ref(false) // 区分「未搜索」与「无匹配结果」两种空态
let leaderSearchSeq = 0 // 请求序号：丢弃过期响应，避免快速输入时旧结果覆盖新结果

// 选项展示名：displayName 优先；与 username 不同时附带登录名便于区分
function leaderOptionLabel(u) {
  const name = u.displayName || u.username
  return u.displayName && u.displayName !== u.username ? `${u.displayName}（${u.username}）` : name
}

// 已选负责人可能不在候选列表中（编辑态初次打开），注入选项保证 el-select 正确回显
function ensureLeaderOption(user) {
  if (!user || !user.id) return
  if (!leaderOptions.value.some((u) => u.id === user.id)) {
    leaderOptions.value = [{ id: user.id, displayName: user.displayName, username: user.username }, ...leaderOptions.value]
  }
}

// 复用用户分页接口的 username 模糊匹配（后端 LIKE %kw% 同时命中 username 与 displayName）
async function fetchLeaderCandidates(kw) {
  const seq = ++leaderSearchSeq
  leaderLoading.value = true
  try {
    const page = await pageUsers({ page: 1, size: 20, username: kw || '' })
    if (seq !== leaderSearchSeq) return // 过期响应丢弃
    leaderOptions.value = page.records || []
    leaderSearched.value = true
  } catch (e) {
    if (seq !== leaderSearchSeq) return
    leaderOptions.value = []
    // 无 system:user:read 权限时后端 403，明确提示而非静默空列表
    ElMessage.warning('用户列表加载失败（可能需要用户查询权限）')
  } finally {
    if (seq === leaderSearchSeq) leaderLoading.value = false
  }
}

// el-select remote 模式：输入即触发；空关键词加载默认列表（最近用户）
function onLeaderSearch(kw) {
  fetchLeaderCandidates((kw || '').trim())
}

// 负责人 id 归一化：VO 已字符串化，null / '0' / 0（历史占位）均视为未设置
function leaderIdOf(row) {
  const id = row.leaderUserId
  if (id == null || id === '' || id === '0' || id === 0) return null
  return String(id)
}

// 点开下拉时若无候选先加载默认列表，避免空面板
function onLeaderDropdown(open) {
  if (open && leaderOptions.value.length === 0 && !leaderLoading.value) {
    fetchLeaderCandidates('')
  }
}

const rules = {
  orgCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
  orgName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择部门类型', trigger: 'change' }]
}

function typeLabel(t) {
  return t === 'COMPANY' ? '公司' : t === 'TEAM' ? '小组' : '部门'
}

const flatOrgs = computed(() => flattenTree(rawTree.value, { nameKey: 'orgName' }))

// 编辑态时排除当前节点及其全部子孙，避免选中造成层级环路
const parentOptions = computed(() => {
  if (editingId.value == null) return flatOrgs.value
  const banned = collectSubtreeIds(rawTree.value, editingId.value)
  return flatOrgs.value.filter((m) => !banned.has(m.id))
})

const treeData = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rawTree.value
  const filter = (nodes) => {
    const res = []
    for (const n of nodes) {
      const match =
        (n.orgName || '').toLowerCase().includes(kw) ||
        (n.orgCode || '').toLowerCase().includes(kw)
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
    rawTree.value = await orgTree()
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    parentId: null,
    orgCode: '',
    orgName: '',
    orgType: 'DEPARTMENT',
    sortNo: 0,
    leaderUserId: null,
    status: 'ACTIVE'
  })
  leaderOptions.value = []
  leaderSearched.value = false
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId || null,
    orgCode: row.orgCode,
    orgName: row.orgName,
    orgType: row.orgType || 'DEPARTMENT',
    sortNo: row.sortNo || 0,
    // VO 已字符串化（防精度丢失），直接作为 el-select 的 value；null / 0（旧数据占位）统一为未设置
    leaderUserId: leaderIdOf(row),
    status: row.status || 'ACTIVE'
  })
  leaderOptions.value = []
  leaderSearched.value = false
  // 注入已选负责人，保证 el-select 回显姓名而非雪花 ID 原文
  if (form.leaderUserId) {
    ensureLeaderOption({
      id: form.leaderUserId,
      displayName: row.leaderUserName,
      username: ''
    })
  }
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // 归一化负责人 id：字符串雪花 ID / null 直接提交，后端 Long 字段接受数字字符串
    const payload = { ...form, leaderUserId: form.leaderUserId || null }
    if (editingId.value != null) {
      await updateOrg(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createOrg(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    editingId.value = null
    load()
  } catch (e) {
    /* 拦截器已统一提示；保持对话框开启便于就地修正 */
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await deleteOrg(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>

<style scoped>
/* .text-muted 已统一到全局 styles.css */
.leader-empty {
  padding: 12px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>
