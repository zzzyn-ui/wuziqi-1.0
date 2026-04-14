<template>
  <div class="pve-view">
    <!-- 顶部导航条 -->
    <div class="pve-nav">
      <button class="pve-back-btn" @click="router.push({ path: '/home' })">
        <span>←</span> 返回首页
      </button>
      <h1 class="pve-title">🤖 人机对战</h1>
      <div class="pve-spacer"></div>
    </div>

    <div class="pve-content">
      <!-- 游戏界面 -->
      <div class="pve-game-area">
        <!-- 游戏头部信息 -->
        <div class="pve-game-header">
          <div class="pve-player-info" :class="{ active: currentTurn === playerColor }">
            <div class="pve-avatar">{{ userStore.userInfo?.nickname?.charAt(0) || '?' }}</div>
            <div class="pve-player-details">
              <div class="pve-player-name">{{ userStore.userInfo?.nickname || '玩家' }}</div>
              <div class="pve-player-color">
                <span class="pve-piece-icon" :class="playerColor">{{ playerColor === 'black' ? '⚫' : '⚪' }}</span>
                {{ playerColor === 'black' ? '黑方' : '白方' }}
              </div>
            </div>
          </div>

          <div class="pve-vs">VS</div>

          <div class="pve-player-info pve-ai" :class="{ active: currentTurn !== playerColor }">
            <div class="pve-player-details" style="text-align: right;">
              <div class="pve-player-name">AI {{ getDifficultyName() }}</div>
              <div class="pve-player-color">
                {{ playerColor === 'black' ? '白方' : '黑方' }}
                <span class="pve-piece-icon" :class="playerColor === 'black' ? 'white' : 'black'">
                  {{ playerColor === 'black' ? '⚪' : '⚫' }}
                </span>
              </div>
            </div>
            <div class="pve-avatar">🤖</div>
          </div>
        </div>

        <!-- 游戏状态 -->
        <div class="pve-game-status">
          <div v-if="currentTurn === playerColor" class="pve-status-turn">你的回合</div>
          <div v-else class="pve-status-thinking">AI思考中...</div>
        </div>

        <!-- 棋盘 -->
        <div class="pve-board-container">
          <div class="pve-board">
            <div v-for="(row, x) in board" :key="x" class="pve-row">
              <div
                v-for="(cell, y) in row"
                :key="y"
                class="pve-cell"
                @click="handleMove(x, y)"
              >
                <div
                  v-if="cell > 0"
                  class="pve-piece"
                  :class="cell === 1 ? 'black' : 'white'"
                >
                  <div v-if="lastMove && lastMove.x === x && lastMove.y === y" class="pve-last-move"></div>
                </div>
                <!-- 星位标记 -->
                <div
                  v-if="isStarPoint(x, y)"
                  class="pve-star-point"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 控制按钮 -->
        <div class="pve-controls">
          <button class="pve-btn" @click="undoMove" :disabled="!canUndo || currentTurn !== playerColor">
            <span>↶</span> 悔棋
          </button>
          <button class="pve-btn pve-btn-danger" @click="resign">
            <span>🏳</span> 认输
          </button>
          <button class="pve-btn pve-btn-warning" @click="restart">
            <span>🔄</span> 重新开始
          </button>
        </div>

        <!-- 选项控制 -->
        <div class="pve-options-panel">
          <div class="pve-option-item-inline">
            <span class="pve-option-label">执子: {{ playerColor === 'black' ? '⚫黑棋' : '⚪白棋' }}</span>
          </div>

          <div class="pve-option-item-inline">
            <span class="pve-option-label">AI时间</span>
            <div class="pve-slider-container">
              <input
                type="range"
                v-model.number="aiThinkTime"
                :min="1"
                :max="10"
                :step="1"
                class="pve-slider"
              />
              <span class="pve-slider-value">{{ aiThinkTime }}秒</span>
            </div>
          </div>

          <div class="pve-option-item-inline">
            <span class="pve-option-label">难度: {{ getDifficultyName() }}</span>
            <button class="pve-change-difficulty-btn" @click="router.push({ path: '/home', query: { panel: 'pve' } })">
              切换难度
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 游戏结束弹窗 -->
    <div v-if="gameOverVisible" class="pve-game-over-modal" @click="gameOverVisible = false">
      <div :class="['pve-game-over-content', gameResult]" @click.stop>
        <div class="pve-result-badge">{{ getResultText() }}</div>
        <div class="pve-result-icon">
          <div v-if="gameResult === 'win'">🎉</div>
          <div v-else-if="gameResult === 'lose'">😔</div>
          <div v-else>🤝</div>
        </div>
        <div class="pve-result-message">
          <div v-if="gameResult === 'win'">恭喜你击败了AI！</div>
          <div v-else-if="gameResult === 'lose'">AI赢得了这场比赛...</div>
          <div v-else>势均力敌！</div>
        </div>
        <div class="pve-game-over-actions">
          <button class="pve-btn-secondary" @click="goHome">
            <span>🏠</span> 返回首页
          </button>
          <button class="pve-btn-primary" @click="restart">
            <span>🔄</span> 再来一局
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { userApi } from '@/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const playerColor = ref<'black' | 'white'>('black')
const aiThinkTime = ref(3)

