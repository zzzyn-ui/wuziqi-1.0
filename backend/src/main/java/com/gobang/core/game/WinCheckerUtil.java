package com.gobang.core.game;

/**
 * 五子棋胜负判定工具类
 * 使用四向扫描算法，性能优化
 */
public class WinCheckerUtil {

    private static final int BOARD_SIZE = 15;
    private static final int WIN_COUNT = 5;

    // 四个扫描方向: 水平、垂直、主对角线、副对角线
    private static final int[][] DIRECTIONS = {
        {1, 0},   // 水平 →
        {0, 1},   // 垂直 ↓
        {1, 1},   // 主对角线 ↘
        {1, -1}   // 副对角线 ↗
    };

    /**
     * 检查是否获胜 - 四向扫描算法
     *
     * @param board  15x15 棋盘数组
     * @param x      最后落子行 (0-14)
     * @param y      最后落子列 (0-14)
     * @param player 玩家 (1=黑, 2=白)
     * @return 是否获胜
     */
    public static boolean checkWin(int[][] board, int x, int y, int player) {
        // 参数校验
        if (board == null || x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
            return false;
        }

        // 检查四个方向
        for (int[] dir : DIRECTIONS) {
            if (checkDirection(board, x, y, player, dir[0], dir[1])) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查指定方向是否有连续5个或更多相同颜色的棋子
     *
     * @param board    棋盘
     * @param x        起始行
     * @param y        起始列
     * @param player   玩家
     * @param deltaX   行方向增量
     * @param deltaY   列方向增量
     * @return 是否获胜
     */
    private static boolean checkDirection(int[][] board, int x, int y, int player, int deltaX, int deltaY) {
        int count = 1; // 当前落子算1个

        // 正向扫描
        for (int i = 1; i < WIN_COUNT; i++) {
            int nx = x + deltaX * i;
            int ny = y + deltaY * i;

            if (!isValidPosition(nx, ny) || board[nx][ny] != player) {
                break;
            }
            count++;
        }

        // 反向扫描
        for (int i = 1; i < WIN_COUNT; i++) {
            int nx = x - deltaX * i;
            int ny = y - deltaY * i;

            if (!isValidPosition(nx, ny) || board[nx][ny] != player) {
                break;
            }
            count++;
        }

        return count >= WIN_COUNT;
    }

    /**
     * 检查位置是否有效
     */
    private static boolean isValidPosition(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    /**
     * 检查是否平局（棋盘已满）
     */
    public static boolean checkDraw(int[][] board) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 获取获胜线（用于高亮显示）
     *
     * @return 获胜棋子位置数组 [x1, y1, x2, y2, ...]，未获胜返回null
     */
    public static int[] getWinningLine(int[][] board, int x, int y, int player) {
        for (int[] dir : DIRECTIONS) {
            int[] line = getLineInDirection(board, x, y, player, dir[0], dir[1]);
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    /**
     * 获取指定方向的获胜线
     */
    private static int[] getLineInDirection(int[][] board, int x, int y, int player, int deltaX, int deltaY) {
        int startX = x, startY = y;
        int endX = x, endY = y;
        int count = 1;

        // 正向
        for (int i = 1; i < WIN_COUNT; i++) {
            int nx = x + deltaX * i;
            int ny = y + deltaY * i;

            if (!isValidPosition(nx, ny) || board[nx][ny] != player) {
                break;
            }
            endX = nx;
            endY = ny;
            count++;
        }

        // 反向
        for (int i = 1; i < WIN_COUNT; i++) {
            int nx = x - deltaX * i;
            int ny = y - deltaY * i;

            if (!isValidPosition(nx, ny) || board[nx][ny] != player) {
                break;
            }
            startX = nx;
            startY = ny;
            count++;
        }

        if (count >= WIN_COUNT) {
            // 返回从start到end的所有位置
            int[] result = new int[count * 2];
            int idx = 0;
            int curX = startX, curY = startY;

            for (int i = 0; i < count; i++) {
                result[idx++] = curX;
                result[idx++] = curY;
                curX += deltaX;
                curY += deltaY;
            }
            return result;
        }

        return null;
    }

    /**
     * 计算当前局面得分（用于AI）
     * 评估棋盘局势，返回优势方的得分
     */
    public static int evaluate(int[][] board, int player) {
        int score = 0;

        // 遍历所有位置，评估潜在的五连
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == 0) continue;

                for (int[] dir : DIRECTIONS) {
                    score += evaluateDirection(board, i, j, board[i][j], dir[0], dir[1]);
                }
            }
        }

        return player == 1 ? score : -score;
    }

    /**
     * 评估某个方向的得分
     */
    private static int evaluateDirection(int[][] board, int x, int y, int player, int deltaX, int deltaY) {
        int count = 1;
        int blocked = 0;

        // 正向
        for (int i = 1; i < WIN_COUNT; i++) {
            int nx = x + deltaX * i;
            int ny = y + deltaY * i;

            if (!isValidPosition(nx, ny)) {
                blocked++;
                break;
            }

            if (board[nx][ny] == player) {
                count++;
            } else if (board[nx][ny] == 0) {
                break;
            } else {
                blocked++;
                break;
            }
        }

        // 反向
        for (int i = 1; i < WIN_COUNT; i++) {
            int nx = x - deltaX * i;
            int ny = y - deltaY * i;

            if (!isValidPosition(nx, ny)) {
                blocked++;
                break;
            }

            if (board[nx][ny] == player) {
                count++;
            } else if (board[nx][ny] == 0) {
                break;
            } else {
                blocked++;
                break;
            }
        }

        // 根据连子数和被封堵情况计算得分
        return getScore(count, blocked);
    }

    /**
     * 根据连子数和被封堵数计算得分
     */
    private static int getScore(int count, int blocked) {
        if (count >= WIN_COUNT) return 100000;
        if (blocked >= 2) return 0;

        switch (count) {
            case 4: return blocked == 0 ? 10000 : 1000;
            case 3: return blocked == 0 ? 1000 : 100;
            case 2: return blocked == 0 ? 100 : 10;
            case 1: return 1;
            default: return 0;
        }
    }
}
