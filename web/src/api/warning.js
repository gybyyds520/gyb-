import request from './request'

/** 预警分页 */
export function pageWarnings(params) {
  return request.get('/warning/page', { params })
}

/** 预警等级统计 */
export function warningStatistics(termId) {
  return request.get('/warning/statistics', { params: { termId } })
}

/** 预警维度类型统计（学业/出勤/作业/挂科/进度/心理/行为等） */
export function warningTypeStatistics(termId) {
  return request.get('/warning/type-statistics', { params: { termId } })
}

/** 处理预警 */
export function handleWarning(id, data) {
  return request.post(`/warning/${id}/handle`, null, { params: data })
}

/** 预警规则列表 */
export function listWarningRules() {
  return request.get('/warning/rules')
}

/** 保存预警规则 */
export function saveWarningRule(data) {
  return request.post('/warning/rules', data)
}

/** 删除预警规则 */
export function deleteWarningRule(id) {
  return request.delete(`/warning/rules/${id}`)
}

/** 预警多维下钻 */
export function warningDrill(termId, dimension) {
  return request.get('/warning/drill', { params: { termId, dimension } })
}

/** 学生风险事件时间线 */
export function warningTimeline(studentId) {
  return request.get('/warning/timeline', { params: { studentId } })
}

/** 学业指标触发判定 */
export function academicTrigger(studentId, termId) {
  return request.get('/warning/academic-trigger', { params: { studentId, termId } })
}

/** 预警大模型归因 */
export function warningAttribution(id) {
  return request.post(`/warning/${id}/attribution`)
}
