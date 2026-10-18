import request from './request'

/** 分页查询学生 */
export function pageStudents(params) {
  return request.get('/student/page', { params })
}

/** 根据ID查询 */
export function getStudent(id) {
  return request.get(`/student/${id}`)
}

/** 新增 */
export function addStudent(data) {
  return request.post('/student', data)
}

/** 修改 */
export function updateStudent(data) {
  return request.put('/student', data)
}

/** 删除 */
export function deleteStudent(id) {
  return request.delete(`/student/${id}`)
}

/** 导入 Excel */
export function importStudents(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/student/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 模板下载地址 */
export const studentTemplateUrl = '/api/student/template'
