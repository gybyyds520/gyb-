import request from './request'

/** 查询学生画像 */
export function getProfile(studentId, termId) {
  return request.get('/profile/student', { params: { studentId, termId } })
}

/** 画像快照历史（成长档案时间轴数据源） */
export function getSnapshots(studentId) {
  return request.get('/profile/snapshots', { params: { studentId } })
}

/** 偏科分析（学生各课程强弱项对比） */
export function getBias(studentId, termId) {
  return request.get('/profile/bias', { params: { studentId, termId } })
}

/** 偏科大模型归因 */
export function biasAttribution(studentId, termId) {
  return request.post('/profile/bias/attribution', null, { params: { studentId, termId } })
}

/** 生成画像快照 */
export function generateProfile(studentId, termId) {
  return request.post('/profile/generate', null, { params: { studentId, termId } })
}

/** 批量生成全体学生画像 */
export function batchGenerateProfile(termId) {
  return request.post('/profile/batch', null, { params: { termId } })
}

/** 标签定义列表 */
export function listTags() {
  return request.get('/profile/tags')
}

/** 保存标签定义 */
export function saveTag(data) {
  return request.post('/profile/tags', data)
}
