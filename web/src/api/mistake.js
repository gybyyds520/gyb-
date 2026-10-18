import request from './request'

/** 手动收录错题（学生本人） */
export function addMistake(data) {
  return request.post('/mistake', data)
}

/** 我的错题本（按课程分组） */
export function myMistakeList() {
  return request.get('/mistake/my')
}

/** 我的错题统计（总量/知识点/错因分布） */
export function myMistakeStats() {
  return request.get('/mistake/my/stats')
}

/** 教师查看某学生错题 */
export function studentMistakeList(studentId) {
  return request.get(`/mistake/student/${studentId}`)
}

/** 标记错题已掌握 */
export function markMistakeMastered(id) {
  return request.put(`/mistake/${id}/mastered`)
}

/** 删除错题 */
export function removeMistake(id) {
  return request.delete(`/mistake/${id}`)
}
