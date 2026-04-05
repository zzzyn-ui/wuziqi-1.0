/**
 * API 服务
 * 统一管理所有 API 请求
 */

import http from './http'

// ========== 基础请求方法 ==========
const getAuthConfig = () => {
  const token = localStorage.getItem('token')
  return {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  }
}

// ========== 认证相关 API ==========
export const authApi = {
  // 登录
  login: (data: { username: string; password: string }) =>
    http.post('/v2/auth/login', data),

  // 注册
  register: (data: { username: string; password: string; nickname?: string; email?: string }) =>
    http.post('/v2/auth/register', data)
}

// ========== 用户相关 API ==========
export const userApi = {
  // 获取用户信息
  getUserInfo: (userId: number) =>
    http.get(`/v2/users/${userId}`),

  // 更新用户信息
  updateUserInfo: (userId: number, data: { nickname?: string }) =>
    http.put(`/v2/users/${userId}`, data),

  // 获取用户统计
  getUserStats: (userId: number) =>
    http.get(`/v2/users/${userId}/stats`),

  // 搜索用户
  searchUsers: (query: string) =>
    http.get(`/v2/users/search?q=${encodeURIComponent(query)}`)
}

// ========== 排行榜 API ==========
export const rankApi = {
  // 获取排行榜
  getRankList: (type: 'all' | 'daily' | 'weekly' | 'monthly' = 'all') =>
    http.get(`/v2/rank/${type}`),

  // 获取用户排名
  getUserRank: (type: string, userId: number) =>
    http.get(`/v2/rank/${type}/user/${userId}`)
}

// ========== 对局记录 API ==========
export const recordApi = {
  // 获取对局记录
  getRecords: (userId: number, filter: 'all' | 'win' | 'loss' | 'draw' = 'all', limit = 20) =>
    http.get(`/v2/records/${userId}?filter=${filter}&limit=${limit}`),

  // 获取对局详情
  getGameRecord: (userId: number, gameId: string) =>
    http.get(`/v2/records/${userId}/game/${gameId}`),

  // 获取复盘数据
  getReplayData: (gameId: string) =>
    http.get(`/v2/replay/${gameId}`)
}

// ========== 好友系统 API ==========
export const friendApi = {
  // 获取好友列表
  getFriendList: (userId: number) =>
    http.get(`/v2/friends/${userId}`),

  // 发送好友请求
  sendFriendRequest: (data: { userId: number; targetUserId: number; message?: string }) =>
    http.post('/v2/friends/request', data),

  // 获取好友请求列表
  getFriendRequests: (userId: number) =>
    http.get(`/v2/friends/requests/${userId}`),

  // 处理好友请求
  handleFriendRequest: (requestId: number, userId: number, accept: boolean) =>
    http.put(`/v2/friends/request/${requestId}?userId=${userId}&accept=${accept}`),

  // 删除好友
  deleteFriend: (userId: number, friendId: number) =>
    http.delete(`/v2/friends/${userId}/${friendId}`)
}

// ========== 残局挑战 API ==========
export const puzzleApi = {
  // 获取残局列表
  getPuzzleList: (difficulty: 'beginner' | 'intermediate' | 'advanced' | 'expert' = 'beginner') =>
    http.get(`/v2/puzzles?difficulty=${difficulty}`),

  // 获取残局详情
  getPuzzleDetail: (puzzleId: number) =>
    http.get(`/v2/puzzles/${puzzleId}`),

  // 提交残局答案
  submitPuzzle: (puzzleId: number, data: { userId: number; moves: number[][] }) =>
    http.post(`/v2/puzzles/${puzzleId}/submit`, data),

  // 获取用户残局统计
  getUserPuzzleStats: (userId: number) =>
    http.get(`/v2/puzzles/stats/${userId}`)
}

// 导出 API 模块
export * from './http'
export * from './websocket'

