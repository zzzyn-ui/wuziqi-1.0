<template>
  <div class="match-container">
    <!-- 未匹配且没有mode参数时显示匹配模式选择 -->
    <div v-if="!isMatching && !gameStarted && !route.query.mode">
      <div class="match-header">
        <el-button class="back-button" circle @click="handleBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <h1 class="page-title">匹配大厅</h1>
      </div>

      <div class="user-info-card">
        <el-avatar :size="64" :src="userStore.userInfo?.avatar" class="user-avatar">
          {{ userStore.userInfo?.username?.charAt(0).toUpperCase() }}
        </el-avatar>
        <div class="user-details">
          <h2 class="user-name">{{ userStore.userInfo?.username }}</h2>
          <div class="user-stats">
            <span class="stat-item">
              <el-icon><Trophy /></el-icon>
              积分: {{ userStore.userInfo?.rating || 0 }}
            </span>
            <span class="stat-item">
              <el-icon><Medal /></el-icon>
              胜率: {{ winRate }}%
            </span>
          </div>
        </div>
      </div>

      <div class="match-modes">
        <div class="match-mode-card" @click="handleStartMatch('casual')">
          <div class="mode-icon casual">
            <el-icon :size="48"><Coffee /></el-icon>
          </div>
          <h3 class="mode-title">休闲模式</h3>
          <p class="mode-description">轻松对局</p>
        </div>

        <div class="match-mode-card primary" @click="handleStartMatch('ranked')">
          <div class="mode-icon ranked">
            <el-icon :size="48"><Star /></el-icon>
          </div>
          <h3 class="mode-title">竞技模式</h3>
          <p class="mode-description">积分对决</p>
        </div>
      </div>
    </div>

    <!-- 匹配中 -->
    <div v-else-if="isMatching && !gameStarted" class="matching-status">
      <div class="loading-animation">
        <div class="circle"></div>
        <div class="circle"></div>
        <div class="circle"></div>
      </div>
      <p class="matching-text">正在寻找对手...</p>
      <p class="matching-mode">{{ matchModeText }}</p>
      <el-button
        type="danger"
        size="large"
        class="cancel-button"
        @click="handleCancelMatch"
      >
        取消匹配
      </el-button>
    </div>

    <!-- 游戏界面 -->
    <div v-else-if="gameStarted" class="game-container">
      <div class="game-header">
        <div class="game-player-info" :class="{ active: currentTurn === myColor }">
          <div class="game-avatar">{{ userStore.userInfo?.username?.charAt(0) || '?' }}</div>
          <div class="game-player-details">
            <div class="game-player-name">{{ userStore.userInfo?.username || '我' }}</div>
            <div class="game-player-color">
              <span class="game-piece-icon" :class="myColor === 1 ? 'black' : 'white'">
                {{ myColor === 1 ? '⚫' : '⚪' }}
              </span>
            </div>
            <div class="game-timer" :class="{ warning: myTime <= 60, danger: myTime <= 10 }">
              {{ formatTime(myTime) }}
            </div>
          </div>
        </div>

        <div class="game-vs">VS</div>

        <div class="game-player-info" :class="{ active: currentTurn !== myColor }">
          <div class="game-player-details" style="text-align: right;">
            <div class="game-player-name">{{ opponentName }}</div>
            <div class="game-player-color">
              <span class="game-piece-icon" :class="myColor === 1 ? 'white' : 'black'">
                {{ myColor === 1 ? '⚪' : '⚫' }}
              </span>
            </div>
            <div class="game-timer" :class="{ warning: opponentTime <= 60, danger: opponentTime <= 10 }">
              {{ formatTime(opponentTime) }}
            </div>
          </div>
          <div class="game-avatar">👤</div>
        </div>
      </div>

      <div class="game-status">
        <div v-if="currentTurn === myColor" class="game-status-turn">你的回合</div>
        <div v-else class="game-status-thinking">对方思考中...</div>
      </div>

      <!-- 棋盘 -->
      <div class="board-container">
        <div class="board">
          <div v-for="(row, x) in board" :key="x" class="row">
            <div
              v-for="(cell, y) in row"
              :key="y"
              class="cell"
              @click="handleMove(x, y)"
            >
              <div
                v-if="cell > 0"
                class="piece"
                :class="cell === 1 ? 'black' : 'white'"
              >
                <div v-if="lastMove && lastMove.x === x && lastMove.y === y" class="last-move"></div>
              </div>
              <!-- 星位标记 -->
              <div
                v-if="isStarPoint(x, y)"
                class="star-point"
              ></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 游戏操作按钮 -->
      <div class="game-actions">
        <el-button class="game-action-btn" @click="handleUndo" :disabled="!canUndo">
          <el-icon><RefreshLeft /></el-icon>
          悔棋
        </el-button>
        <el-button class="game-action-btn" @click="handleDraw">
          <el-icon><CircleCheck /></el-icon>
          和棋
        </el-button>
        <el-button class="game-action-btn danger" @click="handleResign">
          <el-icon><CloseBold /></el-icon>
          认输
        </el-button>
      </div>

      <!-- 等待响应提示 -->
      <div v-if="isWaitingForUndoResponse || isWaitingForDrawResponse" class="waiting-response-card">
        <div class="waiting-icon">⏳</div>
        <div class="waiting-text">
          {{ isWaitingForUndoResponse ? '等待对手响应悔棋请求...' : '等待对手响应和棋请求...' }}
        </div>
        <el-button size="small" type="danger" text @click="cancelRequest">
          取消请求
        </el-button>
      </div>

      <!-- 响应状态提示 -->
      <div v-if="undoResponseStatus.show" :class="['response-status-card', undoResponseStatus.accepted ? 'accepted' : 'rejected']">
        <div class="response-icon">{{ undoResponseStatus.accepted ? '✅' : '❌' }}</div>
        <div class="response-text">
          {{ undoResponseStatus.accepted ? '对手同意了悔棋请求' : '对手拒绝了悔棋请求' }}
        </div>
      </div>

      <div v-if="drawResponseStatus.show" :class="['response-status-card', drawResponseStatus.accepted ? 'accepted' : 'rejected']">
        <div class="response-icon">{{ drawResponseStatus.accepted ? '✅' : '❌' }}</div>
        <div class="response-text">
          {{ drawResponseStatus.accepted ? '对手同意了和棋请求' : '对手拒绝了和棋请求' }}
        </div>
      </div>

      <!-- 聊天面板 -->
      <div class="chat-panel">
        <div class="chat-header">
          <h3>游戏聊天</h3>
        </div>
        <div class="chat-messages" ref="chatMessagesRef">
          <div
            v-for="(msg, index) in chatMessages"
            :key="index"
            class="chat-message"
            :class="{ 'my-message': msg.isMine }"
          >
            <div class="message-sender">{{ msg.sender }}</div>
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ msg.time }}</div>
          </div>
          <div v-if="chatMessages.length === 0" class="chat-empty">
            暂无消息，开始聊天吧~
          </div>
        </div>
        <div class="chat-input">
          <el-input
            v-model="chatInput"
            placeholder="输入消息..."
            @keyup.enter="sendChatMessage"
            :maxlength="100"
          />
          <el-button type="primary" @click="sendChatMessage">发送</el-button>
        </div>
      </div>
    </div>

    <!-- 游戏结束弹窗 -->
    <el-dialog
      v-model="showGameOverDialog"
      width="480px"
      center
      :close-on-click-modal="false"
      :show-close="false"
      class="game-result-dialog-wrapper"
    >
      <div :class="['game-result-content', gameResult.title === '胜利！' ? 'win' : gameResult.title === '和棋' ? 'draw' : 'lose']">
        <div class="result-badge">{{ gameResult.title }}</div>
        <div class="result-icon">{{ gameResult.icon }}</div>
        <div class="result-message">{{ gameResult.message }}</div>
        <div class="result-details" v-if="gameResult.details">
          {{ gameResult.details }}
        </div>

        <!-- 积分变化或模式提示 -->
        <div v-if="gameResult.mode === 'ranked' && (gameResult.ratingChange !== 0 || gameResult.mode === 'ranked')" class="rating-change">
          <div class="rating-label">积分变化</div>
          <div class="rating-value" :class="{ positive: gameResult.ratingChange > 0, negative: gameResult.ratingChange < 0 }">
            {{ gameResult.ratingChange > 0 ? '+' : '' }}{{ gameResult.ratingChange }}
          </div>
        </div>

        <!-- 休闲模式提示 -->
        <div v-else class="mode-hint">
          <div class="hint-icon">💡</div>
          <div class="hint-text">休闲对局不计入积分</div>
        </div>
      </div>
      <template #footer>
        <div class="game-result-footer">
          <el-button size="large" @click="handleBackToHome">
            <el-icon><HomeFilled /></el-icon>
            返回首页
          </el-button>
          <el-button size="large" type="warning" @click="handleChangeTable">
            <el-icon><Refresh /></el-icon>
            换桌匹配
          </el-button>
          <div class="play-again-button-wrapper">
            <!-- 显示状态提示 -->
            <div v-if="playAgainStatus.opponentLeft" class="opponent-ready-tip opponent-left-tip">
              对手已离开
            </div>
            <div v-else-if="playAgainStatus.opponentChanged" class="opponent-ready-tip opponent-changed-tip">
              对手已换桌
            </div>
            <div v-else-if="playAgainStatus.localRequested && !playAgainStatus.opponentRequested" class="opponent-ready-tip">
              等待对方准备...
            </div>
            <div v-else-if="playAgainStatus.opponentRequested && !playAgainStatus.localRequested" class="opponent-ready-tip">
              对手已准备
            </div>
            <el-button size="large" type="primary" @click="handlePlayAgainAccept">
              <el-icon><VideoPlay /></el-icon>
              再来一局
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Trophy,
  Medal,
  Coffee,
  Star,
  RefreshLeft,
  CircleCheck,
  CloseBold,
  HomeFilled,
  Refresh,
  VideoPlay,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { wsClient } from '@/api/websocket'
