<template>
  <el-container class="app-layout">
    <!-- ===== Web / 桌面端：侧边导航 ===== -->
    <el-aside v-show="!isMobile" :width="asideWidth" class="app-aside">
      <div class="logo">
        <span class="logo-badge">学</span>
        <span v-if="!isCollapse" class="logo-text">学情分析系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="app-menu"
        background-color="transparent"
        text-color="#b8c4dd"
        active-text-color="#ffffff"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
      >
        <AppMenuItems />
      </el-menu>
      <div v-if="!isCollapse" class="aside-footer">
        <el-icon><Cpu /></el-icon>
        <span>Ollama 本地大模型 · v1.0</span>
      </div>
    </el-aside>

    <!-- ===== 移动端：全功能抽屉菜单 ===== -->
    <el-drawer v-model="drawerOpen" direction="ltr" :size="250" :with-header="false" class="mobile-drawer">
      <div class="logo">
        <span class="logo-badge">学</span>
        <span class="logo-text">学情分析系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="app-menu"
        background-color="transparent"
        text-color="#b8c4dd"
        active-text-color="#ffffff"
        router
        @select="drawerOpen = false"
      >
        <AppMenuItems />
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon v-if="isMobile" class="collapse-btn" @click="drawerOpen = true"><Menu /></el-icon>
          <el-icon v-else-if="!isElectron" class="collapse-btn" @click="toggleCollapse"><Fold /></el-icon>
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-badge :value="unread" :hidden="!unread" :max="99" class="notify-bell">
            <el-icon :size="20" @click="goNotify"><Bell /></el-icon>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><Avatar /></el-icon>
              <span class="hidden-mobile">{{ userStore.realName || '未登录' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- ===== 移动端：底部 TabBar（功能与 Web/桌面端互通） ===== -->
    <MobileTabBar v-if="isMobile" @open-drawer="drawerOpen = true" />
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { unreadCount } from '@/api/notify'
import AppMenuItems from './MenuItems.vue'
import MobileTabBar from './MobileTabBar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const unread = ref(0)

const isCollapse = ref(false)
const isMobile = ref(false)
// 桌面端（Electron）检测：更紧凑的客户端风格
const isElectron = ref(
  typeof window !== 'undefined' &&
  (!!window.electronAPI || /Electron/i.test(navigator.userAgent))
)
const drawerOpen = ref(false)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '学情总览')
const asideWidth = computed(() => (isElectron.value ? '188px' : isCollapse.value ? '64px' : '210px'))

function toggleCollapse() {
  isCollapse.value = !isCollapse.value
}

function handleResize() {
  isMobile.value = window.innerWidth <= 768
  isCollapse.value = isMobile.value
  if (!isMobile.value) {
    drawerOpen.value = false
  }
}

function goNotify() {
  router.push('/notify')
}

async function loadUnread() {
  try {
    const userId = userStore.userInfo?.userId
    if (userId) {
      unread.value = await unreadCount(userId)
    }
  } catch {
    unread.value = 0
  }
}

function handleCommand(command) {
  if (command === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' }).then(() => {
      userStore.logout()
      router.push('/login')
    })
  }
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
  loadUnread()
  timer = window.setInterval(loadUnread, 60000)
})

let timer = null

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.app-layout {
  height: 100vh;
}

/* ===== 侧栏（Web / 桌面端） ===== */
.app-aside {
  background: linear-gradient(180deg, #16204b 0%, #1d2c66 100%);
  overflow-x: hidden;
  transition: width 0.25s;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.logo-badge {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #4f7cff, #7c5cff);
  box-shadow: 0 6px 14px rgba(79, 124, 255, 0.35);
}

.logo-text {
  white-space: nowrap;
}

.app-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

/* 菜单选中态：圆角渐变卡片（简约醒目） */
.app-menu :deep(.el-menu-item) {
  margin: 3px 10px;
  border-radius: 9px;
  height: 44px;
  transition: all 0.2s;
}

.app-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}

.app-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #4f7cff, #7c5cff);
  color: #fff;
  box-shadow: 0 4px 12px rgba(79, 124, 255, 0.35);
}

.app-menu :deep(.el-sub-menu__title) {
  margin: 3px 10px;
  border-radius: 9px;
  height: 44px;
}

.app-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08);
}

.app-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #fff;
}

.app-menu :deep(.el-menu-item.is-active i) {
  color: #fff;
}

.aside-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 46px;
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
  flex-shrink: 0;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

/* ===== 顶栏 ===== */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 4px rgba(31, 45, 105, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.notify-bell {
  margin-right: 16px;
  cursor: pointer;
}

.hidden-mobile {
  display: inline;
}

/* ===== 内容区 ===== */
.app-main {
  background: var(--xqfx-bg);
  padding: 0;
  overflow-y: auto;
}

/* ===== 移动端抽屉 ===== */
.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: linear-gradient(180deg, #16204b 0%, #1d2c66 100%);
}

@media (max-width: 768px) {
  .hidden-mobile {
    display: none;
  }
}
</style>
