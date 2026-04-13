package com.gobang.controller;

import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import com.gobang.model.entity.GameRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
    private final GameRecordMapper gameRecordMapper;

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
                userStatsMapper.insertStats(stats);
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
        logger.info("📝 [UPDATE USER] 收到更新请求: payload={}", payload);

        Long userId;
        try {
            userId = Long.valueOf(payload.get("id").toString());
        } catch (Exception e) {
            logger.error("❌ [UPDATE USER] 无法解析用户ID: {}", payload.get("id"));
            return Map.of(
                "code", 400,
                "success", false,
                "message", "用户ID格式错误"
            );
        }

        logger.info("🔍 [UPDATE USER] 开始更新用户信息: userId={}", userId);

        try {
            // 获取现有用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                logger.warn("❌ [UPDATE USER] 用户不存在: userId={}", userId);
                return Map.of(
                    "code", 404,
                    "success", false,
                    "message", "用户不存在"
                );
            }

            logger.info("✅ [UPDATE USER] 找到用户: id={}, username={}, nickname={}",
                    user.getId(), user.getUsername(), user.getNickname());

            // 更新 nickname
            if (payload.containsKey("nickname")) {
                String nickname = payload.get("nickname").toString();
                if (nickname != null && !nickname.trim().isEmpty()) {
                    logger.info("📝 [UPDATE USER] 更新昵称: {} -> {}", user.getNickname(), nickname);
                    user.setNickname(nickname);
                }
            }

            // 更新 username（需要检查唯一性）
            if (payload.containsKey("username")) {
                String newUsername = payload.get("username").toString();
                if (newUsername != null && !newUsername.trim().isEmpty()) {
                    // 检查新用户名是否与当前用户名不同
                    if (!newUsername.equals(user.getUsername())) {
                        // 检查新用户名是否已被其他用户使用
                        User existingUser = userMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                .eq(User::getUsername, newUsername)
                                .ne(User::getId, userId)
                        );
                        if (existingUser != null) {
                            logger.warn("⚠️ [UPDATE USER] 用户名 {} 已被其他用户使用", newUsername);
                            return Map.of(
                                "code", 400,
                                "success", false,
                                "message", "用户名已被使用，请选择其他用户名"
                            );
                        }
                        // 用户名可用，执行更新
                        logger.info("✏️ [UPDATE USER] 用户 {} 修改用户名: {} -> {}", userId, user.getUsername(), newUsername);
                        user.setUsername(newUsername);
                    } else {
                        logger.info("ℹ️ [UPDATE USER] 用户名未改变: {}", newUsername);
                    }
                }
            }

            // 保存更新
            logger.info("💾 [UPDATE USER] 保存更新到数据库...");
            int rows = userMapper.updateById(user);
            logger.info("✅ [UPDATE USER] 数据库更新完成: 影响行数={}", rows);

            return Map.of(
                "code", 200,
                "success", true,
                "data", user,
                "message", "更新成功"
            );

        } catch (Exception e) {
            logger.error("❌ [UPDATE USER] 更新用户信息失败", e);
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

        // 获取走棋数据
        List<Map<String, Object>> movesData = null;
        int moveCount = 0;
        String boardState = null;

        if (payload.containsKey("moves") && payload.get("moves") != null) {
            try {
                Object movesObj = payload.get("moves");
                if (movesObj instanceof List) {
                    movesData = (List<Map<String, Object>>) movesObj;
                    moveCount = movesData.size();
                    logger.info("记录游戏结果: userId={}, result={}, gameMode={}, moveCount={}",
                            userId, result, gameMode, moveCount);
                }
            } catch (Exception e) {
                logger.warn("解析moves数据失败: {}", e.getMessage());
            }
        } else {
            logger.info("记录游戏结果: userId={}, result={}, gameMode={}, 无moves数据", userId, result, gameMode);
        }

        // 获取棋盘状态
        if (payload.containsKey("boardState") && payload.get("boardState") != null) {
            boardState = payload.get("boardState").toString();
        }

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
            userStatsMapper.updateStats(stats);
            userMapper.updateById(user);

            // 创建对局记录
            if (movesData != null && movesData.size() > 0) {
                GameRecord record = new GameRecord();
                record.setRoomId("PVE-" + System.currentTimeMillis());
                record.setBlackPlayerId(userId);
                record.setWhitePlayerId(userId); // AI作为白方
                record.setWinnerId("win".equals(result) ? userId : null);
                record.setWinColor("win".equals(result) ? 1 : null);
                record.setEndReason(0); // 正常结束
                record.setMoveCount(moveCount);
                record.setDuration(0); // PVE没有时长
                record.setBlackRatingBefore(user.getRating());
                record.setBlackRatingAfter(user.getRating() + ratingChange);
                record.setBlackRatingChange(ratingChange);
                record.setWhiteRatingBefore(0);
                record.setWhiteRatingAfter(0);
                record.setWhiteRatingChange(0);
                record.setBoardState(boardState != null ? boardState : serializeBoard(movesData));
                record.setMoves(serializeMoves(movesData));
                record.setGameMode("pve");
                record.setCreatedAt(LocalDateTime.now());

                try {
                    gameRecordMapper.insert(record);
                    logger.info("PVE对局记录已保存: recordId={}, moveCount={}", record.getId(), moveCount);
                } catch (Exception e) {
                    logger.error("保存PVE对局记录失败", e);
                }
            }

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

    /**
     * 序列化棋盘状态
     */
    private String serializeBoard(List<Map<String, Object>> movesData) {
        int[][] board = new int[15][15];
        int color = 1; // 黑方先手
        for (Map<String, Object> move : movesData) {
            int x = (Integer) move.get("x");
            int y = (Integer) move.get("y");
            if (x >= 0 && x < 15 && y >= 0 && y < 15) {
                board[y][x] = color;
            }
            color = color == 1 ? 2 : 1; // 切换颜色
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                sb.append(board[i][j]);
                if (i < 14 || j < 14) {
                    sb.append(",");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 序列化走棋记录为JSON格式
     */
    private String serializeMoves(List<Map<String, Object>> movesData) {
        if (movesData == null || movesData.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < movesData.size(); i++) {
            Map<String, Object> move = movesData.get(i);
            sb.append("{\"x\":").append(move.get("x")).append(",\"y\":").append(move.get("y")).append("}");
            if (i < movesData.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