import { userApi } from '@/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isMatching = ref(false)
const matchMode = ref<'casual' | 'ranked'>('casual') // 用户选择的模式（用于结算显示）
const actualMatchMode = ref<'casual' | 'ranked'>('ranked') // 实际匹配模式（始终为ranked）
const matchingSubscription = ref<string>('')

// 游戏状态
const gameStarted = ref(false)
const board = ref<number[][]>(Array.from({ length: 15 }, () => Array(15).fill(0)))
const currentTurn = ref<1 | 2>(1)
const lastMove = ref<{ x: number; y: number } | null>(null)
const lastMovePosition = ref<{ x: number; y: number } | null>(null) // 最后落子位置（用于高亮）
const pendingMove = ref<{ x: number; y: number } | null>(null) // 用于跟踪本地落子
const myColor = ref<1 | 2>(1)
const roomId = ref('')
const opponentName = ref('')
const opponentId = ref<number | null>(null) // 记录对手ID，用于换桌时排除
const gameSubscription = ref<string>('')

// 倒计时状态 - 每方各自5分钟
const myTime = ref(300) // 我的时间 5分钟 = 300秒
const opponentTime = ref(300) // 对手的时间 5分钟 = 300秒
let timerInterval: number | null = null

// 游戏结束弹窗状态
const showGameOverDialog = ref(false)
const gameResult = ref({
  title: '',
  icon: '',
  message: '',
  details: '',
  mode: 'casual',
  ratingChange: 0
})

// 聊天状态
const chatMessages = ref<Array<{ sender: string; content: string; time: string; isMine: boolean }>>([])
const chatInput = ref('')
const chatMessagesRef = ref<HTMLElement | null>(null)

// 游戏操作状态
const canUndo = ref(false) // 是否可以悔棋
const moveHistory = ref<Array<{ x: number; y: number; color: number }>>([]) // 悔棋历史
const isRequestingDraw = ref(false) // 是否正在请求和棋
const isWaitingForDrawResponse = ref(false) // 等待和棋响应
const isWaitingForUndoResponse = ref(false) // 等待悔棋响应
const isChangingTable = ref(false) // 正在换桌中（防止重复处理）

// 响应状态提示
const undoResponseStatus = ref<{ accepted: boolean | null; show: boolean }>({ accepted: null, show: false })
const drawResponseStatus = ref<{ accepted: boolean | null; show: boolean }>({ accepted: null, show: false })

// 再来一局状态
const playAgainStatus = ref({
  opponentRequested: false, // 对手是否点了再来一局
  localRequested: false, // 我是否点了再来一局
  opponentLeft: false, // 对手是否已离开
  opponentChanged: false // 对手是否已换桌
})

const winRate = computed(() => {
  const userInfo = userStore.userInfo
  if (!userInfo || userInfo.wins + userInfo.losses === 0) {
    return 0
  }
  return Math.round((userInfo.wins / (userInfo.wins + userInfo.losses)) * 100)
})

const matchModeText = computed(() => {
  return matchMode.value === 'casual' ? '休闲对局' : '竞技对决'
})

const handleBack = () => {
  if (isMatching.value || gameStarted.value) {
    ElMessage.warning('游戏进行中，无法返回')
    return
  }
  router.push('/home?panel=quick-match')
}

const handleStartMatch = async (mode: 'casual' | 'ranked') => {
  await startMatch(mode)
}

