import axios from 'axios'
import { Message } from 'element-ui'

// axios 实例: 统一拦截错误、附加 baseURL
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截: 可在此附加 token
service.interceptors.request.use(
  config => config,
  error => Promise.reject(error)
)

// 响应拦截: 统一错误提示
service.interceptors.response.use(
  response => response.data,
  error => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    Message.error(msg)
    return Promise.reject(error)
  }
)

export default service
