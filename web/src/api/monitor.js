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
