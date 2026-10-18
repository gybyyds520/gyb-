import request from './request'

/** 预测服务健康检查 */
export function mlHealth() {
  return request.get('/prediction/health')
}

/** 训练模型 */
export function trainModel(modelType) {
  return request.post('/prediction/train', null, { params: { modelType } })
}

/** 执行批量预测 */
export function runPredict(termId, modelType) {
  return request.post('/prediction/run', null, { params: { termId, modelType } })
}

/** 预测结果分页 */
export function pagePredictions(params) {
  return request.get('/prediction/result/page', { params })
}

/** 已训练模型列表 */
export function listModels() {
  return request.get('/prediction/models')
}
