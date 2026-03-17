package com.gobang.util;

import com.gobang.mapper.GameRecordMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 数据库工具类
 * 提供数据库状态查询和统计功能
 */
public class DatabaseUtil {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtil.class);

    private final SqlSessionFactory sqlSessionFactory;

    public DatabaseUtil(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 检查数据库连接
     */
    public boolean checkConnection() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.selectOne("SELECT 1");
            return true;
        } catch (Exception e) {
            logger.error("数据库连接检查失败", e);
            return false;
        }
    }

    /**
     * 获取数据库统计信息
     */
    public DatabaseStats getStats() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);

            DatabaseStats stats = new DatabaseStats();

            // 统计用户数
            int totalUsers = countTotalUsers(session);
            stats.setTotalUsers(totalUsers);

            // 统计对局记录数
            int totalGames = countTotalGames(session);
            stats.setTotalGames(totalGames);

            // 统计在线用户数
            int onlineUsers = countOnlineUsers(session);
            stats.setOnlineUsers(onlineUsers);

            // 获取最高积分
            int maxRating = getMaxRating(session);
            stats.setMaxRating(maxRating);

            return stats;
        }
    }

    /**
     * 打印数据库统计信息
     */
    public void printStats() {
        DatabaseStats stats = getStats();

        System.out.println("\n=================================");
        System.out.println("        数据库统计信息");
        System.out.println("=================================");
        System.out.println("总用户数: " + stats.getTotalUsers());
        System.out.println("总对局数: " + stats.getTotalGames());
        System.out.println("在线用户: " + stats.getOnlineUsers());
        System.out.println("最高积分: " + stats.getMaxRating());
        System.out.println("=================================\n");
    }

    /**
     * 打印排行榜 (Top N)
     */
    public void printLeaderboard(int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);

            System.out.println("\n=================================");
            System.out.println("      排行榜 (Top " + limit + ")");
            System.out.println("=================================");

            List<User> leaderboard = userMapper.getLeaderboard(limit);
            for (int i = 0; i < leaderboard.size(); i++) {
                User user = leaderboard.get(i);
                System.out.printf("%2d. %s (积分: %d)%n",
                    i + 1, user.getNickname(), user.getRating());
            }

            System.out.println("=================================\n");
        }
    }

    /**
     * 打印用户详细信息
     */
    public void printUserInfo(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);

            User user = userMapper.findById(userId);
            if (user == null) {
                System.out.println("用户不存在: userId=" + userId);
                return;
            }

            UserStats stats = statsMapper.findByUserId(userId);

            System.out.println("\n=================================");
            System.out.println("        用户信息");
            System.out.println("=================================");
            System.out.println("ID: " + user.getId());
            System.out.println("用户名: " + user.getUsername());
            System.out.println("昵称: " + user.getNickname());
            System.out.println("积分: " + user.getRating());
            System.out.println("等级: " + user.getLevel());
            System.out.println("经验: " + user.getExp());
            System.out.println("状态: " + getStatusText(user.getStatus()));
            System.out.println("创建时间: " + user.getCreatedAt());

            if (stats != null) {
                System.out.println("--- 对局统计 ---");
                System.out.println("总局数: " + stats.getTotalGames());
                System.out.println("胜场: " + stats.getWins());
                System.out.println("负场: " + stats.getLosses());
                System.out.println("平场: " + stats.getDraws());
                System.out.printf("胜率: %.2f%%%n", stats.getWinRate());
                System.out.println("最高积分: " + stats.getMaxRating());
                System.out.println("当前连胜: " + stats.getCurrentStreak());
                System.out.println("最高连胜: " + stats.getMaxStreak());
            }

            System.out.println("=================================\n");
        }
    }

    /**
     * 打印用户的对局历史
     */
    public void printUserHistory(Long userId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);

            List<GameRecord> history = recordMapper.findByUserId(userId, limit);

            System.out.println("\n=================================");
            System.out.println("      对局历史 (最近" + limit + "场)");
            System.out.println("=================================");

            if (history.isEmpty()) {
                System.out.println("暂无对局记录");
            } else {
                for (int i = 0; i < history.size(); i++) {
                    GameRecord record = history.get(i);
                    System.out.printf("%2d. 房间:%s %s vs %s | 结果:%s | 积分变化:%+d%n",
                        i + 1,
                        record.getRoomId(),
                        getShortId(record.getBlackPlayerId()),
                        getShortId(record.getWhitePlayerId()),
                        getWinnerText(record.getWinnerId(), record.getWinColor()),
                        record.getBlackPlayerId().equals(userId)
                            ? record.getBlackRatingChange()
                            : record.getWhiteRatingChange()
                    );
                }
            }

            System.out.println("=================================\n");
        }
    }

    /**
     * 打印对局详情
     */
    public void printGameDetail(String roomId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);

            GameRecord record = recordMapper.findByRoomId(roomId);
            if (record == null) {
                System.out.println("对局记录不存在: roomId=" + roomId);
                return;
            }

            System.out.println("\n=================================");
            System.out.println("        对局详情");
            System.out.println("=================================");
            System.out.println("房间ID: " + record.getRoomId());
            System.out.println("黑方: ID=" + record.getBlackPlayerId() +
                             ", 积分变化: " + record.getBlackRatingChange() +
                             " (" + record.getBlackRatingBefore() + " → " + record.getBlackRatingAfter() + ")");
            System.out.println("白方: ID=" + record.getWhitePlayerId() +
                             ", 积分变化: " + record.getWhiteRatingChange() +
                             " (" + record.getWhiteRatingBefore() + " → " + record.getWhiteRatingAfter() + ")");
            System.out.println("获胜方: " + getWinnerText(record.getWinnerId(), record.getWinColor()));
            System.out.println("结束原因: " + getEndReasonText(record.getEndReason()));
            System.out.println("落子数: " + record.getMoveCount());
            System.out.println("对局时长: " + formatDuration(record.getDuration()));
            System.out.println("对局时间: " + record.getCreatedAt());
            System.out.println("=================================\n");
        }
    }

    // ==================== 辅助方法 ====================

    private int countTotalUsers(SqlSession session) {
        return session.selectOne("SELECT COUNT(*) FROM user");
    }

    private int countTotalGames(SqlSession session) {
        return session.selectOne("SELECT COUNT(*) FROM game_record");
    }

    private int countOnlineUsers(SqlSession session) {
        return session.selectOne("SELECT COUNT(*) FROM user WHERE status > 0");
    }

    private int getMaxRating(SqlSession session) {
        Integer max = session.selectOne("SELECT MAX(rating) FROM user");
        return max != null ? max : 1200;
    }

    private String getShortId(Long id) {
        if (id == null) return "???";
        if (id < 0) return "Bot" + (-id % 1000);
        return "U" + (id % 1000);
    }

    private String getWinnerText(Long winnerId, Integer winColor) {
        if (winnerId == null) return "平局";
        if (winColor == null) return "平局";
        return winColor == 1 ? "黑方胜" : "白方胜";
    }

    private String getEndReasonText(Integer reason) {
        if (reason == null) return "未知";
        switch (reason) {
            case 0: return "胜利";
            case 1: return "失败";
            case 2: return "平局";
            case 3: return "认输";
            case 4: return "超时";
            default: return "其他(" + reason + ")";
        }
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "离线";
            case 1: return "在线";
            case 2: return "游戏中";
            case 3: return "匹配中";
            default: return "其他(" + status + ")";
        }
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null) return "未知";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + "分" + secs + "秒";
    }

    /**
     * 数据库统计信息
     */
    public static class DatabaseStats {
        private int totalUsers;
        private int totalGames;
        private int onlineUsers;
        private int maxRating;

        public int getTotalUsers() { return totalUsers; }
        public void setTotalUsers(int totalUsers) { this.totalUsers = totalUsers; }

        public int getTotalGames() { return totalGames; }
        public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

        public int getOnlineUsers() { return onlineUsers; }
        public void setOnlineUsers(int onlineUsers) { this.onlineUsers = onlineUsers; }

        public int getMaxRating() { return maxRating; }
        public void setMaxRating(int maxRating) { this.maxRating = maxRating; }
    }
}
