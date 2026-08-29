import request from './request'

/**
 * 对话相关 API
 */
export default {
  /** 同步对话: 一次性返回完整回复 */
  chat(data) {
    return request.post('/chat', data)
  },

  /** 上传附件文件到后端解析,返回提取的文本内容 */
  upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/chat/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  },

  /** 清空指定会话历史 */
  clearSession(sessionId) {
    return request.delete(`/chat/${sessionId}`)
  },

  /** 停止生成(真正取消服务端进行中的流式请求) */
  cancel(requestId) {
    return request.post(`/chat/cancel/${requestId}`)
  },

  /** 健康检查 */
  health() {
    return request.get('/health')
  }
}
