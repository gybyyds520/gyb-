import request from './request'

/** 会员等级与价格 */
export function vipLevels() {
  return request.get('/vip/levels')
}

/** 免费功能与会员功能对照表 */
export function vipFeatures() {
  return request.get('/vip/features')
}

/** 我的会员信息 */
export function myVip() {
  return request.get('/vip/my')
}

/** 下单 */
export function createVipOrder(levelCode, period) {
  return request.post('/vip/order', null, { params: { levelCode, period } })
}

/** 支付并开通（模拟支付） */
export function payVipOrder(orderNo, payMethod) {
  return request.post('/vip/pay', null, { params: { orderNo, payMethod } })
}

/** 我的充值订单 */
export function myVipOrders() {
  return request.get('/vip/orders')
}

/** 成绩趋势（VIP1） */
export function myScoreTrend() {
  return request.get('/portal/score-trend')
}

/** 子女 AI 学情解读（VIP2） */
export function childAiReport(studentId, termId) {
  return request.get('/parent/ai-report', { params: { studentId, termId } })
}
