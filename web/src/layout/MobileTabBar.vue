<template>
  <div class="tabbar">
    <div
      v-for="t in tabs"
      :key="t.key"
      class="tab-item"
      :class="{ active: isActive(t) }"
      @click="onTap(t)"
    >
      <el-icon :size="22"><component :is="t.icon" /></el-icon>
      <span class="tab-label">{{ t.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const emit = defineEmits(['open-drawer'])
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 移动端底部导航：按角色取可达路由，功能与 Web/桌面端完全互通 */
const tabs = computed(() => {
  const byCode = {}
  for (const m of userStore.menus || []) byCode[m.code] = m.path
  const defs = [
    { key: 'home', label: '首页', icon: 'House', path: byCode['DASHBOARD'] || byCode['MY_STUDY'] || byCode['PARENT_PORTAL'] },
    { key: 'agent', label: '智能体', icon: 'ChatLineSquare', path: byCode['AGENT'] },
    { key: 'warn', label: '预警', icon: 'Warning', path: byCode['WARNING'] || byCode['PREDICTION'] },
    { key: 'msg', label: '消息', icon: 'Bell', path: byCode['NOTIFY'] },
    { key: 'more', label: '我的', icon: 'User', path: null }
  ]
  return defs.filter((t) => t.key === 'more' || t.path)
})

function isActive(t) {
  if (t.key === 'more') return false
  return route.path === t.path || route.path.startsWith(t.path + '/')
}

function onTap(t) {
  if (t.key === 'more') {
    emit('open-drawer')
    return
  }
  if (route.path !== t.path) router.push(t.path)
}
</script>

<style scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 54px;
  display: flex;
  align-items: stretch;
  background: #fff;
  border-top: 1px solid #ebeff5;
  box-shadow: 0 -2px 12px rgba(31, 45, 105, 0.06);
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #8a94a6;
  cursor: pointer;
  transition: color 0.2s;
}

.tab-item.active {
  color: var(--xqfx-primary);
}

.tab-label {
  font-size: 11px;
  line-height: 1;
}
</style>
