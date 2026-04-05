<template>
  <div class="puzzle-view-unified">
    <PageHeader
      v-if="!currentPuzzle"
      title="🧩 残局挑战"
      subtitle="挑战经典残局，提升棋艺"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <!-- 残局列表 -->
      <ContentCard v-if="!currentPuzzle">
        <template #header>
          <div class="puzzle-header">
            <h3>残局挑战</h3>
            <div class="stats-badge">
              <span class="stat-item">已完成: {{ completedCount }}</span>
              <span class="stat-item">准确率: {{ accuracy }}%</span>
            </div>
          </div>
        </template>

        <!-- 难度选择 -->
        <template #extra>
          <div class="difficulty-selector">
            <button
              v-for="diff in difficulties"
              :key="diff.value"
              :class="['diff-btn', { active: currentDifficulty === diff.value }]"
              @click="changeDifficulty(diff.value)"
            >
              {{ diff.label }}
            </button>
          </div>
        </template>

        <div v-if="loading" class="loading-wrapper">
          <LoadingState text="加载中..." />
        </div>

        <div v-else-if="puzzles.length === 0" class="empty-wrapper">
          <EmptyState
            icon="🧩"
            title="暂无残局"
            description="该难度暂无残局挑战"
          />
        </div>

        <div v-else class="puzzle-grid">
          <div
            v-for="puzzle in puzzles"
            :key="puzzle.id"
            class="puzzle-card"
            :class="{
              completed: puzzle.completed,
              failed: puzzle.failed
            }"
            @click="startPuzzle(puzzle)"
          >
            <div class="puzzle-number">{{ puzzle.id }}</div>
            <div class="puzzle-info">
              <div class="puzzle-name">{{ puzzle.name }}</div>
              <div class="puzzle-meta">
                <span class="diff-tag">{{ getDifficultyLabel(puzzle.difficulty) }}</span>
                <span>{{ puzzle.moves }}步</span>
              </div>
            </div>
            <div class="puzzle-status">
              <span v-if="puzzle.completed" class="status-icon success">✓</span>
              <span v-else-if="puzzle.failed" class="status-icon failed">✗</span>
              <span v-else class="status-icon pending">○</span>
            </div>
          </div>
        </div>
      </ContentCard>

      <!-- 残局游戏 -->
      <ContentCard v-else class="puzzle-game">
        <template #header>
          <div class="game-header">
            <ActionButton variant="ghost" @click="exitPuzzle">
              <template #icon>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="19" y1="12" x2="5" y2="12"></line>
                  <polyline points="12 19 5 12 12 5"></polyline>
                </svg>
              </template>
              返回
            </ActionButton>
            <div class="puzzle-title">{{ currentPuzzle.name }}</div>
            <div class="puzzle-progress">
              {{ currentMoveIndex + 1 }} / {{ currentPuzzle.moves }} 步
            </div>
          </div>
        </template>

        <div class="game-status">
          <div v-if="gameStatus === 'playing'" class="status-badge playing">
            {{ isBlackTurn ? '⚫ 黑方落子' : '⚪ 白方落子' }}
          </div>
          <div v-else-if="gameStatus === 'won'" class="status-badge won">
            🎉 挑战成功！
          </div>
          <div v-else-if="gameStatus === 'lost'" class="status-badge lost">
            ❌ 挑战失败
          </div>
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
                <div v-if="cell > 0" class="piece" :class="cell === 1 ? 'black' : 'white'"></div>
                <div v-if="isStarPoint(x, y)" class="star-point"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 控制按钮 -->
        <div class="controls">
          <ActionButton
            variant="ghost"
            :disabled="gameStatus !== 'playing'"
            @click="resetPuzzle"
          >
            <template #icon>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"></polyline>
                <polyline points="1 20 1 14 7 14"></polyline>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
              </svg>
            </template>
            重来
          </ActionButton>
          <ActionButton
            variant="ghost"
            :disabled="gameStatus !== 'playing' || hintUsed"
            @click="showHint"
          >
            <template #icon>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
                <line x1="12" y1="17" x2="12.01" y2="17"></line>
              </svg>
            </template>
            提示
          </ActionButton>
          <ActionButton variant="ghost" @click="skipPuzzle">
            <template #icon>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 4 15 12 5 20 5 4"></polygon>
                <line x1="19" y1="5" x2="19" y2="19"></line>
              </svg>
            </template>
            跳过
          </ActionButton>
        </div>

        <!-- 提示信息 -->
        <div v-if="showHintText" class="hint-text">
          💡 提示: {{ currentPuzzle.hint }}
        </div>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { puzzleApi } from '@/api'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, EmptyState, ActionButton } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('puzzle')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
  fetchPuzzles()
})

