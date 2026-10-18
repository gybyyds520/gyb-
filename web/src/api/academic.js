import request from './request'

/** 学生学业指标 */
export function academicIndicator(studentId, termId) {
  return request.get('/academic/indicator', { params: { studentId, termId } })
}

/** 全体学生学业指标（风险优先） */
export function academicBatch(termId, limit) {
  return request.get('/academic/batch', { params: { termId, limit } })
}

/** 百分制成绩换算绩点 */
export function gradePoint(score) {
  return request.get('/academic/grade-point', { params: { score } })
}

/** 导出成绩 */
export function exportScores(termId) {
  return request.get('/export/scores', { params: { termId }, responseType: 'blob' })
}

/** 导出预警 */
export function exportWarnings(termId) {
  return request.get('/export/warnings', { params: { termId }, responseType: 'blob' })
}

/** 导出工单 */
export function exportTickets() {
  return request.get('/export/tickets', { responseType: 'blob' })
}

/** 导出学业指标 */
export function exportIndicators(termId) {
  return request.get('/export/indicators', { params: { termId }, responseType: 'blob' })
}

/** 查询导出字段 Schema */
export function exportSchema(type) {
  return request.get('/export/schema', { params: { type } })
}

/** 触发浏览器下载 */
export function downloadBlob(data, filename) {
  const url = window.URL.createObjectURL(new Blob([data]))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}