const board = ref<number[][]>(Array.from({ length: 15 }, () => Array(15).fill(0)))
const currentTurn = ref<'black' | 'white'>('black')
const lastMove = ref<{ x: number; y: number } | null>(null)
const canUndo = ref(false)
const moveHistory = ref<{ x: number; y: number; color: number }[]>([])

const gameOverVisible = ref(false)
const gameResult = ref<'win' | 'lose' | 'draw'>('win')
const selectedDifficulty = ref('medium')

const difficultyLevels: Record<string, { name: string; icon: string; description: string; rating: number }> = {
  easy: { name: '简单', icon: '🙂', description: '适合新手', rating: 800 },
  medium: { name: '中等', icon: '🤔', description: '有一定挑战', rating: 1200 },
  hard: { name: '困难', icon: '😰', description: '高手挑战', rating: 1600 },
  expert: { name: '专家', icon: '🤖', description: '极限挑战', rating: 2000 }
}

// 获取难度名称
const getDifficultyName = (): string => {
  return difficultyLevels[selectedDifficulty.value]?.name || '未知'
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

// 初始化游戏
const initGame = () => {
  // 随机决定玩家执黑还是执白
  playerColor.value = Math.random() < 0.5 ? 'black' : 'white'

  // 初始化棋盘
  board.value = Array.from({ length: 15 }, () => Array(15).fill(0))
  currentTurn.value = 'black'
  lastMove.value = null
  moveHistory.value = []
  canUndo.value = false

  // 如果玩家选择白棋，AI先手
  if (playerColor.value === 'white') {
    setTimeout(() => makeAIMove(), 500)
  }
}

// 玩家落子
const handleMove = (x: number, y: number) => {
  if (currentTurn.value !== playerColor.value) {
    ElMessage.warning('请等待AI落子')
    return
  }
  if (board.value[x][y] !== 0) {
    ElMessage.warning('该位置已有棋子')
    return
  }

  // 记录落子
  const color = playerColor.value === 'black' ? 1 : 2
  board.value[x][y] = color
  lastMove.value = { x, y }
  moveHistory.value.push({ x, y, color })
  canUndo.value = true

  // 检查胜负
  if (checkWin(x, y, color)) {
    // 记录游戏结果（异步，不阻塞UI）
    recordGame('win').catch(err => console.error('[PVE] 记录游戏失败:', err))
    gameResult.value = 'win'
    gameOverVisible.value = true
    return
  }

  // 检查平局
  if (moveHistory.value.length === 225) {
    // 记录游戏结果（异步，不阻塞UI）
    recordGame('draw').catch(err => console.error('[PVE] 记录游戏失败:', err))
    gameResult.value = 'draw'
    gameOverVisible.value = true
    return
  }

  // 切换到AI回合
  currentTurn.value = playerColor.value === 'black' ? 'white' : 'black'

  // AI落子
  setTimeout(() => makeAIMove(), aiThinkTime.value * 1000)
}

// AI落子
const makeAIMove = async () => {
  try {
    const aiColor = playerColor.value === 'black' ? 2 : 1
    const playerColorNum = playerColor.value === 'black' ? 1 : 2

    // 根据难度选择AI策略
    let move: { x: number; y: number }

    if (selectedDifficulty.value === 'easy') {
      move = getEasyAIMove(aiColor, playerColorNum)
    } else if (selectedDifficulty.value === 'medium') {
      move = getMediumAIMove(aiColor, playerColorNum)
    } else {
      move = getHardAIMove(aiColor, playerColorNum)
    }

    board.value[move.x][move.y] = aiColor
    lastMove.value = { x: move.x, y: move.y }
    moveHistory.value.push({ x: move.x, y: move.y, color: aiColor })

    // 检查胜负
    if (checkWin(move.x, move.y, aiColor)) {
      // 记录游戏结果（异步，不阻塞UI）
      recordGame('lose').catch(err => console.error('[PVE] 记录游戏失败:', err))
      gameResult.value = 'lose'
      gameOverVisible.value = true
      return
    }

    // 检查平局
    if (moveHistory.value.length === 225) {
      // 记录游戏结果（异步，不阻塞UI）
      recordGame('draw').catch(err => console.error('[PVE] 记录游戏失败:', err))
      gameResult.value = 'draw'
      gameOverVisible.value = true
      return
    }

    // 切换到玩家回合
    currentTurn.value = playerColor.value
  } catch (error) {
    ElMessage.error('AI落子失败')
  }
}

// 简单AI：基础攻防逻辑
const getEasyAIMove = (aiColor: number, playerColor: number): { x: number; y: number } => {
  const emptyCells: { x: number; y: number }[] = []
  for (let i = 0; i < 15; i++) {
    for (let j = 0; j < 15; j++) {
      if (board.value[i][j] === 0) {
        emptyCells.push({ x: i, y: j })
      }
    }
  }

  if (emptyCells.length === 0) return { x: 7, y: 7 }

  // 第一步：天元或附近
  if (moveHistory.value.length === 0 && aiColor === 1) {
    return { x: 7, y: 7 }
  }

  // 检查是否有能获胜的位置
  for (const cell of emptyCells) {
    board.value[cell.x][cell.y] = aiColor
    if (checkWin(cell.x, cell.y, aiColor)) {
      board.value[cell.x][cell.y] = 0
      return cell
    }
    board.value[cell.x][cell.y] = 0
  }

  // 检查是否需要阻挡玩家获胜
  for (const cell of emptyCells) {
    board.value[cell.x][cell.y] = playerColor
    if (checkWin(cell.x, cell.y, playerColor)) {
      board.value[cell.x][cell.y] = 0
      return cell
    }
    board.value[cell.x][cell.y] = 0
  }

  // 优先选择靠近已有棋子的位置
  const candidates = emptyCells.filter(cell => hasNeighbor(cell.x, cell.y))
  if (candidates.length > 0) {
    return candidates[Math.floor(Math.random() * candidates.length)]
  }

  // 否则选择靠近中心的位置
  return getBestCenterPosition(emptyCells)
}

// 中等AI：更完善的攻防逻辑
const getMediumAIMove = (aiColor: number, playerColor: number): { x: number; y: number } => {
  const emptyCells: { x: number; y: number }[] = []
  for (let i = 0; i < 15; i++) {
    for (let j = 0; j < 15; j++) {
      if (board.value[i][j] === 0) {
        emptyCells.push({ x: i, y: j })
      }
    }
  }

  if (emptyCells.length === 0) return { x: 7, y: 7 }

  // 第一步：天元
  if (moveHistory.value.length === 0 && aiColor === 1) {
    return { x: 7, y: 7 }
  }

  let bestMove = emptyCells[0]
  let bestScore = -Infinity

  for (const cell of emptyCells) {
    const score = evaluatePosition(cell.x, cell.y, aiColor, playerColor, false)
    if (score > bestScore) {
      bestScore = score
      bestMove = cell
    }
  }

  return bestMove
}

// 困难AI：高级攻防逻辑，考虑多重威胁
const getHardAIMove = (aiColor: number, playerColor: number): { x: number; y: number } => {
  const emptyCells: { x: number; y: number }[] = []
  for (let i = 0; i < 15; i++) {
    for (let j = 0; j < 15; j++) {
      if (board.value[i][j] === 0) {
        emptyCells.push({ x: i, y: j })
      }
    }
  }

  if (emptyCells.length === 0) return { x: 7, y: 7 }

  // 第一步：天元
  if (moveHistory.value.length === 0 && aiColor === 1) {
    return { x: 7, y: 7 }
  }

  // 困难模式使用更深度的评估
  let bestMove = emptyCells[0]
  let bestScore = -Infinity

  for (const cell of emptyCells) {
    const score = evaluatePosition(cell.x, cell.y, aiColor, playerColor, true)
    if (score > bestScore) {
      bestScore = score
      bestMove = cell
    }
  }

  return bestMove
}

// 检查位置是否有相邻棋子
const hasNeighbor = (x: number, y: number, distance: number = 2): boolean => {
  for (let dx = -distance; dx <= distance; dx++) {
    for (let dy = -distance; dy <= distance; dy++) {
      if (dx === 0 && dy === 0) continue
      const nx = x + dx
      const ny = y + dy
      if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
        if (board.value[nx][ny] !== 0) return true
      }
    }
  }
  return false
}

