package com.gobang.core.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 胜负判定器测试类
 */
@DisplayName("胜负判定器测试")
class WinCheckerTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    @Test
    @DisplayName("应该检测到水平五连")
    void shouldDetectHorizontalFive() {
        for (int i = 0; i < 5; i++) {
            board.place(7, 7 + i, Board.BLACK);
        }
        assertTrue(WinChecker.checkWin(board, 7, 11, Board.BLACK),
                "应该检测到水平五连");
    }

    @Test
    @DisplayName("应该检测到垂直五连")
    void shouldDetectVerticalFive() {
        for (int i = 0; i < 5; i++) {
            board.place(7 + i, 7, Board.BLACK);
        }
        assertTrue(WinChecker.checkWin(board, 11, 7, Board.BLACK),
                "应该检测到垂直五连");
    }

    @Test
    @DisplayName("应该检测到主对角线五连")
    void shouldDetectMainDiagonalFive() {
        for (int i = 0; i < 5; i++) {
            board.place(7 + i, 7 + i, Board.BLACK);
        }
        assertTrue(WinChecker.checkWin(board, 11, 11, Board.BLACK),
                "应该检测到主对角线五连");
    }

    @Test
    @DisplayName("应该检测到副对角线五连")
    void shouldDetectAntiDiagonalFive() {
        for (int i = 0; i < 5; i++) {
            board.place(7 + i, 11 - i, Board.BLACK);
        }
        assertTrue(WinChecker.checkWin(board, 11, 7, Board.BLACK),
                "应该检测到副对角线五连");
    }

    @Test
    @DisplayName("应该检测到超过五连")
    void shouldDetectMoreThanFive() {
        for (int i = 0; i < 6; i++) {
            board.place(7, 7 + i, Board.BLACK);
        }
        assertTrue(WinChecker.checkWin(board, 7, 12, Board.BLACK),
                "应该检测到六连");
    }

    @Test
    @DisplayName("四连不应该获胜")
    void fourInRowShouldNotWin() {
        for (int i = 0; i < 4; i++) {
            board.place(7, 7 + i, Board.BLACK);
        }
        assertFalse(WinChecker.checkWin(board, 7, 10, Board.BLACK),
                "四连不应该获胜");
    }

    @Test
    @DisplayName("被阻断的五连不应该获胜")
    void interruptedFiveShouldNotWin() {
        board.place(7, 6, Board.WHITE);
        for (int i = 0; i < 5; i++) {
            board.place(7, 7 + i, Board.BLACK);
        }
        board.place(7, 12, Board.WHITE);
        assertFalse(WinChecker.checkWin(board, 7, 11, Board.BLACK),
                "被阻断的五连不应该获胜");
    }

    @Test
    @DisplayName("空棋盘不应该判定为平局")
    void emptyBoardShouldNotBeDraw() {
        assertFalse(WinChecker.checkDraw(board), "空棋盘不应该判定为平局");
    }

    @Test
    @DisplayName("满棋盘应该判定为平局")
    void fullBoardShouldBeDraw() {
        for (int i = 0; i < Board.SIZE; i++) {
            for (int j = 0; j < Board.SIZE; j++) {
                board.place(i, j, (i + j) % 2 + 1);
            }
        }
        assertTrue(WinChecker.checkDraw(board), "满棋盘应该判定为平局");
    }

    @Test
    @DisplayName("有空位的棋盘不应该判定为平局")
    void boardWithEmptySpaceShouldNotBeDraw() {
        for (int i = 0; i < Board.SIZE; i++) {
            for (int j = 0; j < Board.SIZE; j++) {
                if (i != 7 || j != 7) {
                    board.place(i, j, (i + j) % 2 + 1);
                }
            }
        }
        assertFalse(WinChecker.checkDraw(board), "有空位的棋盘不应该判定为平局");
    }

    @Test
    @DisplayName("应该获取获胜线位置")
    void shouldGetWinningLine() {
        for (int i = 0; i < 5; i++) {
            board.place(7, 7 + i, Board.BLACK);
        }
        int[] winningLine = WinChecker.getWinningLine(board, 7, 11, Board.BLACK);
        assertNotNull(winningLine, "应该获取到获胜线");
        assertEquals(10, winningLine.length, "获胜线应该包含10个坐标");
    }

    @Test
    @DisplayName("未获胜时应该返回null")
    void shouldReturnNullWhenNoWin() {
        board.place(7, 7, Board.BLACK);
        int[] winningLine = WinChecker.getWinningLine(board, 7, 7, Board.BLACK);
        assertNull(winningLine, "未获胜时应该返回null");
    }
}
