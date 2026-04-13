<template>
  <div class="records-view-unified">
    <PageHeader
      title="📜 对局记录"
      subtitle="查看历史对局并进行复盘"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <div class="records-layout">
        <!-- 对局列表 -->
        <ContentCard class="records-list-card">
          <template #header>
            <div class="list-header">
              <h3>对局历史</h3>
              <select v-model="filter" @change="fetchRecords" class="filter-select">
                <option value="all">全部</option>
                <option value="win">胜局</option>
                <option value="loss">负局</option>
                <option value="draw">平局</option>
              </select>
            </div>
          </template>

          <div v-if="loading" class="loading-wrapper">
            <LoadingState text="加载中..." />
          </div>

          <div v-else-if="records.length === 0" class="empty-wrapper">
            <EmptyState
              icon="📜"
              title="暂无对局记录"
              description="开始对局来记录你的精彩表现"
            />
          </div>

          <div v-else class="records-list">
            <div
              v-for="record in records"
              :key="record.id"
              class="record-item"
              :class="{ active: selectedRecord?.id === record.id }"
              @click="selectRecord(record)"
            >
              <div class="record-result" :class="record.result.toLowerCase()">
                {{ getResultText(record.result) }}
              </div>

              <div class="record-info">
                <div class="record-opponent">
                  <div class="opponent-avatar">
                    {{ record.opponent?.nickname?.charAt(0) || record.opponent?.username?.charAt(0) || '?' }}
                  </div>
                  <div class="opponent-detail">
                    <div class="opponent-name">{{ record.opponent?.nickname || record.opponent?.username }}</div>
                    <div class="opponent-rating">Lv.{{ record.opponent?.level }} | {{ record.opponent?.rating }}</div>
                  </div>
                </div>

                <div class="record-meta">
                  <span>📅 {{ formatDate(record.gameTime) }}</span>
                  <span>⏱️ {{ record.duration }}</span>
                </div>
              </div>

              <div class="record-rating" :class="{ gain: record.ratingChange > 0, loss: record.ratingChange < 0 }">
                {{ record.ratingChange > 0 ? '+' : '' }}{{ record.ratingChange }}
              </div>
            </div>

            <!-- 分页 -->
            <div v-if="total > pageSize" class="pagination">
              <button
                :disabled="page === 1"
                @click="page--; fetchRecords()"
                class="page-btn"
              >
                上一页
              </button>
              <span class="page-info">{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
              <button
                :disabled="page >= Math.ceil(total / pageSize)"
                @click="page++; fetchRecords()"
                class="page-btn"
              >
                下一页
              </button>
            </div>
          </div>
        </ContentCard>

        <!-- 复盘区域 -->
        <div v-if="selectedRecord" class="replay-section">
          <div class="replay-header">
            <div class="players-info">
              <div class="player black" :class="{ winner: selectedRecord.winnerId === selectedRecord.black?.id }">
                <div class="player-avatar-small">
                  {{ selectedRecord.black?.nickname?.charAt(0) || '?' }}
                </div>
                <div class="player-info">
                  <div class="player-name">{{ selectedRecord.black?.nickname }}</div>
                  <div class="player-rating">{{ selectedRecord.black?.rating }}</div>
                </div>
                <div class="piece-icon">⚫</div>
                <div v-if="selectedRecord.winnerId === selectedRecord.black?.id" class="winner-badge">🏆 胜</div>
              </div>

              <div class="vs">VS</div>

              <div class="player white" :class="{ winner: selectedRecord.winnerId === selectedRecord.white?.id }">
                <div class="piece-icon">⚪</div>
                <div class="player-info">
                  <div class="player-name">{{ selectedRecord.white?.nickname }}</div>
                  <div class="player-rating">{{ selectedRecord.white?.rating }}</div>
                </div>
                <div class="player-avatar-small">
                  {{ selectedRecord.white?.nickname?.charAt(0) || '?' }}
                </div>
                <div v-if="selectedRecord.winnerId === selectedRecord.white?.id" class="winner-badge">🏆 胜</div>
              </div>
            </div>

            <button class="close-replay-btn" @click="closeReplay">×</button>
          </div>

          <!-- 复盘棋盘 -->
          <div class="replay-board-container">
            <div class="replay-board">
              <div v-for="(row, x) in replayBoard" :key="x" class="board-row">
                <div
                  v-for="(cell, y) in row"
                  :key="y"
                  class="board-cell"
                  :class="{ 'last-move': isLastMove(x, y) }"
                >
                  <div v-if="cell !== 0" class="piece" :class="cell === 1 ? 'black' : 'white'"></div>
                  <div v-if="isStarPoint(x, y)" class="star-point"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 复盘控制 -->
          <div class="replay-controls">
            <div class="step-info">
              第 {{ replayStep }} / {{ selectedRecord.moveCount || 0 }} 手
            </div>

            <div class="control-buttons">
              <button @click="replayStep = 0" :disabled="replayStep === 0" class="control-btn">
                ⏮️
              </button>
              <button @click="prevStep" :disabled="replayStep === 0" class="control-btn">
                ⏪
              </button>
              <button
                @click="toggleAutoPlay"
                :class="['control-btn', 'play-btn', { active: autoPlay }]"
              >
                {{ autoPlay ? '⏸️' : '▶️' }}
              </button>
              <button @click="nextStep" :disabled="replayStep >= (selectedRecord.moveCount || 0)" class="control-btn">
                ⏩
              </button>
              <button @click="replayStep = selectedRecord.moveCount || 0" :disabled="replayStep >= (selectedRecord.moveCount || 0)" class="control-btn">
                ⏭️
              </button>
            </div>

            <div class="speed-control">
              <span class="speed-label">速度:</span>
              <div class="speed-options">
                <button
                  v-for="speed in speeds"
                  :key="speed.value"
                  :class="['speed-btn', { active: autoPlaySpeed === speed.value }]"
                  @click="autoPlaySpeed = speed.value"
                >
                  {{ speed.label }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { recordApi } from '@/api'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, EmptyState } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('records')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
  fetchRecords()
})

