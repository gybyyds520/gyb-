import request from './request'

/** 教师发起签到（返回签到码） */
export function createCheckin(offeringId, title) {
  return request.post('/checkin', null, { params: { offeringId, title } })
}

/** 学生输码签到 */
export function signCheckin(id, code) {
  return request.post(`/checkin/${id}/sign`, null, { params: { code } })
}

/** 教师结束签到 */
export function closeCheckin(id) {
  return request.post(`/checkin/${id}/close`)
}

/** 教师补签 */
export function manualSign(id, studentId) {
  return request.post(`/checkin/${id}/manual-sign`, null, { params: { studentId } })
}

/** 教学班签到活动列表 */
export function listCheckins(offeringId) {
  return request.get('/checkin/list', { params: { offeringId } })
}

/** 签到统计（应到/已到/未到） */
export function checkinStats(id) {
  return request.get(`/checkin/${id}/stats`)
}

/** 学生视角：所属课程签到记录 */
export function studentCheckins() {
  return request.get('/checkin/student/mine')
}
