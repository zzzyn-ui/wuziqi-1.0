package com.gobang.core.game;

/**
 * 五子棋胜负判定器
 * 使用四向扫描算法
 */
public class WinChecker {

    /**
     * 检查是否获胜
     *
     * @param board  棋盘
     * @param lastX  最后落子行
     * @param lastY  最后落子列
     * @param color  棋子颜色
     * @return 是否获胜
     */
    public static boolean checkWin(Board board, int lastX, int lastY, int color) {
        // 检查四个方向：水平、垂直、主对角线、副对角线
        return checkDirection(board, lastX, lastY, color, 1, 0)   // 水平
                || checkDirection(board, lastX, lastY, color, 0, 1)   // 垂直
                || checkDirection(board, lastX, lastY, color, 1, 1)   // 主对角线
                || checkDirection(board, lastX, lastY, color, 1, -1); // 副对角线
    }

    /**
     * 检查指定方向是否有连续5个或更多相同颜色的棋子
     *
     * @param board    棋盘
     * @param x        起始行
     * @param y        起始列
     * @param color    棋子颜色
     * @param deltaX   行方向
     * @param deltaY   列方向
     * @return 是否获胜
     */
    private static boolean checkDirection(Board board, int x, int y, int color, int deltaX, int deltaY) {
        int count = 1; // 当前落子算1个

        // 正向检查
        int i = 1;
        while (true) {
            int nx = x + deltaX * i;
            int ny = y + deltaY * i;
            if (!board.isValidPosition(nx, ny) || board.getCell(nx, ny) != color) {
                break;
            }
            count++;
            i++;
        }

        // 反向检查
        i = 1;
        while (true) {
            int nx = x - deltaX * i;
            int ny = y - deltaY * i;
            if (!board.isValidPosition(nx, ny) || board.getCell(nx, ny) != color) {
                break;
            }
            count++;
            i++;
        }

        return count >= 5;
    }

    /**
     * 检查是否平局（棋盘已满）
     *
     * @param board 棋盘
     * @return 是否平局
     */
    public static boolean checkDraw(Board board) {
        for (int i = 0; i < Board.SIZE; i++) {
            for (int j = 0; j < Board.SIZE; j++) {
                if (board.isEmpty(i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 获取获胜棋子位置数组（用于高亮显示）
     *
     * @param board  棋盘
     * @param lastX  最后落子行
     * @param lastY  最后落子列
     * @param color  棋子颜色
     * @return 获胜棋子位置数组 [x1, y1, x2, y2, ...]，未获胜返回null
     */
    public static int[] getWinningLine(Board board, int lastX, int lastY, int color) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

        for (int[] dir : directions) {
            int[] line = getLineInDirection(board, lastX, lastY, color, dir[0], dir[1]);
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    /**
     * 获取指定方向的获胜线
     */
    private static int[] getLineInDirection(Board board, int x, int y, int color, int deltaX, int deltaY) {
        int count = 1;
        int startX = x, startY = y;
        int endX = x, endY = y;

        // 正向
        int i = 1;
        while (true) {
            int nx = x + deltaX * i;
            int ny = y + deltaY * i;
            if (!board.isValidPosition(nx, ny) || board.getCell(nx, ny) != color) {
                break;
            }
            endX = nx;
            endY = ny;
            count++;
            i++;
        }

        // 反向
        i = 1;
        while (true) {
            int nx = x - deltaX * i;
            int ny = y - deltaY * i;
            if (!board.isValidPosition(nx, ny) || board.getCell(nx, ny) != color) {
                break;
            }
            startX = nx;
            startY = ny;
            count++;
            i++;
        }

        if (count >= 5) {
            // 从start到end的所有位置
            int[] result = new int[count * 2];
            int idx = 0;
            int curX = startX, curY = startY;
            for (int j = 0; j < count; j++) {
                result[idx++] = curX;
                result[idx++] = curY;
                curX += deltaX;
                curY += deltaY;
            }
            return result;
        }
        return null;
    }
}
