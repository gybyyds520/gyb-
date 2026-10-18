import request from './request'

/** 用户登录 */
/**
 * 登录
 * @param {string} loginType 登录入口 TEACHER教师端 / STUDENT学生端 / PARENT家长端
 */
export function login(username, password, loginType) {
  return request.post('/auth/login', { username, password, loginType })
}

/** 查询三端登录入口与功能权限矩阵 */
export function permissionMatrix() {
  return request.get('/auth/permission-matrix')
}

/** 获取当前登录用户 */
export function getCurrentUser() {
  return request.get('/auth/me')
}
