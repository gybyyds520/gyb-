import { describe, it, expect } from 'vitest'
import {
  formatScore,
  formatPercent,
  riskLevelText,
  riskLevelTagType,
  maskPhone,
  maskNo,
  userTypeText
} from './format'

describe('formatScore', () => {
  it.each([
    [95, '95.0'],
    [59.94, '59.9'],
    [0, '0.0'],
    [100, '100.0']
  ])('数值 %s 应格式化为 %s', (input, expected) => {
    expect(formatScore(input)).toBe(expected)
  })

  it.each([[null], [undefined], [''], ['abc'], [NaN]])('非法值 %s 应返回 --', (input) => {
    expect(formatScore(input)).toBe('--')
  })
})

describe('formatPercent', () => {
  it.each([
    [0.85, '85.0%'],
    [1, '100.0%'],
    [0, '0.0%'],
    [0.6667, '66.7%']
  ])('小数 %s 应格式化为 %s', (input, expected) => {
    expect(formatPercent(input)).toBe(expected)
  })

  it('支持自定义小数位', () => {
    expect(formatPercent(0.856, 2)).toBe('85.60%')
  })
})

describe('riskLevelText', () => {
  it.each([
    [1, '蓝色'],
    [2, '黄色'],
    [3, '橙色'],
    [4, '红色'],
    [9, '无'],
    [null, '无']
  ])('等级 %s 应显示为 %s', (level, expected) => {
    expect(riskLevelText(level)).toBe(expected)
  })
})

describe('riskLevelTagType', () => {
  it.each([
    [1, 'info'],
    [2, 'warning'],
    [3, 'warning'],
    [4, 'danger']
  ])('等级 %s 对应标签类型 %s', (level, expected) => {
    expect(riskLevelTagType(level)).toBe(expected)
  })
})

describe('maskPhone', () => {
  it.each([
    ['13812345678', '138****5678'],
    ['15900001111', '159****1111']
  ])('手机号 %s 应脱敏为 %s', (input, expected) => {
    expect(maskPhone(input)).toBe(expected)
  })

  it('长度不足时原样返回', () => {
    expect(maskPhone('138')).toBe('138')
  })

  it('空值返回 --', () => {
    expect(maskPhone('')).toBe('--')
  })
})

describe('maskNo', () => {
  it('学号脱敏', () => {
    expect(maskNo('2301132013')).toBe('23****13')
  })

  it('空值返回 --', () => {
    expect(maskNo(null)).toBe('--')
  })
})

describe('userTypeText', () => {
  it.each([
    ['STUDENT', '学生'],
    ['TEACHER', '教师'],
    ['COUNSELOR', '辅导员'],
    ['ADMIN', '系统管理员'],
    ['UNKNOWN', '未知']
  ])('类型 %s 应显示 %s', (type, expected) => {
    expect(userTypeText(type)).toBe(expected)
  })
})
