import request from './request'

/** 用户分页 */
export function pageUsers(params) {
  return request.get('/sys/users', { params })
}

/** 新增用户 */
export function addUser(data) {
  return request.post('/sys/users', data)
}

/** 修改用户 */
export function updateUser(data) {
  return request.put('/sys/users', data)
}

/** 删除用户 */
export function deleteUser(id) {
  return request.delete(`/sys/users/${id}`)
}

/** 用户角色 */
export function userRoles(id) {
  return request.get(`/sys/users/${id}/roles`)
}

/** 分配角色 */
export function assignRoles(id, roleIds) {
  return request.post(`/sys/users/${id}/roles`, roleIds)
}

/** 角色列表 */
export function listRoles() {
  return request.get('/sys/roles')
}

/** 保存角色 */
export function saveRole(data) {
  return request.post('/sys/roles', data)
}

/** 删除角色 */
export function deleteRole(id) {
  return request.delete(`/sys/roles/${id}`)
}

/** 参数配置列表 */
export function listConfigs() {
  return request.get('/sys/configs')
}

/** 保存配置 */
export function saveConfig(data) {
  return request.post('/sys/configs', data)
}

/** 审计日志分页 */
export function pageAuditLogs(params) {
  return request.get('/sys/audit-logs', { params })
}

/** 数据范围 */
export function dataScope(userId) {
  return request.get(`/sys/data-scope/${userId}`)
}
