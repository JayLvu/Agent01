import request from './request'

/**
 * 定时任务相关 API
 */
export default {
  list() {
    return request.get('/schedules')
  },
  create(data) {
    return request.post('/schedules', data)
  },
  cancel(id) {
    return request.delete(`/schedules/${id}`)
  }
}
