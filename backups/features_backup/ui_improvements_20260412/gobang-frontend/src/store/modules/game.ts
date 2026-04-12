import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { GameState, GameMoveDto, PieceType } from '@/types/game'
import { wsClient } from '@/api/websocket'
import { ElNotification } from 'element-plus'

// 好友状态接口
export interface FriendStatus {
  id: number
  username: string
  nickname?: string
  online: boolean
  inGame: boolean
  status: number // 0=离线, 1=在线, 2=游戏中, 3=匹配中
}

// 游戏邀请接口
export interface GameInvitation {
  id: number
  inviterId: number
  inviterName: string
  gameMode: string
  expiresAt: string
}

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

  // 好友系统状态
  const friends = ref<Map<number, FriendStatus>>(new Map())
  const pendingInvitations = ref<GameInvitation[]>([])
  const friendSubscriptions = ref<string[]>([])

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

    // 通过 WebSocket 发送落子信息
    // 不在这里更新本地棋盘，等待后端确认（通过GAME_STATE消息）
    // 这样可以确保前后端状态一致
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

  // ==================== 好友系统相关 ====================

  /**
   * 初始化好友系统监听
   */
  const initFriendSystem = (): void => {
    console.log('[GameStore] 初始化好友系统...')

    // 先清理旧的订阅（避免重复订阅）
    cleanupFriendSystem()

    // 订阅好友状态更新
    const statusSub = wsClient.subscribeFriendStatus((data) => {
      handleFriendStatusUpdate(data)
    })
    if (statusSub) friendSubscriptions.value.push(statusSub)

    // 订阅游戏邀请
    const inviteSub = wsClient.subscribeFriendInvitation((data) => {
      handleGameInvitation(data)
    })
    if (inviteSub) friendSubscriptions.value.push(inviteSub)

    // 订阅邀请响应
    const responseSub = wsClient.subscribeFriendInvitationResponse((data) => {
      handleInvitationResponse(data)
    })
    if (responseSub) friendSubscriptions.value.push(responseSub)

    // 订阅私聊消息（全局订阅，用于接收所有私聊消息）
    // 需要从 userStore 获取当前用户ID
    const { useUserStore } = require('@/store/modules/user')
    const userStore = useUserStore()
    const currentUserId = userStore.userInfo?.id

    if (currentUserId) {
      const privateMsgSub = wsClient.subscribePrivateMessage(currentUserId, (data) => {
        console.log('[GameStore] 收到私聊消息:', data)
        // 私聊消息会在 FriendsView 中处理，这里只是记录日志
        // 如果需要全局通知（比如未读消息提醒），可以在这里添加
      })
      if (privateMsgSub) friendSubscriptions.value.push(privateMsgSub)
    }

    // 请求好友列表
    wsClient.requestFriendSubscribe()

    console.log('[GameStore] 好友系统初始化完成，订阅数:', friendSubscriptions.value.length)
  }

  /**
   * 清理好友系统监听
   */
  const cleanupFriendSystem = (): void => {
    friendSubscriptions.value.forEach(subId => {
      wsClient.unsubscribe(subId)
    })
    friendSubscriptions.value = []
    friends.value.clear()
    pendingInvitations.value = []
  }

  /**
   * 处理好友状态更新
   */
  const handleFriendStatusUpdate = (data: any): void => {
    console.log('[GameStore] 好友状态更新:', data)

    // 如果是好友列表
    if (data.type === 'FRIEND_LIST' && Array.isArray(data.friends)) {
      data.friends.forEach((friend: any) => {
        friends.value.set(friend.id, {
          id: friend.id,
          username: friend.username,
          nickname: friend.nickname,
          online: friend.online || friend.status > 0,
          inGame: friend.status === 2,
          status: friend.status || 0
        })
      })
      return
    }

    // 处理单个好友状态变化
    if (data.friendId) {
      const friend = friends.value.get(data.friendId)
      if (friend) {
        if (data.type === 'FRIEND_ONLINE') {
          friend.online = true
          friend.status = 1
        } else if (data.type === 'FRIEND_OFFLINE') {
          friend.online = false
          friend.status = 0
          friend.inGame = false
        } else if (data.type === 'FRIEND_STATUS_CHANGE') {
          friend.status = data.status
          friend.online = data.status > 0
          friend.inGame = data.status === 2
        }
      }
    }
  }

  /**
   * 处理游戏邀请
   */
  const handleGameInvitation = (data: any): void => {
    console.log('[GameStore] 收到游戏邀请:', data)

    if (data.type === 'GAME_INVITATION') {
      ElNotification({
        title: '游戏邀请',
        message: `${data.fromNickname || data.fromUsername} 邀请你进行${data.gameMode === 'ranked' ? '排位' : '休闲'}对局`,
        type: 'info',
        duration: 0,
        position: 'top-right',
        onClick: () => {
          // 处理点击邀请通知
          console.log('处理邀请:', data.message)
        }
      })

      // 添加到待处理邀请列表
      pendingInvitations.value.push({
        id: parseInt(data.message || '0'),
        inviterId: data.fromUserId,
        inviterName: data.fromNickname || data.fromUsername,
        gameMode: data.gameMode,
        expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString()
      })
    }
  }

  /**
   * 处理邀请响应
   */
  const handleInvitationResponse = (data: any): void => {
    console.log('[GameStore] 邀请响应:', data)

    if (data.type === 'INVITATION_ACCEPTED') {
      ElNotification({
        title: '邀请已接受',
        message: '对方接受了你的游戏邀请，正在进入房间...',
        type: 'success'
      })

      // 移除待处理邀请
      pendingInvitations.value = pendingInvitations.value.filter(inv => inv.id !== parseInt(data.message || '0'))

      // 可以在这里导航到游戏房间
      // router.push(`/game/${data.roomId}`)
    } else if (data.type === 'INVITATION_REJECTED') {
      ElNotification({
        title: '邀请被拒绝',
        message: data.message || '对方拒绝了你的邀请',
        type: 'warning'
      })
    } else if (data.type === 'INVITATION_CANCELLED') {
      ElNotification({
        title: '邀请已取消',
        message: '邀请已被取消',
        type: 'info'
      })
      pendingInvitations.value = []
    } else if (data.type === 'INVITATION_SENT') {
      // 邀请已发送，添加到待处理列表
      console.log('邀请已发送:', data)
    }
  }

  /**
   * 发送游戏邀请
   */
  const sendGameInvitation = (friendId: number, gameMode: string = 'casual'): void => {
    wsClient.sendFriendInvitation(friendId, gameMode)
  }

  /**
   * 响应游戏邀请
   */
  const respondGameInvitation = (invitationId: number, accept: boolean): void => {
    wsClient.respondFriendInvitation(invitationId, accept)
    if (accept) {
      // 清空待处理邀请
      pendingInvitations.value = pendingInvitations.value.filter(inv => inv.id !== invitationId)
    } else {
      pendingInvitations.value = pendingInvitations.value.filter(inv => inv.id !== invitationId)
    }
  }

  /**
   * 取消游戏邀请
   */
  const cancelGameInvitation = (invitationId: number): void => {
    wsClient.cancelFriendInvitation(invitationId)
  }

  /**
   * 获取好友状态
   */
  const getFriendStatus = (friendId: number): FriendStatus | undefined => {
    return friends.value.get(friendId)
  }

  /**
   * 获取所有在线好友
   */
  const getOnlineFriends = (): FriendStatus[] => {
    return Array.from(friends.value.values()).filter(f => f.online)
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
    friends,
    pendingInvitations,
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
    // 好友系统方法
    initFriendSystem,
    cleanupFriendSystem,
    handleFriendStatusUpdate,
    handleGameInvitation,
    handleInvitationResponse,
    sendGameInvitation,
    respondGameInvitation,
    cancelGameInvitation,
    getFriendStatus,
    getOnlineFriends,
  }
})

// 导出类型
export type GameStore = ReturnType<typeof useGameStore>