// 获取靠近中心的位置
const getBestCenterPosition = (emptyCells: { x: number; y: number }[]): { x: number; y: number } => {
  const center = 7
  emptyCells.sort((a, b) => {
    const distA = Math.abs(a.x - center) + Math.abs(a.y - center)
    const distB = Math.abs(b.x - center) + Math.abs(b.y - center)
    return distA - distB
  })
  return emptyCells[0]
}

// 评估位置的分数（核心AI逻辑）
const evaluatePosition = (x: number, y: number, aiColor: number, playerColor: number, isHard: boolean): number => {
  // 如果位置太孤立，给予较低分数
  if (!hasNeighbor(x, y, isHard ? 2 : 1)) {
    // 只在开局阶段允许孤立位置
    if (moveHistory.value.length > 10) {
      return -1000
    }
    // 越靠近中心越好
    const centerDist = Math.abs(x - 7) + Math.abs(y - 7)
    return 50 - centerDist * 2
  }

  let score = 0

  // 评估AI落子此处的进攻价值
  board.value[x][y] = aiColor
  const attackScore = evaluatePoint(x, y, aiColor, isHard)

  // 评估此处阻挡玩家的防守价值
  board.value[x][y] = playerColor
  const defenseScore = evaluatePoint(x, y, playerColor, isHard)

  // 恢复空位
  board.value[x][y] = 0

  // 困难模式更注重防守
  const attackWeight = isHard ? 1.0 : 1.2
  const defenseWeight = isHard ? 1.1 : 0.9

  score = attackScore * attackWeight + defenseScore * defenseWeight

  // 位置加分：越靠近中心越好
  const centerDist = Math.abs(x - 7) + Math.abs(y - 7)
  score += (14 - centerDist) * 2

  return score
}

