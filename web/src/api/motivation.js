import request from './request'

/** 生成成长激励卡（本地 Stable Diffusion 绘图） */
export function generateMotivationCard(params) {
  return request.post('/motivation/generate', null, { params })
}
