package com.gobang.controller;

import com.gobang.model.entity.Puzzle;
import com.gobang.service.PuzzleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 残局控制器
 */
@RestController
@RequestMapping("/api/puzzle")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PuzzleController {

    private static final Logger logger = LoggerFactory.getLogger(PuzzleController.class);

    private final PuzzleService puzzleService;

    /**
     * 获取残局详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> getPuzzle(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Puzzle puzzle = puzzleService.getPuzzleById(id);
            if (puzzle == null) {
                response.put("success", false);
                response.put("message", "残局不存在");
                return response;
            }

            response.put("success", true);
            response.put("data", puzzle);
        } catch (Exception e) {
            logger.error("获取残局失败", e);
            response.put("success", false);
            response.put("message", "获取残局失败");
        }
        return response;
    }

    /**
     * 根据难度获取残局列表
     */
    @GetMapping("/list")
    public Map<String, Object> getPuzzleList(
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String type
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Puzzle> puzzles;

            if (difficulty != null && !difficulty.isEmpty()) {
                puzzles = puzzleService.getPuzzlesByDifficulty(difficulty);
            } else if (type != null && !type.isEmpty()) {
                puzzles = puzzleService.getPuzzlesByType(type);
            } else {
                puzzles = puzzleService.getAllActivePuzzles();
            }

            response.put("success", true);
            response.put("data", puzzles);
            response.put("count", puzzles.size());
        } catch (Exception e) {
            logger.error("获取残局列表失败", e);
            response.put("success", false);
            response.put("message", "获取残局列表失败");
        }
        return response;
    }

    /**
     * 获取残局统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Long> difficultyStats = puzzleService.getDifficultyStats();

            Map<String, Object> stats = new HashMap<>();
            stats.put("difficulty", difficultyStats);
            stats.put("total", difficultyStats.values().stream().mapToLong(Long::longValue).sum());

            response.put("success", true);
            response.put("data", stats);
        } catch (Exception e) {
            logger.error("获取残局统计失败", e);
            response.put("success", false);
            response.put("message", "获取残局统计失败");
        }
        return response;
    }

    /**
     * 获取难度分类列表
     */
    @GetMapping("/difficulties")
    public Map<String, Object> getDifficulties() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> difficulties = List.of(
                Map.of("value", "easy", "label", "入门级", "icon", "🌱"),
                Map.of("value", "medium", "label", "中级", "icon", "🌿"),
                Map.of("value", "hard", "label", "高级", "icon", "🌳"),
                Map.of("value", "expert", "label", "大师级", "icon", "🏆")
            );

            response.put("success", true);
            response.put("data", difficulties);
        } catch (Exception e) {
            logger.error("获取难度列表失败", e);
            response.put("success", false);
            response.put("message", "获取难度列表失败");
        }
        return response;
    }

    /**
     * 获取残局类型列表
     */
    @GetMapping("/types")
    public Map<String, Object> getTypes() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> types = List.of(
                Map.of("value", "classic", "label", "经典残局", "description", "基础胜利模式"),
                Map.of("value", "four_three", "label", "四三杀法", "description", "五子棋核心杀法"),
                Map.of("value", "double_four", "label", "双四杀法", "description", "最强杀法之一"),
                Map.of("value", "vcf", "label", "VCF", "description", "连续冲四获胜"),
                Map.of("value", "vct", "label", "VCT", "description", "连续威胁获胜"),
                Map.of("value", "strategy", "label", "战术组合", "description", "综合战术运用"),
                Map.of("value", "forbidden", "label", "禁手破解", "description", "破解禁手限制")
            );

            response.put("success", true);
            response.put("data", types);
        } catch (Exception e) {
            logger.error("获取类型列表失败", e);
            response.put("success", false);
            response.put("message", "获取类型列表失败");
        }
        return response;
    }

    /**
     * 提交残局答案
     */
    @PostMapping("/{id}/submit")
    public Map<String, Object> submitPuzzle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        @SuppressWarnings("unchecked")
        List<List<Integer>> movesList = (List<List<Integer>>) payload.get("moves");

        // 转换moves格式
        List<int[]> moves = new java.util.ArrayList<>();
        for (List<Integer> move : movesList) {
            moves.add(new int[]{move.get(0), move.get(1)});
        }

        logger.info("提交残局答案: puzzleId={}, userId={}, moves={}", id, userId, moves.size());

        try {
            Map<String, Object> result = puzzleService.submitPuzzle(id, userId, moves);
            return result;
        } catch (Exception e) {
            logger.error("提交残局答案失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "提交失败: " + e.getMessage());
            return error;
        }
    }
}
