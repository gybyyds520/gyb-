<template>
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
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/**
 * 端级菜单布局：按功能作用归类，教师/管理端、学生端、家长端各自成组。
 * 结构以功能编码为准（与后端 RolePermissionService.MENU 对应），
 * 后端仍负责"角色可见哪些功能"，本表只负责"如何归类展示"。
 */
const END_LAYOUT = {
  STUDENT: {
    top: ['MY_STUDY', 'VIP'],
    groups: [
      { key: 'learn', title: '学习助手', codes: ['AGENT', 'GOAL', 'TODO'] },
      { key: 'msg', title: '消息中心', codes: ['NOTIFY', 'MESSAGE'] }
    ]
  },
  PARENT: {
    top: ['PARENT_PORTAL', 'VIP'],
    groups: [
      { key: 'msg', title: '消息中心', codes: ['NOTIFY', 'MESSAGE', 'TODO'] }
    ]
  },
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

const menuByCode = (code) => (userStore.menus || []).find((m) => m.code === code)
const layoutFor = computed(() => END_LAYOUT[userStore.userType] || END_LAYOUT.DEFAULT)

const layoutTop = computed(() => layoutFor.value.top.map(menuByCode).filter(Boolean))

const layoutGroups = computed(() => {
  const topExtra = []
  const groups = []
  for (const g of layoutFor.value.groups) {
    const items = g.codes.map(menuByCode).filter(Boolean)
    if (!items.length) continue
    if (items.length === 1) {
      topExtra.push(items[0])
      continue
    }
    groups.push({ key: g.key, title: g.title, items })
  }
  return { topExtra, groups }
})

const topMenus = computed(() => [...layoutTop.value, ...layoutGroups.value.topExtra])
const groupMenus = computed(() => layoutGroups.value.groups)

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
  MONITOR: 'Monitor',
  COURSE_SPACE: 'Reading',
  PAPER_SCAN: 'DocumentCopy',
  HOMEWORK_TEACHER: 'Notebook'
}

function iconOf(code) {
  return ICONS[code] || 'Menu'
}
</script>
