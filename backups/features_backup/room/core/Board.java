package com.gobang.core.game;

import java.util.Arrays;

/**
 * 五子棋棋盘
 * 15x15标准棋盘
 */
public class Board {

    public static final int SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private final int[][] cells;

    public Board() {
        this.cells = new int[SIZE][SIZE];
    }

    /**
     * 复制构造函数
     */
    public Board(Board other) {
        this.cells = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(other.cells[i], 0, this.cells[i], 0, SIZE);
        }
    }

    /**
     * 放置棋子
     *
     * @param x     行 (0-14)
     * @param y     列 (0-14)
     * @param color 颜色 (BLACK/WHITE)
     * @return 是否成功
     */
    public boolean place(int x, int y, int color) {
        if (!isValidPosition(x, y) || cells[x][y] != EMPTY) {
            return false;
        }
        cells[x][y] = color;
        return true;
    }

    /**
     * 获取指定位置的棋子
     */
    public int getCell(int x, int y) {
        if (!isValidPosition(x, y)) {
            return -1;
        }
        return cells[x][y];
    }

    /**
     * 检查位置是否有效
     */
    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    /**
     * 检查位置是否为空
     */
    public boolean isEmpty(int x, int y) {
        return isValidPosition(x, y) && cells[x][y] == EMPTY;
    }

    /**
     * 清空棋盘
     */
    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            Arrays.fill(cells[i], EMPTY);
        }
    }

    /**
     * 清除指定位置的棋子（用于悔棋）
     *
     * @param x 行
     * @param y 列
     */
    public void clearCell(int x, int y) {
        if (isValidPosition(x, y)) {
            cells[x][y] = EMPTY;
        }
    }

    /**
     * 获取棋盘的一维数组表示
     */
    public int[] toArray() {
        int[] result = new int[SIZE * SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(cells[i], 0, result, i * SIZE, SIZE);
        }
        return result;
    }

    /**
     * 从一维数组恢复棋盘
     */
    public void fromArray(int[] array) {
        if (array.length != SIZE * SIZE) {
            throw new IllegalArgumentException("Invalid array size");
        }
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(array, i * SIZE, cells[i], 0, SIZE);
        }
    }

    /**
     * 重置棋盘（清空所有棋子）
     */
    public void reset() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                cells[i][j] = EMPTY;
            }
        }
    }

    /**
     * 获取原始二维数组
     */
    public int[][] getCells() {
        return cells;
    }

    /**
     * 获取棋盘（别名方法）
     */
    public int[][] getBoard() {
        return cells;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("   ");
        for (int i = 0; i < SIZE; i++) {
            sb.append(String.format("%2d", i));
        }
        sb.append("\n");
        for (int i = 0; i < SIZE; i++) {
            sb.append(String.format("%2d ", i));
            for (int j = 0; j < SIZE; j++) {
                char c;
                switch (cells[i][j]) {
                    case EMPTY:
                        c = '.';
                        break;
                    case BLACK:
                        c = 'X';
                        break;
                    case WHITE:
                        c = 'O';
                        break;
                    default:
                        c = '?';
                }
                sb.append(c).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
