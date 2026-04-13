import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { post, get } from '@/api/http'
import { wsClient } from '@/api/websocket'
import type { User, LoginDto, RegisterDto, LoginResponse } from '@/types/user'
import type { Result } from '@/types/common'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string | null>(localStorage.getItem('token'))
  const userInfo = ref<User | null>(null)

  // 计算属性
  const isLoggedIn = computed<boolean>(() => {
    return !!token.value && !!userInfo.value
  })

  /**
   * 初始化用户信息
   */
  const initUserInfo = async (): Promise<void> => {
    if (token.value && !userInfo.value) {
      try {
        // 先从localStorage恢复用户信息
        const savedUser = localStorage.getItem('userInfo')
        if (savedUser) {
          userInfo.value = JSON.parse(savedUser)
        }

        // 然后尝试从后端获取最新的用户信息
        try {
          const userId = localStorage.getItem('userId')
          if (userId) {
            const result = await get<any>(`/user/stats?userId=${userId}`)
            if (result.data && result.data.code === 200 && result.data.data) {
              const latestData = result.data.data
              // 更新用户信息
              userInfo.value = {
                ...userInfo.value,
                id: latestData.id,
                username: latestData.username,
                nickname: latestData.nickname || latestData.username,
                rating: latestData.rating,
                level: latestData.level,
                exp: latestData.exp,
                avatar: latestData.avatar
              }
              // 保存到 localStorage
              localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
            }
          }
        } catch (apiError) {
          console.warn('[User Store] 获取最新用户信息失败，使用缓存数据:', apiError)
        }

        // 恢复用户信息后，连接WebSocket
        try {
          await wsClient.connect(token.value)
          console.log('[User Store] WebSocket reconnected successfully')
        } catch (wsError) {
          console.warn('[User Store] WebSocket reconnection failed:', wsError)
        }
      } catch (error) {
        console.error('[User Store] Failed to init user info:', error)
        // 如果获取用户信息失败，静默处理，不中断启动
      }
    } else if (token.value && userInfo.value && !wsClient.isConnected()) {
      // 如果有token和用户信息，但WebSocket未连接，尝试重连
      try {
        await wsClient.connect(token.value)
        console.log('[User Store] WebSocket reconnected successfully')
      } catch (wsError) {
        console.warn('[User Store] WebSocket reconnection failed:', wsError)
      }
    }
  }

  /**
   * 用户登录
   * @param loginDto 登录信息
   * @returns Promise<boolean>
   */
  const login = async (loginDto: LoginDto): Promise<boolean> => {
    try {
      console.log('[User Store] Attempting login with:', loginDto)
      const response = await post<any>('/auth/login', loginDto)
      console.log('[User Store] Login response:', response)

      // 后端返回格式: { success: true, token: "...", user: {...} }
      // http.ts拦截器已经返回response.data，所以直接检查response
      if (response && response.success === true) {
        const { token: newToken, user } = response
        token.value = newToken
        userInfo.value = user
        localStorage.setItem('token', newToken)
        localStorage.setItem('userInfo', JSON.stringify(user))
        localStorage.setItem('userId', String(user.id)) // 保存userId用于API请求

        // 连接 WebSocket（不阻塞登录流程）
        wsClient.connect(newToken).then(() => {
          console.log('[User Store] ✅ WebSocket connected successfully')
          ElMessage.success('WebSocket已连接')

          // 初始化好友系统（包括私聊消息订阅）
          try {
            const { useGameStore } = require('@/store/modules/game')
            const gameStore = useGameStore()
            gameStore.initFriendSystem()
            console.log('[User Store] ✅ 好友系统已初始化')
          } catch (error) {
            console.warn('[User Store] 初始化好友系统失败:', error)
          }
        }).catch((wsError) => {
          console.warn('[User Store] ⚠️ WebSocket connection failed:', wsError)
          // WebSocket 连接失败不影响登录，但显示警告
          ElMessage.warning('WebSocket连接失败，部分功能可能不可用')
        })

        ElMessage.success('登录成功')
        return true
      }
      // 显示后端返回的错误消息
      if (response && response.message) {
        ElMessage.error(response.message)
      } else {
        ElMessage.error('登录失败，请检查用户名和密码')
      }
      return false
    } catch (error) {
      console.error('[User Store] Login failed:', error)
      return false
    }
  }

  /**
   * 用户注册
   * @param registerDto 注册信息
   * @returns Promise<boolean>
   */
  const register = async (registerDto: RegisterDto): Promise<boolean> => {
    try {
      const response = await post<any>('/auth/register', registerDto)
      // 后端返回格式: { success: true, message: "..." }
      // http.ts拦截器已经返回response.data，所以直接检查response
      if (response && (response.success === true || response.success === 'true')) {
        ElMessage.success('注册成功，请登录')
        return true
      }
      return false
    } catch (error) {
      console.error('[User Store] Register failed:', error)
      return false
    }
  }

  /**
   * 用户登出
   */
  const logout = (): void => {
    // 断开 WebSocket 连接
    wsClient.disconnect()

    token.value = null
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('userId')
    ElMessage.success('已登出')
  }

  /**
   * 更新用户信息
   * @param info 用户信息
   */
  const updateUserInfo = (info: Partial<User>): void => {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...info }
    }
  }

  /**
   * 检查是否是当前用户
   * @param userId 用户 ID
   * @returns boolean
   */
  const isCurrentUser = (userId: number): boolean => {
    return userInfo.value?.id === userId
  }

  return {
    // 状态
    token,
    userInfo,
    // 计算属性
    isLoggedIn,
    // 方法
    initUserInfo,
    login,
    register,
    logout,
    updateUserInfo,
    isCurrentUser,
  }
})

// 导出类型
export type UserStore = ReturnType<typeof useUserStore>