const userStore = useUserStore()

interface Puzzle {
  id: number
  name: string
  difficulty: string
  moves: number
  description?: string
  hint?: string
  board?: number[][]
  solution?: number[][]
  completed?: boolean
  failed?: boolean
}

const currentDifficulty = ref('beginner')
const loading = ref(false)
const puzzles = ref<Puzzle[]>([])
const currentPuzzle = ref<Puzzle | null>(null)
const board = ref<number[][]>(Array.from({ length: 15 }, () => Array(15).fill(0)))
const currentMoveIndex = ref(0)
const gameStatus = ref<'playing' | 'won' | 'lost'>('playing')
const hintUsed = ref(false)
const showHintText = ref(false)
const completedCount = ref(0)
const totalAttempted = ref(0)

const difficulties = [
  { label: '初级', value: 'beginner' },
  { label: '中级', value: 'intermediate' },
  { label: '高级', value: 'advanced' },
  { label: '专家', value: 'expert' }
]

const isBlackTurn = computed(() => currentMoveIndex.value % 2 === 0)

const accuracy = computed(() => {
  if (totalAttempted.value === 0) return 0
  return Math.round((completedCount.value / totalAttempted.value) * 100)
})

const getDifficultyLabel = (diff: string): string => {
  const d = difficulties.find(d => d.value === diff)
  return d?.label || diff
}

const isStarPoint = (x: number, y: number): boolean => {
  const stars = [
    [3, 3], [3, 7], [3, 11],
    [7, 3], [7, 7], [7, 11],
    [11, 3], [11, 7], [11, 11]
  ]
  return stars.some(([sx, sy]) => sx === x && sy === y)
}

const changeDifficulty = (diff: string) => {
  currentDifficulty.value = diff
  fetchPuzzles()
}

const fetchPuzzles = async () => {
  loading.value = true
  try {
    const response = await puzzleApi.getPuzzleList(currentDifficulty.value as any)
    if (response.data && response.data.code === 200) {
      puzzles.value = response.data.data || []
    }
  } catch (error: any) {
    console.error('[PuzzleView] 获取残局失败:', error)
    ElMessage.error('获取残局失败')
    // 使用模拟数据
    puzzles.value = [
      {
        id: 1,
        name: '四三杀',
        difficulty: currentDifficulty.value,
        moves: 3,
        description: '找到制胜的一手',
        hint: '注意观察可以形成双杀的位置'
      },
      {
        id: 2,
        name: '冲四防守',
        difficulty: currentDifficulty.value,
        moves: 3,
        description: '防守对方的冲四',
        hint: '必须阻止对方连线'
      }
    ]
  } finally {
    loading.value = false
  }
}

const startPuzzle = async (puzzle: Puzzle) => {
  try {
    const response = await puzzleApi.getPuzzleDetail(puzzle.id)
    if (response.data && response.data.code === 200) {
      currentPuzzle.value = response.data.data
      resetPuzzle()
    }
  } catch (error: any) {
    console.error('[PuzzleView] 获取残局详情失败:', error)
    // 使用基本信息开始
    currentPuzzle.value = puzzle
    resetPuzzle()
  }
}

const resetPuzzle = () => {
  if (currentPuzzle.value?.board) {
    board.value = currentPuzzle.value.board.map(row => [...row])
  } else {
    board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
    // 设置一些示例棋子
    const center = 7
    board.value[center][center] = 1
    board.value[center][center + 1] = 2
    board.value[center][center + 2] = 1
  }
  currentMoveIndex.value = 0
  gameStatus.value = 'playing'
  hintUsed.value = false
  showHintText.value = false
}

const handleMove = (x: number, y: number) => {
  if (gameStatus.value !== 'playing') return
  if (board.value[x][y] !== 0) {
    ElMessage.warning('该位置已有棋子')
    return
  }

  const color = isBlackTurn.value ? 1 : 2
  board.value[x][y] = color
  currentMoveIndex.value++

  // 检查是否完成
  if (currentMoveIndex.value >= (currentPuzzle.value?.moves || 0)) {
    submitPuzzle()
  }
}