onUnmounted(() => {
  stopAutoPlay()
})

const userStore = useUserStore()

const loading = ref(false)
const filter = ref('all')
const records = ref<any[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const selectedRecord = ref<any>(null)
const replayStep = ref(0)
const replayBoard = ref<number[][]>(Array.from({ length: 15 }, () => Array(15).fill(0)))
const autoPlay = ref(false)
const autoPlaySpeed = ref(1000)
let autoPlayTimer: number | null = null

const speeds = [
  { label: '慢', value: 2000 },
  { label: '中', value: 1000 },
  { label: '快', value: 500 }
]

const isLastMove = (x: number, y: number) => {
  if (replayStep.value === 0) return false
  if (!selectedRecord.value?.moves) return false
  const move = selectedRecord.value.moves[replayStep.value - 1]
  return move && move.x === x && move.y === y
}

const isStarPoint = (x: number, y: number) => {
  const stars = [[3,3],[3,7],[3,11],[7,3],[7,7],[7,11],[11,3],[11,7],[11,11]]
  return stars.some(([sx, sy]) => sx === x && sy === y)
}

const fetchRecords = async () => {
  loading.value = true
  try {
    const response = await recordApi.getRecords(userStore.userInfo!.id, filter.value as any, pageSize.value)
    if (response.data && response.data.code === 200) {
      records.value = response.data.data.list || []
      total.value = response.data.data.total || 0
    }
  } catch (error: any) {
    console.error('[RecordsView] 获取对局记录失败:', error)
    ElMessage.error('获取对局记录失败')
  } finally {
    loading.value = false
  }
}

const selectRecord = (record: any) => {
  selectedRecord.value = record
  replayStep.value = record.moveCount || 0
  updateReplayBoard()
}

const closeReplay = () => {
  selectedRecord.value = null
  replayStep.value = 0
  stopAutoPlay()
}

const updateReplayBoard = () => {
  if (!selectedRecord.value) return

  replayBoard.value = Array.from({ length: 15 }, () => Array(15).fill(0))

  if (selectedRecord.value.moves && selectedRecord.value.moves.length > 0) {
    for (let i = 0; i < Math.min(replayStep.value, selectedRecord.value.moves.length); i++) {
      const move = selectedRecord.value.moves[i]
      if (move && move.x !== undefined && move.y !== undefined && move.color !== undefined) {
        replayBoard.value[move.x][move.y] = move.color
      }
    }
  }
}

watch(replayStep, () => {
  updateReplayBoard()
})

const prevStep = () => {
  if (replayStep.value > 0) {
    replayStep.value--
  }
}

const nextStep = () => {
  if (selectedRecord.value && replayStep.value < (selectedRecord.value.moveCount || 0)) {
    replayStep.value++
  }
}

const toggleAutoPlay = () => {
  autoPlay.value = !autoPlay.value
  if (autoPlay.value) {
    if (replayStep.value >= (selectedRecord.value?.moveCount || 0)) {
      replayStep.value = 0
    }

    autoPlayTimer = window.setInterval(() => {
      if (replayStep.value >= (selectedRecord.value?.moveCount || 0)) {
        stopAutoPlay()
      } else {
        replayStep.value++
      }
    }, autoPlaySpeed.value)
  } else {
    stopAutoPlay()
  }
}

const stopAutoPlay = () => {
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
  autoPlay.value = false
}

watch(autoPlaySpeed, () => {
  if (autoPlay.value) {
    stopAutoPlay()
    toggleAutoPlay()
  }
})

const getResultText = (result: string): string => {
  const map: Record<string, string> = { WIN: '胜利', LOSS: '失败', DRAW: '平局' }
  return map[result] || result
}

const formatDate = (date: Date): string => {
  if (!date) return ''
  const now = new Date()
  const diff = now.getTime() - new Date(date).getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return new Date(date).toLocaleDateString()
}
</script>

<style scoped>
.records-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
}

