import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  (config) => {
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      const serverMessage = data?.message || data?.error || ''

      const messages: Record<number, string> = {
        400: serverMessage || '请求参数错误',
        401: '未授权，请登录',
        403: '拒绝访问',
        404: '资源不存在',
        413: '文件大小超出限制',
        500: serverMessage || '服务器内部错误',
        502: serverMessage || '代理错误，请检查后端服务'
      }
      const displayMsg = messages[status] || `请求失败(${status}): ${serverMessage}`
      ElMessage.error(displayMsg)
      error.displayMessage = displayMsg
    } else if (error.message?.includes('timeout') || error.code === 'ECONNABORTED') {
      const msg = '请求超时，请检查后端服务及 Ollama/PGVector 是否正常运行'
      ElMessage.error(msg)
      error.displayMessage = msg
    } else if (error.message?.includes('Network Error')) {
      const msg = '网络错误，请检查后端服务是否已启动'
      ElMessage.error(msg)
      error.displayMessage = msg
    } else {
      ElMessage.error('网络异常，请检查连接')
      error.displayMessage = '网络异常'
    }
    return Promise.reject(error)
  }
)

export default request
