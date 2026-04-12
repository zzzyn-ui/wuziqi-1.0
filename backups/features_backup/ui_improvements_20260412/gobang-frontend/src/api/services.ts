import { get, post, del } from './http'
import type { LoginDto, RegisterDto, LoginResponse, User } from '@/types/user'
import type { RoomInfo, RoomListItem, MatchDto, MatchInfo, GameResult } from '@/types/game'
import type { Result, PageResult, PageParams } from '@/types/common'

/**
 * 用户 API
 */
export const userApi = {
  /**
   * 用户登录
   */
  login: (data: LoginDto) => post<LoginResponse>('/auth/login', data),

  /**
   * 用户注册
   */
  register: (data: RegisterDto) => post<Result<User>>('/auth/register', data),

  /**
   * 获取用户信息
   */
  getUserInfo: () => get<User>('/user/info'),

  /**
   * 更新用户信息
   */
  updateUserInfo: (data: Partial<User>) => post<Result<User>>('/user/update', data),

  /**
   * 用户登出
   */
  logout: () => post<void>('/auth/logout'),
}

/**
 * 房间 API
 */
export const roomApi = {
  /**
   * 创建房间
   */
  createRoom: (data: { name: string; maxPlayers: number }) => post<Result<RoomInfo>>('/room/create', data),

  /**
   * 加入房间
   */
  joinRoom: (roomId: string) => post<Result<RoomInfo>>('/room/join', { roomId }),

  /**
   * 离开房间
   */
  leaveRoom: (roomId: string) => post<void>('/room/leave', { roomId }),

  /**
   * 获取房间信息
   */
  getRoomInfo: (roomId: string) => get<RoomInfo>(`/room/${roomId}`),

  /**
   * 获取房间列表
   */
  getRoomList: (params: PageParams) => get<PageResult<RoomListItem>>('/room/list', { params }),

  /**
   * 开始游戏
   */
  startGame: (roomId: string) => post<void>('/room/start', { roomId }),

  /**
   * 删除房间
   */
  deleteRoom: (roomId: string) => del<void>(`/room/${roomId}`),
}

/**
 * 匹配 API
 */
export const matchApi = {
  /**
   * 开始匹配
   */
  startMatch: (data: MatchDto) => post<Result<MatchInfo>>('/match/start', data),

  /**
   * 取消匹配
   */
  cancelMatch: (matchId: string) => post<void>('/match/cancel', { matchId }),

  /**
   * 获取匹配信息
   */
  getMatchInfo: (matchId: string) => get<MatchInfo>(`/match/${matchId}`),
}

/**
 * 游戏 API
 */
export const gameApi = {
  /**
   * 获取游戏状态
   */
  getGameState: (roomId: string) => get(`/game/${roomId}/state`),

  /**
   * 认输
   */
  surrender: (roomId: string) => post<GameResult>(`/game/${roomId}/surrender`),

  /**
   * 请求平局
   */
  requestDraw: (roomId: string) => post(`/game/${roomId}/draw`),

  /**
   * 响应平局请求
   */
  respondDraw: (roomId: string, accept: boolean) => post(`/game/${roomId}/draw/respond`, { accept }),
}

export default {
  user: userApi,
  room: roomApi,
  match: matchApi,
  game: gameApi,
}
