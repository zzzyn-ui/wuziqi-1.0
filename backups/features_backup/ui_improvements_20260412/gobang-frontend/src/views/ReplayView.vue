<template>
  <div class="replay-view-unified">
    <PageHeader
      v-if="!currentReplay"
      title="🎬 对局复盘"
      subtitle="回放经典对局，学习高招"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <!-- 对局列表 -->
      <ContentCard v-if="!currentReplay">
        <template #header>
          <div class="list-header">
            <h3>对局记录</h3>
            <input
              v-model="searchText"
              type="text"
              placeholder="搜索对局..."
              class="search-input"
            />
          </div>
        </template>

        <div v-if="loading" class="loading-wrapper">
          <LoadingState text="加载中..." />
        </div>

        <div v-else-if="filteredReplays.length === 0" class="empty-wrapper">
          <EmptyState
            icon="🎬"
            title="暂无对局记录"
            description="完成对局后可以在这里复盘学习"
          />
        </div>

        <div v-else class="replay-list">
          <div
            v-for="replay in filteredReplays"
            :key="replay.id"
            class="replay-item"
            @click="startReplay(replay)"
          >
            <div class="replay-players">
              <div class="player black">
                <span class="player-name">{{ replay.blackPlayer }}</span>
                <span class="player-color">⚫</span>
              </div>
              <span class="vs">VS</span>
              <div class="player white">
                <span class="player-color">⚪</span>
                <span class="player-name">{{ replay.whitePlayer }}</span>
              </div>
            </div>

            <div class="replay-result" :class="replay.result.toLowerCase()">
              {{ getResultText(replay.result) }}
            </div>

            <div class="replay-meta">
              <span>📅 {{ formatDate(replay.date) }}</span>
              <span>🎯 {{ replay.moveCount }}手</span>
              <span>⏱️ {{ replay.duration }}</span>
            </div>
          </div>
        </div>
      </ContentCard>

      <!-- 复盘播放器 -->
      <ContentCard v-else class="replay-player">
        <template #header>
          <div class="player-header">
            <ActionButton variant="ghost" size="small" @click="exitReplay">
              ← 返回
            </ActionButton>
            <div class="match-info">
              <div class="players">
                <span class="player black">{{ currentReplay.blackPlayer }}</span>
                <span class="vs">VS</span>
                <span class="player white">{{ currentReplay.whitePlayer }}</span>
              </div>
              <div class="result" :class="currentReplay.result.toLowerCase()">
                {{ getResultText(currentReplay.result) }}
              </div>
            </div>
          </div>
        </template>

        <!-- 棋盘 -->
        <div class="board-section">
          <div class="board">
            <div v-for="(row, x) in board" :key="x" class="board-row">
              <div
                v-for="(cell, y) in row"
                :key="y"
                class="board-cell"
                :class="{ 'last-move': isLastMove(x, y) }"
              >
                <div v-if="cell > 0" class="piece" :class="cell === 1 ? 'black' : 'white'">
                  <span v-if="getMoveNumber(x, y)" class="move-number">{{ getMoveNumber(x, y) }}</span>
                </div>
                <div v-if="isStarPoint(x, y)" class="star-point"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 控制面板 -->
        <div class="control-panel">
          <div class="progress-info">
            <span>第 {{ currentMove }} / {{ currentReplay.moveCount }} 手</span>
            <span>{{ isBlackTurn ? '⚫ 黑方' : '⚪ 白方' }}</span>
          </div>

          <div class="controls">
            <button class="control-btn" @click="goToStart" :disabled="currentMove === 0" title="首手">
              ⏮️
            </button>
            <button class="control-btn" @click="stepBackward" :disabled="currentMove === 0" title="后退">
              ⏪
            </button>
            <button
              :class="['control-btn', 'play-btn', { active: isPlaying }]"
              @click="togglePlay"
              title="播放/暂停"
            >
              {{ isPlaying ? '⏸️' : '▶️' }}
            </button>
            <button class="control-btn" @click="stepForward" :disabled="currentMove >= currentReplay.moveCount" title="前进">
              ⏩
            </button>
            <button class="control-btn" @click="goToEnd" :disabled="currentMove >= currentReplay.moveCount" title="末手">
              ⏭️
            </button>
          </div>

          <div class="speed-control">
            <span class="speed-label">速度:</span>
            <div class="speed-buttons">
              <button
                v-for="speed in speeds"
                :key="speed.value"
                :class="['speed-btn', { active: playbackSpeed === speed.value }]"
                @click="playbackSpeed = speed.value"
              >
                {{ speed.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- 落子列表 -->
        <div class="move-list">
          <h4>落子记录</h4>
          <div class="moves">
            <div
              v-for="(move, index) in currentReplay.moves"
              :key="index"
              :class="[
                'move-item',
                { active: index === currentMove - 1 },
                move.color === 1 ? 'black' : 'white'
              ]"
              @click="goToMove(index + 1)"
            >
              <span class="move-num">{{ index + 1 }}</span>
              <span class="move-coord">({{ move.x }}, {{ move.y }})</span>
            </div>
          </div>
        </div>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, EmptyState, ActionButton } from '@/components/shared'
