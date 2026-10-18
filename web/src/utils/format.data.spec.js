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

/** 生成 0~100 的成绩序列（含一位小数） */
const scores = Array.from({ length: 101 }, (_, i) => i).flatMap((i) => [i, i + 0.05])

/** 生成 0~1 的比例序列 */
const ratios = Array.from({ length: 101 }, (_, i) => i / 100)

/** 生成指定长度的数字串 */
const digits = (len) => Array.from({ length: len }, (_, i) => String(i % 10)).join('')

const phoneLengths = Array.from({ length: 14 }, (_, i) => i + 7)
const noLengths = Array.from({ length: 17 }, (_, i) => i + 4)

describe('formatScore 数据驱动', () => {
  it.each(scores)('成绩 %s 应格式化为一位小数', (value) => {
    const result = formatScore(value)
    expect(result).toBe(value.toFixed(1))
    expect(result).toMatch(/^-?\d+\.\d$/)
  })

  it.each([-10, -0.04, 100.04, 999.99])('越界成绩 %s 仍应保留一位小数', (value) => {
    expect(formatScore(value)).toBe(value.toFixed(1))
  })
})

describe('formatPercent 数据驱动', () => {
  it.each(ratios)('比例 %s 默认格式化为百分比', (value) => {
    expect(formatPercent(value)).toBe(`${(value * 100).toFixed(1)}%`)
  })

  it.each(ratios)('比例 %s 支持两位小数', (value) => {
    expect(formatPercent(value, 2)).toBe(`${(value * 100).toFixed(2)}%`)
  })

  it.each([0, 1, 2, 3])('小数位 %s 输出位数正确', (digitsCount) => {
    const text = formatPercent(0.5, digitsCount)
    const numberPart = text.replace('%', '').split('.')[1] || ''
    expect(numberPart.length).toBe(digitsCount)
  })
})

describe('预警等级映射数据驱动', () => {
  const levels = [null, undefined, 0, 1, 2, 3, 4, 5, -1, 99]

  it.each(levels)('等级 %s 的文本应受控', (level) => {
    expect(['蓝色', '黄色', '橙色', '红色', '无']).toContain(riskLevelText(level))
  })

  it.each(levels)('等级 %s 的标签类型应受控', (level) => {
    expect(['info', 'warning', 'danger']).toContain(riskLevelTagType(level))
  })

  it.each([1, 2, 3, 4])('有效等级 %s 不显示为无', (level) => {
    expect(riskLevelText(level)).not.toBe('无')
  })
})

describe('脱敏数据驱动', () => {
  it.each(phoneLengths)('长度 %s 的手机号脱敏保留前后缀', (len) => {
    const phone = digits(len)
    const masked = maskPhone(phone)
    expect(masked.startsWith(phone.slice(0, 3))).toBe(true)
    expect(masked.endsWith(phone.slice(-4))).toBe(true)
    expect(masked).toContain('****')
  })

  it.each([1, 3, 6])('长度 %s 的短串原样返回', (len) => {
    const value = digits(len)
    expect(maskPhone(value)).toBe(value)
  })

  it.each(noLengths)('长度 %s 的学号脱敏保留前后缀', (len) => {
    const no = digits(len)
    const masked = maskNo(no)
    expect(masked.startsWith(no.slice(0, 2))).toBe(true)
    expect(masked.endsWith(no.slice(-2))).toBe(true)
    expect(masked).toContain('****')
  })

  it.each([1, 2, 3])('长度 %s 的短学号原样返回', (len) => {
    const value = digits(len)
    expect(maskNo(value)).toBe(value)
  })
})

describe('用户类型映射数据驱动', () => {
  const types = ['STUDENT', 'TEACHER', 'COUNSELOR', 'COLLEGE_ADMIN', 'SCHOOL_ADMIN', 'ADMIN']

  it.each(types)('已知类型 %s 返回中文', (type) => {
    expect(userTypeText(type)).not.toBe('未知')
  })

  it.each(['', 'XXX', null, undefined, 0])('未知类型 %s 返回未知', (type) => {
    expect(userTypeText(type)).toBe('未知')
  })
})
