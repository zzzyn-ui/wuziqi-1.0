<template>
  <div class="game-container">
    <div class="game-header">
      <el-button class="back-button" circle @click="handleBack">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="header-info">
        <h1 class="page-title">房间对战</h1>
        <p class="room-id">房间号: {{ roomId }}</p>
      </div>
      <el-button class="invite-button" @click="handleInvite" :icon="Share" circle>
        <el-icon><Share /></el-icon>
      </el-button>
    </div>

    <!-- 玩家信息栏 -->
    <div class="players-bar">
      <div class="player-card" :class="{ active: gameStore.currentTurn === 'black' }">
        <el-avatar :size="48" :src="gameStore.blackPlayer?.avatar" class="player-avatar">
          {{ gameStore.blackPlayer?.username?.charAt(0).toUpperCase() }}
        </el-avatar>
        <div class="player-info">
          <h3 class="player-name">{{ gameStore.blackPlayer?.username }}</h3>
          <p class="player-role">黑方 (先手)</p>
        </div>
        <div v-if="gameStore.currentTurn === 'black'" class="turn-indicator">
          <el-icon><Clock /></el-icon>
          <span>{{ blackTimer }}s</span>
        </div>
      </div>

      <div class="game-status">
        <p class="status-text">{{ statusText }}</p>
        <p v-if="observerCount > 0" class="observer-count">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4-8-11-8-11 8-11 8z"></path>
            <circle cx="12" cy="12" r="3"></circle>
          </svg>
          观战者: {{ observerCount }}
        </p>
      </div>

      <div class="player-card" :class="{ active: gameStore.currentTurn === 'white' }">
        <div v-if="gameStore.currentTurn === 'white'" class="turn-indicator">
          <el-icon><Clock /></el-icon>
          <span>{{ whiteTimer }}s</span>
        </div>
        <div class="player-info">
          <h3 class="player-name">{{ gameStore.whitePlayer?.username }}</h3>
          <p class="player-role">白方</p>
        </div>
        <el-avatar :size="48" :src="gameStore.whitePlayer?.avatar" class="player-avatar">
          {{ gameStore.whitePlayer?.username?.charAt(0).toUpperCase() }}
        </el-avatar>
      </div>
    </div>

    <!-- 等待第二个玩家界面 -->
    <div v-if="isWaitingForPlayer" class="waiting-overlay">
      <div class="waiting-card">
        <div class="waiting-icon">⏳</div>
        <h2 class="waiting-title">等待对手加入</h2>
        <p class="waiting-description">分享房间号给好友，或等待其他玩家加入</p>
        <div class="room-code-display">
          <span class="room-code-label">房间号:</span>
          <span class="room-code-value">{{ roomId }}</span>
          <el-button
            type="primary"
            size="small"
            @click="copyRoomId"
            :icon="DocumentCopy"
          >
            复制
          </el-button>
        </div>
        <div class="waiting-actions">
          <el-button
            type="danger"
            size="large"
            @click="handleDeleteRoom"
          >
            <el-icon><Delete /></el-icon>
            取消房间
          </el-button>
        </div>
      </div>
    </div>

    <!-- 棋盘 -->
    <div class="board-container">
      <div class="board">
        <div
          v-for="(row, rowIndex) in gameStore.board"
          :key="rowIndex"
          class="board-row"
        >
          <div
            v-for="(cell, colIndex) in row"
            :key="colIndex"
            class="board-cell"
            @click="handleCellClick(rowIndex, colIndex)"
          >
            <!-- 星位点标记 -->
            <div v-if="isStarPoint(rowIndex, colIndex) && cell === 'empty'" class="star-dot"></div>
            <div v-if="cell !== 'empty'" class="piece" :class="cell"></div>
            <!-- 最后一步标记 -->
            <div v-if="isLastMove(rowIndex, colIndex)" class="last-move-marker"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 控制按钮 -->
    <div class="game-controls">
      <el-button
        type="warning"
        size="large"
        class="control-button"
        :disabled="!gameStore.isGameActive"
        @click="handleSurrender"
      >
        <el-icon><Warning /></el-icon>
        认输
      </el-button>
      <el-button
        type="info"
        size="large"
        class="control-button"
        :disabled="!gameStore.isGameActive || gameStore.moveHistory.length === 0"
        @click="handleUndo"
      >
        <el-icon><RefreshLeft /></el-icon>
        悔棋
      </el-button>
      <el-button
        type="primary"
        size="large"
        class="control-button"
        :disabled="!gameStore.isGameActive"
        @click="handleDraw"
      >
        <el-icon><Connection /></el-icon>
        和棋
      </el-button>
    </div>

    <!-- 游戏结果弹窗 -->
    <el-dialog
      v-model="showResultDialog"
      title="游戏结束"
      width="400px"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <div class="result-content">
        <div class="result-icon" :class="resultClass">
          <el-icon :size="64">
            <component :is="resultIcon" />
          </el-icon>
        </div>
        <h2 class="result-title">{{ resultTitle }}</h2>
        <p class="result-description">{{ resultDescription }}</p>

        <!-- 输了显示昵称+再接再厉 -->
        <p v-if="resultTitle === '输'" class="result-encourage">
          {{ userStore.userInfo?.nickname || userStore.userInfo?.username }} 再接再厉！
        </p>

        <!-- 和棋显示昵称+势均力敌 -->
        <p v-if="resultTitle === '平局'" class="result-encourage draw">
          {{ userStore.userInfo?.nickname || userStore.userInfo?.username }} 双方势均力敌！
        </p>

        <!-- 对手状态提示 -->
        <div v-if="opponentLeft" class="opponent-status left">
          <span>对手已离开</span>
        </div>
        <div v-else-if="opponentReady" class="opponent-status ready">
          <span>对手已准备</span>
        </div>

        <div class="result-stats">
          <p class="stat">总步数: {{ gameStore.moveHistory.length }}</p>
          <p class="stat">对局时长: {{ gameDuration }}</p>
          <p v-if="gameResult.affectsRating" class="stat">模式: 竞技模式</p>
          <p v-if="gameResult.affectsRating" class="stat rating-change">
            黑方积分: {{ gameResult.blackRatingChange >= 0 ? '+' : '' }}{{ gameResult.blackRatingChange }}
          </p>
          <p v-if="gameResult.affectsRating" class="stat rating-change">
            白方积分: {{ gameResult.whiteRatingChange >= 0 ? '+' : '' }}{{ gameResult.whiteRatingChange }}
          </p>
        </div>
      </div>
      <template #footer>
        <el-button @click="handleBack">返回大厅</el-button>
        <el-button type="primary" @click="handleRestart">再来一局</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Clock,
  Warning,
  RefreshLeft,
  RefreshRight,
  Trophy,
  Close,
  Share,
  Connection,
  DocumentCopy,
  Delete,
} from '@element-plus/icons-vue'
import { useGameStore } from '@/store/modules/game'
import { useUserStore } from '@/store/modules/user'
import { wsClient } from '@/api/websocket'
import { userApi } from '@/api'
// GameMoveDto 和 GameState 已经不再需要从 types/game 导入，因为后端发送不同的格式

