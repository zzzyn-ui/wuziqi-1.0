<template>
  <div class="game-container">
    <div class="game-header">
      <el-button class="back-button" circle @click="handleBack">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <h1 class="page-title">游戏对局</h1>
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
            :class="{
              'black-piece': cell === 'black',
              'white-piece': cell === 'white',
              'last-move': isLastMove(rowIndex, colIndex)
            }"
            @click="handleCellClick(rowIndex, colIndex)"
          >
            <!-- 星位点标记 -->
            <div v-if="isStarPoint(rowIndex, colIndex) && cell === 'empty'" class="star-dot"></div>
            <div v-if="cell !== 'empty'" class="piece" :class="cell"></div>
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
        type="success"
        size="large"
        class="control-button"
        :disabled="gameStore.isGameActive"
        @click="handleRestart"
      >
        <el-icon><RefreshRight /></el-icon>
        再来一局
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
        <el-button @click="handleCloseResult">关闭</el-button>
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
} from '@element-plus/icons-vue'
import { useGameStore } from '@/store/modules/game'
import { useUserStore } from '@/store/modules/user'
import { wsClient } from '@/api/websocket'
// GameMoveDto 和 GameState 已经不再需要从 types/game 导入，因为后端发送不同的格式

const router = useRouter()
const route = useRoute()
const gameStore = useGameStore()
const userStore = useUserStore()

const roomId = ref(route.params.roomId as string)
const blackTimer = ref(30)
const whiteTimer = ref(30)
const showResultDialog = ref(false)
const gameStartTime = ref<Date>(new Date())
const timerInterval = ref<number>()
const moveSubscription = ref<string>('')
const stateSubscription = ref<string>('')
const endSubscription = ref<string>('')

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

const resultTitle = computed(() => {
  if (!gameStore.winner) return '平局'
  if (gameStore.winner === 'black') return '黑方获胜'
  return '白方获胜'
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
  const winner = gameStore.winner === 'black' ? gameStore.blackPlayer : gameStore.whitePlayer
  return `${winner?.username} 获得胜利！`
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

    wsClient.send('/app/game/resign', { roomId: roomId.value })
  } catch {
    // 用户取消
  }
}

const handleUndo = () => {
  ElMessageBox.confirm('确定要悔棋吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  }).then(() => {
    wsClient.send('/app/game/undo', { roomId: roomId.value })
  }).catch(() => {
    // 用户取消
  })
}

const handleRestart = () => {
  showResultDialog.value = false
  gameStore.resetGame()
  router.push('/match')
}

const handleCloseResult = () => {
  showResultDialog.value = false
}

const handleBack = () => {
  if (gameStore.isGameActive) {
    ElMessage.warning('游戏进行中，请先结束游戏')
    return
  }
  router.push('/match')
}

const startTimer = () => {
  timerInterval.value = window.setInterval(() => {
    if (gameStore.currentTurn === 'black') {
      blackTimer.value--
      if (blackTimer.value <= 0) {
        blackTimer.value = 30
        // 超时处理
        wsClient.send('/app/game/timeout', { roomId: roomId.value })
      }
    } else {
      whiteTimer.value--
      if (whiteTimer.value <= 0) {
        whiteTimer.value = 30
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
      case 'GAME_STATE':
        gameStore.setGameState(message)
        if (message.gameStatus === 'PLAYING' && !timerInterval.value) {
          startTimer()
        }
        break

      case 'GAME_MOVE':
        gameStore.handleOpponentMove(message)
        // 重置计时器
        blackTimer.value = 30
        whiteTimer.value = 30
        break

      case 'GAME_OVER':
        stopTimer()
        const winnerColor = message.winColor === 1 ? 'black' : 'white'
        gameStore.endGame(winnerColor)

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

        // 如果是竞技模式，显示积分变化
        if (message.affectsRating) {
          const myColor = userStore.userInfo?.id === gameStore.blackPlayer?.id ? 'black' : 'white'
          const ratingChange = myColor === 'black' ? message.blackRatingChange : message.whiteRatingChange
          if (ratingChange !== undefined) {
            const sign = ratingChange >= 0 ? '+' : ''
            ElMessage.info(`积分变化: ${sign}${ratingChange}`)
          }
        }

        showResultDialog.value = true
        break

      case 'ERROR':
        ElMessage.error(message.message || '游戏错误')
        break
    }
  })

  if (roomSubscription) {
    stateSubscription.value = roomSubscription
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

.board-container {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.board {
  background: #dcb35c;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.board-row {
  display: flex;
}

.board-cell {
  width: 40px;
  height: 40px;
  position: relative;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 五子棋标准棋盘：网格线，棋子下在交叉点上 */
.board-cell::before,
.board-cell::after {
  content: '';
  position: absolute;
  background: #8b5a2b;
}

/* 横线 */
.board-cell::before {
  width: 100%;
  height: 1px;
  top: 50%;
  left: 0;
}

/* 竖线 */
.board-cell::after {
  width: 1px;
  height: 100%;
  left: 50%;
  top: 0;
}

/* 边缘处理：第一行的横线只画下半部分，最后一行的横线只画上半部分 */
.board-cell:first-child::before {
  width: 100%;
  height: 1px;
  top: 50%;
  left: 0;
}

.board-cell:last-child::before {
  width: 100%;
  height: 1px;
  top: 50%;
  left: 0;
}

/* 第一列的竖线只画右半部分 */
.board-row:first-child .board-cell::after {
  width: 1px;
  height: 50%;
  left: 50%;
  bottom: 0;
}

/* 最后一列的竖线只画左半部分 */
.board-row:last-child .board-cell::after {
  width: 1px;
  height: 50%;
  left: 50%;
  top: 0;
}

/* 棋盘上的星位点（天元和四个星位） */
.star-dot {
  position: absolute;
  width: 8px;
  height: 8px;
  background: #8b5a2b;
  border-radius: 50%;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
}

.board-cell:hover::after {
  background: #ff6a88;
  width: 2px;
}

.board-cell.last-move::before {
  background: #ff6a88;
  height: 2px;
}

.piece {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  position: relative;
  z-index: 1;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.piece.black {
  background: radial-gradient(circle at 30% 30%, #555, #000);
}

.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
  border: 1px solid #ccc;
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
  margin: 0 0 24px 0;
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

  .board-cell {
    width: 28px;
    height: 28px;
  }

  .piece {
    width: 22px;
    height: 22px;
  }

  .game-controls {
    flex-direction: column;
  }

  .control-button {
    width: 100%;
  }
}
</style>
