import request from './request'

/**
 * 审计日志 API
 */
export default {
  logs(params) {
    return request.get('/audit/logs', { params })
  }
}