const router = useRouter()
const route = useRoute()
const gameStore = useGameStore()
const userStore = useUserStore()

const roomId = ref(route.params.roomId as string)
const blackTimer = ref(300) // 5分钟倒计时
const whiteTimer = ref(300) // 5分钟倒计时
const showResultDialog = ref(false)
const gameStartTime = ref<Date>(new Date())
const timerInterval = ref<number>()
const moveSubscription = ref<string>('')
const stateSubscription = ref<string>('')
const endSubscription = ref<string>('')
const observerCountSubscription = ref<string>('')

// 观战者数量
const observerCount = ref<number>(0)

// 对手状态（游戏结束后）
const opponentLeft = ref<boolean>(false)
const opponentReady = ref<boolean>(false)
const localReady = ref<boolean>(false)

// 悔棋和和棋请求状态
const pendingUndoRequest = ref<any>(null) // 待处理的悔棋请求
const pendingDrawRequest = ref<any>(null) // 待处理的和棋请求
let undoMessageBoxInstance: any = null // 悔棋弹窗实例
let drawMessageBoxInstance: any = null // 和棋弹窗实例

// 游戏结果数据
const gameResult = ref<any>({
  winner: null as 'black' | 'white' | null,
  winColor: null,
  endReason: null,
  gameMode: '',
  affectsRating: false,
  blackRatingChange: 0,
  whiteRatingChange: 0
})

