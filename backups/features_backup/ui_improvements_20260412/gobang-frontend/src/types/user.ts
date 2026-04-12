// 用户接口
export interface User {
  id: number
  username: string
  nickname?: string
  email?: string
  avatar?: string
  level?: number
  wins: number
  losses: number
  draws: number
  rating: number
  createdAt?: string
  updatedAt?: string
}

// 登录请求 DTO
export interface LoginDto {
  username: string
  password: string
}

// 注册请求 DTO
export interface RegisterDto {
  username: string
  email?: string
  password: string
  confirmPassword: string
  nickname?: string
}

// 登录响应
export interface LoginResponse {
  token: string
  user: User
}

// 用户状态
export interface UserState {
  token: string | null
  userInfo: User | null
  isLoggedIn: boolean
}
