<template>
  <div class="record-view">
    <PageHeader
      title="📜 对局记录"
      subtitle="查看历史对局和复盘"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    >
      <template #actions>
        <ActionButton
          variant="ghost"
          size="small"
          :loading="loading"
          @click="fetchRecords"
        >
          <template #icon>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6"></path>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
            </svg>
          </template>
          刷新
        </ActionButton>
      </template>
    </PageHeader>

    <div class="page-content-wrapper">
      <!-- 统计卡片 -->
      <div class="stats-row" v-if="stats">
        <div class="stat-card">
          <div class="stat-icon">🎮</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.recentGames }}</div>
            <div class="stat-label">近3天场次</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">🏆</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.recentWins }}</div>
            <div class="stat-label">胜利</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">📊</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.recentWinRate }}%</div>
            <div class="stat-label">胜率</div>
          </div>
        </div>
      </div>

      <!-- 对局记录列表 -->
      <ContentCard>
        <template #header>
          <div class="card-title-section">
            <h3>最近对局</h3>
          </div>
        </template>

        <div v-if="loading" class="loading-wrapper">
          <LoadingState text="加载中..." />
        </div>

        <div v-else-if="records.length === 0" class="empty-wrapper">
          <EmptyState
            icon="📜"
            title="暂无对局记录"
            description="快去进行一场对局吧"
          />
        </div>

        <div v-else class="record-list">
          <div
            v-for="record in records"
            :key="record.id"
            class="record-item"
            :class="{ 'win': record.isWin, 'loss': !record.isWin && record.winnerId !== null }"
            @click="viewRecord(record.id)"
          >
            <div class="record-header">
              <div class="record-result">
                <span v-if="record.winnerId === null" class="result-draw">和棋</span>
                <span v-else-if="record.isWin" class="result-win">胜利</span>
                <span v-else class="result-loss">失败</span>
              </div>
              <div class="record-time">{{ formatTime(record.createdAt) }}</div>
            </div>

            <div class="record-players">
              <div class="player" :class="{ 'winner': record.winColor === 1 }">
                <span class="player-color">⚫</span>
                <span class="player-name">{{ record.blackPlayer.nickname }}</span>
                <span class="player-rating">{{ record.blackPlayer.rating }}</span>
              </div>
              <div class="vs">VS</div>
              <div class="player" :class="{ 'winner': record.winColor === 2 }">
                <span class="player-color">⚪</span>
                <span class="player-name">{{ record.whitePlayer.nickname }}</span>
                <span class="player-rating">{{ record.whitePlayer.rating }}</span>
              </div>
            </div>

            <div class="record-info">
              <span class="info-item">{{ record.gameMode === 'pve' ? '人机' : '对战' }}</span>
              <span class="info-item">{{ record.moveCount }}手</span>
              <span class="info-item">{{ formatDuration(record.duration) }}</span>
              <span v-if="record.winColor === 1 && record.blackRatingChange" class="rating-change" :class="{ positive: record.blackRatingChange > 0 }">
                {{ record.blackPlayer.nickname }} {{ record.blackRatingChange > 0 ? '+' : '' }}{{ record.blackRatingChange }}
              </span>
              <span v-if="record.winColor === 2 && record.whiteRatingChange" class="rating-change" :class="{ positive: record.whiteRatingChange > 0 }">
                {{ record.whitePlayer.nickname }} {{ record.whiteRatingChange > 0 ? '+' : '' }}{{ record.whiteRatingChange }}
              </span>
            </div>
          </div>
        </div>
      </ContentCard>
    </div>

    <!-- 复盘对话框 -->
    <el-dialog
      v-model="showReplayDialog"
      title="对局复盘"
      width="90%"
      :close-on-click-modal="false"
      class="replay-dialog"
    >
      <div v-if="currentRecord" class="replay-content">
        <!-- 复盘信息 -->
        <div class="replay-info">
          <div class="replay-players">
            <span class="replay-player">
              <span class="color-icon">⚫</span>
              {{ currentRecord.blackPlayer.nickname }} ({{ currentRecord.blackPlayer.rating }})
            </span>
            <span class="vs">VS</span>
            <span class="replay-player">
              <span class="color-icon">⚪</span>
              {{ currentRecord.whitePlayer.nickname }} ({{ currentRecord.whitePlayer.rating }})
            </span>
          </div>
          <div class="replay-result">
            <span v-if="currentRecord.winnerId === null">和棋</span>
            <span v-else-if="currentRecord.winColor === 1">黑方胜</span>
            <span v-else>白方胜</span>
          </div>
        </div>

        <!-- 棋盘 -->
        <div class="replay-board">
          <GameBoard
            :board-size="15"
            :board-state="replayBoardState"
            :last-move="replayLastMove"
            :disabled="true"
          />
        </div>

        <!-- 控制面板 -->
        <div class="replay-controls">
          <el-button @click="replayFirstMove" :disabled="currentMoveIndex <= 0">
            ⏮ 开始
          </el-button>
          <el-button @click="replayPrevMove" :disabled="currentMoveIndex <= 0">
            ◀ 上一步
          </el-button>
          <el-button @click="toggleAutoPlay" :type="isAutoPlaying ? 'warning' : 'primary'">
            {{ isAutoPlaying ? '⏸ 暂停' : '▶ 自动播放' }}
          </el-button>
          <el-button @click="replayNextMove" :disabled="currentMoveIndex >= replayMoves.length">
            下一步 ▶
          </el-button>
          <el-button @click="replayToLastMove" :disabled="currentMoveIndex >= replayMoves.length">
            结束 ⏭
          </el-button>
          <div class="move-indicator">
            第 {{ currentMoveIndex }} / {{ replayMoves.length }} 手
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { get } from '@/api/http'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, EmptyState, ActionButton } from '@/components/shared'
import GameBoard from '@/components/shared/GameBoard.vue'