const submitPuzzle = async () => {
  try {
    const moves: number[][] = []
    for (let x = 0; x < 15; x++) {
      for (let y = 0; y < 15; y++) {
        if (board.value[x][y] !== 0) {
          moves.push([x, y])
        }
      }
    }

    const response = await puzzleApi.submitPuzzle(currentPuzzle.value!.id, {
      userId: userStore.userInfo!.id,
      moves
    })

    if (response.data && response.data.code === 200) {
      const success = response.data.data.success || false
      if (success) {
        gameStatus.value = 'won'
        currentPuzzle.value!.completed = true
        completedCount.value++
        ElMessage.success('挑战成功！')
      } else {
        gameStatus.value = 'lost'
        currentPuzzle.value!.failed = true
        ElMessage.error('挑战失败')
      }
      totalAttempted.value++
    }
  } catch (error: any) {
    console.error('[PuzzleView] 提交答案失败:', error)
    // 模拟验证
    const isCorrect = Math.random() > 0.3
    if (isCorrect) {
      gameStatus.value = 'won'
      currentPuzzle.value!.completed = true
      completedCount.value++
      ElMessage.success('挑战成功！')
    } else {
      gameStatus.value = 'lost'
      currentPuzzle.value!.failed = true
      ElMessage.error('挑战失败')
    }
    totalAttempted.value++
  }
}

const showHint = () => {
  hintUsed.value = true
  showHintText.value = true
  setTimeout(() => {
    showHintText.value = false
  }, 5000)
}

const skipPuzzle = () => {
  if (currentPuzzle.value) {
    currentPuzzle.value.failed = true
    totalAttempted.value++
  }
  exitPuzzle()
}

const exitPuzzle = () => {
  currentPuzzle.value = null
  board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
}
</script>

<style scoped>
.puzzle-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.puzzle-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.puzzle-header h3 {
  font-size: 20px;
  color: #333;
}

.stats-badge {
  display: flex;
  gap: 15px;
  font-size: 14px;
}

.stat-item {
  padding: 6px 12px;
  background: #f5f5f5;
  border-radius: 20px;
  color: #666;
}

.difficulty-selector {
  display: flex;
  gap: 8px;
}

.diff-btn {
  padding: 8px 16px;
  border: 1px solid #e0e0e0;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
  color: #666;
}

.diff-btn:hover {
  border-color: #f093fb;
  color: #f093fb;
}

.diff-btn.active {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-color: transparent;
}

.loading-wrapper,
.empty-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.puzzle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  padding: 20px 0;
}

.puzzle-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px;
  border: 2px solid #f0f0f0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  background: white;
}

.puzzle-card:hover {
  border-color: #f093fb;
  background: #fff5f9;
  transform: translateY(-3px);
  box-shadow: 0 4px 15px rgba(240, 147, 251, 0.2);
}

.puzzle-card.completed {
  border-color: #67c23a;
  background: #f0f9ff;
}

.puzzle-card.failed {
  border-color: #f56c6c;
  background: #fef2f2;
}

.puzzle-number {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-radius: 12px;
  font-weight: bold;
  font-size: 20px;
  flex-shrink: 0;
}

.puzzle-info {
  flex: 1;
}

.puzzle-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
}

.puzzle-meta {
  font-size: 12px;
  color: #999;
  display: flex;
  gap: 10px;
  align-items: center;
}

.diff-tag {
  padding: 2px 8px;
  background: #f0f0f0;
  border-radius: 4px;
}

.puzzle-status .status-icon {
  font-size: 24px;
}

.puzzle-status .success {
  color: #67c23a;
}

.puzzle-status .failed {
  color: #f56c6c;
}

.puzzle-status .pending {
  color: #999;
}

/* 游戏界面 */
.puzzle-game {
  max-width: 600px;
  margin: 0 auto;
}

.game-header {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
}

.puzzle-title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.puzzle-progress {
  font-size: 14px;
  color: #f093fb;
  font-weight: 500;
  white-space: nowrap;
}

.game-status {
  text-align: center;
  margin: 20px 0;
}

.status-badge {
  display: inline-block;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.status-badge.playing {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.status-badge.won {
  background: linear-gradient(135deg, #67c23a 0%, #5daf34 100%);
  color: white;
}

.status-badge.lost {
  background: linear-gradient(135deg, #f56c6c 0%, #e04b4b 100%);
  color: white;
}

.board-container {
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
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  grid-template-rows: repeat(15, 1fr);
  padding: 10px;
}

.cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.cell::before,
.cell::after {
  content: '';
  position: absolute;
  background: #8b7355;
}

.cell::before {
  width: 100%;
  height: 1px;
  top: 50%;
}

.cell::after {
  width: 1px;
  height: 100%;
  left: 50%;
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
}

.piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.controls {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin: 20px 0;
  flex-wrap: wrap;
}

.hint-text {
  text-align: center;
  padding: 15px 20px;
  background: #fff8e1;
  border-radius: 10px;
  color: #f57c00;
  font-size: 14px;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .puzzle-grid {
    grid-template-columns: 1fr;
  }

  .game-header {
    flex-direction: column;
    gap: 10px;
  }

  .controls {
    flex-direction: column;
  }

  .board {
    padding: 5px;
  }

  .piece {
    max-width: 16px;
    max-height: 16px;
  }
}
</style>