const statusText = computed(() => {
  if (gameStore.gameStatus === 'waiting') return '等待开始'
  if (gameStore.gameStatus === 'ended') return '游戏结束'
  return gameStore.currentTurn === 'black' ? '黑方回合' : '白方回合'
})

// 判断是否等待第二个玩家
const isWaitingForPlayer = computed(() => {
  return gameStore.gameStatus === 'waiting' &&
         (!gameStore.blackPlayer || !gameStore.whitePlayer)
})

const resultTitle = computed(() => {
  if (!gameStore.winner) return '平局'

  // 判断当前用户是黑方还是白方
  const myColor = userStore.userInfo?.id === gameStore.blackPlayer?.id ? 'black' : 'white'

  if (gameStore.winner === myColor) {
    return '赢'
  } else {
    return '输'
  }
})

const resultClass = computed(() => {
  if (!gameStore.winner) return 'draw' as const
  if (gameStore.winner === 'black') return 'win-black' as const
  return 'win-white' as const
})

const resultIcon = computed(() => {
  if (!gameStore.winner) return Close
  return Trophy
})

const resultDescription = computed(() => {
  if (!gameStore.winner) return '双方势均力敌，达成平局'
  // 如果我输了，不显示赢家信息（因为会显示"再接再厉"）
  const myColor = userStore.userInfo?.id === gameStore.blackPlayer?.id ? 'black' : 'white'
  if (gameStore.winner !== myColor) return ''
  const winner = gameStore.winner === 'black' ? gameStore.blackPlayer : gameStore.whitePlayer
  return `${winner?.username}胜利！`
})

const gameDuration = computed(() => {
  const now = new Date()
  const diff = Math.floor((now.getTime() - gameStartTime.value.getTime()) / 1000)
  const minutes = Math.floor(diff / 60)
  const seconds = diff % 60
  return `${minutes}分${seconds}秒`
})

const isLastMove = (row: number, col: number): boolean => {
  const lastMove = gameStore.moveHistory[gameStore.moveHistory.length - 1]
  return lastMove?.row === row && lastMove?.col === col
}

// 判断是否是星位点（天元和四个角的星位）
const isStarPoint = (row: number, col: number): boolean => {
  // 天元（中心点）
  if (row === 7 && col === 7) return true
  // 左上星位
  if (row === 3 && col === 3) return true
  // 右上星位
  if (row === 3 && col === 11) return true
  // 左下星位
  if (row === 11 && col === 3) return true
  // 右下星位
  if (row === 11 && col === 11) return true
  return false
}

const handleCellClick = (row: number, col: number) => {
  if (!gameStore.canMove) return

  const playerRole = userStore.userInfo?.id === gameStore.blackPlayer?.id ? 'black' : 'white'
  if (playerRole !== gameStore.currentTurn) {
    ElMessage.warning('不是您的回合')
    return
  }

  gameStore.makeMove(row, col)
}

const handleSurrender = async () => {
  try {
    await ElMessageBox.confirm('确定要认输吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    console.log('[GameView] 发送认输请求: roomId=', roomId.value)
    wsClient.send('/app/game/resign', { roomId: roomId.value })
    console.log('[GameView] 认输请求已发送')
  } catch {
    // 用户取消
    console.log('[GameView] 用户取消认输')
  }
}

const handleUndo = () => {
  ElMessageBox.confirm('确定要悔棋吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  }).then(() => {
    console.log('[GameView] 发送悔棋请求: roomId=', roomId.value)
    wsClient.send('/app/game/undo', { roomId: roomId.value })
    console.log('[GameView] 悔棋请求已发送')
  }).catch(() => {
    console.log('[GameView] 用户取消悔棋')
  })
}

const handleDraw = () => {
  ElMessageBox.confirm('确定要和棋吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  }).then(() => {
    console.log('[GameView] 发送和棋请求: roomId=', roomId.value)
    wsClient.send('/app/game/draw', { roomId: roomId.value })
    console.log('[GameView] 和棋请求已发送')
  }).catch(() => {
    console.log('[GameView] 用户取消和棋')
  })
}

const handleRestart = () => {
  // 发送玩家准备消息给对手
  wsClient.send('/app/game/player-ready', { roomId: roomId.value })
  // 标记自己已准备
  localReady.value = true

  // 检查双方是否都准备好了
  if (opponentReady.value) {
    // 双方都准备好了，重新开始游戏
    restartGame()
  } else {
  }
}

