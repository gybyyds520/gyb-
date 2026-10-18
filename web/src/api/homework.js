import request from './request'

/** 教师端：作业列表（可按班级筛选） */
export function teacherHomeworkList(clazzId) {
  return request.get('/homework/teacher', { params: { clazzId } })
}

/** 教师端：作业详情（含提交明细） */
export function teacherHomeworkDetail(homeworkId) {
  return request.get(`/homework/teacher/${homeworkId}`)
}

/** 教师布置作业 */
export function createHomework(data) {
  return request.post('/homework', data)
}

/** 教师人工补批文本题 */
export function gradeHomework(homeworkId, submissionId, data) {
  return request.post(`/homework/${homeworkId}/grade/${submissionId}`, data)
}

/** AI 批改文本题 */
export function aiGradeHomework(homeworkId, submissionId) {
  return request.post(`/homework/${homeworkId}/ai-grade/${submissionId}`)
}

/** 学生端：我的作业 */
export function myHomework() {
  return request.get('/homework/my')
}

/** 学生端：作业详情（含题目） */
export function studentHomeworkDetail(homeworkId) {
  return request.get(`/homework/${homeworkId}/student`)
}

/** 学生提交作业 */
export function submitHomework(homeworkId, answers) {
  return request.post(`/homework/${homeworkId}/submit`, { answers })
}

/** 家长端：子女作业动态 */
export function parentHomework() {
  return request.get('/homework/parent')
}
