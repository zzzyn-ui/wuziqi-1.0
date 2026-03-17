package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import io.netty.channel.Channel;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对局记录服务
 * 负责保存和管理游戏对局数据
 */
public class GameRecordService {

    private static final Logger logger = LoggerFactory.getLogger(GameRecordService.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final ObjectMapper objectMapper;

    // 游戏结束原因枚举
    public static final int END_REASON_WIN = 0;        // 正常胜利
    public static final int END_REASON_LOSE = 1;       // 失败
    public static final int END_REASON_DRAW = 2;       // 平局
    public static final int END_REASON_RESIGN = 3;     // 认输
    public static final int END_REASON_TIMEOUT = 4;    // 超时

    public GameRecordService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 保存对局记录
     *
     * @param roomId 房间ID
     * @param blackPlayerId 黑方玩家ID
     * @param whitePlayerId 白方玩家ID
     * @param winnerId 获胜者ID (null表示平局)
     * @param winColor 获胜方颜色 (1=黑, 2=白, null=平局)
     * @param endReason 结束原因
     * @param boardState 棋盘状态数组
     * @param moves 落子记录列表
     * @param duration 对局时长(秒)
     * @param isCasual 是否为休闲模式(不计分)
     * @return 保存的对局记录ID
     */
    public Long saveGameRecord(String roomId, Long blackPlayerId, Long whitePlayerId,
                               Long winnerId, Integer winColor, int endReason,
                               int[] boardState, List<int[]> moves, int duration, boolean isCasual) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);

            // 获取玩家信息
            User blackPlayer = userMapper.findById(blackPlayerId);
            User whitePlayer = userMapper.findById(whitePlayerId);

            if (blackPlayer == null || whitePlayer == null) {
                logger.error("Player not found: black={}, white={}", blackPlayerId, whitePlayerId);
                return null;
            }

            // 计算积分变化
            int blackRatingBefore = blackPlayer.getRating();
            int whiteRatingBefore = whitePlayer.getRating();
            int blackRatingAfter = blackRatingBefore;
            int whiteRatingAfter = whiteRatingBefore;
            int blackRatingChange = 0;
            int whiteRatingChange = 0;

            // 只有竞技模式才计算积分变化
            if (!isCasual && winnerId != null) {
                RatingChange ratingChange = calculateRatingChange(
                    blackRatingBefore, whiteRatingBefore, winColor);

                if (winColor == 1) {
                    // 黑方胜
                    blackRatingAfter = blackRatingBefore + ratingChange.blackChange;
                    whiteRatingAfter = whiteRatingBefore + ratingChange.whiteChange;
                    blackRatingChange = ratingChange.blackChange;
                    whiteRatingChange = ratingChange.whiteChange;
                } else {
                    // 白方胜
                    blackRatingAfter = blackRatingBefore + ratingChange.whiteChange;
                    whiteRatingAfter = whiteRatingBefore + ratingChange.blackChange;
                    blackRatingChange = ratingChange.whiteChange;
                    whiteRatingChange = ratingChange.blackChange;
                }
            }

            // 创建对局记录
            GameRecord record = new GameRecord();
            record.setRoomId(roomId);
            record.setBlackPlayerId(blackPlayerId);
            record.setWhitePlayerId(whitePlayerId);
            record.setWinnerId(winnerId);
            record.setWinColor(winColor);
            record.setEndReason(endReason);
            record.setMoveCount(moves != null ? moves.size() : 0);
            record.setDuration(duration);
            record.setBlackRatingBefore(blackRatingBefore);
            record.setBlackRatingAfter(blackRatingAfter);
            record.setBlackRatingChange(blackRatingChange);
            record.setWhiteRatingBefore(whiteRatingBefore);
            record.setWhiteRatingAfter(whiteRatingAfter);
            record.setWhiteRatingChange(whiteRatingChange);
            record.setCreatedAt(LocalDateTime.now());

            // 序列化棋盘状态和落子记录
            try {
                record.setBoardState(serializeBoard(boardState));
                record.setMoves(serializeMoves(moves));
            } catch (Exception e) {
                logger.error("Failed to serialize game data", e);
            }

            // 保存对局记录
            recordMapper.insert(record);

            // 更新玩家积分
            if (!isCasual) {
                userMapper.updateRating(blackPlayerId, blackRatingAfter,
                    blackPlayer.getLevel(), blackPlayer.getExp());
                userMapper.updateRating(whitePlayerId, whiteRatingAfter,
                    whitePlayer.getLevel(), whitePlayer.getExp());
            }

            // 更新玩家统计
            updatePlayerStats(session, statsMapper, blackPlayerId, winnerId, 1, winColor,
                moves != null ? moves.size() : 0, duration, isCasual);
            updatePlayerStats(session, statsMapper, whitePlayerId, winnerId, 2, winColor,
                moves != null ? moves.size() : 0, duration, isCasual);

