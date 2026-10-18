import request from './request'

/** 校级大屏聚合数据 */
export function bigScreenOverview() {
  return request.get('/bigscreen/overview')
}
