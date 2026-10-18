import request from './request'

/** 试卷列表 */
export function listPapers(termId) {
  return request.get('/exam/papers', { params: { termId } })
}

/** 试卷分析结果 */
export function getAnalysis(paperId) {
  return request.get(`/exam/papers/${paperId}`)
}

/** 重新分析 */
export function analyzePaper(paperId) {
  return request.post(`/exam/papers/${paperId}/analyze`)
}

/** 知识点掌握度 */
export function knowledgeMastery(paperId) {
  return request.get(`/exam/papers/${paperId}/knowledge`)
}

/** 生成演示试卷 */
export function generateDemoPaper(termId) {
  return request.post('/exam/demo', null, { params: { termId } })
}

/** 试卷薄弱题（得分率低于 60%） */
export function weakQuestions(paperId) {
  return request.get(`/exam/papers/${paperId}/weak-questions`)
}

/** 学生在某份试卷上的错题 */
export function studentWrongQuestions(paperId, studentId) {
  return request.get(`/exam/papers/${paperId}/students/${studentId}/wrong-questions`)
}

/** 举一反三：为错题生成同类巩固练习 */
export function generatePractice(questionId, paperId, studentId, count) {
  return request.post('/exam/practice/generate', null, {
    params: { questionId, paperId, studentId, count }
  })
}

/** 练习详情 */
export function practiceDetail(id) {
  return request.get(`/exam/practice/${id}`)
}

/** 某道错题的历史生成记录 */
export function practicesOfQuestion(questionId) {
  return request.get(`/exam/practice/question/${questionId}`)
}

/** 我的巩固练习（学生端） */
export function myPractices() {
  return request.get('/exam/practice/my')
}
