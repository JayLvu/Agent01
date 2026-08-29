import request from './request'

/**
 * Token/成本统计 API
 */
export default {
  overview() {
    return request.get('/stats/overview')
  }
}
