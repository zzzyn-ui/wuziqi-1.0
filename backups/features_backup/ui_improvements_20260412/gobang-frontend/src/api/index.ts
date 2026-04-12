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
    http.get(`/user/${userId}`),

  // 更新用户信息
  updateUserInfo: (userId: number, data: { nickname?: string; username?: string }) =>
    http.post('/user/update', { id: userId, ...data }),

  // 获取用户统计
  getUserStats: (userId: number) =>
    http.get(`/user/stats`, { params: { userId } }),

  // 搜索用户
  searchUsers: (query: string) => {
    const userId = localStorage.getItem('userId')
    return http.get(`/friend/search`, { params: { userId, keyword: query } })
  },

  // 记录游戏结果（通用，支持所有游戏模式）
  recordGame: (
    userId: number,
    result: 'win' | 'lose' | 'draw',
    gameMode: 'PVE' | 'PVP' | 'RANKED' | 'FRIEND' | 'ROOM' = 'PVE',
    moves?: any[],
    boardState?: string
  ) =>
    http.post(`/user/game/record`, {
      userId,
      result,
      gameMode,
      ...(moves && { moves }),
      ...(boardState && { boardState })
    }),

  // 记录PVE游戏结果（保持兼容性）
  recordPVEGame: (userId: number, result: 'win' | 'lose' | 'draw') =>
    http.post(`/user/pve/record`, { userId, result })
}

// ========== 排行榜 API ==========
export const rankApi = {
  // 获取排行榜
  getRankList: (type: 'all' | 'daily' | 'weekly' | 'monthly' = 'all') =>
    http.get(`/rank/${type}`),

  // 获取用户排名
  getUserRank: (type: string, userId: number) =>
    http.get(`/rank/${type}/user/${userId}`)
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
    http.get(`/v2/replay/${gameId}`),

  // 获取对局记录列表（新API）
  getRecordList: (userId: number, days = 7) =>
    http.get(`/api/record/list`, { params: { userId, days } }),

  // 获取对局详情用于复盘（新API）
  getRecordDetail: (recordId: number) =>
    http.get(`/api/record/${recordId}`),

  // 获取对局统计
  getRecordStats: (userId: number) =>
    http.get(`/api/record/stats`, { params: { userId } })
}

// ========== 好友系统 API ==========
export const friendApi = {
  // 获取好友列表
  getFriendList: (userId: number) =>
    http.get(`/friend/list`, { params: { userId } }),

  // 发送好友请求
  sendFriendRequest: (data: { userId: number; targetUserId: number; message?: string }) =>
    http.post('/friend/request', { userId: data.userId, friendId: data.targetUserId, message: data.message }),

  // 获取好友请求列表
  getFriendRequests: (userId: number) =>
    http.get(`/friend/requests`, { params: { userId } }),

  // 处理好友请求
  handleFriendRequest: (requestId: number, userId: number, accept: boolean) =>
    http.post(accept ? '/friend/accept' : '/friend/reject', { userId, requestId }),

  // 删除好友
  deleteFriend: (userId: number, friendId: number) =>
    http.delete(`/friend/delete`, { params: { userId, friendId } })
}

// ========== 残局挑战 API ==========
export const puzzleApi = {
  // 获取残局列表
  getPuzzleList: (difficulty: 'easy' | 'medium' | 'hard' | 'expert' = 'easy') =>
    http.get(`/puzzle/list?difficulty=${difficulty}`),

  // 获取残局详情
  getPuzzleDetail: (puzzleId: number) =>
    http.get(`/puzzle/${puzzleId}`),

  // 提交残局答案
  submitPuzzle: (puzzleId: number, data: { userId: number; moves: number[][] }) =>
    http.post(`/puzzle/${puzzleId}/submit`, data),

  // 获取用户残局统计
  getUserPuzzleStats: (userId: number) =>
    http.get(`/puzzle/stats?userId=${userId}`),

  // 获取难度列表
  getDifficulties: () =>
    http.get('/puzzle/difficulties'),

  // 获取类型列表
  getTypes: () =>
    http.get('/puzzle/types')
}

// ========== 观战模式 API ==========
export const observerApi = {
  // 获取可观战房间列表
  getObservableRooms: () =>
    http.get('/observer/rooms'),

  // 加入观战
  joinObserver: (roomId: string, userId: number) =>
    http.post('/observer/join', { roomId, userId }),

  // 离开观战
  leaveObserver: (roomId: string, userId: number) =>
    http.post('/observer/leave', { roomId, userId }),

  // 获取房间观战者列表
  getObservers: (roomId: string) =>
    http.get(`/observer/${roomId}/observers`),

  // 获取房间观战者数量
  getObserverCount: (roomId: string) =>
    http.get(`/observer/${roomId}/count`)
}

// 导出 API 模块
export * from './http'
export * from './websocket'