// 评估某位置的棋形分数
const evaluatePoint = (x: number, y: number, color: number, isHard: boolean): number => {
  const directions = [[1, 0], [0, 1], [1, 1], [1, -1]]
  let totalScore = 0

  for (const [dx, dy] of directions) {
    const lineInfo = getLineInfo(x, y, dx, dy, color)
    totalScore += getLineScore(lineInfo, isHard)
  }

  return totalScore
}

// 获取某方向上的棋形信息
const getLineInfo = (x: number, y: number, dx: number, dy: number, color: number) => {
  let count = 1
  let openEnds = 0
  let blocked = 0

  // 正方向
  let i = 1
  while (true) {
    const nx = x + dx * i
    const ny = y + dy * i
    if (nx < 0 || nx >= 15 || ny < 0 || ny >= 15) {
      blocked++
      break
    }
    if (board.value[nx][ny] === color) {
      count++
    } else if (board.value[nx][ny] === 0) {
      openEnds++
      break
    } else {
      blocked++
      break
    }
    i++
  }

  // 反方向
  i = 1
  while (true) {
    const nx = x - dx * i
    const ny = y - dy * i
    if (nx < 0 || nx >= 15 || ny < 0 || ny >= 15) {
      blocked++
      break
    }
    if (board.value[nx][ny] === color) {
      count++
    } else if (board.value[nx][ny] === 0) {
      openEnds++
      break
    } else {
      blocked++
      break
    }
    i++
  }

  return { count, openEnds, blocked }
}

