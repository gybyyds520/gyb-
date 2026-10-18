import request from './request'

/** 模型分页列表 */
export function pageModels(params) {
  return request.get('/model/list', { params })
}

/** 模型详情（含指标与特征重要性） */
export function getModel(id) {
  return request.get(`/model/${id}`)
}

/** 多模型指标对比 */
export function modelCompare() {
  return request.get('/model/compare')
}

/** 模型上线/下线 */
export function updateModelStatus(id, status) {
  return request.post(`/model/${id}/status`, null, { params: { status } })
}
