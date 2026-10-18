import request from './request'

/** 生成演示数据 */
export function generateDemo(studentCount) {
  return request.post('/demo/generate', null, { params: { studentCount } })
}

/** 清空演示数据 */
export function clearDemo() {
  return request.delete('/demo/clear')
}