const startMatch = async (mode: 'casual' | 'ranked', avoidOpponentId?: number) => {
  // 检查登录状态
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }

  // 检查并连接WebSocket
  console.log('[Match] 检查WebSocket连接状态...')
  const isConnected = wsClient.isConnected()
  console.log('[Match] WebSocket已连接:', isConnected)

  if (!isConnected) {
    ElMessage.warning({
      message: '正在连接WebSocket...',
      duration: 2000
    })

    try {
      console.log('[Match] 开始连接WebSocket...')
      await wsClient.connect(token)
      console.log('[Match] WebSocket连接成功')
      ElMessage.success('WebSocket已连接')
    } catch (error) {
      console.error('[Match] WebSocket连接失败:', error)
      ElMessage.error('WebSocket连接失败，请检查网络或刷新页面重试')
      return
    }
  }

  matchMode.value = mode
  isMatching.value = true

  try {
    // 定义匹配消息处理函数
    const handleMatchMessage = (data: any, source: string) => {
      console.log(`[Match] 收到匹配消息 (${source}):`, data)

      switch (data.type) {
        case 'MATCH_SUCCESS':
          ElMessage.success(`匹配成功！对手：${data.opponentName || '未知'}`)
          isMatching.value = false
          // 初始化游戏
          roomId.value = data.roomId
          myColor.value = data.color
          opponentName.value = data.opponentName || '未知'
          opponentId.value = data.opponentId || null
          board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
          currentTurn.value = 1
          lastMove.value = null
          pendingMove.value = null
          gameStarted.value = true

          // 重置倒计时 - 每方各自5分钟
          myTime.value = 300
          opponentTime.value = 300

          // 重置聊天和游戏操作状态
          chatMessages.value = []
          chatInput.value = ''
          moveHistory.value = []
          canUndo.value = false
          isRequestingDraw.value = false

          // 订阅游戏消息（后端已经在创建房间时添加了两个玩家）
          subscribeToGame()

          // 开始计时
          startTimer()
          break

        case 'MATCH_WAITING':
          console.log('[Match] 正在等待对手...', data.queueSize)
          // ElMessage.info(`正在寻找对手... 当前等待人数: ${data.queueSize}`)
          break

        case 'MATCH_ERROR':
          ElMessage.error(data.message || '匹配失败')
          isMatching.value = false
          break

        default:
          console.warn('[Match] 未知的匹配消息类型:', data.type)
      }
    }

    // 订阅用户特定的匹配 topic
    const userId = userStore.userInfo?.id
    if (!userId) {
      throw new Error('用户未登录')
    }

    console.log('[Match] 订阅匹配结果 /topic/match/' + userId + '...')
    matchingSubscription.value = wsClient.subscribe('/topic/match/' + userId, (data: any) => {
      handleMatchMessage(data, 'topic')
    })

    if (!matchingSubscription.value) {
      throw new Error('订阅匹配结果失败')
    }

    console.log('[Match] 匹配订阅成功: topic=' + matchingSubscription.value)

    // 订阅成功后再发送匹配请求
    console.log('[Match] 发送匹配请求:', mode, avoidOpponentId)
    const payload: any = { mode: mode } // 使用用户选择的模式
    if (avoidOpponentId) {
      payload.avoidOpponentId = avoidOpponentId
    }
    wsClient.send('/app/match/start', payload)
  } catch (error) {
    console.error('[Match] Start match error:', error)
    ElMessage.error('匹配失败，请重试')
    isMatching.value = false
  }
}

