package com.gobang.controller;

import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户统计控制器
 * 提供用户统计数据接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserStatsController {

    private static final Logger logger = LoggerFactory.getLogger(UserStatsController.class);

    private final UserMapper userMapper;
    private final UserStatsMapper userStatsMapper;

    /**
     * 获取用户统计数据
     * GET /api/user/stats
     */
    @GetMapping("/stats")
    public Map<String, Object> getUserStats(@RequestParam Long userId) {
        logger.info("获取用户统计: userId={}", userId);

        try {
            // 获取用户基本信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Map.of(
                    "code", 404,
                    "success", false,
                    "message", "用户不存在"
                );
            }

            // 获取用户统计
            UserStats stats = userStatsMapper.findByUserId(userId);
            if (stats == null) {
                // 如果没有统计记录，创建一个默认的
                stats = new UserStats(userId);
                userStatsMapper.insert(stats);
            }

            // 组装返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("avatar", user.getAvatar());
            data.put("level", user.getLevel());
            data.put("rating", user.getRating());
            data.put("exp", user.getExp());

            // 统计数据
            int totalGames = stats.getTotalGames();
            int wins = stats.getWins();
            int losses = stats.getLosses();
            int draws = stats.getDraws();
            double winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;

            data.put("totalGames", totalGames);
            data.put("wins", wins);
            data.put("losses", losses);
            data.put("draws", draws);
            data.put("winRate", Math.round(winRate * 10.0) / 10.0);
            data.put("maxRating", stats.getMaxRating());
            data.put("currentStreak", stats.getCurrentStreak());
            data.put("maxStreak", stats.getMaxStreak());
            data.put("online", user.isOnline());

            return Map.of(
                "code", 200,
                "success", true,
                "data", data,
                "message", "获取成功"
            );

        } catch (Exception e) {
            logger.error("获取用户统计失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 更新用户信息
     * POST /api/user/update
     */
    @PostMapping("/update")
    public Map<String, Object> updateUserInfo(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("id").toString());
        logger.info("更新用户信息: userId={}", userId);

        try {
            // 获取现有用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Map.of(
                    "code", 404,
                    "success", false,
                    "message", "用户不存在"
                );
            }

            // 更新字段
            if (payload.containsKey("nickname")) {
                String nickname = payload.get("nickname").toString();
                user.setNickname(nickname);
            }
            if (payload.containsKey("username")) {
                String username = payload.get("username").toString();
                user.setUsername(username);
            }

            // 保存更新
            userMapper.updateById(user);

            return Map.of(
                "code", 200,
                "success", true,
                "data", user,
                "message", "更新成功"
            );

        } catch (Exception e) {
            logger.error("更新用户信息失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "更新失败: " + e.getMessage()
            );
        }
    }

    /**
     * 记录游戏结果（通用，支持所有游戏模式）
     * POST /api/user/game/record
     */
    @PostMapping("/game/record")
    public Map<String, Object> recordGame(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        String result = payload.get("result").toString(); // win, lose, draw
        String gameMode = payload.containsKey("gameMode") ? payload.get("gameMode").toString() : "PVE";
        logger.info("记录游戏结果: userId={}, result={}, gameMode={}", userId, result, gameMode);

        try {
            // 获取用户和统计
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Map.of(
                    "code", 404,
                    "success", false,
                    "message", "用户不存在"
                );
            }

            UserStats stats = userStatsMapper.findByUserId(userId);
            if (stats == null) {
                stats = new UserStats(userId);
            }

            // 更新统计数据
            stats.setTotalGames(stats.getTotalGames() + 1);

            // 根据游戏模式计算积分变化
            int ratingChange = 0;
            boolean isRanked = "RANKED".equals(gameMode);

            // 只有竞技模式才计算积分变化
            if (!isRanked) {
                // PVE、休闲PVP、房间对战都不改变积分，只记录战局
                ratingChange = 0;
            }

            if ("win".equals(result)) {
                stats.setWins(stats.getWins() + 1);
                if (isRanked) {
                    ratingChange = 15; // 竞技模式胜利+15分
                }
                stats.setCurrentStreak(stats.getCurrentStreak() > 0 ? stats.getCurrentStreak() + 1 : 1);
            } else if ("lose".equals(result)) {
                stats.setLosses(stats.getLosses() + 1);
                if (isRanked) {
                    ratingChange = -15; // 竞技模式失败-15分
                }
                stats.setCurrentStreak(stats.getCurrentStreak() < 0 ? stats.getCurrentStreak() - 1 : -1);
            } else {
                stats.setDraws(stats.getDraws() + 1);
                ratingChange = 0; // 平局不变
            }

            // 更新最大连胜
            if (stats.getCurrentStreak() > stats.getMaxStreak()) {
                stats.setMaxStreak(stats.getCurrentStreak());
            }

            // 更新用户积分
            user.setRating(user.getRating() + ratingChange);

            // 更新最大积分
            if (user.getRating() > stats.getMaxRating()) {
                stats.setMaxRating(user.getRating());
            }

            // 保存到数据库
            userStatsMapper.update(stats);
            userMapper.updateById(user);

            return Map.of(
                "code", 200,
                "success", true,
                "message", "记录成功",
                "data", Map.of(
                    "rating", user.getRating(),
                    "level", user.getLevel(),
                    "exp", user.getExp(),
                    "ratingChange", ratingChange
                )
            );

        } catch (Exception e) {
            logger.error("记录游戏结果失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "记录失败: " + e.getMessage()
            );
        }
    }

    /**
     * 记录PVE游戏结果（保持兼容性）
     * POST /api/user/pve/record
     */
    @PostMapping("/pve/record")
    public Map<String, Object> recordPVEGame(@RequestBody Map<String, Object> payload) {
        // 转换为通用格式
        Map<String, Object> gameData = new HashMap<>(payload);
        gameData.put("gameMode", "PVE");
        return recordGame(gameData);
    }
}