// 重新开始游戏
const restartGame = () => {
  showResultDialog.value = false
  gameStore.resetGame()
  // 重置状态
  opponentLeft.value = false
  opponentReady.value = false
  localReady.value = false
}

const handleCloseResult = () => {
  showResultDialog.value = false
}

const handleInvite = async () => {
  // 复制房间链接到剪贴板
  const inviteLink = `${window.location.origin}/game/${roomId.value}`
  try {
    await navigator.clipboard.writeText(inviteLink)
  } catch {
    // 如果剪贴板API不可用，使用传统方法
    const textArea = document.createElement('textarea')
    textArea.value = inviteLink
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    document.body.appendChild(textArea)
    textArea.select()
    try {
      document.execCommand('copy')
    } catch {
      ElMessage.error('复制失败，请手动复制房间号: ' + roomId.value)
    }
    document.body.removeChild(textArea)
  }
}

const handleBack = () => {
  if (gameStore.isGameActive) {
    ElMessage.warning('游戏进行中，请先结束游戏')
    return
  }
  // 如果游戏已结束，发送离开消息给对手，但不立即退出
  if (!gameStore.isGameActive && showResultDialog.value) {
    wsClient.send('/app/game/leave', { roomId: roomId.value })
    // 延迟退出，让消息发送完成
    setTimeout(() => {
      router.push('/home?panel=room')
    }, 500)
  } else {
    router.push('/home?panel=room')
  }
}

// 删除房间
const handleDeleteRoom = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要取消并删除这个房间吗？',
      '取消房间',
      {
        confirmButtonText: '确定',
        cancelButtonText: '再等等',
        type: 'warning',
      }
    )

    console.log('[GameView] 发送删除房间请求: roomId=', roomId.value)
    wsClient.send('/app/room/delete', { roomId: roomId.value })
    console.log('[GameView] 删除房间请求已发送')
  } catch {
    // 用户取消
    console.log('[GameView] 用户取消删除房间')
  }
}

// 复制房间号
const copyRoomId = () => {
  const textArea = document.createElement('textarea')
  textArea.value = roomId.value
  document.body.appendChild(textArea)
  textArea.select()
  try {
    document.execCommand('copy')
  } catch {
    ElMessage.error('复制失败，请手动复制房间号: ' + roomId.value)
  }
  document.body.removeChild(textArea)
}

const startTimer = () => {
  timerInterval.value = window.setInterval(() => {
    if (gameStore.currentTurn === 'black') {
      blackTimer.value--
      if (blackTimer.value <= 0) {
        blackTimer.value = 300
        // 超时处理
        wsClient.send('/app/game/timeout', { roomId: roomId.value })
      }
    } else {
      whiteTimer.value--
      if (whiteTimer.value <= 0) {
        whiteTimer.value = 300
        // 超时处理
        wsClient.send('/app/game/timeout', { roomId: roomId.value })
      }
    }
  }, 1000)
}

const stopTimer = () => {
  if (timerInterval.value) {
    clearInterval(timerInterval.value)
    timerInterval.value = undefined
  }
}

