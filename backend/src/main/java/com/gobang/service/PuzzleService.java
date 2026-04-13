package com.gobang.service;

import com.gobang.model.entity.Puzzle;

import java.util.List;
import java.util.Map;

/**
 * 残局服务接口
 */
public interface PuzzleService {

    /**
     * 根据ID获取残局
     */
    Puzzle getPuzzleById(Long id);

    /**
     * 根据难度获取残局列表
     */
    List<Puzzle> getPuzzlesByDifficulty(String difficulty);

    /**
     * 获取所有活跃残局
     */
    List<Puzzle> getAllActivePuzzles();

    /**
     * 根据类型获取残局列表
     */
    List<Puzzle> getPuzzlesByType(String puzzleType);

    /**
     * 获取各难度残局统计
     */
    Map<String, Long> getDifficultyStats();

    /**
     * 解析棋盘状态为二维数组
     */
    int[][] parseBoardState(String boardState);

    /**
     * 将二维数组转换为棋盘状态字符串
     */
    String formatBoardState(int[][] board);

    /**
     * 提交残局答案
     * @param puzzleId 残局ID
     * @param userId 用户ID
     * @param moves 玩家的走法
     * @return 包含成功状态、是否正确、星级等信息的Map
     */
    Map<String, Object> submitPuzzle(Long puzzleId, Long userId, List<int[]> moves);

    /**
     * 检查答案是否正确
     * @param puzzle 残局
     * @param moves 玩家的走法
     * @return 包含是否正确、星级等信息的Map
     */
    Map<String, Object> checkAnswer(Puzzle puzzle, List<int[]> moves);
}