import { recordApi } from '@/api'
import { useUserStore } from '@/store/modules/user'

// 获取页面主题
const theme = getPageTheme('replay')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
  fetchReplays()
})

onUnmounted(() => {
  stopAutoPlay()
})

const route = useRoute()
const userStore = useUserStore()

interface Move {
  x: number
  y: number
  color: number
}

interface Replay {
  id: number
  blackPlayer: string
  whitePlayer: string
  result: string
  date: Date
  moveCount: number
  duration: string
  moves: Move[]
}

const loading = ref(false)
const searchText = ref('')
const replays = ref<Replay[]>([])
const currentReplay = ref<Replay>({} as Replay)
const board = ref<number[][]>(Array.from({ length: 15 }, () => Array(15).fill(0)))
const currentMove = ref(0)
const isPlaying = ref(false)
const playbackSpeed = ref(2)

const speeds = [
  { label: '0.5x', value: 1 },
  { label: '1x', value: 2 },
  { label: '2x', value: 4 },
  { label: '4x', value: 8 }
]

let playTimer: number | null = null

const filteredReplays = computed(() => {
  if (!searchText.value) return replays.value
  const search = searchText.value.toLowerCase()
  return replays.value.filter(r =>
    r.blackPlayer.toLowerCase().includes(search) ||
    r.whitePlayer.toLowerCase().includes(search)
  )
})

const isBlackTurn = computed(() => currentMove.value % 2 === 0)

const isLastMove = (x: number, y: number): boolean => {
  if (currentMove.value === 0) return false
  const move = currentReplay.value.moves[currentMove.value - 1]
  return move && move.x === x && move.y === y
}

const getMoveNumber = (x: number, y: number): number | null => {
  for (let i = 0; i < currentMove.value; i++) {
    const move = currentReplay.value.moves[i]
    if (move.x === x && move.y === y) {
      return i + 1
    }
  }
  return null
}

const isStarPoint = (x: number, y: number): boolean => {
  const stars = [
    [3, 3], [3, 7], [3, 11],
    [7, 3], [7, 7], [7, 11],
    [11, 3], [11, 7], [11, 11]
  ]
  return stars.some(([sx, sy]) => sx === x && sy === y)
}

const getResultText = (result: string): string => {
  const map: Record<string, string> = {
    BLACK: '黑胜',
    WHITE: '白胜',
    DRAW: '平局'
  }
  return map[result] || result
}

const formatDate = (date: Date): string => {
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / 86400000)

  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  return new Date(date).toLocaleDateString()
}

const fetchReplays = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      ElMessage.warning('请先登录')
      return
    }

    const response = await recordApi.getRecordList(userId, 30)

    if (response.data.success && response.data.data) {
      // 转换后端数据格式到前端格式
      replays.value = response.data.data.map((record: any) => ({
        id: record.id,
        roomId: record.roomId,
        blackPlayer: record.blackPlayer.nickname,
        whitePlayer: record.whitePlayer.nickname,
        blackPlayerId: record.blackPlayer.id,
        whitePlayerId: record.whitePlayer.id,
        result: record.winColor || 'DRAW',
        date: new Date(record.createdAt),
        moveCount: record.moveCount || 0,
        duration: formatDuration(record.duration),
        moves: [] // 将在获取详情时填充
      }))
    } else {
      replays.value = []
    }
  } catch (error) {
    console.error('获取对局记录失败:', error)
    ElMessage.error('获取对局记录失败')
    replays.value = []
  } finally {
    loading.value = false
  }
}

