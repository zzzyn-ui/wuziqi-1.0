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
              <div class="puzzle-name">{{ puzzle.title || puzzle.name }}</div>
              <div class="puzzle-meta">
                <span class="diff-tag">{{ getDifficultyLabel(puzzle.difficulty) }}</span>
                <span>{{ puzzle.optimalMoves || puzzle.moves }}步</span>
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
            <div class="puzzle-title">{{ currentPuzzle.title || currentPuzzle.name }}</div>
            <div class="puzzle-progress">
              {{ currentMoveIndex + 1 }} / {{ currentPuzzle.maxMoves || 50 }} 步
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
        <GameBoard
          :board="board"
          :show-last-move="false"
          :winning-cells="winningCells"
          @cell-click="handleMove"
        />

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

        <!-- 调试信息 -->
        <div v-if="currentPuzzle" class="debug-info">
          <details open>
            <summary>🔧 调试信息</summary>
            <div>残局ID: {{ currentPuzzle.id }}</div>
            <div>标题: {{ currentPuzzle.title }}</div>
            <div>难度: {{ currentPuzzle.difficulty }}</div>
            <div>最大步数: {{ currentPuzzle.maxMoves }}</div>
            <div>当前步数: {{ currentMoveIndex }}</div>
            <div>棋盘尺寸: {{ board.length }} x {{ board[0]?.length }}</div>
            <div>总棋子数: {{ board.flat().filter(c => c !== 0).length }}</div>
            <div>黑子数: {{ getPieceCount(1) }}</div>
            <div>白子数: {{ getPieceCount(2) }}</div>
            <div>黑子位置: {{ getPiecePositions(1) }}</div>
            <div>白子位置: {{ getPiecePositions(2) }}</div>
          </details>
        </div>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { get } from '@/api/http'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, EmptyState, ActionButton, GameBoard } from '@/components/shared'

const router = useRouter()
const route = useRoute()

// 获取页面主题
const theme = getPageTheme('puzzle')

// 应用主题
onMounted(async () => {
  applyPageTheme(theme)

  console.log('[PuzzleView] Component mounted')
  console.log('[PuzzleView] Route query:', route.query)

  // 检查URL中是否有残局ID，如果有则直接开始该残局
  const puzzleIdFromUrl = route.query.id as string
  if (puzzleIdFromUrl) {
    console.log('[PuzzleView] Loading puzzle from URL:', puzzleIdFromUrl)
    try {
      const res = await get(`/puzzle/${puzzleIdFromUrl}`)
      console.log('[PuzzleView] Puzzle API response:', res)
      if (res.success && res.data) {
        const puzzle = {
          ...res.data,
          name: res.data.title,
          moves: res.data.optimalMoves || '?',
          maxMoves: res.data.maxMoves || 50
        }
        console.log('[PuzzleView] Starting puzzle:', puzzle)
        await startPuzzle(puzzle)
      } else {
        console.error('[PuzzleView] API returned success=false or no data:', res)
        ElMessage.error(res.message || '残局不存在')
        // 移除URL中的残局ID参数，返回列表
        router.replace({ path: '/puzzle', query: {} })
      }
    } catch (error: any) {
      console.error('[PuzzleView] 加载残局失败:', error)
      ElMessage.error('加载残局失败: ' + (error.message || '未知错误'))
      // 移除URL中的残局ID参数，返回列表
      router.replace({ path: '/puzzle', query: {} })
    }
  } else {
    console.log('[PuzzleView] No puzzle ID in URL, loading list')
  }

  // 加载残局列表
  fetchPuzzles()
})

const userStore = useUserStore()

interface Puzzle {
  id: number
  title: string
  name: string
  difficulty: string
  puzzleType: string
  moves: number
  optimalMoves: number
  maxMoves: number
  description?: string
  hint?: string
  boardState?: string
  board?: number[][]
  solution?: string
  completed?: boolean
  failed?: boolean
  totalCompletions?: number
  completionRate?: number
}

const currentDifficulty = ref('easy')
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
const winningCells = ref<[number, number][]>([])

