import request from './request'

/** 教务适配器状态 */
export function acadHealth() {
  return request.get('/acad/health')
}

/** 执行教务同步 */
export function acadSync(type) {
  return request.post('/acad/sync', null, { params: { type } })
}

/** 教务同步记录 */
export function acadLogs() {
  return request.get('/acad/sync/logs')
}