// 根据棋形给分
const getLineScore = (line: { count: number; openEnds: number; blocked: number }, isHard: boolean): number => {
  const { count, openEnds, blocked } = line

  // 五连 - 必胜
  if (count >= 5) return 100000

  // 活四 - 必胜
  if (count === 4 && openEnds === 2) return 50000

  // 冲四 - 很强
  if (count === 4 && openEnds === 1) return 10000

  // 活三 - 强力进攻
  if (count === 3 && openEnds === 2) return isHard ? 5000 : 3000

  // 眠三 - 有潜力
  if (count === 3 && openEnds === 1) return isHard ? 1000 : 500

  // 活二
  if (count === 2 && openEnds === 2) return isHard ? 500 : 200

  // 眠二
  if (count === 2 && openEnds === 1) return isHard ? 100 : 50

  // 单子
  if (count === 1 && openEnds >= 1) return isHard ? 20 : 10

  return 0
}

// 检查胜负（简化版）
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

// 悔棋
const undoMove = async () => {
  if (moveHistory.value.length < 2) {
    ElMessage.warning('无法悔棋')
    return
  }

  try {
    await ElMessageBox.confirm('确定要悔棋吗？', '提示', {
      type: 'warning'
    })

    // 撤销两步（玩家一步，AI一步）
    for (let i = 0; i < 2 && moveHistory.value.length > 0; i++) {
      const last = moveHistory.value.pop()!
      board.value[last.x][last.y] = 0
    }

    lastMove.value = moveHistory.value[moveHistory.value.length - 1] || null
    currentTurn.value = playerColor.value
    canUndo.value = moveHistory.value.length >= 2
  } catch {
    // 用户取消
  }
}

// 认输
const resign = async () => {
  try {
    await ElMessageBox.confirm('确定要认输吗？', '提示', {
      type: 'warning'
    })
    // 记录游戏结果（异步，不阻塞UI）
    recordGame('lose').catch(err => console.error('[PVE] 记录游戏失败:', err))
    gameResult.value = 'lose'
    gameOverVisible.value = true
  } catch {
    // 用户取消
  }
}

