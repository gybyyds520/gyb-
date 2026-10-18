<template>
  <el-container class="app-layout">
    <el-aside :width="isCollapse ? '64px' : '210px'" class="app-aside" :class="{ 'is-mobile': isMobile }">
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
        <!-- 菜单项由后端按角色下发，教师/学生/家长三端各不相同 -->
        <template v-for="item in topMenus" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon><component :is="iconOf(item.code)" /></el-icon>
            <template #title>{{ item.name }}</template>
          </el-menu-item>
        </template>
        <el-sub-menu v-for="g in groupMenus" :key="g.key" :index="g.key">
          <template #title>
            <el-icon><component :is="iconOf(g.items[0].code)" /></el-icon>
            <span>{{ g.title }}</span>
          </template>
          <el-menu-item v-for="m in g.items" :key="m.path" :index="m.path">{{ m.name }}</el-menu-item>
        </el-sub-menu>
        <!-- 兜底：后端新功能自动按原分组展示，防止遗漏 -->
        <el-sub-menu v-for="g in extraGroups" :key="'x-' + g.key" :index="'x-' + g.key">
          <template #title>
            <el-icon><component :is="iconOf(g.items[0].code)" /></el-icon>
            <span>{{ GROUP_TITLES[g.key] || g.title }}</span>
          </template>
          <el-menu-item v-for="m in g.items" :key="m.path" :index="m.path">{{ m.name }}</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse"><Fold /></el-icon>
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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const unread = ref(0)

const isCollapse = ref(false)
const isMobile = ref(false)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '学情总览')

/**
 * 端级菜单布局：按功能作用归类，教师/管理端、学生端、家长端各自成组。
 * 结构以功能编码为准（与后端 RolePermissionService.MENU 对应），
 * 后端仍负责"角色可见哪些功能"，本表只负责"如何归类展示"。
 */
const END_LAYOUT = {
  // 学生端：个人学情门户
  STUDENT: {
    top: ['MY_STUDY', 'VIP'],
    groups: [
      { key: 'learn', title: '学习助手', codes: ['AGENT', 'GOAL', 'TODO'] },
      { key: 'msg', title: '消息中心', codes: ['NOTIFY', 'MESSAGE'] }
    ]
  },
  // 家长端：家校协同门户
  PARENT: {
    top: ['PARENT_PORTAL', 'VIP'],
    groups: [
      { key: 'msg', title: '消息中心', codes: ['NOTIFY', 'MESSAGE', 'TODO'] }
    ]
  },
  // 教师/管理端：学情治理工作台
  DEFAULT: {
    top: ['DASHBOARD', 'BIG_SCREEN'],
    groups: [
      { key: 'data', title: '学情数据', codes: ['STUDENT_LIST', 'SCORE_LIST', 'EXAM', 'CLAZZ', 'QUALITY', 'DEMO_DATA'] },
      { key: 'analysis', title: '学情分析', codes: ['PROFILE', 'AI', 'AGENT', 'MODEL'] },
      { key: 'warning', title: '预测预警', codes: ['PREDICTION', 'WARNING', 'INTERVENTION', 'GOVERNANCE', 'ACADEMIC'] },
      { key: 'collab', title: '协同沟通', codes: ['NOTIFY', 'MESSAGE', 'TODO', 'GOAL'] },
      { key: 'sys', title: '系统管理', codes: ['ACAD', 'SYS', 'MONITOR'] }
    ]
  }
}

/** 后端下发菜单按 code 索引，便于按布局结构取名称与路由 */
const menuByCode = (code) => (userStore.menus || []).find((m) => m.code === code)

/** 当前端布局配置（未知角色回退教师端结构） */
const layoutFor = computed(() => END_LAYOUT[userStore.userType] || END_LAYOUT.DEFAULT)

/** 顶级菜单项：布局指定的顶级项（仅保留当前角色可见的） */
const layoutTop = computed(() =>
  layoutFor.value.top.map(menuByCode).filter(Boolean)
)

/** 分组菜单：布局分组的可见项；组内仅 1 项时提升为顶级，避免孤零子菜单 */
const layoutGroups = computed(() => {
  const topExtra = []
  const groups = []
  for (const g of layoutFor.value.groups) {
    const items = g.codes.map(menuByCode).filter(Boolean)
    if (!items.length) continue
    // 单项目提升为顶级菜单项
    if (items.length === 1) {
      topExtra.push(items[0])
      continue
    }
    groups.push({ key: g.key, title: g.title, items })
  }
  return { topExtra, groups }
})

/** 最终顶级菜单：布局顶级 + 单项目提升 */
const topMenus = computed(() => [...layoutTop.value, ...layoutGroups.value.topExtra])

/** 最终分组菜单 */
const groupMenus = computed(() => layoutGroups.value.groups)

/** 兜底：后端新下发的、布局尚未登记的功能，按原分组追加为子菜单，保证不漏菜单 */
const extraGroups = computed(() => {
  const known = new Set([
    ...layoutFor.value.top,
    ...layoutFor.value.groups.flatMap((g) => g.codes)
  ])
  const byGroup = {}
  for (const m of userStore.menus || []) {
    if (known.has(m.code)) continue
    if (!byGroup[m.group]) byGroup[m.group] = { key: m.group, title: m.group, items: [] }
    byGroup[m.group].items.push(m)
  }
  return Object.values(byGroup)
})

/** 分组标题与图标映射 */
const GROUP_TITLES = {
  data: '学情数据',
  prediction: '预测与预警',
  profile: '画像与可视化',
  analysis: '教学分析',
  governance: '预警治理',
  sys: '系统管理'
}

const ICONS = {
  DASHBOARD: 'DataLine',
  MY_STUDY: 'User',
  PARENT_PORTAL: 'House',
  STUDENT_LIST: 'Folder',
  SCORE_LIST: 'Document',
  DEMO_DATA: 'MagicStick',
  PREDICTION: 'TrendCharts',
  WARNING: 'Warning',
  INTERVENTION: 'Connection',
  AI: 'ChatDotRound',
  PROFILE: 'UserFilled',
  NOTIFY: 'Bell',
  MESSAGE: 'ChatLineRound',
  VIP: 'Coin',
  EXAM: 'EditPen',
  CLAZZ: 'School',
  QUALITY: 'DataAnalysis',
  ACADEMIC: 'Medal',
  GOVERNANCE: 'Setting',
  SYS: 'Tools',
  GOAL: 'Aim',
  TODO: 'Finished',
  ACAD: 'Link',
  MODEL: 'Cpu',
  AGENT: 'ChatLineSquare',
  BIG_SCREEN: 'DataBoard',
  MONITOR: 'Monitor'
}

function iconOf(code) {
  return ICONS[code] || 'Menu'
}

function toggleCollapse() {
  isCollapse.value = !isCollapse.value
}

function handleResize() {
  isMobile.value = window.innerWidth <= 768
  isCollapse.value = isMobile.value
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

.app-main {
  background: var(--xqfx-bg);
  padding: 0;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .app-aside.is-mobile {
    position: fixed;
    height: 100vh;
    z-index: 2000;
  }
}
</style>
