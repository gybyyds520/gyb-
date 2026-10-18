import request from './request'

/** 执行全量校验 */
export function runQualityCheck(taskId) {
  return request.post('/data/quality/check', null, { params: { taskId } })
}

/** 质量报告列表 */
export function listQualityReports(dataset) {
  return request.get('/data/quality/reports', { params: { dataset } })
}

/** 质量汇总 */
export function qualitySummary() {
  return request.get('/data/quality/summary')
}
