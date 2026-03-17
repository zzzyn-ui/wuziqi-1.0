package com.gobang.controller;

import com.gobang.core.room.RoomManager;
import com.gobang.service.GameService;
import com.gobang.service.RoomService;
import com.gobang.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 清理控制器 - 用于清理游戏状态
 */
@RestController
@RequestMapping("/api/cleanup")
public class CleanupController {

    private static final Logger logger = LoggerFactory.getLogger(CleanupController.class);

    private final RedisUtil redisUtil;
    private final RoomManager roomManager;
    private final GameService gameService;
    private final RoomService roomService;

    public CleanupController(RedisUtil redisUtil, RoomManager roomManager,
                             GameService gameService, RoomService roomService) {
        this.redisUtil = redisUtil;
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.roomService = roomService;
    }

    /**
     * 清理所有游戏状态
     */
    @PostMapping("/all")
    @GetMapping("/all")
    public Map<String, Object> cleanupAll() {
        Map<String, Object> result = new HashMap<>();

        try {
            logger.info("=== 开始清理所有游戏状态 ===");

            // 清理匹配队列
            long casualCount = redisUtil.scard("match:q:c");
            long rankedCount = redisUtil.scard("match:q:r");
            redisUtil.del("match:q:c", "match:q:r");

            // 清理所有玩家数据
            Set<String> playerKeys = redisUtil.keys("match:player:*");
            if (playerKeys != null && !playerKeys.isEmpty()) {
                redisUtil.del(playerKeys.toArray(new String[0]));
            }

            // 清理所有房间
            roomManager.getAllRooms().forEach(room -> {
                roomManager.removeRoom(room.getRoomId());
            });

            logger.info("清理完成 - 休闲队列: {}, 竞技队列: {}, 玩家: {}, 房间: {}",
                    casualCount, rankedCount, playerKeys != null ? playerKeys.size() : 0,
                    roomManager.getAllRooms().size());

            result.put("success", true);
            result.put("message", "清理成功");
            result.put("casualQueue", casualCount);
            result.put("rankedQueue", rankedCount);
            result.put("players", playerKeys != null ? playerKeys.size() : 0);
            result.put("rooms", roomManager.getAllRooms().size());

        } catch (Exception e) {
            logger.error("清理失败", e);
            result.put("success", false);
            result.put("message", "清理失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 清理指定用户
     */
    @PostMapping("/user")
    public Map<String, Object> cleanupUser(@RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            logger.info("清理用户状态: userId={}", userId);

            // 从匹配队列移除
            String playerKey = "match:player:" + userId;
            String playerJson = redisUtil.get(playerKey);

            if (playerJson != null) {
                redisUtil.del(playerKey);
                redisUtil.srem("match:q:c", String.valueOf(userId));
                redisUtil.srem("match:q:r", String.valueOf(userId));
                logger.info("从匹配队列移除用户: {}", userId);
            }

            // 从房间移除
            roomManager.getAllRooms().forEach(room -> {
                if (userId.equals(room.getBlackPlayerId()) || userId.equals(room.getWhitePlayerId())) {
                    logger.info("用户 {} 在房间 {} 中", userId, room.getRoomId());
                }
            });

            result.put("success", true);
            result.put("message", "用户清理成功");

        } catch (Exception e) {
            logger.error("清理用户失败", e);
            result.put("success", false);
            result.put("message", "清理失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取当前状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();

        try {
            long casualCount = redisUtil.scard("match:q:c");
            long rankedCount = redisUtil.scard("match:q:r");
            Set<String> playerKeys = redisUtil.keys("match:player:*");

            result.put("casualQueue", casualCount);
            result.put("rankedQueue", rankedCount);
            result.put("playersInQueue", playerKeys != null ? playerKeys.size() : 0);
            result.put("activeRooms", roomManager.getAllRooms().size());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("获取状态失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取用户的未完成游戏
     */
    @GetMapping("/unfinished")
    public Map<String, Object> getUnfinishedGame(@RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> gameInfo = gameService.getUnfinishedGame(userId);

            if (gameInfo != null && !gameInfo.isEmpty()) {
                result.put("success", true);
                result.put("game", gameInfo);

                // 保存房间ID到前端使用
                if (gameInfo.containsKey("room_id")) {
                    // 可以选择保存到会话或其他地方
                }
            } else {
                result.put("success", true);
                result.put("game", null);
            }

        } catch (Exception e) {
            logger.error("获取未完成游戏失败: userId={}", userId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}
