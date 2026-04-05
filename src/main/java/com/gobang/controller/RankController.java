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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

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

            // 获取当前时间，用于判断在线状态（5分钟内活跃算在线）
            LocalDateTime onlineThreshold = LocalDateTime.now().minusMinutes(5);

            // 构建排行榜数据
            List<Map<String, Object>> rankData = new ArrayList<>();
            for (User user : userList) {
                Map<String, Object> userData = new HashMap<>();

                // 基本信息
                userData.put("id", user.getId());
                userData.put("username", user.getUsername());
                userData.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                userData.put("avatar", user.getAvatar());
                userData.put("level", user.getLevel());
                userData.put("rating", user.getRating());

                // 在线状态判断：status>0 或 lastOnline在5分钟内
                boolean isOnline = user.isOnline() ||
                    (user.getLastOnline() != null && user.getLastOnline().isAfter(onlineThreshold));
                userData.put("online", isOnline);

                // 获取用户统计信息
                UserStats stats = userStatsMapper.findByUserId(user.getId());
                if (stats != null) {
                    int totalGames = stats.getTotalGames();
                    int wins = stats.getWins();
                    int losses = stats.getLosses();
                    double winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;

                    userData.put("totalGames", totalGames);
                    userData.put("wins", wins);
                    userData.put("losses", losses);
                    userData.put("winRate", Math.round(winRate * 10.0) / 10.0); // 保留一位小数
                } else {
                    userData.put("totalGames", 0);
                    userData.put("wins", 0);
                    userData.put("losses", 0);
                    userData.put("winRate", 0.0);
                }

                rankData.add(userData);
            }

            return Map.of(
                "code", 200,
                "success", true,
                "data", rankData,
                "message", "获取成功"
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
}
