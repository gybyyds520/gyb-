import request from './request'

/** 系统概览统计 */
export function overview(termId) {
  return request.get('/dashboard/overview', { params: { termId } })
}

/** 班级学情对比 */
export function clazzComparison(termId) {
  return request.get('/dashboard/clazz-comparison', { params: { termId } })
}

/** 最新待处理预警 */
export function latestWarnings() {
  return request.get('/dashboard/latest-warnings')
}
