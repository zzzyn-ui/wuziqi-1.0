<template>
  <div class="game-board-wrapper">
    <!-- 棋盘 -->
    <div class="board-container">
      <div class="board">
        <div
          v-for="(row, rowIndex) in boardData"
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
              'last-move': isLastMove(rowIndex, colIndex),
              'winning-cell': isWinningCell(rowIndex, colIndex)
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'

interface Props {
  board?: number[][] | string[][]
  boardString?: string
  onCellClick?: (row: number, col: number) => void
  showLastMove?: boolean
  winningCells?: [number, number][]
  lastMove?: { row: number; col: number } | null
}

const props = withDefaults(defineProps<Props>(), {
  showLastMove: true,
  winningCells: () => []
})

const emit = defineEmits<{
  cellClick: [row: number, col: number]
}>()

// 转换棋盘数据为统一格式
const boardData = computed(() => {
  console.log('[GameBoard] Computing boardData, props.board:', props.board)

  if (props.boardString) {
    // 从字符串解析
    console.log('[GameBoard] Parsing from boardString')
    return parseBoardString(props.boardString)
  } else if (props.board && props.board.length > 0) {
    // 检查第一个元素的类型来判断格式
    const firstRow = props.board[0]
    console.log('[GameBoard] First row:', firstRow, 'type of firstRow:', typeof firstRow)

    if (firstRow && firstRow.length > 0) {
      const firstCell = firstRow[0]
      console.log('[GameBoard] First cell:', firstCell, 'type:', typeof firstCell)

      if (typeof firstCell === 'number') {
        // 数字格式的棋盘，需要转换
        console.log('[GameBoard] Converting number board to string board')
        return convertNumberBoard(props.board as number[][])
      } else if (typeof firstCell === 'string') {
        // 已经是字符串格式的二维数组
        console.log('[GameBoard] Using string board directly')
        return props.board as string[][]
      }
    }
    // 无法判断类型，尝试转换数字格式
    console.log('[GameBoard] Cannot determine type, trying number conversion')
    return convertNumberBoard(props.board as number[][])
  }
  // 默认空棋盘
  console.log('[GameBoard] Using empty board')
  return createEmptyBoard()
})

// 创建空棋盘
const createEmptyBoard = (): string[][] => {
  return Array.from({ length: 15 }, () =>
    Array(15).fill('empty')
  )
}

// 从字符串解析棋盘状态
const parseBoardString = (boardState: string): string[][] => {
  const board: string[][] = createEmptyBoard()
  if (!boardState) return board

  const rows = boardState.split('\n')
  for (let i = 0; i < Math.min(15, rows.length); i++) {
    const row = rows[i]
    for (let j = 0; j < Math.min(15, row.length); j++) {
      const char = row.charAt(j)
      if (char === 'B' || char === 'b') {
        board[i][j] = 'black'
      } else if (char === 'W' || char === 'w') {
        board[i][j] = 'white'
      }
    }
  }
  return board
}

// 将数字格式的棋盘转换为字符串格式
const convertNumberBoard = (numBoard: number[][]): string[][] => {
  const board: string[][] = createEmptyBoard()
  let blackCount = 0
  let whiteCount = 0
  for (let i = 0; i < 15; i++) {
    for (let j = 0; j < 15; j++) {
      if (numBoard[i][j] === 1) {
        board[i][j] = 'black'
        blackCount++
      } else if (numBoard[i][j] === 2) {
        board[i][j] = 'white'
        whiteCount++
      }
    }
  }
  console.log('[GameBoard] Board converted: black pieces:', blackCount, 'white pieces:', whiteCount)
  console.log('[GameBoard] Converted board sample:', board[0][0], board[0][1], board[0][2])
  return board
}

// 检查是否为星位点
const isStarPoint = (row: number, col: number): boolean => {
  const stars = [
    [3, 3], [3, 7], [3, 11],
    [7, 3], [7, 7], [7, 11],
    [11, 3], [11, 7], [11, 11]
  ]
  return stars.some(([sx, sy]) => sx === row && sy === col)
}

