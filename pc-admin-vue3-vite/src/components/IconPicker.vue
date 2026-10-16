<template>
  <!-- 图标选择器：菜单管理「图标」字段的可视化选择入口，替代手背 Element Plus 图标名 -->
  <div class="icon-picker">
    <el-input :model-value="displayValue" readonly placeholder="点击选择图标（留空则不显示）" class="icon-trigger">
      <template #prepend>
        <el-icon v-if="modelValue" class="icon-preview"><component :is="modelValue" /></el-icon>
        <el-icon v-else><Picture /></el-icon>
      </template>
      <template #append>
        <el-button v-if="modelValue" size="small" text class="icon-clear" title="清除图标" @click.stop="clear">
          <el-icon><Close /></el-icon>
        </el-button>
        <el-button v-else size="small" text title="选择图标" @click.stop="open">
          <el-icon><Search /></el-icon>
        </el-button>
      </template>
    </el-input>

    <el-dialog v-model="visible" title="选择图标" width="720px" append-to-body destroy-on-close>
      <el-input v-model="query" placeholder="搜索图标名（支持中文关键字，如：监控/用户/定时）" clearable class="icon-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-alert type="info" :closable="false" show-icon class="icon-tip"
        title="图标来自 Element Plus 图标库（全局注册），按语义分组；运维管理相关图标已归入「监控运维」组。" />
      <div v-for="group in filteredGroups" :key="group.name" class="icon-group">
        <div class="icon-group-title">{{ group.name }}（{{ group.icons.length }}）</div>
        <div class="icon-grid">
          <div v-for="name in group.icons" :key="name"
            class="icon-cell" :class="{ 'is-active': name === modelValue }"
            :title="name" @click="pick(name)">
            <el-icon class="icon-cell-icon"><component :is="name" /></el-icon>
            <span class="icon-cell-name">{{ name }}</span>
          </div>
        </div>
        <el-empty v-if="!group.icons.length" :image-size="40" description="无匹配图标" />
      </div>
      <el-empty v-if="!filteredGroups.length" description="未找到匹配图标" />
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="modelValue" @click="clear">清除当前图标</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Close, Picture, Search } from '@element-plus/icons-vue'

/**
 * 图标选择器（菜单管理「图标」字段）。
 *
 * <p>设计约定：
 * <ul>
 *   <li>v-model 绑定图标名字符串（Element Plus 组件名，如 {@code Setting}），与后端
 *       {@code sys_menu.icon} 字段、MenuTree 的 {@code component :is="item.icon"} 渲染链路一致；</li>
 *   <li>图标库全量在 main.js 全局注册，此处精选约 130 个按语义分组，覆盖系统管理 /
 *       监控运维 / 工作流 / 报表 / 通用操作场景，避免 280+ 全量罗列造成选择困难；</li>
 *   <li>新增运维类菜单（V30「运维管理」子树）优先从「监控运维」组选用，保证图标风格统一
 *       （均为 Element Plus 线性图标，与既有菜单观感一致）。</li>
 * </ul>
 */
const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const query = ref('')

// 展示值：仅回显图标名（输入框只读，真实选择走弹窗）
const displayValue = computed(() => props.modelValue || '')

const GROUPS = [
  {
    name: '监控运维',
    icons: ['Odometer', 'Monitor', 'Box', 'Link', 'Key', 'Document', 'Connection', 'DataLine',
      'DataAnalysis', 'PieChart', 'TrendCharts', 'Aim', 'Cpu', 'SetUp', 'Tools', 'Setting',
      'Warning', 'WarningFilled', 'Bell', 'AlarmClock', 'Timer', 'Stopwatch', 'Lightning',
      'Magnet', 'Present', 'FirstAidKit', 'ColdDrink']
  },
  {
    name: '系统管理',
    icons: ['User', 'UserFilled', 'Avatar', 'Menu', 'OfficeBuilding', 'Notebook', 'Coin',
      'School', 'Briefcase', 'Suitcase', 'Postcard', 'Reading', 'Collection', 'Files',
      'Folder', 'FolderOpened', 'FolderChecked', 'FolderAdd', 'Tickets', 'Stamp', 'PriceTag']
  },
  {
    name: '工作流协作',
    icons: ['Share', 'Edit', 'EditPen', 'MagicStick', 'Switch', 'Refresh',
      'RefreshLeft', 'RefreshRight', 'CircleCheck', 'CircleClose', 'CirclePlus', 'Finished',
      'Select', 'Rank', 'Histogram', 'Guide', 'ChatDotRound', 'ChatLineRound', 'Message',
      'Comment', 'Phone', 'Location', 'Position', 'Paperclip']
  },
  {
    name: '报表统计',
    icons: ['DataBoard', 'Sort', 'SortUp', 'SortDown', 'Grid', 'List',
      'Medal', 'Trophy', 'Discount', 'SoldOut', 'ShoppingCart', 'Goods', 'Wallet', 'Money',
      'CreditCard']
  },
  {
    name: '通用操作',
    icons: ['Search', 'Plus', 'Minus', 'Close', 'Check', 'Delete', 'View', 'Hide',
      'Download', 'Upload', 'Top', 'Bottom', 'Back', 'FullScreen', 'Lock', 'Unlock', 'Star',
      'StarFilled', 'Flag', 'QuestionFilled', 'InfoFilled', 'More', 'MoreFilled', 'Operation',
      'Sunrise', 'Moon', 'Sunny']
  },
  {
    name: '开发工具',
    icons: ['Platform', 'Iphone', 'Camera', 'Picture', 'VideoCamera',
      'Microphone', 'Headset', 'Mouse', 'Printer', 'Brush', 'MilkTea', 'Lollipop',
      'IceCream', 'Coffee', 'Goblet']
  }
]

const filteredGroups = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return GROUPS
  return GROUPS
    .map((g) => ({ name: g.name, icons: g.icons.filter((n) => n.toLowerCase().includes(q)) }))
    .filter((g) => g.icons.length)
})

function open() { visible.value = true }
function pick(name) {
  emit('update:modelValue', name)
  visible.value = false
}
function clear() {
  emit('update:modelValue', '')
  visible.value = false
}
</script>

<style scoped>
.icon-picker { width: 100%; }
.icon-trigger :deep(.el-input__inner) { cursor: pointer; }
.icon-preview { color: var(--brand); }
.icon-clear { color: #f56c6c; }
.icon-search { margin-bottom: 12px; }
.icon-tip { margin-bottom: 12px; }
.icon-group { margin-bottom: 16px; }
.icon-group-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 8px; }
.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding: 4px;
}
.icon-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}
.icon-cell:hover { border-color: var(--brand); background: #ecf5ff; }
.icon-cell.is-active { border-color: var(--brand); background: #ecf5ff; }
.icon-cell-icon { font-size: 20px; color: #606266; }
.icon-cell.is-active .icon-cell-icon { color: var(--brand); }
.icon-cell-name {
  font-size: 11px;
  color: #909399;
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
