import request from './request'

/** 查询基础数据列表：college / major / clazz / term / course / teacher / offering */
export function listBase(type) {
  return request.get(`/base/${type}/list`)
}

export function getBase(type, id) {
  return request.get(`/base/${type}/${id}`)
}

export function saveBase(type, data) {
  return request.post(`/base/${type}`, data)
}

export function deleteBase(type, id) {
  return request.delete(`/base/${type}/${id}`)
}
