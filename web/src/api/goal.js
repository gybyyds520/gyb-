import request from './request'

/** 学习目标列表（学生本人无需传 studentId） */
export function listGoals(studentId, termId) {
  return request.get('/goal/list', { params: { studentId, termId } })
}

/** 保存学习目标 */
export function saveGoal(studentId, data) {
  return request.post('/goal/save', data, { params: { studentId } })
}

/** 更新目标状态 */
export function updateGoalStatus(id, status) {
  return request.post(`/goal/${id}/status`, null, { params: { status } })
}

/** 删除目标 */
export function deleteGoal(id) {
  return request.delete(`/goal/${id}`)
}

/** 学习资源推荐 */
export function getRecommendations(studentId, termId) {
  return request.get('/goal/recommendations', { params: { studentId, termId } })
}
