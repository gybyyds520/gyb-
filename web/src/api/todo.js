import request from './request'

/** 我的待办（按角色聚合） */
export function getTodos() {
  return request.get('/todo/list')
}
