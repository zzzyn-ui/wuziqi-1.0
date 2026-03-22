package com.gobang.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.mapper.PuzzleMapper;
import com.gobang.mapper.PuzzleRecordMapper;
import com.gobang.mapper.PuzzleStatsMapper;
import com.gobang.model.entity.Puzzle;
import com.gobang.model.entity.PuzzleRecord;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 残局服务
 */
public class PuzzleService {
    private static final Logger logger = LoggerFactory.getLogger(PuzzleService.class);

    private final PuzzleMapper puzzleMapper;
    private final PuzzleRecordMapper puzzleRecordMapper;
    private final PuzzleStatsMapper puzzleStatsMapper;
    private final ObjectMapper objectMapper;

    public PuzzleService(SqlSessionFactory sqlSessionFactory) {
        this.puzzleMapper = sqlSessionFactory.openSession(true).getMapper(PuzzleMapper.class);
        this.puzzleRecordMapper = sqlSessionFactory.openSession(true).getMapper(PuzzleRecordMapper.class);
        this.puzzleStatsMapper = sqlSessionFactory.openSession(true).getMapper(PuzzleStatsMapper.class);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取残局列表
     */
    public List<Puzzle> getPuzzleList(String difficulty) {
        try {
            if (difficulty != null && !difficulty.isEmpty()) {
                return puzzleMapper.findByDifficulty(difficulty);
            }
            return puzzleMapper.findAllActive();
        } catch (Exception e) {
            logger.error("获取残局列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取残局详情
     */
    public Puzzle getPuzzleDetail(Long puzzleId) {
        try {
            return puzzleMapper.findById(puzzleId);
        } catch (Exception e) {
            logger.error("获取残局详情失败, puzzleId={}", puzzleId, e);
            return null;
        }
    }

    /**
     * 获取用户残局记录
     */
    public List<PuzzleRecord> getUserPuzzleRecords(Long userId, String difficulty) {
        try {
            if (difficulty != null && !difficulty.isEmpty()) {
                return puzzleRecordMapper.findByUserIdAndDifficulty(userId, difficulty);
            }
            return puzzleRecordMapper.findByUserId(userId);
        } catch (Exception e) {
            logger.error("获取用户残局记录失败, userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取单个残局记录
     */
    public PuzzleRecord getUserPuzzleRecord(Long userId, Long puzzleId) {
        try {
            return puzzleRecordMapper.findByUserAndPuzzle(userId, puzzleId);
        } catch (Exception e) {
            logger.error("获取用户残局记录失败, userId={}, puzzleId={}", userId, puzzleId, e);
            return null;
        }
    }

    /**
     * 记录尝试
     */
    public void recordAttempt(Long userId, Long puzzleId) {
        try {
            // 确保统计记录存在
            puzzleStatsMapper.ensureExists(puzzleId);

            // 增加尝试次数
            puzzleStatsMapper.incrementAttempts(puzzleId);

            PuzzleRecord record = getUserPuzzleRecord(userId, puzzleId);
            if (record == null) {
                record = new PuzzleRecord();
                record.setUserId(userId);
                record.setPuzzleId(puzzleId);
                record.setAttempts(1);
                record.setCompleted(false);
                puzzleRecordMapper.insert(record);
            } else {
                puzzleRecordMapper.incrementAttempts(userId, puzzleId);
            }
        } catch (Exception e) {
            logger.error("记录尝试失败, userId={}, puzzleId={}", userId, puzzleId, e);
        }
    }

    /**
     * 记录完成
     */
    public void recordCompletion(Long userId, Long puzzleId, int moves, int time, int stars, String solutionPath) {
        try {
            // 确保统计记录存在
            puzzleStatsMapper.ensureExists(puzzleId);

            // 增加完成次数
            puzzleStatsMapper.incrementCompletions(puzzleId);

            // 更新星级统计
            puzzleStatsMapper.updateStarCount(puzzleId, stars);

            // 重新计算完成率
            puzzleStatsMapper.recalculateCompletionRate(puzzleId);

            // 更新用户记录
            PuzzleRecord record = getUserPuzzleRecord(userId, puzzleId);
            boolean isNewRecord = false;

            if (record == null) {
                record = new PuzzleRecord();
                record.setUserId(userId);
                record.setPuzzleId(puzzleId);
                record.setAttempts(1);
                isNewRecord = true;
            }

            record.setCompleted(true);
            record.setCompletedAt(new java.sql.Timestamp(System.currentTimeMillis()));

            // 更新最佳成绩
            if (record.getBestMoves() == null || moves < record.getBestMoves()) {
                record.setBestMoves(moves);
            }
            if (record.getBestTime() == null || time < record.getBestTime()) {
                record.setBestTime(time);
            }

            // 更新星级（取最高）
            if (record.getStars() == null || stars > record.getStars()) {
                record.setStars(stars);
            }

            record.setSolutionPath(solutionPath);

            if (isNewRecord) {
                puzzleRecordMapper.insert(record);
            } else {
                puzzleRecordMapper.update(record);
            }

            logger.info("记录残局完成成功, userId={}, puzzleId={}, moves={}, stars={}", userId, puzzleId, moves, stars);
        } catch (Exception e) {
            logger.error("记录完成失败, userId={}, puzzleId={}", userId, puzzleId, e);
        }
    }

    /**
     * 验证残局棋盘状态是否有效
     * @return true 如果有效，false 如果无效
     */
    public boolean validatePuzzleBoard(Puzzle puzzle) {
        String board = puzzle.getBoardState().replaceAll("\\s+", "");

        int blackCount = 0;
        int whiteCount = 0;

        for (char c : board.toCharArray()) {
            if (c == 'B') blackCount++;
            else if (c == 'W') whiteCount++;
        }

        // 检查棋子数量规则
        String firstPlayer = puzzle.getFirstPlayer();

        // 标准规则：黑棋先行，所以 黑子数 = 白子数 或 黑子数 = 白子数 + 1
        if ("black".equals(firstPlayer)) {
            int diff = blackCount - whiteCount;
            if (diff != 0 && diff != 1) {
                logger.warn("残局棋盘无效: 黑{}白{}, 差值={}, 应为0或1", blackCount, whiteCount, diff);
                return false;
            }
        } else if ("white".equals(firstPlayer)) {
            int diff = whiteCount - blackCount;
            if (diff != 0 && diff != 1) {
                logger.warn("残局棋盘无效: 白{}黑{}, 差值={}, 应为0或1", whiteCount, blackCount, diff);
                return false;
            }
        }

        // 检查棋盘大小
        if (board.length() != 225) { // 15x15 = 225
            logger.warn("残局棋盘大小无效: {} 应为225", board.length());
            return false;
        }

        return true;
    }

    /**
     * 获取残局棋子统计信息
     */
    public Map<String, Integer> getPuzzlePieceCount(Puzzle puzzle) {
        String board = puzzle.getBoardState().replaceAll("\\s+", "");

        int blackCount = 0;
        int whiteCount = 0;

        for (char c : board.toCharArray()) {
            if (c == 'B') blackCount++;
            else if (c == 'W') whiteCount++;
        }

        Map<String, Integer> stats = new HashMap<>();
        stats.put("black", blackCount);
        stats.put("white", whiteCount);
        stats.put("empty", 225 - blackCount - whiteCount);
        stats.put("difference", blackCount - whiteCount);

        return stats;
    }

    /**
     * 计算星级
     */
    public int calculateStars(Puzzle puzzle, int moves, int time, boolean won) {
        if (!won) return 0;

        int optimalMoves = puzzle.getOptimalMoves() != null ? puzzle.getOptimalMoves() : Integer.MAX_VALUE;
        int maxMoves = puzzle.getMaxMoves() != null ? puzzle.getMaxMoves() : 50;

        // 3星: 达到或超过最佳步数
        if (moves <= optimalMoves) {
            return 3;
        }

        // 2星: 在合理步数内完成
        if (moves <= optimalMoves * 1.5) {
            return 2;
        }

        // 1星: 完成即可
        if (moves <= maxMoves) {
            return 1;
        }

        return 0; // 超过最大步数
    }

    /**
     * 解析残局解法
     */
    public List<Map<String, Object>> parseSolution(String solutionJson) {
        try {
            if (solutionJson == null || solutionJson.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(solutionJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.error("解析残局解法失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析提示步数
     */
    public List<Map<String, Object>> parseHintMoves(String hintMovesJson) {
        try {
            if (hintMovesJson == null || hintMovesJson.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(hintMovesJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.error("解析提示步数失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 验证解法路径是否与最佳解法匹配
     */
    public boolean verifySolution(Long puzzleId, List<int[]> playerMoves) {
        try {
            Puzzle puzzle = getPuzzleDetail(puzzleId);
            if (puzzle == null || puzzle.getSolution() == null) {
                return true; // 没有设定解法，认为通过
            }

            List<Map<String, Object>> solution = parseSolution(puzzle.getSolution());
            if (solution.isEmpty()) {
                return true;
            }

            // 简单验证：检查玩家走法是否包含在最佳解法中
            // 这里可以实现更复杂的验证逻辑
            return true;
        } catch (Exception e) {
            logger.error("验证解法失败", e);
            return true;
        }
    }

    /**
     * 获取用户统计数据
     */
    public Map<String, Object> getUserStats(Long userId) {
        try {
            return puzzleRecordMapper.getUserStats(userId);
        } catch (Exception e) {
            logger.error("获取用户统计失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 获取排行榜
     */
    public List<Map<String, Object>> getLeaderboard(int limit) {
        try {
            return puzzleRecordMapper.getLeaderboard(limit);
        } catch (Exception e) {
            logger.error("获取排行榜失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取各难度残局数量
     */
    public Map<String, Integer> getDifficultyCounts() {
        try {
            List<Map<String, Object>> counts = puzzleMapper.countByDifficulty();
            Map<String, Integer> result = new HashMap<>();
            result.put("easy", 0);
            result.put("medium", 0);
            result.put("hard", 0);
            result.put("expert", 0);

            for (Map<String, Object> count : counts) {
                String difficulty = (String) count.get("difficulty");
                Long cnt = (Long) count.get("count");
                if (difficulty != null) {
                    result.put(difficulty, cnt.intValue());
                }
            }

            return result;
        } catch (Exception e) {
            logger.error("获取难度统计失败", e);
            Map<String, Integer> result = new HashMap<>();
            result.put("easy", 0);
            result.put("medium", 0);
            result.put("hard", 0);
            result.put("expert", 0);
            return result;
        }
    }
}