// 格式化时长（秒转分钟）
const formatDuration = (seconds: number): string => {
  if (!seconds) return '0分钟'
  const minutes = Math.floor(seconds / 60)
  return minutes > 0 ? `${minutes}分钟` : '1分钟'
}

const generateMockMoves = (count: number): Move[] => {
  const moves: Move[] = []
  const used = new Set<string>()

  for (let i = 0; i < count; i++) {
    let x: number, y: number
    let key: string

    do {
      x = Math.floor(Math.random() * 15)
      y = Math.floor(Math.random() * 15)
      key = `${x},${y}`
    } while (used.has(key))

    used.add(key)
    moves.push({
      x,
      y,
      color: (i % 2) + 1 as 1 | 2
    })
  }

  return moves
}

const startReplay = async (replay: Replay) => {
  try {
    // 获取对局详情，包含完整的走棋记录
    const response = await recordApi.getRecordDetail(replay.id)

    if (response.data.success && response.data.data) {
      const detail = response.data.data

      // 解析走棋记录
      let moves: Move[] = []
      if (detail.moves && Array.isArray(detail.moves)) {
        moves = detail.moves.map((m: any) => ({
          x: m.x,
          y: m.y,
          color: m.color
        }))
      }

      // 更新当前复盘数据
      currentReplay.value = {
        ...replay,
        moves: moves.length > 0 ? moves : generateMockMoves(replay.moveCount)
      }
      currentMove.value = 0
      resetBoard()
    } else {
      ElMessage.error('获取对局详情失败')
    }
  } catch (error) {
    console.error('获取对局详情失败:', error)
    ElMessage.error('获取对局详情失败，将使用模拟数据')
    // 如果获取详情失败，使用模拟数据
    currentReplay.value = {
      ...replay,
      moves: generateMockMoves(replay.moveCount)
    }
    currentMove.value = 0
    resetBoard()
  }
}

const resetBoard = () => {
  board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
}

const goToMove = (move: number) => {
  currentMove.value = move
  updateBoard()
}

const updateBoard = () => {
  resetBoard()
  for (let i = 0; i < currentMove.value; i++) {
    const move = currentReplay.value.moves[i]
    if (move) {
      board.value[move.x][move.y] = move.color
    }
  }
}

const goToStart = () => {
  currentMove.value = 0
  updateBoard()
}

const goToEnd = () => {
  currentMove.value = currentReplay.value.moveCount
  updateBoard()
}

const stepBackward = () => {
  if (currentMove.value > 0) {
    currentMove.value--
    updateBoard()
  }
}

const stepForward = () => {
  if (currentMove.value < currentReplay.value.moveCount) {
    currentMove.value++
    updateBoard()
  }
}

const togglePlay = () => {
  isPlaying.value = !isPlaying.value

  if (isPlaying.value) {
    startAutoPlay()
  } else {
    stopAutoPlay()
  }
}

const startAutoPlay = () => {
  if (playTimer) clearInterval(playTimer)

  const interval = 2000 / playbackSpeed.value
  playTimer = window.setInterval(() => {
    if (currentMove.value >= currentReplay.value.moveCount) {
      stopAutoPlay()
      isPlaying.value = false
    } else {
      stepForward()
    }
  }, interval)
}

const stopAutoPlay = () => {
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = null
  }
}

const exitReplay = () => {
  stopAutoPlay()
  isPlaying.value = false
  currentReplay.value = {} as Replay
  resetBoard()
  currentMove.value = 0
}

watch(() => route.params.id, async (newId) => {
  if (newId) {
    await fetchReplays()
    const replay = replays.value.find(r => r.id === Number(newId))
    if (replay) {
      startReplay(replay)
    }
  }
}, { immediate: true })
</script>

<style scoped>
.replay-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
}

.list-header h3 {
  font-size: 18px;
  color: #333;
}

.search-input {
  padding: 8px 14px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  width: 200px;
}

.search-input:focus {
  border-color: #a18cd1;
}

.loading-wrapper,
.empty-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.replay-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  padding: 20px 0;
}

.replay-item {
  padding: 20px;
  border: 2px solid #f0f0f0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  background: white;
}