// 记录游戏结果
const recordGame = async (result: 'win' | 'lose' | 'draw') => {
  if (!userStore.userInfo?.id) {
    console.warn('[PVE] 用户未登录，跳过记录游戏结果')
    return
  }

  try {
    console.log('[PVE] 记录游戏结果:', result)
    console.log('[PVE] 走棋历史:', moveHistory.value)
    console.log('[PVE] 走棋数量:', moveHistory.value.length)

    // 序列化棋盘状态
    const boardStateStr = board.value.map(row => row.join(',')).join(',')
    console.log('[PVE] 棋盘状态长度:', boardStateStr.length)

    // 准备走棋数据
    const movesData = moveHistory.value.map(m => ({ x: m.x, y: m.y }))
    console.log('[PVE] 发送的moves数据:', movesData)

    // 调用后端API记录游戏结果，包含走棋数据
    const response = await userApi.recordGame(
      userStore.userInfo.id,
      result,
      'PVE',
      movesData,
      boardStateStr
    )

    console.log('[PVE] API响应:', response)

    if (response && response.success) {
      console.log('[PVE] 游戏结果已记录:', response.data)

      // 更新本地用户信息
      if (userStore.userInfo && response.data) {
        userStore.userInfo.rating = response.data.rating || userStore.userInfo.rating
        userStore.userInfo.level = response.data.level || userStore.userInfo.level
        userStore.userInfo.exp = response.data.exp || 0

        // 更新localStorage
        localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      }
    } else {
      console.error('[PVE] 记录失败:', response)
    }
  } catch (error) {
    console.error('[PVE] 记录游戏结果失败:', error)
    ElMessage.error('记录游戏结果失败: ' + (error as any).message)
  }
}

// 重新开始
const restart = () => {
  gameOverVisible.value = false
  initGame()
}

// 页面加载时初始化游戏
onMounted(() => {
  // 从URL获取难度
  const difficulty = (route.query.difficulty as string) || 'medium'
  selectedDifficulty.value = ['easy', 'medium', 'hard', 'expert'].includes(difficulty) ? difficulty : 'medium'

  // 初始化游戏
  initGame()
})

// 获取结果文本
const getResultText = (): string => {
  const map = {
    win: '你赢了！',
    lose: '你输了！',
    draw: '平局'
  }
  return map[gameResult.value]
}

// 返回首页
const goHome = () => {
  router.push({ path: '/home' })
}
</script>

<style scoped>
.pve-view {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
}

/* 顶部导航条 */
.pve-nav {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 50%, #ffa07a 100%);
  padding: 20px 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 4px 20px rgba(255, 107, 53, 0.3);
}

.pve-back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 25px;
  color: white;
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.pve-back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateX(-2px);
}

.pve-title {
  font-size: 24px;
  font-weight: bold;
  color: white;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  margin: 0;
}

.pve-spacer {
  width: 100px;
}

/* 内容区 */
.pve-content {
  max-width: 900px;
  margin: 30px auto;
  padding: 0 20px;
}

/* 游戏区域 */

.pve-color-selector {
  display: flex;
  gap: 15px;
}

.pve-color-option {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: 2px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.pve-color-option:hover {
  border-color: #ff8c61;
  background: #fff5f0;
}

.pve-color-option.active {
  border-color: #ff6b35;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
}

.pve-color-piece {
  font-size: 18px;
}

/* 游戏区域 */
.pve-game-area {
  background: white;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.pve-game-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 15px 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border-radius: 12px;
}

.pve-player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-radius: 10px;
  transition: all 0.3s;
}

.pve-player-info.active {
  background: rgba(255, 107, 53, 0.2);
  box-shadow: 0 0 15px rgba(255, 107, 53, 0.3);
}

.pve-avatar {
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

.pve-player-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pve-player-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.pve-player-color {
  font-size: 12px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
}

.pve-piece-icon {
  font-size: 14px;
}

.pve-vs {
  font-size: 24px;
  font-weight: bold;
  color: #999;
}

/* 选项面板 */
.pve-options-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  justify-content: center;
  margin-top: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border-radius: 12px;
}

