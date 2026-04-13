package com.gobang.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import com.gobang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 排行榜控制器
 * 提供排行榜数据接口
 */
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RankController {

    private static final Logger logger = LoggerFactory.getLogger(RankController.class);

    private final UserMapper userMapper;
    private final UserStatsMapper userStatsMapper;
    private final UserService userService;

    /**
     * 获取排行榜数据
     * GET /api/rank/{type}
     *
     * @param type 排行榜类型: all(总榜), daily(日榜), weekly(周榜), monthly(月榜)
     * @return 排行榜数据
     */
    @GetMapping("/{type}")
    @Cacheable(value = "rankList", key = "#type + '_' + #limit")
    public Map<String, Object> getRankList(
            @PathVariable String type,
            @RequestParam(defaultValue = "100") int limit
    ) {
        logger.info("获取排行榜请求: type={}, limit={}", type, limit);

        try {
            // 获取所有用户，按积分排序
            List<User> userList = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .orderByDesc(User::getRating)
                            .last("LIMIT " + limit)
            );

            // 构建排行榜数据
            List<Map<String, Object>> rankData = new ArrayList<>();
            for (User user : userList) {
                // 获取用户统计信息 - 使用DTO避免实体映射问题
                com.gobang.model.dto.UserStatsDTO stats = userStatsMapper.findStatsDTOByUserId(user.getId());

                Map<String, Object> userData = new HashMap<>();

                // 基本信息
                userData.put("id", user.getId());
                userData.put("username", user.getUsername());
                userData.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                userData.put("avatar", user.getAvatar() != null ? user.getAvatar() : "/default-avatar.png");

                // 使用 User 表的积分和等级
                userData.put("level", user.getLevel() != null ? user.getLevel() : 1);
                userData.put("rating", user.getRating() != null ? user.getRating() : 700);
                userData.put("exp", user.getExp() != null ? user.getExp() : 0);

                // 统计信息 - 从 DTO 获取
                if (stats != null) {
                    int totalGames = stats.getTotalGames() != null ? stats.getTotalGames() : 0;
                    int wins = stats.getWins() != null ? stats.getWins() : 0;
                    int losses = stats.getLosses() != null ? stats.getLosses() : 0;
                    int draws = stats.getDraws() != null ? stats.getDraws() : 0;
                    double winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;

                    userData.put("totalGames", totalGames);
                    userData.put("wins", wins);
                    userData.put("losses", losses);
                    userData.put("draws", draws);
                    userData.put("winRate", Math.round(winRate * 10.0) / 10.0); // 保留一位小数
                } else {
                    // 没有统计信息时使用默认值
                    userData.put("totalGames", 0);
                    userData.put("wins", 0);
                    userData.put("losses", 0);
                    userData.put("draws", 0);
                    userData.put("winRate", 0.0);
                }

                rankData.add(userData);
            }

            return Map.of(
                "code", 200,
                "success", true,
                "data", rankData,
                "message", "获取成功",
                "type", type
            );

        } catch (Exception e) {
            logger.error("获取排行榜失败: {}", e.getMessage(), e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取排行榜失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取在线用户数
     * GET /api/rank/online/count
     */
    @GetMapping("/online/count")
    public Map<String, Object> getOnlineCount() {
        try {
            int count = userMapper.countOnlineUsers();
            return Map.of(
                "code", 200,
                "success", true,
                "data", count
            );
        } catch (Exception e) {
            logger.error("获取在线人数失败: {}", e.getMessage());
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取在线人数失败"
            );
        }
    }

    /**
     * 清除排行榜缓存
     * 当游戏结束后调用此方法
     */
    @CacheEvict(value = "rankList", allEntries = true)
    public void evictRankCache() {
        logger.info("排行榜缓存已清除");
    }

    /**
     * 异步更新排行榜
     * 定时任务：每5分钟更新一次排行榜缓存
     */
    @Async
    @Scheduled(fixedRate = 300000) // 5分钟
    public void refreshRankCache() {
        logger.info("开始刷新排行榜缓存...");
        try {
            // 通过调用不同类型的排行榜来预热缓存
            getRankList("all", 100);
            getRankList("daily", 50);
            getRankList("weekly", 50);
            getRankList("monthly", 50);
            logger.info("排行榜缓存刷新完成");
        } catch (Exception e) {
            logger.error("刷新排行榜缓存失败", e);
        }
    }

    /**
     * 游戏结束后清除排行榜缓存
     * 当玩家积分变化时调用
     */
    @CacheEvict(value = "rankList", allEntries = true)
    @PostMapping("/invalidate")
    public Map<String, Object> invalidateRankCache() {
        logger.info("排行榜缓存已手动清除");
        return Map.of(
            "code", 200,
            "success", true,
            "message", "缓存已清除"
        );
    }
}
