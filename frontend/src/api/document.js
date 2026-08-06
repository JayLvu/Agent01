import request from './request'

/**
 * 文档管理 API(RAG 模块)
 */
export default {
  /** 上传文档 */
  upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  },

  /** 列出所有文档 */
  list() {
    return request.get('/documents')
  },

  /** 删除文档 */
  remove(documentId) {
    return request.delete(`/documents/${documentId}`)
  }
}