.replay-item:hover {
  border-color: #a18cd1;
  background: #f8f4ff;
  transform: translateY(-3px);
  box-shadow: 0 4px 15px rgba(161, 140, 209, 0.2);
}

.replay-players {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  margin-bottom: 15px;
}

.replay-players .player {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.replay-players .player.black {
  color: #333;
}

.replay-players .player.white {
  color: #666;
}

.replay-players .vs {
  font-size: 12px;
  color: #999;
}

.replay-result {
  text-align: center;
  font-weight: bold;
  padding: 8px;
  border-radius: 8px;
  margin-bottom: 12px;
}

.replay-result.black {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  color: #333;
}

.replay-result.white {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  color: #666;
}

.replay-meta {
  display: flex;
  justify-content: center;
  gap: 15px;
  font-size: 11px;
  color: #999;
  flex-wrap: wrap;
}

/* 复盘播放器 */
.replay-player {
  max-width: 800px;
  margin: 0 auto;
}

.player-header {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
}

.match-info {
  flex: 1;
  text-align: center;
}

.match-info .players {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  margin-bottom: 8px;
}

.match-info .player {
  font-size: 15px;
  font-weight: 500;
}

.match-info .player.black {
  color: #333;
}

.match-info .player.white {
  color: #666;
}

.match-info .vs {
  font-size: 13px;
  color: #999;
}

.match-info .result {
  font-weight: bold;
  padding: 6px 14px;
  border-radius: 20px;
  display: inline-block;
}

.board-section {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.board {
  width: 100%;
  max-width: 450px;
  aspect-ratio: 1;
  background: linear-gradient(135deg, #f5e6d3 0%, #e8d5c4 100%);
  border-radius: 8px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  grid-template-rows: repeat(15, 1fr);
  padding: 15px;
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
  background: #a18cd1;
  height: 2px;
}

.board-cell.last-move::after {
  background: #a18cd1;
  width: 2px;
}

.star-point {
  width: 6px;
  height: 6px;
  background: #8b7355;
  border-radius: 50%;
  position: absolute;
  z-index: 1;
}

.piece {
  width: 80%;
  height: 80%;
  max-width: 24px;
  max-height: 24px;
  border-radius: 50%;
  z-index: 2;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.move-number {
  position: absolute;
  font-size: 9px;
  color: #a18cd1;
  font-weight: bold;
  text-shadow: 0 0 2px white;
}

.control-panel {
  background: #f8f4ff;
  border-radius: 12px;
  padding: 20px;
  margin: 20px 0;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.controls {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-bottom: 15px;
}

.control-btn {
  width: 48px;
  height: 48px;
  border: 1px solid #e0e0e0;
  background: white;
  border-radius: 12px;
  cursor: pointer;
  font-size: 18px;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.control-btn:hover:not(:disabled) {
  border-color: #a18cd1;
  background: #f8f4ff;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.play-btn.active {
  background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);
  color: white;
  border-color: transparent;
}

.speed-control {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.speed-label {
  font-size: 13px;
  color: #999;
}

.speed-buttons {
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
  background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);
  color: white;
  border-color: transparent;
}

.move-list {
  background: #f8f4ff;
  border-radius: 12px;
  padding: 15px;
}

.move-list h4 {
  font-size: 14px;
  color: #333;
  margin: 0 0 12px 0;
}

.moves {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
  gap: 8px;
  max-height: 250px;
  overflow-y: auto;
}

.move-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  background: white;
}

.move-item:hover {
  border-color: #a18cd1;
  background: #f8f4ff;
}

.move-item.active {
  border-color: #a18cd1;
  background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);
  color: white;
}

.move-item.black .move-num {
  color: #333;
}

.move-item.white .move-num {
  color: #666;
}

.move-item.active .move-num,
.move-item.active .move-coord {
  color: white;
}

.move-num {
  font-size: 12px;
  font-weight: bold;
}

.move-coord {
  font-size: 10px;
  color: #999;
}

@media (max-width: 768px) {
  .replay-list {
    grid-template-columns: 1fr;
  }

  .board {
    max-width: 100%;
  }

  .piece {
    max-width: 18px;
    max-height: 18px;
  }

  .controls {
    flex-wrap: wrap;
  }

  .control-btn {
    width: 44px;
    height: 44px;
    font-size: 16px;
  }
}
</style>
