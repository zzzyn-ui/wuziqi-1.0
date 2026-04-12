<template>
  <div class="observer-view">
    <PageHeader
      title="👁 观战模式"
      subtitle="观看高手对局，学习棋艺"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    >
      <template #actions>
        <ActionButton variant="ghost" @click="router.push('/home')">
          <template #icon>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="19" y1="12" x2="5" y2="12"></line>
              <polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </template>
          返回大厅
        </ActionButton>
      </template>
    </PageHeader>

    <div class="observer-content" v-if="!loading && roomData">
      <!-- 玩家信息 -->
      <div class="players-info">
        <div class="player-card black-player">
          <div class="player-avatar">⚫</div>
          <div class="player-details">
            <h3>{{ roomData.blackPlayer?.nickname || roomData.blackPlayer?.username || '黑方玩家' }}</h3>
            <p class="rating">Lv.{{ roomData.blackPlayer?.level || 1 }} | {{ roomData.blackPlayer?.rating || 1200 }}分</p>
          </div>
          <div class="player-status" v-if="currentTurn === 1">🎯 思考中</div>
        </div>

        <div class="vs-badge">VS</div>

        <div class="player-card white-player">
          <div class="player-avatar">⚪</div>
          <div class="player-details">
            <h3>{{ roomData.whitePlayer?.nickname || roomData.whitePlayer?.username || '白方玩家' }}</h3>
            <p class="rating">Lv.{{ roomData.whitePlayer?.level || 1 }} | {{ roomData.whitePlayer?.rating || 1200 }}分</p>
          </div>
          <div class="player-status" v-if="currentTurn === 2">🎯 思考中</div>
        </div>
      </div>

      <!-- 游戏棋盘 -->
      <div class="game-board-container">
        <GameBoard
          :board="board"
          :current-turn="currentTurn"
          :read-only="true"
          :show-coordinates="true"
          @cell-click="handleCellClick"
        />

        <!-- 游戏状态 -->
        <div class="game-status">
          <div class="status-badge" :class="gameStatusClass">
            {{ gameStatusText }}
          </div>
          <div class="observer-count" v-if="observerCount > 0">
            👁 观战者: {{ observerCount }}
          </div>
        </div>
      </div>

      <!-- 游戏信息 -->
      <div class="game-info">
        <div class="info-item">
          <span class="label">房间ID:</span>
          <span class="value">{{ roomId }}</span>
        </div>
        <div class="info-item">
          <span class="label">游戏模式:</span>
          <span class="value">{{ gameModeText }}</span>
        </div>
        <div class="info-item" v-if="moveHistory.length > 0">
          <span class="label">总步数:</span>
          <span class="value">{{ moveHistory.length }}</span>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-else-if="loading" class="loading-container">
      <LoadingState text="正在加载对局..." />
    </div>

    <!-- 错误状态 -->
    <div v-else class="error-container">
      <EmptyState
        icon="❌"
        title="无法加载对局"
        description="房间不存在或已关闭"
      />
      <ActionButton variant="primary" @click="router.push('/home')" style="margin-top: 20px;">
        返回大厅
      </ActionButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { wsClient } from '@/api/websocket'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, LoadingState, EmptyState, ActionButton } from '@/components/shared'
import GameBoard from '@/components/shared/GameBoard.vue'

const route = useRoute()
const router = useRouter()

// 获取页面主题
const theme = getPageTheme('observer')

// 状态
const loading = ref(true)
const roomId = ref(route.params.roomId as string)
const roomData = ref<any>(null)
const board = ref<number[][]>([])
const currentTurn = ref<number>(1) // 1=黑, 2=白
const gameStatus = ref<string>('WAITING')
const moveHistory = ref<any[]>([])
const observerCount = ref<number>(0)

// 订阅ID
let gameStateSubscription = ''
let observerCountSubscription = ''
let observerResponseSubscription = ''

// 计算属性
const gameStatusText = computed(() => {
  switch (gameStatus.value) {
    case 'WAITING': return '等待开始'
    case 'PLAYING': return '对局中'
    case 'FINISHED': return '已结束'
    default: return '未知'
  }
})

const gameStatusClass = computed(() => {
  switch (gameStatus.value) {
    case 'WAITING': return 'status-waiting'
    case 'PLAYING': return 'status-playing'
    case 'FINISHED': return 'status-finished'
    default: return ''
  }
})

const gameModeText = computed(() => {
  const mode = roomData.value?.gameMode
  return mode === 'ranked' ? '竞技模式' : '休闲模式'
})

// 初始化
onMounted(() => {
  applyPageTheme(theme)
  initObserverMode()
})

// 清理
onUnmounted(() => {
  cleanup()
})