// 主题
const theme = getPageTheme('record')
onMounted(() => {
  applyPageTheme(theme)
})

const userStore = useUserStore()
const loading = ref(false)
const records = ref<any[]>([])
const stats = ref<any>(null)

// 复盘相关
const showReplayDialog = ref(false)
const currentRecord = ref<any>(null)
const replayMoves = ref<any[]>([])
const replayBoardState = ref<number[][]>([])
const replayLastMove = ref<{ x: number; y: number } | null>(null)
const currentMoveIndex = ref(0)
const isAutoPlaying = ref(false)
let autoPlayTimer: number | null = null

// 获取对局记录
const fetchRecords = async () => {
  if (!userStore.userInfo?.id) return

  loading.value = true
  try {
    const response = await get('/record/list', {
      params: {
        userId: userStore.userInfo.id,
        days: 3
      }
    })

    if (response.code === 200) {
      records.value = response.data || []
    } else {
      throw new Error(response.message || '获取失败')
    }
  } catch (error: any) {
    console.error('获取对局记录失败:', error)
    ElMessage.error('获取对局记录失败')
  } finally {
    loading.value = false
  }
}

// 获取统计数据
const fetchStats = async () => {
  if (!userStore.userInfo?.id) return

  try {
    const response = await get('/record/stats', {
      params: {
        userId: userStore.userInfo.id
      }
    })

    if (response.code === 200) {
      stats.value = response.data
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

// 查看复盘
const viewRecord = async (recordId: number) => {
  try {
    const response = await get(`/record/${recordId}`)

    if (response.code === 200) {
      currentRecord.value = response.data

      // 解析走棋记录
      const movesStr = currentRecord.value.moves || '[]'
      replayMoves.value = JSON.parse(movesStr)

      // 解析棋盘状态
      const boardStateStr = currentRecord.value.boardState
      if (boardStateStr) {
        try {
          replayBoardState.value = JSON.parse(boardStateStr)
        } catch (e) {
          // 如果解析失败，创建空棋盘
          replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))
        }
      } else {
        replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))
      }

      currentMoveIndex.value = replayMoves.value.length
      replayLastMove.value = replayMoves.value.length > 0 ? replayMoves.value[replayMoves.value.length - 1] : null

      showReplayDialog.value = true
    } else {
      throw new Error(response.message || '获取失败')
    }
  } catch (error: any) {
    console.error('获取对局详情失败:', error)
    ElMessage.error('获取对局详情失败')
  }
}

