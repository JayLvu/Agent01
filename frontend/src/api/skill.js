import request from './request'

/**
 * Skill 管理 API
 */
export default {
  /** 上传 Skill(.md / .markdown / .txt) */
  upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/skills', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000
    })
  },

  /** 列出所有 Skill */
  list() {
    return request.get('/skills')
  },

  /** 读取 Skill 内容 */
  getContent(id) {
    return request.get(`/skills/${id}/content`)
  },

  /** 切换启用状态 */
  toggleEnabled(id, enabled) {
    return request.patch(`/skills/${id}/enabled`, null, {
      params: { enabled }
    })
  },

  /** 删除 Skill */
  remove(id) {
    return request.delete(`/skills/${id}`)
  }
}