.records-layout {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 25px;
  align-items: start;
}

/* 对局列表 */
.records-list-card {
  position: sticky;
  top: 20px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
}

.list-header h3 {
  font-size: 18px;
  color: #333;
}

.filter-select {
  padding: 8px 30px 8px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  color: #666;
  outline: none;
  cursor: pointer;
  background: white url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23999' d='M6 8L2 4h8z'/%3E%3C/svg%3E") no-repeat right 10px center;
  appearance: none;
}

.loading-wrapper,
.empty-wrapper {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-item {
  display: flex;
  align-items: center;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  gap: 12px;
  border: 2px solid transparent;
}

.record-item:hover {
  background: #fff8f5;
  transform: translateX(3px);
}

.record-item.active {
  border-color: #a8edea;
  background: linear-gradient(135deg, #f0fffe 0%, #fff8f5 100%);
}

.record-result {
  width: 55px;
  height: 55px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-weight: bold;
  font-size: 14px;
  flex-shrink: 0;
}

.record-result.win {
  background: linear-gradient(135deg, #67c23a 0%, #5daf34 100%);
  color: white;
}

.record-result.loss {
  background: linear-gradient(135deg, #f56c6c 0%, #e04b4b 100%);
  color: white;
}

.record-result.draw {
  background: linear-gradient(135deg, #909399 0%, #73767a 100%);
  color: white;
}

.record-info {
  flex: 1;
  min-width: 0;
}

.record-opponent {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.opponent-avatar {
  width: 35px;
  height: 35px;
  border-radius: 50%;
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

.opponent-detail {
  flex: 1;
  min-width: 0;
}

.opponent-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.opponent-rating {
  font-size: 11px;
  color: #999;
}

.record-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #999;
}

.record-rating {
  font-size: 16px;
  font-weight: bold;
  flex-shrink: 0;
}

.record-rating.gain {
  color: #67c23a;
}

.record-rating.loss {
  color: #f56c6c;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 15px;
  padding-top: 15px;
}

.page-btn {
  padding: 6px 14px;
  border: 1px solid #e0e0e0;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  border-color: #a8edea;
  color: #a8edea;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #999;
}

/* 复盘区域 */
.replay-section {
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 4px 20px rgba(168, 237, 234, 0.15);
}

.replay-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.players-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px;
  background: #fafafa;
  border-radius: 12px;
  flex: 1;
}

.replay-header .player {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  transition: all 0.3s;
}

.replay-header .player.winner {
  background: linear-gradient(135deg, #fff9e6 0%, #ffe8cc 100%);
  box-shadow: 0 0 15px rgba(255, 215, 0, 0.3);
}

.player-avatar-small {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

.replay-header .player-info {
  text-align: center;
}

.replay-header .player-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 2px;
}

.replay-header .player-rating {
  font-size: 11px;
  color: #a8edea;
  font-weight: bold;
}

.replay-header .piece-icon {
  font-size: 22px;
}

.winner-badge {
  background: #ffc107;
  color: #fff;
  padding: 3px 8px;
  border-radius: 8px;
  font-size: 11px;
}

.vs {
  font-size: 16px;
  font-weight: bold;
  color: #999;
}

.close-replay-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #f5f5f5;
  border-radius: 50%;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s;
  margin-left: 15px;
}

.close-replay-btn:hover {
  background: #e0e0e0;
}

.replay-board-container {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.replay-board {
  width: 100%;
  max-width: 420px;
  aspect-ratio: 1;
  background: linear-gradient(135deg, #f5e6d3 0%, #e8d5c4 100%);
  border-radius: 8px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  grid-template-rows: repeat(15, 1fr);
  padding: 10px;
}

.board-row {
  display: contents;
}

.board-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.board-cell::before,
.board-cell::after {
  content: '';
  position: absolute;
  background: #8b7355;
}

.board-cell::before {
  width: 100%;
  height: 1px;
  top: 50%;
}

.board-cell::after {
  width: 1px;
  height: 100%;
  left: 50%;
}

.board-cell.last-move::before {
  background: #a8edea;
  height: 2px;
}

.board-cell.last-move::after {
  background: #a8edea;
  width: 2px;
}

.replay-board .piece {
  width: 80%;
  height: 80%;
  max-width: 22px;
  max-height: 22px;
  border-radius: 50%;
  z-index: 2;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}

.replay-board .piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.replay-board .piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.replay-board .star-point {
  width: 5px;
  height: 5px;
  background: #8b7355;
  border-radius: 50%;
  position: absolute;
  z-index: 1;
}

.replay-controls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 15px;
}

.step-info {
  font-size: 15px;
  color: #666;
  font-weight: 500;
}

.control-buttons {
  display: flex;
  gap: 8px;
}

.control-btn {
  width: 44px;
  height: 44px;
  border: 1px solid #e0e0e0;
  background: white;
  border-radius: 10px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.control-btn:hover:not(:disabled) {
  border-color: #a8edea;
  background: #f0fffe;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-btn.play-btn {
  width: 54px;
}

.control-btn.play-btn.active {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
  color: white;
  border-color: transparent;
}

.speed-control {
  display: flex;
  align-items: center;
  gap: 10px;
}

.speed-label {
  font-size: 12px;
  color: #999;
}

.speed-options {
  display: flex;
  gap: 6px;
}

.speed-btn {
  padding: 6px 12px;
  border: 1px solid #e0e0e0;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.3s;
}

.speed-btn.active {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
  color: white;
  border-color: transparent;
}

@media (max-width: 1024px) {
  .records-layout {
    grid-template-columns: 1fr;
  }

  .records-list-card {
    position: static;
  }

  .replay-section {
    order: -1;
  }
}

@media (max-width: 768px) {
  .players-info {
    flex-direction: column;
    gap: 10px;
  }

  .replay-board {
    max-width: 100%;
  }

  .replay-board .piece {
    max-width: 16px;
    max-height: 16px;
  }
}
</style>
