<template>
  <el-container class="app-layout">
    <!-- 桌面端：固定侧边菜单 -->
    <el-aside v-show="!isMobile" :width="isCollapse ? '64px' : '210px'" class="app-aside">
      <div class="logo">
        <span v-if="!isCollapse">学情分析系统</span>
        <span v-else>学情</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="app-menu"
        background-color="#1f2b48"
        text-color="#c0c4cc"
        active-text-color="#ffffff"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
      >
        <AppMenuItems />
      </el-menu>
    </el-aside>

    <!-- 移动端：抽屉式菜单 -->
    <el-drawer v-model="drawerOpen" direction="ltr" :size="220" :with-header="false" class="mobile-drawer">
      <div class="logo" style="background: var(--xqfx-sidebar-bg)">学情分析系统</div>
      <el-menu
        :default-active="activeMenu"
        class="app-menu"
        background-color="#1f2b48"
        text-color="#c0c4cc"
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
          <el-icon v-else class="collapse-btn" @click="toggleCollapse"><Fold /></el-icon>
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
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
// 菜单图标依赖 main.js 全局注册的 Element Plus 图标，通过名称动态渲染
import { useUserStore } from '@/stores/user'
import { unreadCount } from '@/api/notify'
import AppMenuItems from './MenuItems.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const unread = ref(0)

const isCollapse = ref(false)
const isMobile = ref(false)
const drawerOpen = ref(false)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '学情总览')

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
  // 每 60 秒刷新一次未读数
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

.app-aside {
  background: var(--xqfx-sidebar-bg);
  overflow-x: hidden;
  transition: width 0.25s;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 1px;
}

.app-menu {
  border-right: none;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
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

.app-main {
  background: var(--xqfx-bg);
  padding: 0;
  overflow-y: auto;
}

.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--xqfx-sidebar-bg);
}

@media (max-width: 768px) {
  .hidden-mobile {
    display: none;
  }
}
</style>