const setupWebSocket = () => {
  // 订阅房间消息（包括游戏状态、落子、游戏结束）
  // 后端使用 /topic/room/${roomId} 发送所有房间相关消息
  const roomSubscription = wsClient.subscribe(`/topic/room/${roomId.value}`, (message: any) => {
    console.log('[GameView] 收到房间消息:', message)

    switch (message.type) {
      case 'GAME_START':
        console.log('[GameView] 收到 GAME_START 消息，清空棋盘并重置游戏')
        // 使用 gameStore.startGame() 方法来正确设置游戏状态
        gameStore.startGame()
        // 重置计时器
        blackTimer.value = 300
        whiteTimer.value = 300
        stopTimer()
        // 隐藏结果对话框
        showResultDialog.value = false
        // 重置准备状态
        localReady.value = false
        opponentReady.value = false
        console.log('[GameView] 棋盘已清空，等待后端发送 GAME_STATE')
        break

      case 'GAME_STATE':
        console.log('[GameView] 处理 GAME_STATE:', {
          gameStatus: message.gameStatus,
          blackPlayer: message.blackPlayer,
          whitePlayer: message.whitePlayer,
          currentTurn: message.currentTurn
        })
        gameStore.setGameState(message)

        // 等待 Vue 响应式更新后再检查
        setTimeout(() => {
          const bothPlayersExist = gameStore.blackPlayer && gameStore.whitePlayer
          console.log('[GameView] 检查玩家状态:', {
            blackPlayer: gameStore.blackPlayer,
            whitePlayer: gameStore.whitePlayer,
            bothPlayersExist,
            gameStatus: message.gameStatus,
            timerRunning: !!timerInterval.value
          })

          if (message.gameStatus === 'PLAYING' && bothPlayersExist && !timerInterval.value) {
            console.log('[GameView] 两个玩家都已加入，开始计时器')
            startTimer()
          } else if (!bothPlayersExist) {
            console.log('[GameView] 等待第二个玩家加入...')
          } else if (timerInterval.value) {
            console.log('[GameView] 计时器已在运行')
          }
        }, 50)
        break

      case 'GAME_MOVE':
        gameStore.handleOpponentMove(message)
        // 不重置计时器，保持 5 分钟总倒计时
        break

      case 'GAME_OVER':
        stopTimer()
        // 正确处理和棋情况：winColor为0表示和棋
        let winnerColor: 'black' | 'white' | null = null
        if (message.winColor === 1) {
          winnerColor = 'black'
        } else if (message.winColor === 2) {
          winnerColor = 'white'
        }
        // winColor为0时，winnerColor保持为null（和棋）
        gameStore.endGame(winnerColor)

        // 清理所有待处理的弹窗
        if (undoMessageBoxInstance) {
          try {
            undoMessageBoxInstance.close()
          } catch (e) {}
          undoMessageBoxInstance = null
        }
        if (drawMessageBoxInstance) {
          try {
            drawMessageBoxInstance.close()
          } catch (e) {}
          drawMessageBoxInstance = null
        }
        pendingUndoRequest.value = null
        pendingDrawRequest.value = null

        // 保存游戏结果
        gameResult.value = {
          winner: winnerColor,
          winColor: message.winColor,
          endReason: message.endReason,
          gameMode: message.gameMode,
          affectsRating: message.affectsRating,
          blackRatingChange: message.blackRatingChange || 0,
          whiteRatingChange: message.whiteRatingChange || 0
        }

        // 记录游戏结果到后端（异步，不阻塞UI）
        if (userStore.userInfo?.id) {
          // 判断是玩家胜利、失败还是平局
          const myColor = userStore.userInfo?.id === gameStore.blackPlayer?.id ? 'black' : 'white'
          let resultType: 'win' | 'lose' | 'draw' = 'draw'

          if (winnerColor === 'draw') {
            resultType = 'draw'
          } else if (winnerColor === myColor) {
            resultType = 'win'
          } else {
            resultType = 'lose'
          }

          // 根据游戏模式选择正确的gameMode
          let gameModeType = 'PVP'
          if (message.gameMode === 'RANKED') {
            gameModeType = 'RANKED'
          } else if (message.gameMode === 'FRIEND') {
            gameModeType = 'FRIEND'
          } else if (message.gameMode === 'ROOM') {
            gameModeType = 'ROOM'
          }

          // 异步调用API记录游戏结果
          userApi.recordGame(userStore.userInfo.id, resultType, gameModeType)
            .then(response => {
              if (response && response.success) {
                console.log('[GameView] 游戏结果已记录:', response.data)
                // 更新本地用户信息
                if (userStore.userInfo && response.data) {
                  userStore.userInfo.rating = response.data.rating || userStore.userInfo.rating
                  userStore.userInfo.level = response.data.level || userStore.userInfo.level
                  userStore.userInfo.exp = response.data.exp || 0
                  localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
                }
              }
            })
            .catch(err => {
              console.error('[GameView] 记录游戏结果失败:', err)
            })
        }

        // 如果是竞技模式，显示积分变化
        if (message.affectsRating) {
          const myColor = userStore.userInfo?.id === gameStore.blackPlayer?.id ? 'black' : 'white'
          const ratingChange = myColor === 'black' ? message.blackRatingChange : message.whiteRatingChange
          if (ratingChange !== undefined) {
            const sign = ratingChange >= 0 ? '+' : ''
          }
        }

        showResultDialog.value = true
        break

      case 'UNDO_REQUEST':
        console.log('[GameView] 收到悔棋请求:', message)
        // 判断是否是对手的请求
        if (message.requesterId !== userStore.userInfo?.id) {
          // 保存请求信息
          pendingUndoRequest.value = message

          // 关闭之前的弹窗（如果存在）
          if (undoMessageBoxInstance) {
            try {
              undoMessageBoxInstance.close()
            } catch (e) {
              console.log('[GameView] 关闭旧弹窗失败:', e)
            }
            undoMessageBoxInstance = null
          }

          // 显示确认弹窗
          undoMessageBoxInstance = ElMessageBox.confirm('对手请求悔棋，是否同意？', '悔棋请求', {
            confirmButtonText: '同意',
            cancelButtonText: '拒绝',
            type: 'info'
          }).then(() => {
            wsClient.send('/app/game/undo', { roomId: roomId.value, accept: true })
            pendingUndoRequest.value = null
            undoMessageBoxInstance = null
          }).catch(() => {
            wsClient.send('/app/game/undo', { roomId: roomId.value, accept: false })
            pendingUndoRequest.value = null
            undoMessageBoxInstance = null
          })
        }
        break

      case 'UNDO_RESPONSE':
        console.log('[GameView] 收到悔棋响应:', message)
        if (message.accepted) {
        } else {
        }
        // 清理悔棋弹窗
        if (undoMessageBoxInstance) {
          try {
            undoMessageBoxInstance.close()
          } catch (e) {}
          undoMessageBoxInstance = null
        }
        pendingUndoRequest.value = null
        break

      case 'UNDO_SUCCESS':
        console.log('[GameView] 悔棋成功:', message)
        // 确保 gameStatus 为 PLAYING（悔棋后游戏继续）
        message.gameStatus = 'PLAYING'
        // 清除胜者信息（因为悔棋后游戏继续）
        message.winner = null
        message.winnerId = null
        // 更新棋盘状态
        gameStore.setGameState(message)
        // 清理悔棋弹窗
        if (undoMessageBoxInstance) {
          try {
            undoMessageBoxInstance.close()
          } catch (e) {}
          undoMessageBoxInstance = null
        }
        pendingUndoRequest.value = null
        break

      case 'DRAW_REQUEST':
        console.log('[GameView] 收到和棋请求:', message)
        // 判断是否是对手的请求
        if (message.requesterId !== userStore.userInfo?.id) {
          // 保存请求信息
          pendingDrawRequest.value = message

          // 关闭之前的弹窗（如果存在）
          if (drawMessageBoxInstance) {
            try {
              drawMessageBoxInstance.close()
            } catch (e) {
              console.log('[GameView] 关闭旧弹窗失败:', e)
            }
            drawMessageBoxInstance = null
          }

          // 显示确认弹窗
          drawMessageBoxInstance = ElMessageBox.confirm('对手请求和棋，是否同意？', '和棋请求', {
            confirmButtonText: '同意',
            cancelButtonText: '拒绝',
            type: 'info'
          }).then(() => {
            wsClient.send('/app/game/draw', { roomId: roomId.value, accept: true })
            pendingDrawRequest.value = null
            drawMessageBoxInstance = null
          }).catch(() => {
            wsClient.send('/app/game/draw', { roomId: roomId.value, accept: false })
            pendingDrawRequest.value = null
            drawMessageBoxInstance = null
          })
        }
        break

      case 'DRAW_RESPONSE':
        console.log('[GameView] 收到和棋响应:', message)
        if (message.requesterId === userStore.userInfo?.id) {
          if (message.accepted) {
          } else {
          }
        }
        // 清理和棋弹窗
        if (drawMessageBoxInstance) {
          try {
            drawMessageBoxInstance.close()
          } catch (e) {}
          drawMessageBoxInstance = null
        }
        pendingDrawRequest.value = null
        break

      case 'OPPONENT_LEFT':
        console.log('[GameView] 对手已离开:', message)
        // 只有在游戏结束后才标记对手离开
        if (!gameStore.isGameActive && showResultDialog.value) {
          opponentLeft.value = true
        }
        break

      case 'CHANGE_TABLE':
        console.log('[GameView] 对手已换桌:', message)
        // 只有在游戏结束后才标记对手换桌
        if (!gameStore.isGameActive && showResultDialog.value) {
          opponentLeft.value = true
        }
        break

      case 'PLAYER_READY':
        console.log('[GameView] 对手已准备:', message)
        // 只有在游戏结束后才标记对手准备
        if (!gameStore.isGameActive && showResultDialog.value) {
          // 只有当消息来自对手（不是自己）时才处理
          if (message.userId && message.userId !== userStore.userInfo?.id) {
            opponentReady.value = true
            opponentLeft.value = false // 对手准备了，说明没有离开

            // 如果我也准备好了，双方都准备完毕，直接重新开始
            if (localReady.value) {
              console.log('[GameView] 双方都准备好了，重新开始游戏')
              restartGame()
            }
          }
        }
        break

      case 'ERROR':
        ElMessage.error(message.message || '游戏错误')
        break
    }
  })

  if (roomSubscription) {
    stateSubscription.value = roomSubscription
  }

  // 订阅观战者数量变化
  observerCountSubscription.value = wsClient.subscribeObserverCount(roomId.value, (message: any) => {
    console.log('[GameView] 观战者数量更新:', message)
    if (message.type === 'OBSERVER_COUNT_CHANGE') {
      observerCount.value = message.observerCount || 0
    }
  })

  // 订阅房间删除响应
  const userId = userStore.userInfo?.id
  if (userId) {
    wsClient.subscribe(`/topic/user/${userId}/room`, (message: any) => {
      console.log('[GameView] 收到房间操作消息:', message)
      if (message.type === 'ROOM_DELETED' && message.success) {
        // 跳转回首页
        setTimeout(() => {
          router.push('/home?panel=room')
        }, 500)
      } else if (message.type === 'ROOM_ERROR') {
        ElMessage.error(message.message || '操作失败')
      }
    })
  }

  // 订阅成功后，立即请求当前游戏状态
  // 这样可以确保即使错过了之前的广播，也能获取当前状态
  setTimeout(() => {
    console.log('[GameView] 请求游戏状态')
    wsClient.send('/app/game/state', { roomId: roomId.value })
  }, 500)
}

