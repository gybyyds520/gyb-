import request from './request'

/** 记录课堂表现 */
export function recordPerformance(data) {
  return request.post('/performance', data)
}

/** 学生课堂表现记录 */
export function listPerformance(studentId, termId) {
  return request.get(`/performance/student/${studentId}`, { params: { termId } })
}

/** 学生表现汇总 */
export function performanceSummary(studentId, termId) {
  return request.get('/performance/summary', { params: { studentId, termId } })
}

/** 表现排名 */
export function performanceRanking(termId, limit) {
  return request.get('/performance/ranking', { params: { termId, limit } })
}

/** 生成演示数据 */
export function generateDemoPerformance(termId, timesPerStudent) {
  return request.post('/performance/demo', null, { params: { termId, timesPerStudent } })
}
