import request from './request'

/** 我的通知列表 */
export function listNotify(userId, unreadOnly) {
  return request.get('/notify/list', { params: { userId, unreadOnly } })
}

/** 未读数量 */
export function unreadCount(userId) {
  return request.get('/notify/unread-count', { params: { userId } })
}

/** 标记已读 */
export function markRead(id, userId) {
  return request.post(`/notify/${id}/read`, null, { params: { userId } })
}

/** 全部标记已读 */
export function markAllRead(userId) {
  return request.post('/notify/read-all', null, { params: { userId } })
}

/** 按学期同步预警通知 */
export function syncWarningNotify(termId) {
  return request.post('/notify/sync-warnings', null, { params: { termId } })
}