// 订阅游戏消息
const subscribeToGame = () => {
  if (!roomId.value) return

  gameSubscription.value = wsClient.subscribe(`/topic/room/${roomId.value}`, (data: any) => {
    console.log('[Match] 收到游戏消息:', data)
    console.log('[Match] 消息类型:', data.type)

    switch (data.type) {
      case 'GAME_STATE':
        console.log('[Match] 收到游戏状态更新:', data)

        // 检查并同步颜色（防止前端颜色与后端不一致）
        if (data.blackPlayer && data.whitePlayer && userStore.userInfo?.id) {
          const myId = userStore.userInfo.id
          // 如果我是黑棋
          if (data.blackPlayer.id === myId && myColor.value !== 1) {
            myColor.value = 1
            console.log('[Match] 颜色同步：我是黑棋')
          }
          // 如果我是白棋
          else if (data.whitePlayer.id === myId && myColor.value !== 2) {
            myColor.value = 2
            console.log('[Match] 颜色同步：我是白棋')
          }
        }

        // 更新整个棋盘
        if (data.board && Array.isArray(data.board)) {
          // 找到最新落子的位置（用于高亮显示）
          let newMove: { x: number; y: number } | null = null
          const previousColor = currentTurn.value === 1 ? 2 : 1 // 上一个落子的颜色

          // 如果有pending move，优先使用它（这是我们自己的落子）
          if (pendingMove.value) {
            newMove = pendingMove.value
            pendingMove.value = null // 清除pending
          } else {
            // 否则查找对手的落子
            for (let x = 0; x < 15; x++) {
              for (let y = 0; y < 15; y++) {
                if (data.board[x] && data.board[x][y] !== 0 && board.value[x][y] === 0) {
                  newMove = { x, y }
                  // 记录对手的落子到历史
                  moveHistory.value.push({ x, y, color: previousColor })
                  break
                }
              }
              if (newMove) break
            }
          }

          board.value = data.board
          lastMove.value = newMove
          canUndo.value = moveHistory.value.length > 0
        }

        // 更新回合
        if (data.currentTurn) {
          currentTurn.value = data.currentTurn
        }

        // 同步倒计时 - 每方各自的时间
        if (data.blackTime !== undefined) {
          if (myColor.value === 1) {
            myTime.value = data.blackTime
          } else {
            opponentTime.value = data.blackTime
          }
        }
        if (data.whiteTime !== undefined) {
          if (myColor.value === 2) {
            myTime.value = data.whiteTime
          } else {
            opponentTime.value = data.whiteTime
          }
        }

        // 检查游戏状态
        if (data.gameStatus === 'FINISHED') {
          gameStarted.value = false
        }
        break

      case 'GAME_OVER':
        console.log('[Match] 游戏结束:', data)
        // 关闭所有可能存在的弹窗
        ElMessageBox.close()
        // 重置所有等待状态
        isWaitingForDrawResponse.value = false
        isWaitingForUndoResponse.value = false
        isRequestingDraw.value = false

        const myUserId = userStore.userInfo?.id
        const isWin = data.winnerId === myUserId
        const endReason = data.endReason || data.reason || ''
        const mode = data.mode || 'casual'
        // 计算我的积分变化
        let myRatingChange = 0
        if (myColor.value === 1) {
          myRatingChange = data.blackRatingChange || 0
        } else {
          myRatingChange = data.whiteRatingChange || 0
        }
        handleGameOver(isWin, endReason, mode, myRatingChange)
        break

      case 'GAME_MOVE':
        console.log('[Match] 收到落子消息:', data)
        // 兼容旧格式，如果服务器发送单步落子消息
        if (data.x !== undefined && data.y !== undefined && data.color !== undefined) {
          board.value[data.x][data.y] = data.color
          lastMove.value = { x: data.x, y: data.y }
          const nextTurn = data.color === 1 ? 2 : 1
          currentTurn.value = nextTurn

          if (checkWin(data.x, data.y, data.color)) {
            ElMessage.success(data.color === myColor.value ? '你赢了！' : '对手赢了！')
            gameStarted.value = false
          }
        }
        break

      case 'CHAT_MESSAGE':
        console.log('[Match] 收到聊天消息:', data)
        const chatSender = data.sender || data.username || '对手'
        const chatContent = data.content || data.message || ''
        if (chatContent) {
          // 检查发送者是否是自己
          const isMine = chatSender === userStore.userInfo?.username
          addChatMessage(chatSender, chatContent, isMine)
        }
        break

      case 'DRAW_REQUEST':
        console.log('[Match] 收到和棋请求')
        // 判断是否是自己发送的请求
        const isMyRequest = data.requesterId === userStore.userInfo?.id
        if (!isMyRequest) {
          // 对方发送的请求，显示确认弹窗
          ElMessageBox.confirm('对手请求和棋，是否同意？', '和棋请求', {
            confirmButtonText: '同意',
            cancelButtonText: '拒绝',
            type: 'info'
          }).then(() => {
            wsClient.send('/app/game/draw', { roomId: roomId.value, accept: true })
          }).catch(() => {
            wsClient.send('/app/game/draw', { roomId: roomId.value, accept: false })
          })
        }
        break

      case 'DRAW_RESPONSE':
        console.log('[Match] 收到和棋响应:', data)
        // 关闭所有可能存在的弹窗
        ElMessageBox.close()

        isWaitingForDrawResponse.value = false
        isRequestingDraw.value = false

        if (data.accepted) {
          // 和棋成功，等待游戏结束消息
          ElMessage.success('对手同意了和棋请求')
        } else {
          // 显示拒绝状态卡片
          drawResponseStatus.value = { accepted: false, show: true }
          // 3秒后自动隐藏
          setTimeout(() => {
            drawResponseStatus.value.show = false
          }, 3000)
        }
        break

      case 'UNDO_REQUEST':
        console.log('[Match] 收到悔棋请求')
        // 判断是否是自己发送的请求
        const isMyUndoRequest = data.requesterId === userStore.userInfo?.id
        if (!isMyUndoRequest) {
          // 对方发送的请求，显示确认弹窗
          ElMessageBox.confirm('对手请求悔棋，是否同意？', '悔棋请求', {
            confirmButtonText: '同意',
            cancelButtonText: '拒绝',
            type: 'info'
          }).then(() => {
            wsClient.send('/app/game/undo', { roomId: roomId.value, accept: true })
          }).catch(() => {
            wsClient.send('/app/game/undo', { roomId: roomId.value, accept: false })
          })
        }
        break

      case 'UNDO_RESPONSE':
        console.log('[Match] 收到悔棋响应:', data)
        isWaitingForUndoResponse.value = false
        // 显示响应状态卡片
        undoResponseStatus.value = { accepted: data.accepted, show: true }
        // 3秒后自动隐藏
        setTimeout(() => {
          undoResponseStatus.value.show = false
        }, 3000)
        break

      case 'UNDO_SUCCESS':
        console.log('[Match] 收到悔棋成功消息:', data)
        ElMessage.success('悔棋成功！')
        // 关闭所有可能存在的弹窗
        ElMessageBox.close()

        // 重置等待状态
        isWaitingForUndoResponse.value = false

        // 更新棋盘状态
        if (data.board && Array.isArray(data.board)) {
          board.value = data.board
        }

        // 更新当前回合
        if (data.currentTurn !== undefined) {
          currentTurn.value = data.currentTurn
        }

        // 移除最后一步历史记录
        if (moveHistory.value.length > 0 && data.undoneMove) {
          moveHistory.value.pop()
        }

        // 同步倒计时
        if (data.blackTime !== undefined) {
          if (myColor.value === 1) {
            myTime.value = data.blackTime
          } else {
            opponentTime.value = data.blackTime
          }
        }
        if (data.whiteTime !== undefined) {
          if (myColor.value === 2) {
            myTime.value = data.whiteTime
          } else {
            opponentTime.value = data.whiteTime
          }
        }

        // 更新最后落子位置
        if (moveHistory.value.length > 0) {
          const lastMove = moveHistory.value[moveHistory.value.length - 1]
          lastMovePosition.value = { x: lastMove.x, y: lastMove.y }
        } else {
          lastMovePosition.value = null
        }

        canUndo.value = false
        break

      case 'PLAY_AGAIN_REQUEST':
        console.log('[Match] 收到再来一局请求:', data)
        // 对手点了再来一局，更新状态
        playAgainStatus.value.opponentRequested = true
        // 不显示弹窗，只更新按钮状态
        console.log('[Match] 对手已点击再来一局')
        break

      case 'PLAY_AGAIN_ACCEPT':
        console.log('[Match] 收到再来一局接受:', data)
        // 后端已经重置了游戏状态，更新前端状态
        showGameOverDialog.value = false
        gameStarted.value = true
        // 重置再来一局状态
        playAgainStatus.value = {
          opponentRequested: false,
          localRequested: false,
          opponentLeft: false,
          opponentChanged: false
        }
        break

      case 'CHANGE_TABLE':
        console.log('[Match] 收到换桌消息')
        // ElMessage.info('对手选择了换桌')  // 已有状态显示，不需要提示
        // 设置对手已换桌状态
        playAgainStatus.value.opponentChanged = true
        playAgainStatus.value.opponentLeft = false
        playAgainStatus.value.opponentRequested = false
        // 延迟后自动开始新的匹配
        setTimeout(() => {
          showGameOverDialog.value = false
          playAgainStatus.value = {
            opponentRequested: false,
            localRequested: false,
            opponentLeft: false,
            opponentChanged: false
          }
          // 自动开始新的匹配，排除刚刚对战的对手
          console.log('[Match] 对手换桌，自动开始新的匹配')
          const avoidOpponent = opponentId.value ?? undefined
          startMatch(matchMode.value, avoidOpponent)
        }, 2000)
        break

      case 'OPPONENT_LEFT':
        console.log('[Match] 对手离开了')
        console.log('[Match] 设置 opponentLeft = true')
        // 不关闭弹窗，保持打开状态
        // 更新状态显示对手已离开
        playAgainStatus.value.opponentLeft = true
        console.log('[Match] opponentLeft 状态:', playAgainStatus.value.opponentLeft)
        // ElMessage.info('对手离开了游戏')  // 已有状态显示，不需要提示
        break

      case 'PLAYER_READY':
        console.log('[Match] 对手已准备再来一局')
        // 只有当消息来自对手（不是自己）时才处理
        if (data.userId && data.userId !== userStore.userInfo?.id) {
          // 对手点了一起来，更新状态
          playAgainStatus.value.opponentRequested = true
          playAgainStatus.value.opponentLeft = false
          playAgainStatus.value.opponentChanged = false
          // ElMessage.info('对方已准备')  // 已有状态显示，不需要提示

          // 如果我也准备好了，双方都准备完毕，直接重新开始
          if (playAgainStatus.value.localRequested) {
            console.log('[Match] 双方都准备好了，重新开始游戏')
            startNewGame()
          }
        }
        break

      default:
        console.log('[Match] 未知消息类型:', data.type, data)
    }
  })

  console.log('[Match] 已订阅游戏房间:', roomId.value)
}

// 玩家落子
const handleMove = (x: number, y: number) => {
  console.log('[Match] handleMove 被调用:', { x, y, currentTurn: currentTurn.value, myColor: myColor.value })

  if (currentTurn.value !== myColor.value) {
    console.log('[Match] 不是我的回合')
    ElMessage.warning('请等待对手落子')
    return
  }
  if (board.value[x][y] !== 0) {
    console.log('[Match] 位置已有棋子')
    ElMessage.warning('该位置已有棋子')
    return
  }

  // 本地立即更新棋盘（优化用户体验）
  board.value[x][y] = myColor.value
  lastMove.value = { x, y }
  pendingMove.value = { x, y } // 标记为pending move

  // 发送落子消息到服务器
  console.log('[Match] 发送落子到服务器:', { roomId: roomId.value, x, y, color: myColor.value })
  wsClient.send('/app/game/move', {
    roomId: roomId.value,
    x,
    y,
    color: myColor.value
  })

  // 记录落子历史（用于悔棋）- 注意：只有在服务器确认后才能真正悔棋
  // 这里先记录，如果服务器返回错误则需要移除
  moveHistory.value.push({ x, y, color: myColor.value })
  canUndo.value = true

  console.log('[Match] 本地棋盘已更新，等待WebSocket同步回合')
}

