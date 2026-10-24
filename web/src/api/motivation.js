import request from './request'

/** 生成成长激励卡（本地 Stable Diffusion 绘图） */
export function generateMotivationCard(params) {
  return request.post('/motivation/generate', null, { params })
}

/** 历史激励卡记录（学生=本人；教师/家长按数据范围；管理员查全部） */
export function motivationRecords(params) {
  return request.get('/motivation/records', { params })
}

/** 批量生成激励卡（管理员/教师，单次最多 20 人） */
export function motivationBatchGenerate(data) {
  return request.post('/motivation/batch-generate', data)
}
