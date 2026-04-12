package com.gobang.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import com.gobang.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对局记录控制器
 */
@RestController
@RequestMapping("/api/record")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecordController {

    private static final Logger logger = LoggerFactory.getLogger(RecordController.class);

    private final GameRecordMapper gameRecordMapper;
    private final UserMapper userMapper;
    private final RecordService recordService;

    /**
     * 获取用户最近的对局记录
     * GET /api/record/list?userId={userId}&days={days}
     *
     * @param userId 用户ID
     * @param days 天数，默认3天
     * @return 对局记录列表
     */
    @GetMapping("/list")
    public Map<String, Object> getRecordList(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "3") int days
    ) {
        logger.info("获取对局记录: userId={}, days={}", userId, days);

        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);

            // 获取对局记录
            List<GameRecord> records = gameRecordMapper.selectList(
                    new LambdaQueryWrapper<GameRecord>()
                            .and(wrapper -> wrapper
                                    .eq(GameRecord::getBlackPlayerId, userId)
                                    .or()
                                    .eq(GameRecord::getWhitePlayerId, userId)
                            )
                            .ge(GameRecord::getCreatedAt, startDate)
                            .orderByDesc(GameRecord::getCreatedAt)
            );

            // 获取用户信息缓存
            Map<Long, User> userCache = new HashMap<>();
            List<Long> userIds = new ArrayList<>();
            for (GameRecord record : records) {
                if (!userIds.contains(record.getBlackPlayerId())) userIds.add(record.getBlackPlayerId());
                if (!userIds.contains(record.getWhitePlayerId())) userIds.add(record.getWhitePlayerId());
            }

            if (!userIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(userIds);
                for (User user : users) {
                    userCache.put(user.getId(), user);
                }
            }

            // 构建响应数据
            List<Map<String, Object>> recordData = records.stream().map(record -> {
                Map<String, Object> data = new HashMap<>();

                User blackPlayer = userCache.get(record.getBlackPlayerId());
                User whitePlayer = userCache.get(record.getWhitePlayerId());

                // 基本信息
                data.put("id", record.getId());
                data.put("roomId", record.getRoomId());
                data.put("gameMode", record.getGameMode());
                data.put("createdAt", record.getCreatedAt().toString());

                // 玩家信息
                data.put("blackPlayer", Map.of(
                        "id", record.getBlackPlayerId(),
                        "nickname", blackPlayer != null ? blackPlayer.getNickname() : "未知",
                        "rating", record.getBlackRatingAfter()
                ));

                data.put("whitePlayer", Map.of(
                        "id", record.getWhitePlayerId(),
                        "nickname", whitePlayer != null ? whitePlayer.getNickname() : "未知",
                        "rating", record.getWhiteRatingAfter()
                ));

                // 对局结果
                data.put("winnerId", record.getWinnerId());
                data.put("winColor", record.getWinColor());
                data.put("endReason", record.getEndReason());
                data.put("moveCount", record.getMoveCount());
                data.put("duration", record.getDuration());

                // 积分变化
                data.put("blackRatingChange", record.getBlackRatingChange());
                data.put("whiteRatingChange", record.getWhiteRatingChange());

                // 是否当前用户获胜
                boolean isWin = record.getWinnerId() != null && record.getWinnerId().equals(userId);
                data.put("isWin", isWin);

                // 对手信息
                Long opponentId = record.getBlackPlayerId().equals(userId) ? record.getWhitePlayerId() : record.getBlackPlayerId();
                User opponent = userCache.get(opponentId);
                data.put("opponent", Map.of(
                        "id", opponentId,
                        "nickname", opponent != null ? opponent.getNickname() : "未知"
                ));

                return data;
            }).collect(Collectors.toList());

            return Map.of(
                    "code", 200,
                    "success", true,
                    "data", recordData,
                    "message", "获取成功"
            );

        } catch (Exception e) {
            logger.error("获取对局记录失败", e);
            return Map.of(
                    "code", 500,
                    "success", false,
                    "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取对局详情用于复盘
     * GET /api/record/{recordId}
     *
     * @param recordId 对局记录ID
     * @return 对局详情
     */
    @GetMapping("/{recordId}")
    public Map<String, Object> getRecordDetail(@PathVariable Long recordId) {
        logger.info("获取对局详情: recordId={}", recordId);

        try {
            GameRecord record = gameRecordMapper.selectById(recordId);
            if (record == null) {
                return Map.of(
                        "code", 404,
                        "success", false,
                        "message", "对局记录不存在"
                );
            }

            // 获取玩家信息
            User blackPlayer = userMapper.selectById(record.getBlackPlayerId());
            User whitePlayer = userMapper.selectById(record.getWhitePlayerId());

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("id", record.getId());
            data.put("roomId", record.getRoomId());
            data.put("gameMode", record.getGameMode());
            data.put("createdAt", record.getCreatedAt().toString());

            // 玩家信息
            data.put("blackPlayer", Map.of(
                    "id", record.getBlackPlayerId(),
                    "nickname", blackPlayer != null ? blackPlayer.getNickname() : "未知",
                    "rating", record.getBlackRatingBefore()
            ));

            data.put("whitePlayer", Map.of(
                    "id", record.getWhitePlayerId(),
                    "nickname", whitePlayer != null ? whitePlayer.getNickname() : "未知",
                    "rating", record.getWhiteRatingBefore()
            ));

            // 对局结果
            data.put("winnerId", record.getWinnerId());
            data.put("winColor", record.getWinColor());
            data.put("endReason", record.getEndReason());
            data.put("moveCount", record.getMoveCount());
            data.put("duration", record.getDuration());

            // 积分变化
            data.put("blackRatingChange", record.getBlackRatingChange());
            data.put("whiteRatingChange", record.getWhiteRatingChange());

            // 棋盘状态和走棋记录
            data.put("boardState", record.getBoardState());
            data.put("moves", record.getMoves());

            return Map.of(
                    "code", 200,
                    "success", true,
                    "data", data,
                    "message", "获取成功"
            );

        } catch (Exception e) {
            logger.error("获取对局详情失败", e);
            return Map.of(
                    "code", 500,
                    "success", false,
                    "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取对局记录统计
     * GET /api/record/stats?userId={userId}
     *
     * @param userId 用户ID
     * @return 统计数据
     */
    @GetMapping("/stats")
    public Map<String, Object> getRecordStats(@RequestParam Long userId) {
        logger.info("获取对局统计: userId={}", userId);

        try {
            Map<String, Object> stats = recordService.getUserStats(userId);

            return Map.of(
                    "code", 200,
                    "success", true,
                    "data", stats,
                    "message", "获取成功"
            );

        } catch (Exception e) {
            logger.error("获取对局统计失败", e);
            return Map.of(
                    "code", 500,
                    "success", false,
                    "message", "获取失败: " + e.getMessage()
            );
        }
    }
}
