import request from './request'

/** 监察中心：三端概览 */
export function monitorOverview() {
  return request.get('/monitor/overview')
}

/** 监察中心：各端最新动态 */
export function monitorLatest() {
  return request.get('/monitor/latest')
}

/** 监察中心：指定学生数据全景 */
export function monitorStudent(studentId) {
  return request.get(`/monitor/student/${studentId}`)
}

/** 监察中心：任意维度全量记录 */
export function monitorRecords(params) {
  return request.get('/monitor/records', { params })
}

/** 监察中心：三端全部账号 */
export function monitorAccounts(role) {
  return request.get('/monitor/accounts', { params: { role } })
}

/** 监察中心：任意账号数据全景 */
export function monitorAccount(id) {
  return request.get(`/monitor/account/${id}`)
}

/** 监察中心：删除任意学生/教师/家长账号 */
export function monitorDeleteAccount(id) {
  return request.delete(`/monitor/account/${id}`)
}
