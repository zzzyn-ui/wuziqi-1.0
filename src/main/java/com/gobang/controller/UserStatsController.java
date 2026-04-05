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
}