// 初始化观战模式
const initObserverMode = () => {
  loading.value = true

  // 订阅观战响应
  observerResponseSubscription = wsClient.subscribeObserverResponse((data) => {
    console.log('[ObserverView] 观战响应:', data)
    if (data.type === 'OBSERVER_JOINED') {
      ElMessage.success('加入观战成功')
      observerCount.value = data.observerCount || 0
    } else if (data.type === 'OBSERVER_LEFT') {
      console.log('已离开观战')
    }
  })

  // 订阅游戏状态
  gameStateSubscription = wsClient.subscribeGameState(roomId.value, (data) => {
    console.log('[ObserverView] 游戏状态更新:', data)
    handleGameStateUpdate(data)
  })

  // 订阅观战者数量
  observerCountSubscription = wsClient.subscribeObserverCount(roomId.value, (data) => {
    console.log('[ObserverView] 观战者数量:', data)
    if (data.type === 'OBSERVER_COUNT_CHANGE') {
      observerCount.value = data.observerCount || 0
    }
  })

  // 发送加入观战请求
  wsClient.joinObserver(roomId.value)

  // 请求房间状态
  wsClient.send('/app/game/state', { roomId: roomId.value })

  // 设置超时
  setTimeout(() => {
    if (loading.value) {
      loading.value = false
      if (!roomData.value) {
        ElMessage.error('加载对局超时')
      }
    }
  }, 10000)
}

// 处理游戏状态更新
const handleGameStateUpdate = (data: any) => {
  loading.value = false

  if (data.type === 'GAME_STATE' || data.board) {
    // 更新棋盘
    if (data.board && Array.isArray(data.board)) {
      board.value = data.board
    }

    // 更新当前回合
    if (data.currentTurn !== undefined) {
      currentTurn.value = data.currentTurn
    }

    // 更新游戏状态
    if (data.gameStatus) {
      gameStatus.value = data.gameStatus
    }

    // 更新房间数据
    if (data.blackPlayer || data.whitePlayer) {
      roomData.value = {
        blackPlayer: data.blackPlayer,
        whitePlayer: data.whitePlayer,
        gameMode: data.gameMode || 'casual'
      }
    }

    // 更新移动历史
    if (data.moveHistory) {
      moveHistory.value = data.moveHistory
    }
  }
}

// 处理点击棋盘（观战模式只显示）
const handleCellClick = (row: number, col: number) => {
  ElMessage.info('观战模式无法落子')
}

// 清理
const cleanup = () => {
  if (gameStateSubscription) {
    wsClient.unsubscribe(gameStateSubscription)
  }
  if (observerCountSubscription) {
    wsClient.unsubscribe(observerCountSubscription)
  }
  if (observerResponseSubscription) {
    wsClient.unsubscribe(observerResponseSubscription)
  }

  // 离开观战
  if (roomId.value) {
    wsClient.leaveObserver(roomId.value)
  }
}
</script>

<style scoped>
.observer-view {
  min-height: 100vh;
  padding-bottom: 80px;
}

.observer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.players-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-bottom: 30px;
  flex-wrap: wrap;
}

.player-card {
  background: white;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  min-width: 250px;
  position: relative;
}

.player-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.player-details h3 {
  margin: 0 0 5px 0;
  font-size: 16px;
  color: #333;
}

.player-details .rating {
  margin: 0;
  font-size: 14px;
  color: #666;
}

.player-status {
  position: absolute;
  top: -10px;
  right: -10px;
  background: #ffd700;
  color: #333;
  padding: 5px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: bold;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.vs-badge {
  font-size: 24px;
  font-weight: bold;
  color: #667eea;
  background: white;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.game-board-container {
  background: white;
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  margin-bottom: 30px;
}

.game-status {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 20px;
}

.status-badge {
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: bold;
  font-size: 14px;
}

.status-waiting {
  background: #fff3cd;
  color: #856404;
}

.status-playing {
  background: #d1ecf1;
  color: #0c5460;
}

.status-finished {
  background: #d4edda;
  color: #155724;
}

.observer-count {
  color: #666;
  font-size: 14px;
}

.game-info {
  background: white;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  justify-content: space-around;
  flex-wrap: wrap;
  gap: 15px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.info-item {
  display: flex;
  gap: 10px;
}

.info-item .label {
  color: #666;
  font-weight: 500;
}

.info-item .value {
  color: #333;
  font-weight: bold;
}

.loading-container,
.error-container {
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .observer-content {
    padding: 10px;
  }

  .players-info {
    flex-direction: column;
  }

  .player-card {
    min-width: 200px;
  }

  .game-board-container {
    padding: 15px;
  }

  .game-info {
    flex-direction: column;
    align-items: center;
  }
}
</style>
