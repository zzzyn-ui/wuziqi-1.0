package com.gobang.controller;

import com.gobang.model.dto.ResponseDto;
import com.gobang.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 残局挑战控制器
 * 处理残局挑战相关API
 */
@RestController
@RequestMapping("/api/v2/puzzles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PuzzleController {

    private static final Logger logger = LoggerFactory.getLogger(PuzzleController.class);

    private final UserServiceImpl userService;

    /**
     * 获取残局列表
     * GET /api/v2/puzzles?difficulty={difficulty}
     *
     * @param difficulty 难度: beginner(初级), intermediate(中级), advanced(高级), expert(专家)
     */
    @GetMapping
    public ResponseDto getPuzzleList(@RequestParam(defaultValue = "beginner") String difficulty) {
        logger.info("获取残局列表: difficulty={}", difficulty);
        try {
            List<Map<String, Object>> puzzles = userService.getPuzzleList(difficulty);
            return ResponseDto.success(puzzles);
        } catch (Exception e) {
            logger.error("获取残局列表失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取残局详情
     * GET /api/v2/puzzles/{id}
     */
    @GetMapping("/{id}")
    public ResponseDto getPuzzleDetail(@PathVariable Long id) {
        logger.info("获取残局详情: puzzleId={}", id);
        try {
            Map<String, Object> puzzle = userService.getPuzzleDetail(id);
            return ResponseDto.success(puzzle);
        } catch (Exception e) {
            logger.error("获取残局详情失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 提交残局答案
     * POST /api/v2/puzzles/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public ResponseDto submitPuzzle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> submission
    ) {
        Long userId = Long.valueOf(submission.get("userId").toString());
        @SuppressWarnings("unchecked")
        List<int[]> moves = (List<int[]>) submission.get("moves");

        logger.info("提交残局答案: puzzleId={}, userId={}", id, userId);
        try {
            boolean success = userService.checkPuzzleAnswer(id, moves);
            if (success) {
                userService.recordPuzzleCompletion(userId, id, true);
                return ResponseDto.success(Map.of("success", true, "message", "挑战成功！"));
            } else {
                userService.recordPuzzleCompletion(userId, id, false);
                return ResponseDto.success(Map.of("success", false, "message", "答案错误，请再试一次"));
            }
        } catch (Exception e) {
            logger.error("提交残局答案失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取用户残局统计
     * GET /api/v2/puzzles/stats/{userId}
     */
    @GetMapping("/stats/{userId}")
    public ResponseDto getUserPuzzleStats(@PathVariable Long userId) {
        logger.info("获取用户残局统计: userId={}", userId);
        try {
            Map<String, Object> stats = userService.getUserPuzzleStats(userId);
            return ResponseDto.success(stats);
        } catch (Exception e) {
            logger.error("获取用户残局统计失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }
}
