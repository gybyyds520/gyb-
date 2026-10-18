import request from './request'

/** 工单列表 */
export function listTickets(params) {
  return request.get('/intervention/tickets', { params })
}

/** 由预警创建工单 */
export function createTicket(params) {
  return request.post('/intervention/tickets', null, { params })
}

/** 关闭工单 */
export function closeTicket(id, params) {
  return request.post(`/intervention/tickets/${id}/close`, null, { params })
}

/** 追加干预记录 */
export function appendLog(id, params) {
  return request.post(`/intervention/tickets/${id}/logs`, null, { params })
}

/** 干预记录列表 */
export function listLogs(id) {
  return request.get(`/intervention/tickets/${id}/logs`)
}

/** 扫描超时工单并升级 */
export function scanOverdue() {
  return request.post('/intervention/sla/scan')
}

/** 处置时效统计 */
export function slaStatistics() {
  return request.get('/intervention/sla/statistics')
}

/** 辅导员有效性反馈打标 */
export function ticketFeedback(id, feedback, note) {
  return request.post(`/intervention/tickets/${id}/feedback`, null, { params: { feedback, note } })
}
