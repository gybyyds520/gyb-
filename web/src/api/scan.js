import request from './request'

/** 上传试卷（图片/PDF/Word）并提取文本 */
export function uploadPaper(formData) {
  return request.post('/scan/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 录入列表 */
export function paperScanList(params) {
  return request.get('/scan/list', { params })
}

/** 录入详情（含解析题目） */
export function paperScanDetail(id) {
  return request.get(`/scan/${id}`)
}

/** AI 解析题目并生成试卷分析 */
export function parsePaper(id) {
  return request.post(`/scan/${id}/parse`)
}

/** 删除录入批次 */
export function deletePaper(id) {
  return request.delete(`/scan/${id}`)
}

/** 题库列表（跨批次检索） */
export function bankList(params) {
  return request.get('/bank/list', { params })
}

/** 编辑题库题目 */
export function bankEdit(id, data) {
  return request.put(`/bank/${id}`, null, { params: data })
}

/** 删除题库题目 */
export function bankDelete(id) {
  return request.delete(`/bank/${id}`)
}

/** 题库题目一键布置作业 */
export function bankToHomework(data) {
  return request.post('/bank/to-homework', null, { params: data })
}
