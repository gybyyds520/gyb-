import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '学情总览', code: 'DASHBOARD' }
      },
      {
        path: 'data/student',
        name: 'StudentList',
        component: () => import('@/views/data/StudentListView.vue'),
        meta: { title: '学生管理', code: 'STUDENT_LIST' }
      },
      {
        path: 'data/score',
        name: 'ScoreList',
        component: () => import('@/views/data/ScoreListView.vue'),
        meta: { title: '成绩管理', code: 'SCORE_LIST' }
      },
      {
        path: 'data/demo',
        name: 'DemoData',
        component: () => import('@/views/data/DemoDataView.vue'),
        meta: { title: '演示数据', code: 'DEMO_DATA' }
      },
      {
        path: 'prediction',
        name: 'Prediction',
        component: () => import('@/views/prediction/PredictionView.vue'),
        meta: { title: '学业预测', code: 'PREDICTION' }
      },
      {
        path: 'warning',
        name: 'Warning',
        component: () => import('@/views/warning/WarningView.vue'),
        meta: { title: '学业预警', code: 'WARNING' }
      },
      {
        path: 'intervention',
        name: 'Intervention',
        component: () => import('@/views/intervention/InterventionView.vue'),
        meta: { title: '干预闭环', code: 'INTERVENTION' }
      },
      {
        path: 'ai',
        name: 'AiAnalysis',
        component: () => import('@/views/ai/AiAnalysisView.vue'),
        meta: { title: 'AI 学情分析', code: 'AI' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '学生画像', code: 'PROFILE' }
      },
      {
        path: 'notify',
        name: 'Notify',
        component: () => import('@/views/notify/NotifyView.vue'),
        meta: { title: '通知中心', code: 'NOTIFY' }
      },
      {
        path: 'message',
        name: 'Message',
        component: () => import('@/views/message/MessageView.vue'),
        meta: { title: '站内通讯', code: 'MESSAGE' }
      },
      {
        path: 'vip',
        name: 'Vip',
        component: () => import('@/views/vip/VipView.vue'),
        meta: { title: '会员中心', code: 'VIP' }
      },
      {
        path: 'sys',
        name: 'System',
        component: () => import('@/views/sys/SystemView.vue'),
        meta: { title: '系统管理', code: 'SYS' }
      },
      {
        path: 'exam',
        name: 'Exam',
        component: () => import('@/views/exam/ExamView.vue'),
        meta: { title: '试卷分析', code: 'EXAM' }
      },
      {
        path: 'clazz',
        name: 'Clazz',
        component: () => import('@/views/clazz/ClazzView.vue'),
        meta: { title: '班级学情', code: 'CLAZZ' }
      },
      {
        path: 'quality',
        name: 'Quality',
        component: () => import('@/views/data/QualityView.vue'),
        meta: { title: '数据质量', code: 'QUALITY' }
      },
      {
        path: 'academic',
        name: 'Academic',
        component: () => import('@/views/academic/AcademicView.vue'),
        meta: { title: '学业指标', code: 'ACADEMIC' }
      },
      {
        path: 'governance',
        name: 'Governance',
        component: () => import('@/views/governance/GovernanceView.vue'),
        meta: { title: '预警治理', code: 'GOVERNANCE' }
      },
      {
        path: 'my',
        name: 'MyStudy',
        component: () => import('@/views/student/MyStudyView.vue'),
        meta: { title: '我的学情', code: 'MY_STUDY' }
      },
      {
        path: 'goal',
        name: 'Goal',
        component: () => import('@/views/goal/GoalView.vue'),
        meta: { title: '学习目标', code: 'GOAL' }
      },
      {
        path: 'todo',
        name: 'Todo',
        component: () => import('@/views/todo/TodoView.vue'),
        meta: { title: '待办中心', code: 'TODO' }
      },
      {
        path: 'acad',
        name: 'Acad',
        component: () => import('@/views/acad/AcadView.vue'),
        meta: { title: '教务对接', code: 'ACAD' }
      },
      {
        path: 'model',
        name: 'Model',
        component: () => import('@/views/model/ModelView.vue'),
        meta: { title: '模型管理', code: 'MODEL' }
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/AiAgentView.vue'),
        meta: { title: '学情智能体', code: 'AGENT' }
      },
      {
        path: 'bigscreen',
        name: 'BigScreen',
        component: () => import('@/views/bigscreen/BigScreenView.vue'),
        meta: { title: '校级大屏', code: 'BIG_SCREEN' }
      },
      {
        path: 'parent',
        name: 'Parent',
        component: () => import('@/views/parent/ParentView.vue'),
        meta: { title: '家校协同', code: 'PARENT_PORTAL' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 各角色的默认首页：学生与家长进入各自的个人门户 */
export function homePathOf(userStore) {
  if (userStore.isStudent) return '/my'
  if (userStore.isParent) return '/parent'
  return '/dashboard'
}

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && userStore.token && to.name === 'Login') {
    return { path: homePathOf(userStore) }
  }
  // 角色隔离：菜单之外，直接在地址栏输入越权路径也会被拦截
  if (to.meta.code && userStore.menus.length && !userStore.can(to.meta.code)) {
    return { path: homePathOf(userStore) }
  }
  // 根路径按角色分流
  if (to.path === '/' && userStore.token) {
    return { path: homePathOf(userStore) }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 学情预测分析系统` : '学情预测分析系统'
})

export default router
