package com.gobang.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对局记录控制器
 * 提供对局记录查询和复盘功能
 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameRecordController {

    private static final Logger logger = LoggerFactory.getLogger(GameRecordController.class);

    private final GameRecordMapper gameRecordMapper;
    private final UserMapper userMapper;

    /**
     * 获取对局记录列表
     * GET /api/records
     */
    @GetMapping
    public Map<String, Object> getRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        logger.info("获取对局记录: userId={}, result={}, page={}, pageSize={}", userId, result, page, pageSize);

        try {
            // 创建分页对象
            Page<GameRecord> pageParam = new Page<>(page, pageSize);

            // 构建查询条件
            LambdaQueryWrapper<GameRecord> wrapper = new LambdaQueryWrapper<>();

            if (userId != null) {
                // 查询该用户参与的所有对局
                wrapper.and(w -> w.eq(GameRecord::getBlackPlayerId, userId)
                        .or()
                        .eq(GameRecord::getWhitePlayerId, userId));
            }

            // 按创建时间倒序
            wrapper.orderByDesc(GameRecord::getCreatedAt);

            // 执行分页查询
            IPage<GameRecord> recordPage = gameRecordMapper.selectPage(pageParam, wrapper);

            // 转换为VO
            List<Map<String, Object>> records = recordPage.getRecords().stream()
                    .map(record -> convertToVO(record, userId))
                    .collect(Collectors.toList());

            // 如果指定了result过滤，在内存中过滤
            if (result != null && !result.isEmpty() && !result.equals("all")) {
                final String filterResult = result;
                records = records.stream()
                        .filter(record -> {
                            String recordResult = (String) record.get("result");
                            if (filterResult.equals("win")) return "WIN".equals(recordResult);
                            if (filterResult.equals("loss")) return "LOSS".equals(recordResult);
                            if (filterResult.equals("draw")) return "DRAW".equals(recordResult);
                            return true;
                        })
                        .collect(Collectors.toList());
            }

            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "list", records,
                    "total", recordPage.getTotal(),
                    "page", page,
                    "pageSize", pageSize
                ),
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
     * 获取对局详情（用于复盘）
     * GET /api/records/{id}
     */
    @GetMapping("/{id}")
    public Map<String, Object> getRecordDetail(@PathVariable Long id) {
        logger.info("获取对局详情: id={}", id);

        try {
            GameRecord record = gameRecordMapper.selectById(id);
            if (record == null) {
                return Map.of(
                    "code", 404,
                    "success", false,
                    "message", "对局记录不存在"
                );
            }

            Map<String, Object> detail = convertToDetailVO(record);

            return Map.of(
                "code", 200,
                "success", true,
                "data", detail,
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
     * 转换为列表VO
     */
    private Map<String, Object> convertToVO(GameRecord record, Long currentUserId) {
        Map<String, Object> vo = new HashMap<>();

        // 确定对手
        boolean isBlack = record.getBlackPlayerId().equals(currentUserId);
        Long opponentId = isBlack ? record.getWhitePlayerId() : record.getBlackPlayerId();
        User opponent = userMapper.selectById(opponentId);

        // 确定结果
        String result;
        if (record.getWinnerId() == null) {
            result = "DRAW";
        } else if (record.getWinnerId().equals(currentUserId)) {
            result = "WIN";
        } else {
            result = "LOSS";
        }

        // 计算积分变化
        int ratingChange = isBlack ? record.getBlackRatingChange() : record.getWhiteRatingChange();

        vo.put("id", record.getId());
        vo.put("result", result);
        vo.put("mode", getModeText(record.getEndReason()));
        vo.put("gameTime", record.getCreatedAt());
        vo.put("duration", formatDuration(record.getDuration()));
        vo.put("ratingChange", ratingChange);
        vo.put("moveCount", record.getMoveCount());

        // 对手信息
        Map<String, Object> opponentInfo = new HashMap<>();
        opponentInfo.put("id", opponent.getId());
        opponentInfo.put("username", opponent.getUsername());
        opponentInfo.put("nickname", opponent.getNickname());
        opponentInfo.put("avatar", opponent.getAvatar());
        opponentInfo.put("level", opponent.getLevel());
        opponentInfo.put("rating", opponent.getRating());
        vo.put("opponent", opponentInfo);

        return vo;
    }

    /**
     * 转换为详情VO
     */
    private Map<String, Object> convertToDetailVO(GameRecord record) {
        Map<String, Object> vo = new HashMap<>();

        vo.put("id", record.getId());
        vo.put("roomId", record.getRoomId());
        vo.put("mode", getModeText(record.getEndReason()));
        vo.put("gameMode", record.getGameMode());
        vo.put("duration", record.getDuration());
        vo.put("moveCount", record.getMoveCount());
        vo.put("createdAt", record.getCreatedAt());

        // 解析落子记录
        List<Map<String, Object>> moves = parseMoves(record.getMoves());
        vo.put("moves", moves);

        // 黑方信息
        User blackPlayer = userMapper.selectById(record.getBlackPlayerId());
        Map<String, Object> blackInfo = new HashMap<>();
        blackInfo.put("id", blackPlayer.getId());
        blackInfo.put("username", blackPlayer.getUsername());
        blackInfo.put("nickname", blackPlayer.getNickname() != null ? blackPlayer.getNickname() : blackPlayer.getUsername());
        blackInfo.put("avatar", blackPlayer.getAvatar());
        blackInfo.put("rating", record.getBlackRatingBefore());
        blackInfo.put("ratingChange", record.getBlackRatingChange());
        vo.put("black", blackInfo);

        // 白方信息
        User whitePlayer = userMapper.selectById(record.getWhitePlayerId());
        Map<String, Object> whiteInfo = new HashMap<>();
        whiteInfo.put("id", whitePlayer.getId());
        whiteInfo.put("username", whitePlayer.getUsername());
        whiteInfo.put("nickname", whitePlayer.getNickname() != null ? whitePlayer.getNickname() : whitePlayer.getUsername());
        whiteInfo.put("avatar", whitePlayer.getAvatar());
        whiteInfo.put("rating", record.getWhiteRatingBefore());
        whiteInfo.put("ratingChange", record.getWhiteRatingChange());
        vo.put("white", whiteInfo);

        // 胜者信息
        if (record.getWinnerId() != null) {
            vo.put("winnerId", record.getWinnerId());
            vo.put("winColor", record.getWinColor() == 1 ? "black" : "white");
        } else {
            vo.put("winnerId", null);
            vo.put("winColor", null);
        }

        return vo;
    }

    /**
     * 解析落子记录JSON
     */
    private List<Map<String, Object>> parseMoves(String movesJson) {
        List<Map<String, Object>> moves = new ArrayList<>();

        if (movesJson == null || movesJson.isEmpty()) {
            return moves;
        }

        try {
            // 简单解析：假设格式为 [{"x":7,"y":7,"color":1},...]
            // 实际应该使用JSON库（Jackson/Gson）
            if (movesJson.startsWith("[") && movesJson.endsWith("]")) {
                // 去除外层方括号
                String content = movesJson.substring(1, movesJson.length() - 1);

                // 分割每个对象
                String[] objects = content.split("\\},\\{");

                for (String obj : objects) {
                    if (obj.trim().isEmpty()) continue;

                    // 清理并添加回花括号
                    String cleanObj = obj.trim();
                    if (!cleanObj.startsWith("{")) {
                        cleanObj = "{" + cleanObj;
                    }
                    if (!cleanObj.endsWith("}")) {
                        cleanObj = cleanObj + "}";
                    }

                    // 解析x, y, color
                    Map<String, Object> move = new HashMap<>();
                    String[] parts = cleanObj.replaceAll("[{}\"]", "").split(",");

                    for (String part : parts) {
                        String[] keyValue = part.split(":");
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim();
                            String value = keyValue[1].trim();

                            switch (key) {
                                case "x" -> move.put("x", Integer.parseInt(value));
                                case "y" -> move.put("y", Integer.parseInt(value));
                                case "color" -> move.put("color", Integer.parseInt(value));
                            }
                        }
                    }

                    if (move.containsKey("x") && move.containsKey("y")) {
                        moves.add(move);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析落子记录失败: {}", e.getMessage());
        }

        return moves;
    }

    /**
     * 获取模式文本
     */
    private String getModeText(Integer endReason) {
        if (endReason == null) return "CLASSIC";
        // 根据结束原因判断模式
        return switch (endReason) {
            case 0, 1, 2 -> "CLASSIC";
            case 4 -> "BLITZ";
            default -> "CLASSIC";
        };
    }

    /**
     * 格式化时长
     */
    private String formatDuration(Integer seconds) {
        if (seconds == null) return "0分钟";
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "分钟";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        return hours + "小时" + remainingMinutes + "分钟";
    }
}
