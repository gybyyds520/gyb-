import request from './request'

/** 我的学情总览（学生端） */
export function myOverview(termId) {
  return request.get('/portal/overview', { params: { termId } })
}

/** 我的成绩（学生端） */
export function myScores(termId) {
  return request.get('/portal/scores', { params: { termId } })
}

/** 我的学业指标（学生端） */
export function myIndicator(termId) {
  return request.get('/portal/indicator', { params: { termId } })
}

/** 我的子女列表（家长端） */
export function myChildren(termId) {
  return request.get('/parent/children', { params: { termId } })
}

/** 子女学业详情（家长端） */
export function childDetail(studentId, termId) {
  return request.get(`/parent/children/${studentId}`, { params: { termId } })
}

/** 家长回执（家长端） */
export function parentFeedback(studentId, bizType, bizId, ackStatus, content) {
  return request.post('/parent/feedback', null, {
    params: { studentId, bizType, bizId, ackStatus, content }
  })
}

/** 三端演示账号 */
export function demoAccounts() {
  return request.get('/sys/accounts/demo')
}

/** 初始化三端账号 */
export function initAccounts() {
  return request.post('/sys/accounts/init')
}
