import request from './request'

/**
 * 对话相关 API
 */
export default {
  /** 同步对话: 一次性返回完整回复 */
  chat(data) {
    return request.post('/chat', data)
  },

  /** 清空指定会话历史 */
  clearSession(sessionId) {
    return request.delete(`/chat/${sessionId}`)
  },

  /** 健康检查 */
  health() {
    return request.get('/health')
  }
}
