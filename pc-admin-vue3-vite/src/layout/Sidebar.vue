<template>
  <div ref="scrollRef" class="app-menu-scroll" @wheel="onWheel">
    <el-menu
      :collapse="collapsed"
      :default-active="activeIndex"
      router
      background-color="#001529"
      text-color="#c0c4cc"
      active-text-color="#ffffff"
      class="app-menu"
    >
      <!-- 菜单完全由后端「我的菜单」接口按当前用户角色动态返回，无前端硬编码 -->
      <MenuTree v-for="item in menus" :key="item.id ?? item.menuCode" :item="item" />
    </el-menu>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { authState } from '@/store/auth'
import MenuTree from './MenuTree.vue'

const props = defineProps({ collapsed: Boolean })

const route = useRoute()
const activeIndex = computed(() => route.path)
const menus = computed(() => authState.menus || [])

const scrollRef = ref(null)

/**
 * 将激活菜单项滚动到可视区域。
 *
 * 仅在激活项被滚动容器遮挡时触发（顶部或底部被裁剪），避免无谓滚动。
 * 使用 scrollIntoView({ block: 'nearest' }}) 让浏览器自动判定方向：
 * 激活项在视口上方时向上滚、在下方时向下滚、在视口内则不动。
 */
async function scrollToActive() {
  await nextTick()
  const container = scrollRef.value
  if (!container) return
  const active = container.querySelector('.el-menu-item.is-active')
  if (!active) return
  const cRect = container.getBoundingClientRect()
  const aRect = active.getBoundingClientRect()
  if (aRect.top < cRect.top + 1 || aRect.bottom > cRect.bottom - 1) {
    active.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
}

/** 路由变化后自动定位激活项。 */
watch(activeIndex, scrollToActive, { flush: 'post' })

/** 折叠/展开切换后重新定位（高度骤变可能使激活项移出可视区）。 */
watch(() => props.collapsed, async () => {
  await nextTick()
  // 折叠后 submenu 以 popup 展示，主体仅 top-level items；延迟等动画完
  setTimeout(scrollToActive, 210)
})

/**
 * 滚轮边界守卫：到达容器顶/底时阻止默认行为并阻止冒泡，
 * 避免滚动链式传播到页面主体（overscroll-behavior: contain 的 JS 兜底，
 * 兼容不支持该属性的旧版浏览器）。
 */
function onWheel(e) {
  const el = scrollRef.value
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  const delta = e.deltaY
  // 到顶仍向上滚（deltaY < 0）
  if (scrollTop <= 0 && delta < 0) {
    e.preventDefault()
    return
  }
  // 到底仍向下滚（deltaY > 0）
  if (scrollTop + clientHeight >= scrollHeight - 1 && delta > 0) {
    e.preventDefault()
  }
}
</script>

<style scoped>
.app-menu-scroll {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  /* iOS Safari 惯性滚动 */
  -webkit-overflow-scrolling: touch;
  /* 桌面端编程式滚动（路由跳转后自动定位激活项）使用平滑动画 */
  scroll-behavior: smooth;
  /* 阻止滚动链式传播到页面主体（现代浏览器原生支持） */
  overscroll-behavior: contain;
  /* Firefox 自定义滚动条 */
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.15) transparent;
}

.app-menu {
  border-right: none;
  width: 100%;
  /*
   * 原为 height: 100%，内容不足时撑满、超出时被 overflow:hidden 裁剪无法查看。
   * 改为 min-height + auto：内容不足时仍撑满容器（保持背景色填充），
   * 超出时自然增高由外层 scroll 容器接管滚动。
   */
  min-height: 100%;
  height: auto;
}

/* --- 自定义滚动条（WebKit / Blink）--- */

.app-menu-scroll::-webkit-scrollbar {
  width: 6px;
}

.app-menu-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.app-menu-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 3px;
}

/* 悬停时提亮，兼顾可发现性与视觉简洁 */
.app-menu-scroll:hover::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.3);
}

/* 拖动滚动时进一步提亮 */
.app-menu-scroll::-webkit-scrollbar-thumb:active {
  background: rgba(255, 255, 255, 0.4);
}
</style>