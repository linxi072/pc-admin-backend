<template>
  <el-dropdown trigger="click" @command="onChange">
    <el-button text class="theme-btn" :icon="Brush">
      <span class="theme-label">{{ currentLabel }}</span>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="item in THEMES"
          :key="item.key"
          :command="item.key"
          :class="{ 'is-active': item.key === current }"
        >
          <span class="dot" :style="{ background: dotColor(item.key) }" />
          {{ tx('theme.' + item.key) }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { Brush } from '@element-plus/icons-vue'
import { THEMES, currentTheme, setTheme } from '@/utils/theme'
import { tx } from '@/locales/zh-cn'

const current = computed(() => currentTheme.value)
const currentLabel = computed(() => tx('theme.' + current.value))

const dots = {
  light: '#409eff',
  dark: '#1f2937',
  warm: '#c2410c',
  brand: '#7c3aed',
  green: '#10b981',
  rose: '#ec4899',
  cyan: '#06b6d4',
  amber: '#d97706'
}
function dotColor(key) {
  return dots[key] || '#409eff'
}

function onChange(key) {
  setTheme(key)
}
</script>

<style scoped>
/* 位于深色铬顶栏内，文字用铬色弱化态，与周边图标按钮一致 */
.theme-btn {
  color: var(--chrome-text-muted);
}
.theme-label {
  margin-left: 4px;
  font-size: 13px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 8px;
  vertical-align: middle;
}
.is-active {
  font-weight: 600;
}
</style>
