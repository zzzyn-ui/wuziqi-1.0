import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { GameState, GameMoveDto, PieceType } from '@/types/game'
import { wsClient } from '@/api/websocket'

export const useGameStore = defineStore('game', () => {
  // 状态
  const roomId = ref<string>('')
  const board = ref<PieceType[][]>([])
  const currentTurn = ref<'black' | 'white'>('black')
  const blackPlayer = ref<GameState['blackPlayer'] | null>(null)
  const whitePlayer = ref<GameState['whitePlayer'] | null>(null)
  const gameStatus = ref<'waiting' | 'playing' | 'ended'>('waiting')
  const winner = ref<'black' | 'white' | null>(null)
  const moveHistory = ref<GameMoveDto[]>([])

  // 计算属性
  const isGameActive = computed<boolean>(() => {
    return gameStatus.value === 'playing'
  })

  const isGameEnded = computed<boolean>(() => {
    return gameStatus.value === 'ended'
  })

  const canMove = computed<boolean>(() => {
    return isGameActive.value && !!roomId.value
  })

  /**
   * 初始化游戏棋盘
   * @param size 棋盘大小，默认 15
   */
  const initBoard = (size: number = 15): void => {
    board.value = Array(size)
      .fill(null)
      .map(() => Array(size).fill('empty'))
  }

  /**
   * 设置房间ID
   * @param id 房间ID
   */
  const setRoomId = (id: string): void => {
    roomId.value = id
  }

  /**
   * 设置游戏状态
   * @param state 游戏状态 (后端返回的格式)
   */
  const setGameState = (state: any): void => {
    roomId.value = state.roomId

    // 转换棋盘格式：后端发送的是数字数组 (0,1,2)，前端需要 ('empty','black','white')
    if (state.board && Array.isArray(state.board)) {
      board.value = state.board.map((row: number[]) =>
        row.map((cell: number) => {
          if (cell === 0) return 'empty'
          if (cell === 1) return 'black'
          if (cell === 2) return 'white'
          return 'empty'
        })
      )
    }

    // 转换当前回合：后端发送的是数字 (1或2)，前端需要 ('black'或'white')
    if (typeof state.currentTurn === 'number') {
      currentTurn.value = state.currentTurn === 1 ? 'black' : 'white'
    } else {
      currentTurn.value = state.currentTurn
    }

    // 玩家信息 - 后端现在发送完整的玩家对象
    if (state.blackPlayer) {
      blackPlayer.value = state.blackPlayer
    } else if (state.blackPlayerId) {
      // 兼容旧格式：只有ID
      blackPlayer.value = { id: state.blackPlayerId, username: '玩家' + state.blackPlayerId }
    }

    if (state.whitePlayer) {
      whitePlayer.value = state.whitePlayer
    } else if (state.whitePlayerId) {
      // 兼容旧格式：只有ID
      whitePlayer.value = { id: state.whitePlayerId, username: '玩家' + state.whitePlayerId }
    }

    // 游戏状态
    if (state.gameStatus === 'PLAYING') {
      gameStatus.value = 'playing'
    } else if (state.gameStatus === 'FINISHED') {
      gameStatus.value = 'ended'
    } else if (state.gameStatus === 'WAITING') {
      gameStatus.value = 'waiting'
    } else {
      gameStatus.value = state.status || 'waiting'
    }

    // 胜者信息
    if (state.winnerId) {
      // 根据胜者ID推断胜者颜色
      winner.value = state.winnerId === blackPlayer.value?.id ? 'black' : 'white'
    } else {
      winner.value = state.winner || null
    }

    // 移动历史（如果后端发送了）
    moveHistory.value = state.moveHistory || []

    console.log('[GameStore] 设置游戏状态:', {
      currentTurn: currentTurn.value,
      blackPlayer: blackPlayer.value,
      whitePlayer: whitePlayer.value,
      gameStatus: gameStatus.value
    })
  }

  /**
   * 落子
   * @param x X坐标
   * @param y Y坐标
   */
  const makeMove = (x: number, y: number): void => {
    if (!canMove.value) {
      return
    }

    // 检查位置是否为空
    if (board.value[x][y] !== 'empty') {
      return
    }

    const playerColor = currentTurn.value
    const move: GameMoveDto = {
      roomId: roomId.value,
      x,
      y
    }

    // 更新棋盘
    board.value[x][y] = playerColor
    moveHistory.value.push({
      row: x,
      col: y,
      player: playerColor
    } as any)

    // 通过 WebSocket 发送落子信息
    if (roomId.value) {
      wsClient.sendMove(move)
    }
  }

  /**
   * 处理对手落子
   * @param move 落子信息 (后端返回的格式)
   */
  const handleOpponentMove = (move: any): void => {
    // 对手的颜色应该是当前回合的反方
    const opponentColor = currentTurn.value === 'black' ? 'white' : 'black'

    // 更新棋盘
    board.value[move.x][move.y] = opponentColor
    moveHistory.value.push({
      row: move.x,
      col: move.y,
      player: opponentColor
    } as any)

    // 切换回合到下一玩家（对手落子后，应该轮到当前玩家）
    // 如果对手是白棋，落子后轮到黑棋；如果对手是黑棋，落子后轮到白棋
    currentTurn.value = opponentColor === 'black' ? 'white' : 'black'
  }

  /**
   * 开始游戏
   */
  const startGame = (): void => {
    gameStatus.value = 'playing'
    initBoard()
  }

  /**
   * 结束游戏
   * @param win 获胜方
   */
  const endGame = (win: 'black' | 'white' | null): void => {
    gameStatus.value = 'ended'
    winner.value = win
  }

  /**
   * 重置游戏
   */
  const resetGame = (): void => {
    roomId.value = ''
    board.value = []
    currentTurn.value = 'black'
    blackPlayer.value = null
    whitePlayer.value = null
    gameStatus.value = 'waiting'
    winner.value = null
    moveHistory.value = []
  }

  /**
   * 获取指定位置的棋子
   * @param row 行
   * @param col 列
   * @returns 棋子类型
   */
  const getPiece = (row: number, col: number): PieceType => {
    return board.value[row]?.[col] ?? 'empty'
  }

  /**
   * 检查是否在棋盘范围内
   * @param row 行
   * @param col 列
   * @returns boolean
   */
  const isInBoard = (row: number, col: number): boolean => {
    return row >= 0 && row < board.value.length && col >= 0 && col < board.value[0].length
  }

  return {
    // 状态
    roomId,
    board,
    currentTurn,
    blackPlayer,
    whitePlayer,
    gameStatus,
    winner,
    moveHistory,
    // 计算属性
    isGameActive,
    isGameEnded,
    canMove,
    // 方法
    initBoard,
    setRoomId,
    setGameState,
    makeMove,
    handleOpponentMove,
    startGame,
    endGame,
    resetGame,
    getPiece,
    isInBoard,
  }
})

// 导出类型
export type GameStore = ReturnType<typeof useGameStore>
