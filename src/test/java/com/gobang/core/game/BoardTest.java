package com.gobang.core.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 棋盘测试类
 */
@DisplayName("棋盘测试")
class BoardTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    @Test
    @DisplayName("新棋盘应该为空")
    void newBoardShouldBeEmpty() {
        for (int i = 0; i < Board.SIZE; i++) {
            for (int j = 0; j < Board.SIZE; j++) {
                assertEquals(Board.EMPTY, board.getCell(i, j),
                        "新棋盘所有位置应该为空");
            }
        }
    }

    @Test
    @DisplayName("应该能够放置棋子")
    void shouldPlacePiece() {
        assertTrue(board.place(7, 7, Board.BLACK), "应该成功放置黑子");
        assertEquals(Board.BLACK, board.getCell(7, 7), "位置(7,7)应该是黑子");
    }

    @Test
    @DisplayName("不应该在同一位置重复放置")
    void shouldNotPlaceAtSamePosition() {
        board.place(7, 7, Board.BLACK);
        assertFalse(board.place(7, 7, Board.WHITE), "不应该在同一位置重复放置");
    }

    @Test
    @DisplayName("不应该在无效位置放置棋子")
    void shouldNotPlaceAtInvalidPosition() {
        assertFalse(board.place(-1, 7, Board.BLACK), "不应该在负坐标放置");
        assertFalse(board.place(7, -1, Board.BLACK), "不应该在负坐标放置");
        assertFalse(board.place(Board.SIZE, 7, Board.BLACK), "不应该在超出边界的位置放置");
        assertFalse(board.place(7, Board.SIZE, Board.BLACK), "不应该在超出边界的位置放置");
    }

    @Test
    @DisplayName("应该正确判断位置是否为空")
    void shouldCheckIsEmpty() {
        assertTrue(board.isEmpty(7, 7), "空位置应该返回true");
        board.place(7, 7, Board.BLACK);
        assertFalse(board.isEmpty(7, 7), "有棋子的位置应该返回false");
    }

    @Test
    @DisplayName("应该正确判断位置是否有效")
    void shouldCheckIsValidPosition() {
        assertTrue(board.isValidPosition(0, 0), "边界内位置应该有效");
        assertTrue(board.isValidPosition(14, 14), "边界内位置应该有效");
        assertFalse(board.isValidPosition(-1, 0), "负坐标应该无效");
        assertFalse(board.isValidPosition(0, -1), "负坐标应该无效");
        assertFalse(board.isValidPosition(Board.SIZE, 0), "超出边界应该无效");
        assertFalse(board.isValidPosition(0, Board.SIZE), "超出边界应该无效");
    }

    @Test
    @DisplayName("应该能够清空棋盘")
    void shouldClearBoard() {
        board.place(7, 7, Board.BLACK);
        board.place(7, 8, Board.WHITE);
        board.clear();
        for (int i = 0; i < Board.SIZE; i++) {
            for (int j = 0; j < Board.SIZE; j++) {
                assertEquals(Board.EMPTY, board.getCell(i, j), "清空后所有位置应该为空");
            }
        }
    }

    @Test
    @DisplayName("应该正确转换为一维数组")
    void shouldConvertToArray() {
        board.place(0, 0, Board.BLACK);
        board.place(0, 1, Board.WHITE);
        board.place(14, 14, Board.BLACK);

        int[] array = board.toArray();
        assertEquals(Board.SIZE * Board.SIZE, array.length, "数组长度应该正确");
        assertEquals(Board.BLACK, array[0], "位置(0,0)应该是黑子");
        assertEquals(Board.WHITE, array[1], "位置(0,1)应该是白子");
        assertEquals(Board.BLACK, array[array.length - 1], "位置(14,14)应该是黑子");
    }

    @Test
    @DisplayName("应该能从一维数组恢复棋盘")
    void shouldRestoreFromArray() {
        int[] array = new int[Board.SIZE * Board.SIZE];
        array[0] = Board.BLACK;
        array[1] = Board.WHITE;
        array[array.length - 1] = Board.BLACK;

        board.fromArray(array);
        assertEquals(Board.BLACK, board.getCell(0, 0), "恢复后位置(0,0)应该是黑子");
        assertEquals(Board.WHITE, board.getCell(0, 1), "恢复后位置(0,1)应该是白子");
        assertEquals(Board.BLACK, board.getCell(14, 14), "恢复后位置(14,14)应该是黑子");
    }

    @Test
    @DisplayName("复制构造函数应该创建独立副本")
    void copyConstructorShouldCreateIndependentCopy() {
        board.place(7, 7, Board.BLACK);
        Board copy = new Board(board);

        assertEquals(board.getCell(7, 7), copy.getCell(7, 7), "副本应该有相同的棋子");

        copy.place(7, 8, Board.WHITE);
        assertNotEquals(board.getCell(7, 8), copy.getCell(7, 8),
                "修改副本不应该影响原棋盘");
    }

    @Test
    @DisplayName("应该能够清除指定位置的棋子")
    void shouldClearSpecificCell() {
        board.place(7, 7, Board.BLACK);
        board.clearCell(7, 7);
        assertEquals(Board.EMPTY, board.getCell(7, 7), "清除后位置应该为空");
    }
}