// 悔棋
const handleUndo = () => {
  // 只有刚下完棋的人才能悔棋（当前轮到对手下棋）
  if (currentTurn.value === myColor.value) {
    ElMessage.warning('只能在对手下棋前悔棋')
    return
  }

  if (!canUndo.value) {
    ElMessage.warning('无法悔棋')
    return
  }

  // 发送悔棋请求
  wsClient.send('/app/game/undo', {
    roomId: roomId.value,
    request: true
  })

  isWaitingForUndoResponse.value = true
  ElMessage.info('已发送悔棋请求，等待对手同意...')
}

// 和棋
const handleDraw = () => {
  if (isRequestingDraw.value || isWaitingForDrawResponse.value) {
    ElMessage.warning('已发送和棋请求，请等待对手响应')
    return
  }

  ElMessageBox.confirm('确定要请求和棋吗？', '和棋请求', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(() => {
    wsClient.send('/app/game/draw', {
      roomId: roomId.value,
      request: true
    })
    isRequestingDraw.value = true
    isWaitingForDrawResponse.value = true
    ElMessage.info('已发送和棋请求，等待对手响应...')
  }).catch(() => {
    // 用户取消
  })
}

// 认输
const handleResign = () => {
  ElMessageBox.confirm('确定要认输吗？', '认输', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    wsClient.send('/app/game/resign', {
      roomId: roomId.value
    })
    ElMessage.info('已认输')
    gameStarted.value = false
  }).catch(() => {
    // 用户取消
  })
}

// 取消请求（悔棋或和棋）
const cancelRequest = () => {
  if (isWaitingForUndoResponse.value) {
    // 取消悔棋请求
    isWaitingForUndoResponse.value = false
    ElMessage.info('已取消悔棋请求')
  } else if (isWaitingForDrawResponse.value) {
    // 取消和棋请求
    isRequestingDraw.value = false
    isWaitingForDrawResponse.value = false
    ElMessage.info('已取消和棋请求')
  }
}

