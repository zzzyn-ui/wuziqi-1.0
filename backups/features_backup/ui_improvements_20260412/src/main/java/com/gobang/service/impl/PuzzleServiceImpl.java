package com.gobang.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.mapper.PuzzleMapper;
import com.gobang.model.entity.Puzzle;
import com.gobang.service.PuzzleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 残局服务实现
 */
@Service
public class PuzzleServiceImpl implements PuzzleService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleServiceImpl.class);

    private final PuzzleMapper puzzleMapper;
    private final ObjectMapper objectMapper;

    public PuzzleServiceImpl(PuzzleMapper puzzleMapper) {
        this.puzzleMapper = puzzleMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Puzzle getPuzzleById(Long id) {
        log.debug("获取残局: id={}", id);
        Puzzle puzzle = puzzleMapper.findById(id);
        if (puzzle == null) {
            log.warn("残局不存在: id={}", id);
        }
        return puzzle;
    }

    @Override
    public List<Puzzle> getPuzzlesByDifficulty(String difficulty) {
        log.debug("获取残局列表: difficulty={}", difficulty);
        return puzzleMapper.findByDifficulty(difficulty);
    }

    @Override
    public List<Puzzle> getAllActivePuzzles() {
        log.debug("获取所有活跃残局");
        return puzzleMapper.findAllActive();
    }

    @Override
    public List<Puzzle> getPuzzlesByType(String puzzleType) {
        log.debug("获取残局列表: puzzleType={}", puzzleType);
        return puzzleMapper.findByType(puzzleType);
    }

    @Override
    public Map<String, Long> getDifficultyStats() {
        log.debug("获取残局难度统计");
        List<Map<String, Object>> stats = puzzleMapper.countByDifficulty();
        Map<String, Long> result = new HashMap<>();

        // 初始化所有难度为0
        result.put("easy", 0L);
        result.put("medium", 0L);
        result.put("hard", 0L);
        result.put("expert", 0L);

        // 填充实际数据
        for (Map<String, Object> stat : stats) {
            String difficulty = (String) stat.get("difficulty");
            Long count = ((Number) stat.get("count")).longValue();
            if (difficulty != null) {
                result.put(difficulty, count);
            }
        }

        return result;
    }

    @Override
    public int[][] parseBoardState(String boardState) {
        int[][] board = new int[15][15];

        if (boardState == null || boardState.isEmpty()) {
            return board;
        }

        // boardState是15行，每行15个字符
        String[] rows = boardState.split("\n");
        if (rows.length != 15) {
            // 如果没有换行符，尝试按固定长度分割
            if (boardState.length() >= 225) {
                for (int i = 0; i < 15; i++) {
                    String row = boardState.substring(i * 15, (i + 1) * 15);
                    for (int j = 0; j < 15; j++) {
                        char c = row.charAt(j);
                        if (c == 'B') {
                            board[i][j] = 1;  // 黑子
                        } else if (c == 'W') {
                            board[i][j] = 2;  // 白子
                        }
                    }
                }
            }
            return board;
        }

        for (int i = 0; i < Math.min(15, rows.length); i++) {
            String row = rows[i].trim();
            for (int j = 0; j < Math.min(15, row.length()); j++) {
                char c = row.charAt(j);
                if (c == 'B') {
                    board[i][j] = 1;  // 黑子
                } else if (c == 'W') {
                    board[i][j] = 2;  // 白子
                }
            }
        }

        return board;
    }

    @Override
    public String formatBoardState(int[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                if (board[i][j] == 1) {
                    sb.append('B');
                } else if (board[i][j] == 2) {
                    sb.append('W');
                } else {
                    sb.append('.');
                }
            }
            if (i < 14) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public Map<String, Object> submitPuzzle(Long puzzleId, Long userId, List<int[]> moves) {
        log.info("提交残局答案: puzzleId={}, userId={}, moves={}", puzzleId, userId, moves.size());

        Map<String, Object> result = new HashMap<>();

        try {
            // 获取残局
            Puzzle puzzle = getPuzzleById(puzzleId);
            if (puzzle == null) {
                result.put("success", false);
                result.put("message", "残局不存在");
                return result;
            }

            // 检查答案
            Map<String, Object> checkResult = checkAnswer(puzzle, moves);
            boolean isCorrect = (Boolean) checkResult.get("correct");

            // 更新统计（简化版本，实际需要数据库表支持）
            result.put("success", true);
            result.put("correct", isCorrect);
            result.put("stars", checkResult.get("stars"));
            result.put("message", isCorrect ? "恭喜！答案正确" : "答案错误，请再试一次");

            log.info("残局答案提交结果: correct={}, stars={}", isCorrect, checkResult.get("stars"));

        } catch (Exception e) {
            log.error("提交残局答案失败", e);
            result.put("success", false);
            result.put("message", "提交失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> checkAnswer(Puzzle puzzle, List<int[]> moves) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 解析标准答案
            List<int[]> solution = new ArrayList<>();
            if (puzzle.getSolution() != null && !puzzle.getSolution().isEmpty()) {
                try {
                    solution = objectMapper.readValue(puzzle.getSolution(), new TypeReference<List<int[]>>() {});
                } catch (Exception e) {
                    log.warn("解析残局答案失败: {}", e.getMessage());
                }
            }

            // 如果没有标准答案，简单检查步数
            if (solution.isEmpty()) {
                int optimalMoves = puzzle.getOptimalMoves() != null ? puzzle.getOptimalMoves() : 5;
                int movesUsed = moves.size();

                boolean isCorrect = movesUsed <= optimalMoves * 2; // 允许2倍步数
                int stars = movesUsed <= optimalMoves ? 3 : (movesUsed <= optimalMoves * 1.5 ? 2 : 1);

                result.put("correct", isCorrect);
                result.put("stars", isCorrect ? stars : 0);
                result.put("movesUsed", movesUsed);
                result.put("optimalMoves", optimalMoves);
                return result;
            }

            // 比较答案
            boolean matches = true;
            int starCount = 3;

            if (moves.size() != solution.size()) {
                matches = false;
                // 根据步数差距给星级
                int diff = Math.abs(moves.size() - solution.size());
                starCount = Math.max(0, 3 - diff);
            } else {
                // 比较每一步
                for (int i = 0; i < Math.min(moves.size(), solution.size()); i++) {
                    int[] move = moves.get(i);
                    int[] sol = solution.get(i);
                    if (move.length < 2 || sol.length < 2 ||
                        move[0] != sol[0] || move[1] != sol[1]) {
                        matches = false;
                        starCount = Math.max(0, 3 - (solution.size() - i));
                        break;
                    }
                }
            }

            result.put("correct", matches);
            result.put("stars", matches ? 3 : starCount);
            result.put("movesUsed", moves.size());
            result.put("optimalMoves", solution.size());

        } catch (Exception e) {
            log.error("检查答案失败", e);
            result.put("correct", false);
            result.put("stars", 0);
        }

        return result;
    }
}