onMounted(() => {
  // 设置房间ID到 gameStore
  gameStore.setRoomId(roomId.value)
  gameStore.initBoard()
  setupWebSocket()

  // 从路由查询参数获取玩家角色
  const color = route.query.color as string
  if (color === 'black') {
    // 我是黑棋
    gameStore.blackPlayer = userStore.userInfo
  } else if (color === 'white') {
    // 我是白棋
    gameStore.whitePlayer = userStore.userInfo
  }

  console.log('[GameView] 初始化游戏:', {
    roomId: roomId.value,
    color,
    userId: userStore.userInfo?.id
  })
})

onUnmounted(() => {
  stopTimer()
  if (moveSubscription.value) wsClient.unsubscribe(moveSubscription.value)
  if (stateSubscription.value) wsClient.unsubscribe(stateSubscription.value)
  if (endSubscription.value) wsClient.unsubscribe(endSubscription.value)
  if (observerCountSubscription.value) wsClient.unsubscribe(observerCountSubscription.value)

  // 清理弹窗
  if (undoMessageBoxInstance) {
    try {
      undoMessageBoxInstance.close()
    } catch (e) {}
    undoMessageBoxInstance = null
  }
  if (drawMessageBoxInstance) {
    try {
      drawMessageBoxInstance.close()
    } catch (e) {}
    drawMessageBoxInstance = null
  }

  // 重置状态
  pendingUndoRequest.value = null
  pendingDrawRequest.value = null
})
</script>

