import request from './request'

/** 我的课程（教师=所授教学班；学生=所在班级课程） */
export function myCourses() {
  return request.get('/course-space/my-courses')
}

// ---------- 章节 ----------

export function chapters(offeringId) {
  return request.get(`/course-space/${offeringId}/chapters`)
}

export function createChapter(offeringId, data) {
  return request.post(`/course-space/${offeringId}/chapters`, null, { params: data })
}

export function updateChapter(id, data) {
  return request.put(`/course-space/chapters/${id}`, null, { params: data })
}

export function deleteChapter(id) {
  return request.delete(`/course-space/chapters/${id}`)
}

// ---------- 资料 ----------

export function materials(offeringId, chapterId) {
  return request.get(`/course-space/${offeringId}/materials`, { params: { chapterId } })
}

export function uploadMaterial(offeringId, formData) {
  return request.post(`/course-space/${offeringId}/materials`, formData)
}

export function deleteMaterial(id) {
  return request.delete(`/course-space/materials/${id}`)
}

export function materialFileUrl(id) {
  return `/api/course-space/materials/${id}/file`
}

// ---------- 讨论区 ----------

export function discussions(offeringId) {
  return request.get(`/course-space/${offeringId}/discussions`)
}

export function createDiscussion(offeringId, content) {
  return request.post(`/course-space/${offeringId}/discussions`, null, { params: { content } })
}

export function replyDiscussion(id, content) {
  return request.post(`/course-space/discussions/${id}/reply`, null, { params: { content } })
}

export function likeDiscussion(id) {
  return request.post(`/course-space/discussions/${id}/like`)
}

export function pinDiscussion(id, pinned) {
  return request.post(`/course-space/discussions/${id}/pin`, null, { params: { pinned } })
}
