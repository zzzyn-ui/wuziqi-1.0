import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/common'

// 创建 axios 实例
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
http.interceptors.request.use(
  (config) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
http.interceptors.response.use(
  (response: AxiosResponse<any>) => {
    const { data } = response

    // 调试日志（开发环境）
    if (import.meta.env.DEV) {
      console.log('[HTTP] Response:', data)
    }

    // 后端返回格式检查：
    // 1. 登录/注册: { success: true, token: "...", user: {...} }
    // 2. 其他API: { code: 200, success: true, data: {...} }

    // 成功响应判断
    const isSuccess = data.success === true || data.success === 'true' || data.code === 200

    if (isSuccess) {
      // 成功，直接返回
      return response
    } else {
      // 失败，显示错误信息
      const errorMsg = data.message || data.error || '请求失败'
      console.error('[HTTP] Error response:', data)

      // 只显示一次错误消息（避免重复）
      if (errorMsg && !errorMsg.includes('请求失败')) {
        ElMessage.error(errorMsg)
      }

      return Promise.reject(new Error(errorMsg))
    }
  },
  (error: AxiosError<Result<any>>) => {
    if (error.response) {
      const { status, data } = error.response

      switch (status) {
        case 401:
          // 未授权，清除所有用户数据并跳转到登录页
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          localStorage.removeItem('userId')

          // 避免已经在登录页时重复跳转
          if (!window.location.pathname.includes('/login')) {
            ElMessage.error('登录已过期，请重新登录')
            window.location.href = '/login'
          }
          break
        case 403:
          // 对于非关键API，静默处理403错误
          const url = error.config?.url || ''
          // 用户统计等非关键功能的403错误不显示弹窗
          if (!url.includes('/user/stats') && !url.includes('/user/info')) {
            if (import.meta.env.DEV) {
              console.error('[HTTP] 403 Forbidden:', data)
            }
            ElMessage.error('没有权限访问')
          } else {
            console.warn('[HTTP] 403 - 静默处理:', url)
          }
          break
        case 404:
          // 静默处理404，避免弹窗干扰
          console.warn('[HTTP] 404 Not Found:', error.config?.url)
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(data?.message || `请求失败，状态码：${status}`)
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error(error.message || '请求配置错误')
    }

    return Promise.reject(error)
  }
)

// 通用请求方法
export const request = <T = any>(config: AxiosRequestConfig): Promise<Result<T>> => {
  return http.request(config)
}

export const get = <T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> => {
  return http.get(url, config)
}

export const post = <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> => {
  return http.post(url, data, config)
}

export const put = <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> => {
  return http.put(url, data, config)
}

export const del = <T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> => {
  return http.delete(url, config)
}

export default http