<style scoped>
.game-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
  padding: 20px;
}

.game-header {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
}

.back-button {
  background: white;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.back-button:hover {
  transform: translateX(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.page-title {
  flex: 1;
  text-align: center;
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.header-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.room-id {
  font-size: 14px;
  color: #666;
  font-weight: 400;
  margin: 0;
  background: #f0f0f0;
  padding: 2px 12px;
  border-radius: 12px;
}

.invite-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: white;
  transition: all 0.3s ease;
}

.invite-button:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.players-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.player-card {
  background: white;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  flex: 1;
}

.player-card.active {
  box-shadow: 0 4px 16px rgba(255, 106, 136, 0.3);
  border: 2px solid #ff6a88;
}

.player-avatar {
  background: linear-gradient(135deg, #ff9a8b 0%, #ff6a88 100%);
  color: white;
  font-weight: 600;
  flex-shrink: 0;
}

.player-info {
  flex: 1;
  min-width: 0;
}

.player-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-role {
  font-size: 12px;
  color: #999;
  margin: 0;
}

.turn-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #ff6a88;
  font-weight: 600;
  font-size: 18px;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

.game-status {
  background: white;
  border-radius: 12px;
  padding: 16px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  min-width: 150px;
  text-align: center;
}

.status-text {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.observer-count {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  color: #666;
  margin: 5px 0 0 0;
}

.board-container {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.board {
  background: linear-gradient(135deg, #f5e6d3 0%, #e8d5c4 100%);
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: grid;
  grid-template-columns: repeat(15, 30px);
  grid-template-rows: repeat(15, 30px);
  position: relative;
}

.board::before {
  content: '';
  position: absolute;
  top: 16px;
  left: 16px;
  right: 16px;
  bottom: 16px;
  background:
    linear-gradient(#ff8c61 1px, transparent 1px),
    linear-gradient(90deg, #ff8c61 1px, transparent 1px);
  background-size: 30px 30px;
  background-position: 15px 15px;
  pointer-events: none;
  z-index: 0;
}

.board-row {
  display: contents;
}

.board-cell {
  width: 30px;
  height: 30px;
  position: relative;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.board-cell:hover {
  background: rgba(255, 107, 53, 0.15);
}

/* 棋盘上的星位点 */
.star-dot {
  position: absolute;
  width: 6px;
  height: 6px;
  background: #ff8c61;
  border-radius: 50%;
  z-index: 0;
}

/* 最后一步标记 */
.last-move-marker {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #ff6b35;
  border-radius: 50%;
  z-index: 2;
}

.piece {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  position: relative;
  z-index: 1;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}

.piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.game-controls {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.control-button {
  border-radius: 8px;
  padding: 12px 32px;
  font-size: 16px;
}

.result-content {
  text-align: center;
  padding: 20px 0;
}

.result-icon {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
  color: white;
}

.result-icon.win-black {
  background: linear-gradient(135deg, #333 0%, #000 100%);
}

.result-icon.win-white {
  background: linear-gradient(135deg, #fff 0%, #ddd 100%);
  color: #333;
}

.result-icon.draw {
  background: linear-gradient(135deg, #999 0%, #666 100%);
}

.result-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.result-description {
  font-size: 16px;
  color: #666;
  margin: 0 0 8px 0;
}

.result-encourage {
  font-size: 16px;
  color: #666;
  font-weight: normal;
  margin: 0 0 16px 0;
  text-align: center;
}

.result-encourage.draw {
  color: #666;
}

/* 对手状态提示 */
.opponent-status {
  display: inline-block;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 16px;
}

.opponent-status.left {
  background: #fff3e0;
  color: #e65100;
  border: 1px solid #ffcc80;
}

.opponent-status.changed {
  background: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}

.opponent-status.ready {
  background: #e3f2fd;
  color: #1565c0;
  border: 1px solid #90caf9;
}

.result-stats {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;
}

.stat {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.stat.rating-change {
  font-weight: 600;
  color: #ff6a88;
}

@media (max-width: 768px) {
  .players-bar {
    flex-direction: column;
  }

  .player-card {
    width: 100%;
  }

  .board {
    grid-template-columns: repeat(15, 20px);
    grid-template-rows: repeat(15, 20px);
    padding: 8px;
  }

  .board::before {
    background-size: 20px 20px;
    background-position: 10px 10px;
  }

  .board-cell {
    width: 20px;
    height: 20px;
  }

  .piece {
    width: 16px;
    height: 16px;
  }

  .star-dot {
    width: 4px;
    height: 4px;
  }

  .last-move-marker {
    width: 6px;
    height: 6px;
  }

  .game-controls {
    flex-direction: column;
  }

  .control-button {
    width: 100%;
  }
}

/* 等待界面样式 */
.waiting-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.waiting-card {
  background: white;
  border-radius: 20px;
  padding: 40px;
  max-width: 400px;
  width: 90%;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    transform: translateY(30px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.waiting-icon {
  font-size: 64px;
  margin-bottom: 20px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.waiting-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.waiting-description {
  font-size: 16px;
  color: #666;
  margin: 0 0 24px 0;
}

.room-code-display {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #f5f5f5;
  padding: 16px;
  border-radius: 12px;
  margin-bottom: 24px;
}

.room-code-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.room-code-value {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  font-family: 'Courier New', monospace;
  letter-spacing: 2px;
}

.waiting-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