// 检查是否为最后一步
const isLastMove = (row: number, col: number): boolean => {
  if (!props.showLastMove || !props.lastMove) return false
  return props.lastMove.row === row && props.lastMove.col === col
}

// 检查是否为获胜棋子
const isWinningCell = (row: number, col: number): boolean => {
  return props.winningCells.some(([r, c]) => r === row && c === col)
}

// 处理格子点击
const handleCellClick = (row: number, col: number) => {
  emit('cellClick', row, col)
  if (props.onCellClick) {
    props.onCellClick(row, col)
  }
}

// 调试：监控 boardData 变化
watch(boardData, (newBoardData) => {
  console.log('[GameBoard] boardData 变化:', newBoardData)
  console.log('[GameBoard] boardData 长度:', newBoardData?.length)
  if (newBoardData && newBoardData.length > 0) {
    console.log('[GameBoard] boardData 第一行:', newBoardData[0])
    console.log('[GameBoard] boardData 第一行前5个格子:', newBoardData[0]?.slice(0, 5))
  }
}, { deep: true, immediate: true })

onMounted(() => {
  console.log('[GameBoard] 组件已挂载')
  console.log('[GameBoard] 当前 boardData:', boardData.value)
  console.log('[GameBoard] boardData 第一行:', boardData.value?.[0])
})
</script>

<style scoped>
.game-board-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.board-container {
  display: flex;
  justify-content: center;
  margin: 20px 0;
  width: 100%;
  min-width: 300px;
}

.board {
  width: 100%;
  max-width: 500px;
  aspect-ratio: 1;
  /* 白橙配色 */
  background: #ffffff;
  border: 3px solid #ff8c00;
  border-radius: 12px;
  box-shadow:
    0 8px 24px rgba(255, 140, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  grid-template-rows: repeat(15, 1fr);
  padding: 12px;
  position: relative;
  /* 确保网格正确显示 */
  min-height: 300px !important;
  min-width: 300px !important;
}

.board::before {
  content: '';
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  bottom: 12px;
  /* 橙色线条 */
  border: 1px solid #ffcc80;
  border-radius: 8px;
  pointer-events: none;
}

.board-row {
  display: contents;
}

.board-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.board-cell::before,
.board-cell::after {
  content: '';
  position: absolute;
  /* 橙色线条 */
  background: #ffcc80;
}

.board-cell::before {
  width: 100%;
  height: 1px;
  top: 50%;
  transform: translateY(-50%);
}

.board-cell::after {
  width: 1px;
  height: 100%;
  left: 50%;
  transform: translateX(-50%);
}

.board-cell:hover {
  background: rgba(240, 147, 251, 0.1);
}

.star-dot {
  width: 6px;
  height: 6px;
  background: #8b7355;
  border-radius: 50%;
  position: absolute;
  z-index: 1;
}

.piece {
  width: 85%;
  height: 85%;
  max-width: 28px;
  max-height: 28px;
  border-radius: 50%;
  position: absolute;
  z-index: 2;
  box-shadow: 2px 2px 5px rgba(0, 0, 0, 0.4);
  transition: transform 0.15s ease;
}

.piece.black {
  background: radial-gradient(circle at 35% 35%, #4a4a4a, #1a1a1a);
  border: 1px solid #000;
}

.piece.white {
  background: radial-gradient(circle at 35% 35%, #ffffff, #f0f0f0);
  border: 3px solid #333;
  box-shadow:
    2px 2px 5px rgba(0, 0, 0, 0.5),
    inset -2px -2px 4px rgba(0, 0, 0, 0.1);
}

.piece.white::after {
  content: '';
  position: absolute;
  top: 15%;
  left: 15%;
  width: 35%;
  height: 35%;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
}

.board-cell.last-move .piece {
  transform: scale(1.1);
  box-shadow: 0 0 8px 2px rgba(240, 147, 251, 0.6);
}

.board-cell.winning-cell .piece {
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.15);
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .board {
    max-width: 100%;
    padding: 8px;
  }

  .piece {
    max-width: 22px;
    max-height: 22px;
  }
}
</style>
