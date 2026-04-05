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
 * 对局记录控制器
 * 处理对局记录相关API
 */
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecordController {

    private static final Logger logger = LoggerFactory.getLogger(RecordController.class);

    private final UserServiceImpl userService;

    /**
     * 获取用户对局记录
     * GET /api/v2/records/{userId}?filter={filter}&limit={limit}
     *
     * @param userId 用户ID
     * @param filter 筛选类型: all(全部), win(胜局), loss(负局), draw(平局)
     * @param limit 限制数量，默认20
     */
    @GetMapping("/records/{userId}")
    public ResponseDto getUserRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "20") int limit
    ) {
        logger.info("获取对局记录: userId={}, filter={}, limit={}", userId, filter, limit);
        try {
            List<Map<String, Object>> records = userService.getUserRecords(userId, filter, limit);
            return ResponseDto.success(records);
        } catch (Exception e) {
            logger.error("获取对局记录失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取对局详情
     * GET /api/v2/records/{userId}/game/{gameId}
     */
    @GetMapping("/records/{userId}/game/{gameId}")
    public ResponseDto getGameRecord(
            @PathVariable Long userId,
            @PathVariable String gameId
    ) {
        logger.info("获取对局详情: userId={}, gameId={}", userId, gameId);
        try {
            Map<String, Object> record = userService.getGameRecord(userId, gameId);
            return ResponseDto.success(record);
        } catch (Exception e) {
            logger.error("获取对局详情失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取对局复盘数据
     * GET /api/v2/replay/{gameId}
     */
    @GetMapping("/replay/{gameId}")
    public ResponseDto getReplayData(@PathVariable String gameId) {
        logger.info("获取复盘数据: gameId={}", gameId);
        try {
            Map<String, Object> replayData = userService.getReplayData(gameId);
            return ResponseDto.success(replayData);
        } catch (Exception e) {
            logger.error("获取复盘数据失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }
}