// 复盘控制
const replayFirstMove = () => {
  currentMoveIndex.value = 0
  replayLastMove.value = null
  replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))
}

const replayPrevMove = () => {
  if (currentMoveIndex.value > 0) {
    currentMoveIndex.value--
    applyMovesToBoard()
  }
}

const replayNextMove = () => {
  if (currentMoveIndex.value < replayMoves.value.length) {
    currentMoveIndex.value++
    applyMovesToBoard()
  }
}

const replayToLastMove = () => {
  currentMoveIndex.value = replayMoves.value.length
  applyMovesToBoard()
}

const toggleAutoPlay = () => {
  if (isAutoPlaying.value) {
    stopAutoPlay()
  } else {
    startAutoPlay()
  }
}

const startAutoPlay = () => {
  isAutoPlaying.value = true
  autoPlayTimer = window.setInterval(() => {
    if (currentMoveIndex.value < replayMoves.value.length) {
      replayNextMove()
    } else {
      stopAutoPlay()
    }
  }, 1000)
}

const stopAutoPlay = () => {
  isAutoPlaying.value = false
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

const applyMovesToBoard = () => {
  // 重置棋盘
  replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))

  // 应用前 currentMoveIndex 步
  for (let i = 0; i < currentMoveIndex.value && i < replayMoves.value.length; i++) {
    const move = replayMoves.value[i]
    const color = i % 2 === 0 ? 1 : 2 // 黑1白2
    replayBoardState.value[move.y][move.x] = color
  }

  // 设置最后一步
  if (currentMoveIndex.value > 0 && currentMoveIndex.value <= replayMoves.value.length) {
    replayLastMove.value = replayMoves.value[currentMoveIndex.value - 1]
  } else {
    replayLastMove.value = null
  }
}

// 格式化时间
const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else if (diff < 2592000000) {
    return `${Math.floor(diff / 86400000)}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

// 格式化时长
const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

// 监听对话框关闭
const handleCloseDialog = () => {
  stopAutoPlay()
  showReplayDialog.value = false
  currentRecord.value = null
  replayMoves.value = []
  currentMoveIndex.value = 0
}

onMounted(() => {
  fetchRecords()
  fetchStats()
})
</script>

<style scoped>
.record-view {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.stat-icon {
  font-size: 32px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.card-title-section h3 {
  font-size: 20px;
  color: #333;
}

.loading-wrapper,
.empty-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.record-item {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: all 0.3s;
  border-left: 4px solid #ddd;
}

.record-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.record-item.win {
  border-left-color: #52c41a;
}

.record-item.loss {
  border-left-color: #ff4d4f;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.record-result {
  font-size: 18px;
  font-weight: bold;
}

.result-win {
  color: #52c41a;
}

.result-loss {
  color: #ff4d4f;
}

.result-draw {
  color: #999;
}

.record-time {
  font-size: 12px;
  color: #999;
}

.record-players {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.player {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.player.winner {
  font-weight: bold;
  color: #52c41a;
}

.player-color {
  font-size: 18px;
}

.vs {
  font-size: 12px;
  color: #999;
}

.record-info {
  display: flex;
  gap: 15px;
  font-size: 12px;
  color: #666;
}

.info-item {
  padding: 4px 8px;
  background: #f5f5f5;
  border-radius: 4px;
}

.rating-change {
  padding: 4px 8px;
  border-radius: 4px;
}

.rating-change.positive {
  background: #f6ffed;
  color: #52c41a;
}

.rating-change:not(.positive) {
  background: #fff1f0;
  color: #ff4d4f;
}

/* 复盘对话框 */
.replay-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.replay-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.replay-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
}

.replay-players {
  display: flex;
  gap: 20px;
  font-size: 16px;
}

.replay-player {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-icon {
  font-size: 20px;
}

.replay-result {
  font-size: 18px;
  font-weight: bold;
}

.replay-board {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: #f5f5f5;
  border-radius: 8px;
}

.replay-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
}

.move-indicator {
  margin-left: auto;
  font-size: 14px;
  font-weight: bold;
  color: #333;
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: 1fr;
  }

  .record-players {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