// 添加聊天消息
const addChatMessage = (sender: string, content: string, isMine: boolean) => {
  const now = new Date()
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`

  chatMessages.value.push({
    sender,
    content,
    time,
    isMine
  })

  // 滚动到底部
  nextTick(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
    }
  })
}

// 发送聊天消息
const sendChatMessage = () => {
  const content = chatInput.value.trim()
  if (!content) {
    ElMessage.warning('请输入消息内容')
    return
  }

  console.log('[Match] 发送聊天消息:', { roomId: roomId.value, content })

  try {
    // 发送到服务器
    wsClient.send('/app/game/chat', {
      roomId: roomId.value,
      content
    })

    // 清空输入框（不立即添加到聊天记录，等待服务器广播）
    chatInput.value = ''
  } catch (error) {
    console.error('[Match] 发送聊天消息失败:', error)
    ElMessage.error('发送失败，请重试')
  }
}

// 格式化时间显示
const formatTime = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

// 判断是否是星位
const isStarPoint = (x: number, y: number): boolean => {
  const stars = [
    [3, 3], [3, 7], [3, 11],
    [7, 3], [7, 7], [7, 11],
    [11, 3], [11, 7], [11, 11]
  ]
  return stars.some(([sx, sy]) => sx === x && sy === y)
}

// 开始计时
const startTimer = () => {
  // 清除旧的定时器
  stopTimer()

  // 记录上次更新时间
  let lastUpdateTime = Date.now()

  timerInterval = window.setInterval(() => {
    const now = Date.now()
    const elapsed = Math.floor((now - lastUpdateTime) / 1000)

    // 只有经过足够时间才更新（每秒）
    if (elapsed >= 1) {
      // 只减少当前回合玩家的时间
      if (currentTurn.value === myColor.value) {
        myTime.value = Math.max(0, myTime.value - elapsed)
        if (myTime.value <= 0) {
          // 我超时 - 通知服务器
          try {
            wsClient.send('/app/game/timeout', {
              roomId: roomId.value,
              userId: userStore.userInfo?.id
            })
          } catch (error) {
            console.error('[Match] 发送超时消息失败:', error)
          }
          // 本地也结束游戏
          handleGameOver(false, '超时')
          return
        }
      } else {
        opponentTime.value = Math.max(0, opponentTime.value - elapsed)
        if (opponentTime.value <= 0) {
          // 对手超时 - 不需要通知（对手会通知）
          handleGameOver(true, '超时')
          return
        }
      }

      lastUpdateTime = now
    }
  }, 100) // 更频繁地检查，以确保准确性

  console.log('[Match] 开始计时')
}

// 停止计时
const stopTimer = () => {
  if (timerInterval !== null) {
    clearInterval(timerInterval)
    timerInterval = null
    console.log('[Match] 停止计时')
  }
}

// 处理游戏结束
const handleGameOver = (isWin: boolean, reason: string | number, mode: string = 'casual', ratingChange: number = 0) => {
  console.log('[Match] 游戏结束:', { isWin, reason, mode, ratingChange })

  stopTimer()
  gameStarted.value = false

  // 记录游戏结果到后端（异步，不阻塞UI）
  if (userStore.userInfo?.id) {
    let resultType: 'win' | 'lose' | 'draw' = 'draw'
    if (isWin) {
      resultType = 'win'
    } else if (reason === 2 || reason === '和棋') {
      resultType = 'draw'
    } else {
      resultType = 'lose'
    }

    // 根据游戏模式选择正确的gameMode
    const gameModeType = mode === 'ranked' ? 'RANKED' : 'PVP'

    // 异步调用API记录游戏结果
    userApi.recordGame(userStore.userInfo.id, resultType, gameModeType)
      .then(response => {
        if (response && response.success) {
          console.log('[Match] 游戏结果已记录:', response.data)
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
        console.error('[Match] 记录游戏结果失败:', err)
      })
  }

  // 使用用户选择的模式用于显示
  const displayMode = matchMode.value

  // 将数字reason转换为字符串reason
  let reasonStr = ''
  if (typeof reason === 'number') {
    // 0: 正常胜负, 1: 超时, 2: 和棋, 3: 认输
    switch (reason) {
      case 1: reasonStr = '超时'; break
      case 2: reasonStr = '和棋'; break
      case 3: reasonStr = '认输'; break
      default: reasonStr = '正常胜负'
    }
  } else {
    reasonStr = reason
  }

  // 根据模式和结果设置结算信息
  if (reasonStr === '超时') {
    if (isWin) {
      gameResult.value = {
        title: '胜利！',
        icon: '🏆',
        message: '对手超时，你赢了！',
        details: displayMode === 'ranked' ? `积分 ${ratingChange > 0 ? '+' : ''}${ratingChange}` : '恭喜获得胜利',
        mode: displayMode,
        ratingChange: ratingChange
      }
    } else {
      gameResult.value = {
        title: '失败',
        icon: '⏰',
        message: '思考时间超时',
        details: displayMode === 'ranked' ? `积分 ${ratingChange > 0 ? '+' : ''}${ratingChange}` : '很遗憾，你输了',
        mode: displayMode,
        ratingChange: ratingChange
      }
    }
  } else if (reasonStr === '认输') {
    if (isWin) {
      gameResult.value = {
        title: '胜利！',
        icon: '🏆',
        message: '对手认输',
        details: displayMode === 'ranked' ? `积分 ${ratingChange > 0 ? '+' : ''}${ratingChange}` : '恭喜获得胜利',
        mode: displayMode,
        ratingChange: ratingChange
      }
    } else {
      gameResult.value = {
        title: '失败',
        icon: '🏳️',
        message: '你认输了',
        details: displayMode === 'ranked' ? `积分 ${ratingChange > 0 ? '+' : ''}${ratingChange}` : '再接再厉',
        mode: displayMode,
        ratingChange: ratingChange
      }
    }
  } else if (reasonStr === '和棋') {
    gameResult.value = {
      title: '和棋',
      icon: '🤝',
      message: '双方达成和棋',
      details: displayMode === 'ranked' ? '积分无变化' : '平局结束',
      mode: displayMode,
      ratingChange: 0
    }
  } else {
    // 正常胜负
    if (isWin) {
      gameResult.value = {
        title: '胜利！',
        icon: '🏆',
        message: '恭喜你赢了！',
        details: displayMode === 'ranked' ? `积分 ${ratingChange > 0 ? '+' : ''}${ratingChange}` : '五子连珠，精彩对局',
        mode: displayMode,
        ratingChange: ratingChange
      }
    } else {
      gameResult.value = {
        title: '失败',
        icon: '😔',
        message: '很遗憾，你输了',
        details: displayMode === 'ranked' ? `积分 ${ratingChange > 0 ? '+' : ''}${ratingChange}` : '对手更胜一筹',
        mode: displayMode,
        ratingChange: ratingChange
      }
    }
  }

  // 显示结算弹窗
  showGameOverDialog.value = true
}

// 返回首页
const handleBackToHome = () => {
  console.log('[Match] 返回首页，清理状态')
  console.log('[Match] showGameOverDialog:', showGameOverDialog.value)

  // 如果游戏已结束，发送消息后跳转
  if (showGameOverDialog.value) {
    // 发送离开房间消息，通知对手
    if (roomId.value) {
      console.log('[Match] 发送离开房间消息，通知对手，roomId:', roomId.value)
      console.log('[Match] 我的userId:', userStore.userInfo?.id)
      console.log('[Match] WebSocket连接状态:', wsClient.isConnected())

      const payload = {
        roomId: roomId.value,
        userId: userStore.userInfo?.id
      }
      console.log('[Match] 发送payload:', payload)

      wsClient.send('/app/game/leave', payload)
      console.log('[Match] 已发送 /app/game/leave 消息，等待对手接收...')
    }
    // 关闭弹窗
    showGameOverDialog.value = false
    // 延迟跳转，确保消息发送出去
    setTimeout(() => {
      console.log('[Match] 跳转到首页')
      router.push('/home?panel=quick-match')
    }, 500)
    return
  }

  // 游戏进行中的处理（保持原有逻辑）
  showGameOverDialog.value = false

  // 先检查状态（在重置之前）
  const needCancelMyRequest = playAgainStatus.value.localRequested

  if (needCancelMyRequest) {
    wsClient.send('/app/game/play-again', {
      roomId: roomId.value,
      cancel: true
    })
  }

  // 发送离开房间消息
  if (roomId.value) {
    wsClient.send('/app/game/leave', {
      roomId: roomId.value
    })
  }

  // 重置再来一局状态
  playAgainStatus.value = {
    opponentRequested: false,
    localRequested: false,
    opponentLeft: false,
    opponentChanged: false
  }

  // 清理订阅和状态
  stopTimer()
  if (matchingSubscription.value) {
    wsClient.unsubscribe(matchingSubscription.value)
    matchingSubscription.value = null
  }
  if (gameSubscription.value) {
    wsClient.unsubscribe(gameSubscription.value)
    gameSubscription.value = null
  }

  // 如果正在匹配，取消匹配
  if (isMatching.value) {
    wsClient.send('/app/match/cancel', { mode: matchMode.value })
    isMatching.value = false
  }

  // 重置游戏状态
  gameStarted.value = false
  roomId.value = ''
  myColor.value = 1
  opponentName.value = ''
  opponentId.value = null
  board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
  currentTurn.value = 1
  lastMove.value = null
  lastMovePosition.value = null
  pendingMove.value = null
  moveHistory.value = []
  canUndo.value = false
  isRequestingDraw.value = false
  myTime.value = 300
  opponentTime.value = 300

  router.push('/home?panel=quick-match')
}

// 换桌 - 重新开始匹配
const handleChangeTable = (notifyOpponent = true) => {
  // 防止重复调用
  if (isChangingTable.value) {
    console.log('[Match] 正在换桌中，跳过重复调用')
    return
  }

  isChangingTable.value = true

  showGameOverDialog.value = false
  // 重置再来一局状态
  playAgainStatus.value = {
    opponentRequested: false,
    localRequested: false,
    opponentLeft: false,
    opponentChanged: false
  }

  // 先设置 isMatching = true，确保显示匹配界面
  isMatching.value = true

  // 然后重置游戏状态
  gameStarted.value = false
  board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
  currentTurn.value = 1
  lastMove.value = null
  lastMovePosition.value = null
  pendingMove.value = null
  myTime.value = 300
  opponentTime.value = 300
  chatMessages.value = []
  moveHistory.value = []
  canUndo.value = false
  isRequestingDraw.value = false

  // 通知后端换桌（仅在主动点击换桌时通知）
  if (notifyOpponent) {
    wsClient.send('/app/game/change-table', { roomId: roomId.value })
  }

  // 开始新的匹配，排除刚刚对战的对手
  const avoidOpponent = opponentId.value
  startMatch(matchMode.value, avoidOpponent)

  // 延迟重置标志
  setTimeout(() => {
    isChangingTable.value = false
  }, 2000)
}

// 发送再来一局请求
const handlePlayAgainRequest = () => {
  // 发送再来一局请求
  wsClient.send('/app/game/play-again', {
    roomId: roomId.value,
    request: true
  })
  playAgainStatus.value.localRequested = true
  ElMessage.info('已发送再来一局请求，等待对手响应...')
}

// 接收方点再来一局（同意）
const handlePlayAgainAccept = () => {
  console.log('[Match] 点击再来一局')

  // 如果对手已经请求了，我同意并发送accept
  if (playAgainStatus.value.opponentRequested) {
    console.log('[Match] 对手已请求，我发送accept')
    wsClient.send('/app/game/play-again', {
      roomId: roomId.value,
      accept: true
    })
    playAgainStatus.value.localRequested = true
  } else {
    // 我是第一个点击的，发送request
    console.log('[Match] 我是第一个点击的，发送request')
    wsClient.send('/app/game/play-again', {
      roomId: roomId.value,
      request: true
    })
    playAgainStatus.value.localRequested = true
  }

  playAgainStatus.value.opponentLeft = false // 重置对手离开状态
  playAgainStatus.value.opponentChanged = false // 重置对手换桌状态
}

// 重置并开始新游戏（双方都同意后）
const startNewGame = () => {
  console.log('[Match] 双方都同意，等待后端重置游戏状态')

  // 重置游戏状态
  showGameOverDialog.value = false
  gameStarted.value = true

  // 重置所有请求状态
  isRequestingDraw.value = false
  isWaitingForDrawResponse.value = false
  isWaitingForUndoResponse.value = false
  drawResponseStatus.value = { accepted: null, show: false }
  undoResponseStatus.value = { accepted: null, show: false }
  playAgainStatus.value = {
    opponentRequested: false,
    localRequested: false,
    opponentLeft: false,
    opponentChanged: false
  }

  // 不清空棋盘，等待后端发送新的 GAME_STATE 消息
  console.log('[Match] 等待后端发送新的游戏状态...')
}

// 检查胜负
const checkWin = (x: number, y: number, color: number): boolean => {
  const directions = [
    [1, 0],   // 横向
    [0, 1],   // 纵向
    [1, 1],   // 对角线
    [1, -1]   // 反对角线
  ]

  for (const [dx, dy] of directions) {
    let count = 1

    // 正方向
    for (let i = 1; i < 5; i++) {
      const nx = x + dx * i
      const ny = y + dy * i
      if (nx < 0 || nx >= 15 || ny < 0 || ny >= 15) break
      if (board.value[nx][ny] !== color) break
      count++
    }

    // 反方向
    for (let i = 1; i < 5; i++) {
      const nx = x - dx * i
      const ny = y - dy * i
      if (nx < 0 || nx >= 15 || ny < 0 || ny >= 15) break
      if (board.value[nx][ny] !== color) break
      count++
    }

    if (count >= 5) return true
  }

  return false
}

const handleCancelMatch = () => {
  try {
    // 发送取消匹配请求，包含模式信息
    wsClient.send('/app/match/cancel', { mode: matchMode.value })
    if (matchingSubscription.value) {
      wsClient.unsubscribe(matchingSubscription.value)
      matchingSubscription.value = ''
    }
    isMatching.value = false
    ElMessage.info('已取消匹配')
    // 跳转到首页
    router.push('/home?panel=quick-match')
  } catch (error) {
    console.error('[Match] Cancel match error:', error)
    ElMessage.error('取消匹配失败')
  }
}

onMounted(async () => {
  // 确保用户信息已加载
  if (!userStore.userInfo) {
    await userStore.initUserInfo()
  }

  // 检查URL中是否有mode参数，如果有则自动开始匹配
  const mode = route.query.mode as 'casual' | 'ranked'
  if (mode && (mode === 'casual' || mode === 'ranked')) {
    matchMode.value = mode
    // 自动开始匹配
    await startMatch(mode)
  }
})

// 监听路由变化，处理换桌等情况
watch(() => route.query.mode, async (newMode) => {
  if (newMode && (newMode === 'casual' || newMode === 'ranked')) {
    // 只有在当前没有游戏且没有正在匹配时才开始新匹配
    if (!gameStarted.value && !isMatching.value) {
      matchMode.value = newMode as 'casual' | 'ranked'
      await startMatch(newMode as 'casual' | 'ranked')
    }
  }
})

onBeforeUnmount(() => {
  console.log('[Match] onBeforeUnmount - 清理订阅')
  // 清理订阅和状态
  stopTimer()
  if (matchingSubscription.value) {
    wsClient.unsubscribe(matchingSubscription.value)
    matchingSubscription.value = null
  }
  if (gameSubscription.value) {
    wsClient.unsubscribe(gameSubscription.value)
    gameSubscription.value = null
  }
})

onUnmounted(() => {
  // 停止计时
  stopTimer()

  // 清理订阅
  if (matchingSubscription.value) {
    wsClient.unsubscribe(matchingSubscription.value)
  }
  if (gameSubscription.value) {
    wsClient.unsubscribe(gameSubscription.value)
  }
  // 取消匹配
  if (isMatching.value) {
    handleCancelMatch()
  }
})
</script>

<style scoped>
.match-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
  padding: 20px;
}

.match-header {
  display: flex;
  align-items: center;
  margin-bottom: 32px;
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

.user-info-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.user-avatar {
  background: linear-gradient(135deg, #ff9a8b 0%, #ff6a88 100%);
  color: white;
  font-size: 24px;
  font-weight: 600;
}

.user-details {
  flex: 1;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.user-stats {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #666;
  font-size: 14px;
}

.stat-item .el-icon {
  color: #ff6a88;
}

.match-modes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.match-mode-card {
  background: white;
  border-radius: 16px;
  padding: 40px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.match-mode-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
}

.mode-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  color: white;
}

.mode-icon.casual {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
}

.mode-icon.ranked {
  background: linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%);
}

.mode-title {
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.mode-description {
  font-size: 14px;
  color: #999;
  margin: 0;
}

.match-start-section {
  max-width: 400px;
  margin: 40px auto;
}

.match-mode-card-start {
  background: white;
  border-radius: 16px;
  padding: 40px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.match-mode-card-start:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
}

.matching-status {
  background: white;
  border-radius: 16px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  max-width: 500px;
  margin: 0 auto;
}

.loading-animation {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
}

.circle {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff9a8b 0%, #ff6a88 100%);
  animation: bounce 1.4s infinite ease-in-out both;
}

.circle:nth-child(1) {
  animation-delay: -0.32s;
}

.circle:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.matching-text {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.matching-mode {
  font-size: 16px;
  color: #999;
  margin: 0 0 40px 0;
}

.cancel-button {
  width: 200px;
  border-radius: 8px;
  height: 44px;
  font-size: 16px;
}

/* 游戏界面样式 */
.game-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.game-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 15px 20px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.game-player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-radius: 10px;
  transition: all 0.3s;
}

.game-player-info.active {
  background: rgba(255, 107, 53, 0.2);
  box-shadow: 0 0 15px rgba(255, 107, 53, 0.3);
}

.game-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  font-size: 20px;
}

.game-player-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.game-player-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.game-player-color {
  font-size: 12px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
}

.game-piece-icon {
  font-size: 14px;
}

.game-timer {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: #333;
  font-family: 'Courier New', monospace;
}

.game-timer.warning {
  color: #e6a23c;
}

.game-timer.danger {
  color: #f56c6c;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.game-vs {
  font-size: 24px;
  font-weight: bold;
  color: #999;
}

.game-status {
  text-align: center;
  margin-bottom: 20px;
}

.game-status-turn {
  display: inline-block;
  padding: 8px 24px;
  background: linear-gradient(135deg, #67c23a, #85ce61);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.game-status-thinking {
  display: inline-block;
  padding: 8px 24px;
  background: linear-gradient(135deg, #e6a23c, #f0c78a);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

/* 棋盘样式 */
.board-container {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.board {
  width: 450px;
  height: 450px;
  background: linear-gradient(135deg, #f5e6d3 0%, #e8d5c4 100%);
  border-radius: 8px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  display: grid;
  grid-template-columns: repeat(15, 30px);
  grid-template-rows: repeat(15, 30px);
  padding: 0;
  position: relative;
}

.board::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    linear-gradient(#ff8c61 1px, transparent 1px),
    linear-gradient(90deg, #ff8c61 1px, transparent 1px);
  background-size: 30px 30px;
  background-position: 15px 15px;
  pointer-events: none;
  z-index: 0;
}

.row {
  display: contents;
}

.cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1;
}

.cell:hover {
  background: rgba(255, 107, 53, 0.15);
}

.piece {
  position: absolute;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  z-index: 2;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}

.piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.last-move {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #ff6b35;
  border-radius: 50%;
}

.star-point {
  position: absolute;
  width: 8px;
  height: 8px;
  background: #ff8c61;
  border-radius: 50%;
  z-index: 0;
}

/* 游戏操作按钮 */
.game-actions {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin: 20px 0;
}

.game-action-btn {
  min-width: 100px;
  height: 40px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 0.3s;
}

.game-action-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}

.game-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.game-action-btn.danger {
  background: linear-gradient(135deg, #f56c6c, #e85555);
  border: none;
  color: white;
}

.game-action-btn.danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #e85555, #d14e4e);
}

/* 等待响应提示卡片 */
.waiting-response-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 15px auto;
  padding: 12px 20px;
  max-width: 400px;
  background: linear-gradient(135deg, #fff5e6 0%, #ffe8cc 100%);
  border: 2px solid #ffd591;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(255, 165, 0, 0.2);
  animation: slideInDown 0.3s ease-out;
}

.waiting-icon {
  font-size: 24px;
  animation: pulse 1.5s ease-in-out infinite;
}

.waiting-text {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #d48806;
  text-align: center;
}

@keyframes slideInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.2);
  }
}

/* 响应状态提示卡片 */
.response-status-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 15px auto;
  padding: 12px 20px;
  max-width: 400px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  animation: slideInDown 0.3s ease-out;
}

.response-status-card.accepted {
  background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%);
  border: 2px solid #b8dabc;
}

.response-status-card.rejected {
  background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%);
  border: 2px solid #f1b0b7;
}

.response-icon {
  font-size: 24px;
}

.response-text {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
}

.response-status-card.accepted .response-text {
  color: #155724;
}

.response-status-card.rejected .response-text {
  color: #721c24;
}

/* 聊天面板 */
.chat-panel {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  margin-top: 20px;
}

.chat-header {
  padding: 12px 16px;
  background: linear-gradient(135deg, #ff8c61, #ff6b35);
  color: white;
}

.chat-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.chat-messages {
  height: 200px;
  overflow-y: auto;
  padding: 12px;
  background: #f9f9f9;
}

.chat-empty {
  text-align: center;
  color: #999;
  padding: 40px 20px;
  font-size: 14px;
}

.chat-message {
  margin-bottom: 12px;
  max-width: 80%;
}

.chat-message.my-message {
  margin-left: auto;
}

.message-sender {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.chat-message.my-message .message-sender {
  text-align: right;
}

.message-content {
  padding: 8px 12px;
  border-radius: 8px;
  word-break: break-word;
  background: white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.chat-message.my-message .message-content {
  background: linear-gradient(135deg, #ff8c61, #ff6b35);
  color: white;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.chat-message.my-message .message-time {
  text-align: right;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: white;
  border-top: 1px solid #eee;
}

.chat-input .el-input {
  flex: 1;
}

/* 游戏结束弹窗样式 */
.game-result-dialog-wrapper {
  border-radius: 20px;
  overflow: hidden;
}

.game-result-dialog-wrapper :deep(.el-dialog__header) {
  padding: 0;
  display: none;
}

.game-result-dialog-wrapper :deep(.el-dialog__body) {
  padding: 0;
  margin: 0;
}

.game-result-dialog-wrapper :deep(.el-dialog) {
  border-radius: 20px;
  overflow: hidden;
}

.game-result-content {
  text-align: center;
  padding: 40px 50px 50px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.game-result-content.win {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.game-result-content.lose {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.game-result-content.draw {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.result-badge {
  display: inline-block;
  padding: 6px 16px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  color: white;
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 15px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.result-icon {
  font-size: 64px;
  margin: 20px 0 30px 0;
  filter: drop-shadow(0 6px 15px rgba(0, 0, 0, 0.2));
  animation: popIn 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.game-result-content.win .result-icon {
  animation: bounceIn 0.8s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.game-result-content.lose .result-icon {
  animation: shake 0.8s ease-in-out;
}

.game-result-content.draw .result-icon {
  animation: pulse 1s ease-in-out infinite;
}

.result-message {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 10px;
  color: white;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  letter-spacing: 1px;
}

.result-details {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 10px;
  font-weight: 500;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 积分变化显示 */
.rating-change {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
}

.rating-label {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.rating-value {
  font-size: 18px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 6px;
}

.rating-value.positive {
  background: linear-gradient(135deg, #27ae60, #2ecc71);
  color: white;
}

.rating-value.negative {
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: white;
}

.rating-value:not(.positive):not(.negative) {
  background: #95a5a6;
  color: white;
}

/* 模式提示 */
.mode-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
}

.hint-icon {
  font-size: 18px;
}

.hint-text {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

/* 动画效果 */
@keyframes bounceIn {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes bounceIn {
  0% {
    transform: scale(0.3);
    opacity: 0;
  }
  50% {
    transform: scale(1.05);
  }
  70% {
    transform: scale(0.9);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes shake {
  0%, 100% {
    transform: rotate(0deg);
  }
  10%, 30%, 50%, 70%, 90% {
    transform: rotate(-5deg);
  }
  20%, 40%, 60%, 80% {
    transform: rotate(5deg);
  }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

@keyframes popIn {
  0% {
    transform: scale(0.5);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 游戏结果footer样式 */
.game-result-dialog-wrapper :deep(.el-dialog__footer) {
  padding: 25px 40px 35px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-top: 1px solid rgba(255, 255, 255, 0.3);
}

.game-result-footer {
  display: flex;
  gap: 12px;
  justify-content: center;
  align-items: center;
}

.game-result-footer .el-button {
  border-radius: 12px;
  padding: 12px 24px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.game-result-footer .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.play-again-button-wrapper {
  position: relative;
}

.opponent-ready-tip {
  position: absolute;
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.4);
  animation: bounceIn 0.4s ease-out;
}

.opponent-ready-tip.both-ready-tip {
  background: linear-gradient(135deg, #11998e, #38ef7d);
  box-shadow: 0 2px 8px rgba(56, 239, 125, 0.4);
}

.opponent-ready-tip.opponent-left-tip {
  background: linear-gradient(135deg, #eb3349, #f45c43);
  box-shadow: 0 2px 8px rgba(235, 51, 73, 0.4);
}

.opponent-ready-tip.opponent-changed-tip {
  background: linear-gradient(135deg, #11998e, #38ef7d);
  box-shadow: 0 2px 8px rgba(56, 239, 125, 0.4);
}

@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .user-info-card {
    flex-direction: column;
    text-align: center;
  }

  .user-stats {
    justify-content: center;
  }

  .match-modes {
    grid-template-columns: 1fr;
  }

  .board {
    width: 300px;
    height: 300px;
  }

  .piece {
    width: 16px;
    height: 16px;
  }

  .game-header {
    flex-direction: column;
    gap: 10px;
  }
}
</style>