            session.commit();

            logger.info("Game record saved: roomId={}, winner={}, duration={}s",
                roomId, winnerId, duration);

            return record.getId();
        } catch (Exception e) {
            logger.error("Failed to save game record", e);
            return null;
        }
    }

    /**
     * 获取对局记录
     */
    public GameRecord getGameRecord(Long recordId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            return recordMapper.findById(recordId);
        }
    }

    /**
     * 根据房间ID获取对局记录
     */
    public GameRecord getGameRecordByRoomId(String roomId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            return recordMapper.findByRoomId(roomId);
        }
    }

    /**
     * 获取用户的对局历史
     */
    public List<GameRecord> getUserGameHistory(Long userId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            return recordMapper.findByUserId(userId, limit);
        }
    }

    /**
     * 获取用户的游戏统计信息
     */
    public Map<String, Object> getUserGameStats(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);

            User user = userMapper.findById(userId);
            UserStats stats = statsMapper.findByUserId(userId);
            int totalGames = recordMapper.countByUserId(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("stats", stats);
            result.put("totalGames", totalGames);

            if (stats != null) {
                result.put("winRate", stats.getWinRate());
            }

            return result;
        }
    }

    /**
     * 计算积分变化 (基于ELO算法)
     */
    private RatingChange calculateRatingChange(int blackRating, int whiteRating, Integer winColor) {
        // ELO算法参数
        final int K_FACTOR = 32;
        final double EXPECTED_SCORE = 1.0 / (1.0 + Math.pow(10, (whiteRating - blackRating) / 400.0));

        int blackChange, whiteChange;

        if (winColor == 1) {
            // 黑方胜
            blackChange = (int) Math.round(K_FACTOR * (1 - EXPECTED_SCORE));
            whiteChange = (int) Math.round(K_FACTOR * (0 - (1 - EXPECTED_SCORE)));
        } else {
            // 白方胜
            blackChange = (int) Math.round(K_FACTOR * (0 - EXPECTED_SCORE));
            whiteChange = (int) Math.round(K_FACTOR * (1 - (1 - EXPECTED_SCORE)));
        }

        // 限制单场积分变化范围
        final int MAX_CHANGE = 32;
        blackChange = Math.max(-MAX_CHANGE, Math.min(MAX_CHANGE, blackChange));
        whiteChange = Math.max(-MAX_CHANGE, Math.min(MAX_CHANGE, whiteChange));

        return new RatingChange(blackChange, whiteChange);
    }

    /**
     * 更新玩家统计
     */
    private void updatePlayerStats(SqlSession session, UserStatsMapper statsMapper,
                                   Long userId, Long winnerId, int playerColor,
                                   Integer winColor, int moveCount, int duration, boolean isCasual) {
        UserStats stats = statsMapper.findByUserId(userId);
        if (stats == null) {
            stats = new UserStats(userId);
            statsMapper.insert(stats);
        }

        // 计算胜负
        int winAdd = 0;
        int lossAdd = 0;
        int drawAdd = 0;
        int streak = stats.getCurrentStreak();

        if (winnerId == null) {
            // 平局
            drawAdd = 1;
            streak = 0;
        } else if (winnerId.equals(userId)) {
            // 胜利
            winAdd = 1;
            streak = streak > 0 ? streak + 1 : 1;
            if (stats.getMaxStreak() == null || streak > stats.getMaxStreak()) {
                stats.setMaxStreak(streak);
            }
        } else {
            // 失败
            lossAdd = 1;
            streak = streak < 0 ? streak - 1 : -1;
        }

        // 更新统计
        statsMapper.updateGameStats(userId, winAdd, lossAdd, drawAdd, moveCount, streak);

        // 更新最高积分
        User user = session.getMapper(UserMapper.class).findById(userId);
        if (user != null && stats.getMaxRating() < user.getRating()) {
            stats.setMaxRating(user.getRating());
            statsMapper.update(stats);
        }
    }

    /**
     * 序列化棋盘状态
     */
    private String serializeBoard(int[] board) {
        if (board == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < board.length; i++) {
            sb.append(board[i]);
            if (i < board.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    /**
     * 序列化落子记录
     */
    private String serializeMoves(List<int[]> moves) {
        if (moves == null || moves.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(moves);
        } catch (Exception e) {
            logger.error("Failed to serialize moves", e);
            return "[]";
        }
    }

    /**
     * 积分变化结果
     */
    private static class RatingChange {
        final int blackChange;
        final int whiteChange;

        RatingChange(int blackChange, int whiteChange) {
            this.blackChange = blackChange;
            this.whiteChange = whiteChange;
        }
    }
}
