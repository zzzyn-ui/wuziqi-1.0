// 棋子类型
export type PieceType = 'empty' | 'black' | 'white'

// 游戏状态
export type GameStatus = 'waiting' | 'playing' | 'ended'

// 玩家角色
export type PlayerRole = 'black' | 'white' | 'observer'

// 游戏落子 DTO
export interface GameMoveDto {
  roomId: string
  x: number
  y: number
}

// 游戏状态
export interface GameState {
  roomId: string
  board: PieceType[][]
  currentTurn: 'black' | 'white'
  blackPlayer: {
    id: number
    username: string
    avatar?: string
  }
  whitePlayer: {
    id: number
    username: string
    avatar?: string
  }
  status: GameStatus
  winner?: 'black' | 'white' | null
  moveHistory: GameMoveDto[]
  createdAt: string
  updatedAt: string
}

// 房间信息
export interface RoomInfo {
  id: string
  name: string
  host: {
    id: number
    username: string
    avatar?: string
  }
  players: Array<{
    id: number
    username: string
    avatar?: string
    role: PlayerRole
  }>
  maxPlayers: number
  currentPlayerCount: number
  status: GameStatus
  createdAt: string
}

// 房间列表项
export interface RoomListItem {
  id: string
  name: string
  hostName: string
  currentPlayerCount: number
  maxPlayers: number
  status: GameStatus
}

// 匹配请求 DTO
export interface MatchDto {
  mode: 'quick' | 'ranked'
  timeout?: number
}

// 匹配信息
export interface MatchInfo {
  matchId: string
  mode: 'quick' | 'ranked'
  estimatedTime: number
  status: 'matching' | 'found' | 'cancelled' | 'timeout'
}

// 游戏结果
export interface GameResult {
  roomId: string
  winner: 'black' | 'white' | 'draw'
  loser?: 'black' | 'white'
  isDraw: boolean
  moveCount: number
  duration: number
  ratingChange?: {
    winner: number
    loser: number
  }
}
