import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'

const TOKEN_KEY = 'xqfx_token'
const USER_KEY = 'xqfx_user'

/** 管理端角色：可进入学情总览、学生名单、预警与干预闭环 */
const STAFF_TYPES = ['ADMIN', 'SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COUNSELOR', 'TEACHER']

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    realName: (state) => state.userInfo?.realName || state.userInfo?.username || '',
    userType: (state) => state.userInfo?.userType || '',
    loginType: (state) => state.userInfo?.loginType || '',
    roles: (state) => state.userInfo?.roles || [],
    /** 后端下发的可见菜单，前端据此渲染，避免菜单与权限不一致 */
    menus: (state) => state.userInfo?.menus || [],
    studentId: (state) => state.userInfo?.studentId || null,
    isStaff: (state) => STAFF_TYPES.includes(state.userInfo?.userType),
    isStudent: (state) => state.userInfo?.userType === 'STUDENT',
    isParent: (state) => state.userInfo?.userType === 'PARENT'
  },
  actions: {
    async login(username, password, loginType = 'TEACHER') {
      const data = await loginApi(username, password, loginType)
      this.token = data.accessToken
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        userType: data.userType,
        loginType: data.loginType,
        roles: data.roles || [],
        menus: data.menus || [],
        studentId: data.studentId || null,
        teacherId: data.teacherId || null
      }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo))
      return data
    },
    /** 是否可见指定功能编码（与后端 RolePermissionService 保持一致） */
    can(code) {
      return (this.menus || []).some((m) => m.code === code)
    },
    /** 是否可见指定路由 */
    canVisit(path) {
      if (!this.menus.length) return true
      return (this.menus || []).some((m) => m.path === path)
    },
    /** 按角色判断（用于细粒度按钮级控制） */
    hasRole(...types) {
      return types.some((t) => this.userType === t || (this.roles || []).includes(t))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})

export { STAFF_TYPES }
