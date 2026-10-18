/**
 * 通用格式化工具
 */

/** 成绩保留一位小数，非法值返回 '--' */
export function formatScore(value) {
  if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) {
    return '--'
  }
  return Number(value).toFixed(1)
}

/** 百分比格式化，入参为 0~1 的小数 */
export function formatPercent(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '--'
  }
  return `${(Number(value) * 100).toFixed(digits)}%`
}

/** 预警等级转文本：1蓝 2黄 3橙 4红 */
export function riskLevelText(level) {
  return { 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }[level] || '无'
}

/** 预警等级转 Element Tag 类型 */
export function riskLevelTagType(level) {
  return { 1: 'info', 2: 'warning', 3: 'warning', 4: 'danger' }[level] || 'info'
}

/** 手机号脱敏：138****1234 */
export function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || '--'
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

/** 学号/工号脱敏 */
export function maskNo(no) {
  if (!no || no.length < 4) return no || '--'
  return `${no.slice(0, 2)}****${no.slice(-2)}`
}

/** 用户类型转中文 */
export function userTypeText(type) {
  const map = {
    STUDENT: '学生',
    TEACHER: '教师',
    COUNSELOR: '辅导员',
    COLLEGE_ADMIN: '院级管理者',
    SCHOOL_ADMIN: '校级管理者',
    ADMIN: '系统管理员'
  }
  return map[type] || '未知'
}