const difficulties = [
  { label: '🌱 入门', value: 'easy' },
  { label: '🌿 中级', value: 'medium' },
  { label: '🌳 高级', value: 'hard' },
  { label: '🏆 大师', value: 'expert' }
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

const changeDifficulty = (diff: string) => {
  currentDifficulty.value = diff
  fetchPuzzles()
}

const fetchPuzzles = async () => {
  console.log('[PuzzleView] Fetching puzzles with difficulty:', currentDifficulty.value)
  loading.value = true
  try {
    const res = await get(`/puzzle/list?difficulty=${currentDifficulty.value}`)
    console.log('[PuzzleView] Fetch puzzles response:', res)
    if (res.success) {
      puzzles.value = res.data.map((p: any) => ({
        ...p,
        name: p.title,
        moves: p.optimalMoves || '?'
      }))
      console.log('[PuzzleView] Puzzles loaded:', puzzles.value.length)
    } else {
      console.error('[PuzzleView] Fetch puzzles failed:', res)
      ElMessage.error(res.message || '获取残局失败')
      puzzles.value = []
    }
  } catch (error: any) {
    console.error('[PuzzleView] 获取残局失败:', error)
    ElMessage.error('获取残局失败: ' + (error.message || '网络错误'))
    puzzles.value = []
  } finally {
    loading.value = false
  }
}

const startPuzzle = async (puzzle: Puzzle) => {
  try {
    const res = await get(`/puzzle/${puzzle.id}`)
    if (res.success) {
      // 确保所有必需的字段都存在
      currentPuzzle.value = {
        ...res.data,
        name: res.data.title,
        moves: res.data.optimalMoves || res.data.maxMoves || 50,
        maxMoves: res.data.maxMoves || 50
      }
      resetPuzzle()
    } else {
      ElMessage.error(res.message || '获取残局详情失败')
    }
  } catch (error: any) {
    console.error('[PuzzleView] 获取残局详情失败:', error)
    // 使用基本信息开始
    currentPuzzle.value = {
      ...puzzle,
      maxMoves: puzzle.maxMoves || 50
    }
    resetPuzzle()
  }
}

const resetPuzzle = () => {
  // 解析棋盘状态
  if (currentPuzzle.value?.boardState) {
    try {
      board.value = parseBoardState(currentPuzzle.value.boardState)
    } catch (error) {
      console.error('[PuzzleView] Failed to parse boardState:', error)
      board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
    }
  } else if (currentPuzzle.value?.board) {
    board.value = currentPuzzle.value.board.map(row => [...row])
  } else {
    board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
  }

  currentMoveIndex.value = 0
  gameStatus.value = 'playing'
  hintUsed.value = false
  showHintText.value = false
  winningCells.value = []
}

// 解析棋盘状态
const parseBoardState = (boardState: string): number[][] => {
  const board: number[][] = Array.from({ length: 15 }, () => Array(15).fill(0))

  if (!boardState) return board

  // boardState可能是连续的225个字符，或者是用换行符分隔的15行
  // 先检查是否有换行符
  if (boardState.includes('\n')) {
    // 有换行符，按行解析
    const rows = boardState.split('\n')

    for (let i = 0; i < Math.min(15, rows.length); i++) {
      const row = rows[i].trim() // 移除每行两端的空白
      for (let j = 0; j < Math.min(15, row.length); j++) {
        const char = row.charAt(j)
        if (char === 'B') {
          board[i][j] = 1
        } else if (char === 'W') {
          board[i][j] = 2
        }
      }
    }
  } else {
    // 没有换行符，按连续字符解析（每15个字符一行）
    // 移除所有空白字符（空格、制表符等）
    const cleanState = boardState.replace(/\s/g, '')

    for (let i = 0; i < 15; i++) {
      for (let j = 0; j < 15; j++) {
        const idx = i * 15 + j
        if (idx < cleanState.length) {
          const char = cleanState.charAt(idx)
          if (char === 'B') {
            board[i][j] = 1
          } else if (char === 'W') {
            board[i][j] = 2
          }
        }
      }
    }
  }

  return board
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

  // 检查是否获胜（五连）
  if (checkWinAt(x, y, color)) {
    gameStatus.value = 'won'
    currentPuzzle.value!.completed = true
    completedCount.value++
    return
  }

  // 检查是否超过最大步数
  if (currentMoveIndex.value >= (currentPuzzle.value?.maxMoves || 50)) {
    gameStatus.value = 'lost'
    currentPuzzle.value!.failed = true
    ElMessage.error('❌ 超过最大步数，挑战失败！')
    totalAttempted.value++
  }
}

// 检查某位置落子后是否胜利
const checkWinAt = (row: number, col: number, type: number): boolean => {
  const directions = [
    [[0, 1], [0, -1]],   // 横向
    [[1, 0], [-1, 0]],   // 纵向
    [[1, 1], [-1, -1]], // 右下斜
    [[1, -1], [-1, 1]]  // 左下斜
  ]

  for (const [dir1, dir2] of directions) {
    let count = 1
    const cells: [number, number][] = [[row, col]]

    // 向一个方向计数
    let r = row + dir1[0]
    let c = col + dir1[1]
    while (r >= 0 && r < 15 && c >= 0 && c < 15 && board.value[r][c] === type) {
      count++
      cells.push([r, c])
      r += dir1[0]
      c += dir1[1]
    }

    // 向相反方向计数
    r = row + dir2[0]
    c = col + dir2[1]
    while (r >= 0 && r < 15 && c >= 0 && c < 15 && board.value[r][c] === type) {
      count++
      cells.push([r, c])
      r += dir2[0]
      c += dir2[1]
    }

    if (count >= 5) {
      winningCells.value = cells
      return true
    }
  }

  return false
}

const submitPuzzle = async () => {
  // 检查是否五连胜利
  const hasWon = checkWin()

  if (hasWon) {
    gameStatus.value = 'won'
    currentPuzzle.value!.completed = true
    completedCount.value++
  } else {
    gameStatus.value = 'lost'
    currentPuzzle.value!.failed = true
    ElMessage.error('❌ 挑战失败，请再试一次！')
  }
  totalAttempted.value++
}

// 检查是否有五连胜利
const checkWin = (): boolean => {
  const boardState = board.value

  // 检查四个方向
  const directions = [
    [[0, 1], [0, -1]],   // 横向
    [[1, 0], [-1, 0]],   // 纵向
    [[1, 1], [-1, -1]], // 右下斜
    [[1, -1], [-1, 1]]  // 左下斜
  ]

  for (let i = 0; i < 15; i++) {
    for (let j = 0; j < 15; j++) {
      const type = boardState[i][j]
      if (type === 0) continue

      for (const [dir1, dir2] of directions) {
        let count = 1

        // 向一个方向计数
        let r = i + dir1[0]
        let c = j + dir1[1]
        while (r >= 0 && r < 15 && c >= 0 && c < 15 && boardState[r][c] === type) {
          count++
          r += dir1[0]
          c += dir1[1]
        }

        // 向相反方向计数
        r = i + dir2[0]
        c = j + dir2[1]
        while (r >= 0 && r < 15 && c >= 0 && c < 15 && boardState[r][c] === type) {
          count++
          r += dir2[0]
          c += dir2[1]
        }

        if (count >= 5) return true
      }
    }
  }

  return false
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

// 获取棋子位置（用于调试）
const getPiecePositions = (type: number): string => {
  const positions: string[] = []
  for (let i = 0; i < 15; i++) {
    for (let j = 0; j < 15; j++) {
      if (board.value[i][j] === type) {
        positions.push(`(${i},${j})`)
      }
    }
  }
  return positions.length > 0 ? positions.join(', ') : '无'
}

// 获取棋子数量（用于调试）
const getPieceCount = (type: number): number => {
  return board.value.flat().filter(c => c === type).length
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
}

.debug-info {
  margin-top: 20px;
  padding: 15px;
  background: #f5f5f5;
  border-radius: 8px;
  font-size: 12px;
  color: #666;
}

.debug-info details {
  cursor: pointer;
}

.debug-info summary {
  font-weight: 500;
  margin-bottom: 10px;
  color: #333;
}

.debug-info div {
  margin: 5px 0;
  word-break: break-all;
}
</style>
