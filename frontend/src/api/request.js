// ============================================================
// axios 实例：统一前缀 /api、统一 JWT、统一错误提示
// 响应约定：{ code, message, data }，code === 0 为成功
// ============================================================
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken, clearAuth } from '@/stores/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截：带上 JWT
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一拆包 + 错误提示
request.interceptors.response.use(
  (response) => {
    // 文件流响应（导出下载）原样返回
    const ctype = String(response.headers['content-type'] || '')
    if (response.config.responseType === 'blob') {
      return response
    }
    if (ctype.includes('application/json') && typeof response.data === 'object' && response.data !== null) {
      const res = response.data
      if (res.code === 0) {
        return res.data
      }
      if (res.code === 401) {
        ElMessage.error(res.message || '登录已过期，请重新登录')
        clearAuth()
        router.push('/login')
        return Promise.reject(new Error(res.message || '401'))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    let msg = body?.message || error.message || '网络错误'
    if (status === 401) {
      msg = msg || '登录已过期，请重新登录'
      clearAuth()
      router.push('/login')
      return Promise.reject(error)
    }
    if (status === 403) {
      msg = msg || '没有操作权限'
    } else if (status === 404) {
      msg = msg || '请求的资源不存在'
    } else if (!status) {
      msg = '无法连接服务器，请稍后再试'
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
