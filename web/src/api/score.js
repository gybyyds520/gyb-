import request from './request'

/** 分页查询成绩 */
export function pageScores(params) {
  return request.get('/score/page', { params })
}

/** 导入 Excel */
export function importScores(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/score/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 模板下载地址 */
export const scoreTemplateUrl = '/api/score/template'