.pve-option-item-inline {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pve-option-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.pve-color-selector {
  display: flex;
  gap: 8px;
}

.pve-color-option {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border: 2px solid #f0f0f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  background: white;
  font-size: 14px;
}

.pve-color-option:hover {
  border-color: #ff8c61;
  background: #fff5f0;
}

.pve-color-option.active {
  border-color: #ff6b35;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  box-shadow: 0 2px 8px rgba(255, 107, 53, 0.2);
}

.pve-color-piece {
  font-size: 18px;
}

.pve-slider-container {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pve-slider {
  width: 100px;
  height: 6px;
  -webkit-appearance: none;
  appearance: none;
  background: #f0f0f0;
  border-radius: 3px;
  outline: none;
}

.pve-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  border-radius: 50%;
  cursor: pointer;
}

.pve-slider-value {
  font-size: 14px;
  color: #ff6b35;
  font-weight: 600;
  min-width: 40px;
  text-align: center;
}

.pve-change-difficulty-btn {
  padding: 8px 16px;
  background: white;
  border: 2px solid #ff6b35;
  color: #ff6b35;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.pve-change-difficulty-btn:hover {
  background: #ff6b35;
  color: white;
}

.pve-game-status {
  text-align: center;
  margin-bottom: 20px;
}

.pve-status-turn {
  display: inline-block;
  padding: 8px 24px;
  background: linear-gradient(135deg, #67c23a, #85ce61);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.pve-status-thinking {
  display: inline-block;
  padding: 8px 24px;
  background: linear-gradient(135deg, #e6a23c, #f0c78a);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

/* 棋盘 */
.pve-board-container {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.pve-board {
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

/* 棋盘网格线 - 使用绝对定位 */
.pve-board::before {
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

.pve-row {
  display: contents;
}

.pve-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1;
}

.pve-cell:hover {
  background: rgba(255, 107, 53, 0.15);
}

.pve-star-point {
  width: 8px;
  height: 8px;
  background: #ff8c61;
  border-radius: 50%;
  position: absolute;
  z-index: 1;
}

.pve-piece {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  z-index: 2;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
  position: relative;
}

.pve-piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.pve-piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.pve-last-move {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #ff6b35;
  border-radius: 50%;
}

/* 控制按钮 */
.pve-controls {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.pve-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, #f5f5f5, #e8e8e8);
  color: #333;
  display: flex;
  align-items: center;
  gap: 6px;
}

.pve-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.pve-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pve-btn-danger {
  background: linear-gradient(135deg, #f56c6c, #f89898);
  color: white;
}

.pve-btn-warning {
  background: linear-gradient(135deg, #e6a23c, #f0c78a);
  color: white;
}

.pve-btn-primary {
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
}

.pve-btn-secondary {
  background: #f5f5f5;
  color: #666;
}

/* 游戏结束弹窗 */
.pve-game-over-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(10px);
}

.pve-game-over-content {
  padding: 40px 50px 50px;
  text-align: center;
  border-radius: 20px;
  max-width: 480px;
  margin: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.pve-game-over-content.win {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.pve-game-over-content.lose {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.pve-game-over-content.draw {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.pve-result-badge {
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

.pve-result-icon {
  font-size: 64px;
  margin: 20px 0 30px 0;
  filter: drop-shadow(0 6px 15px rgba(0, 0, 0, 0.2));
  animation: popIn 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.pve-game-over-content.win .pve-result-icon {
  animation: bounceIn 0.8s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.pve-game-over-content.lose .pve-result-icon {
  animation: shake 0.8s ease-in-out;
}

.pve-game-over-content.draw .pve-result-icon {
  animation: pulse 1s ease-in-out infinite;
}

.pve-result-message {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 10px;
  color: white;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  letter-spacing: 1px;
  line-height: 1.4;
}

.pve-game-over-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.pve-game-over-actions button {
  flex: 1;
  padding: 14px 24px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.pve-game-over-actions button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
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

/* 响应式 */
@media (max-width: 768px) {
  .pve-nav {
    padding: 15px 20px;
  }

  .pve-title {
    font-size: 18px;
  }

  .pve-back-btn span {
    display: none;
  }

  .pve-board-container {
    overflow-x: auto;
    padding: 10px;
  }

  .pve-board {
    width: 300px;
    height: 300px;
  }

  .pve-piece {
    width: 16px;
    height: 16px;
  }

  .pve-piece {
    width: 26px;
    height: 26px;
  }

  .pve-game-header {
    flex-direction: column;
    gap: 10px;
  }

  .pve-controls {
    flex-wrap: wrap;
  }

  .pve-btn {
    flex: 1;
    min-width: 80px;
  }
}
</style>
