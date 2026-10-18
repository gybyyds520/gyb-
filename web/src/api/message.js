import request from './request'

/** 我的通讯录（可联系对象由身份关系推导） */
export function listContacts() {
  return request.get('/message/contacts')
}

/** 会话列表（含未读数与最后一条消息） */
export function listConversations() {
  return request.get('/message/conversations')
}

/** 与某人的聊天记录 */
export function messageHistory(peerUserId, limit) {
  return request.get('/message/history', { params: { peerUserId, limit } })
}

/** 发送消息 */
export function sendMessage(receiverUserId, content) {
  return request.post('/message/send', { receiverUserId, content })
}

/** 将与某人的会话标为已读 */
export function markConversationRead(peerUserId) {
  return request.post('/message/read', null, { params: { peerUserId } })
}

/** 未读消息总数 */
export function unreadMessageCount() {
  return request.get('/message/unread')
}
