package com.gobang.util;

import com.gobang.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据填充工具
 * 用于向空表填充测试数据
 */
public class TestDataFiller {

    private static final Logger logger = LoggerFactory.getLogger(TestDataFiller.class);

    private final HikariDataSource dataSource;

    public TestDataFiller(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriver());
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setPoolName("TestDataFillerPool");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * 填充所有测试数据
     */
    public void fillAllTestData() {
        System.out.println("\n========================================");
        System.out.println("      开始填充测试数据");
        System.out.println("========================================\n");

        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();

            // 1. 填充用户设置
            fillUserSettings(stmt);

            // 2. 填充活动日志
            fillActivityLog(stmt);

            // 3. 填充对局记录
            fillGameRecord(stmt);

            // 4. 填充对局收藏 (需要在game_record填充后获取ID)
            fillGameFavorite(stmt);

            // 5. 填充游戏邀请
            fillGameInvitation(stmt);

            // 6. 打印最终统计
            printFinalStats(stmt);

            System.out.println("\n========================================");
            System.out.println("      测试数据填充完成！");
            System.out.println("========================================\n");

        } catch (Exception e) {
            logger.error("填充测试数据失败", e);
            System.err.println("错误: " + e.getMessage());
        }
    }

    /**
     * 1. 填充用户设置
     */
    private void fillUserSettings(Statement stmt) throws Exception {
        System.out.println("【1/5】填充用户设置...");

        // 检查现有用户
        List<Long> userIds = getUserIds(stmt);
        if (userIds.isEmpty()) {
            System.out.println("  ⚠️  没有用户，跳过");
            return;
        }

        int count = 0;
        for (Long userId : userIds) {
            // 使用 INSERT IGNORE 避免重复
            String sql = String.format(
                "INSERT IGNORE INTO user_settings (user_id) VALUES (%d)",
                userId
            );
            try {
                stmt.execute(sql);
                count++;
            } catch (Exception e) {
                // 可能已存在，忽略
            }
        }

        System.out.println("  ✓ 为 " + count + " 个用户创建了默认设置");
    }

    /**
     * 2. 填充活动日志
     */
    private void fillActivityLog(Statement stmt) throws Exception {
        System.out.println("\n【2/5】填充活动日志...");

        List<Long> userIds = getUserIds(stmt);
        if (userIds.isEmpty()) {
            System.out.println("  ⚠️  没有用户，跳过");
            return;
        }

        int count = 0;
        // 为前3个用户创建登录记录
        for (int i = 0; i < Math.min(3, userIds.size()); i++) {
            Long userId = userIds.get(i);
            String sql = String.format(
                "INSERT IGNORE INTO user_activity_log (user_id, activity_type, ip_address, created_at) " +
                "VALUES (%d, 'login', '127.0.0.1', NOW())",
                userId
            );
            try {
                stmt.execute(sql);
                count++;
            } catch (Exception e) {
                // 可能已存在
            }
        }

        System.out.println("  ✓ 创建了 " + count + " 条活动日志");
    }

    /**
     * 3. 填充对局记录
     */
    private void fillGameRecord(Statement stmt) throws Exception {
        System.out.println("\n【3/5】填充对局记录...");

        List<Long> userIds = getUserIds(stmt);
        if (userIds.size() < 2) {
            System.out.println("  ⚠️  至少需要2个用户，跳过");
            return;
        }

        String sql = String.format(
            "INSERT IGNORE INTO game_record (" +
            "room_id, black_player_id, white_player_id, winner_id, win_color, " +
            "end_reason, move_count, duration, " +
            "black_rating_before, black_rating_after, black_rating_change, " +
            "white_rating_before, white_rating_after, white_rating_change, " +
            "moves, created_at" +
            ") VALUES (" +
            "'test_room_001', %d, %d, %d, 1, " +  // 用户1黑方胜
            "0, 45, 180, " +                       // 正常胜利，45手，3分钟
            "1200, 1216, 16, " +                   // 黑方积分+16
            "1180, 1164, -16, " +                  // 白方积分-16
            "'[[7,7],[7,8],[8,7],[8,8],[9,7],[9,8],[9,9]]', " +
            "NOW()" +
            ")",
            userIds.get(0), userIds.get(1), userIds.get(0)
        );

        stmt.execute(sql);
        System.out.println("  ✓ 创建了测试对局记录");
    }

    /**
     * 4. 填充对局收藏
     */
    private void fillGameFavorite(Statement stmt) throws Exception {
        System.out.println("\n【4/5】填充对局收藏...");

        // 获取game_record的ID
        Long gameRecordId = getGameRecordId(stmt);
        if (gameRecordId == null) {
            System.out.println("  ⚠️  没有对局记录，跳过");
            return;
        }

        List<Long> userIds = getUserIds(stmt);
        if (userIds.isEmpty()) {
            System.out.println("  ⚠️  没有用户，跳过");
            return;
        }

        String sql = String.format(
            "INSERT IGNORE INTO game_favorite (user_id, game_record_id, note, tags, is_public, created_at) " +
            "VALUES (%d, %d, '精彩的残局！', '残局,战术', 1, NOW())",
            userIds.get(0), gameRecordId
        );

        stmt.execute(sql);
        System.out.println("  ✓ 创建了测试收藏记录");
    }

    /**
     * 5. 填充游戏邀请
     */
    private void fillGameInvitation(Statement stmt) throws Exception {
        System.out.println("\n【5/5】填充游戏邀请...");

        List<Long> userIds = getUserIds(stmt);
        if (userIds.size() < 2) {
            System.out.println("  ⚠️  至少需要2个用户，跳过");
            return;
        }

        String sql = String.format(
            "INSERT IGNORE INTO game_invitation (inviter_id, invitee_id, invitation_type, status, expires_at, created_at) " +
            "VALUES (%d, %d, 'casual', 'pending', DATE_ADD(NOW(), INTERVAL 5 MINUTE), NOW())",
            userIds.get(0), userIds.get(1)
        );

        stmt.execute(sql);
        System.out.println("  ✓ 创建了测试邀请记录");
    }

    /**
     * 打印最终统计
     */
    private void printFinalStats(Statement stmt) throws Exception {
        System.out.println("\n========================================");
        System.out.println("      数据填充统计");
        System.out.println("========================================");

        String[] tables = {
            "user", "user_stats", "game_record", "friend",
            "chat_message", "observer_record", "user_settings",
            "user_activity_log", "game_favorite", "game_invitation"
        };

        for (String table : tables) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
            if (rs.next()) {
                long count = rs.getLong(1);
                System.out.printf("  %-20s %d 条记录%n", table + ":", count);
            }
            rs.close();
        }
    }

    /**
     * 获取所有用户ID
     */
    private List<Long> getUserIds(Statement stmt) throws Exception {
        List<Long> ids = new ArrayList<>();
        ResultSet rs = stmt.executeQuery("SELECT id FROM `user` ORDER BY id");
        while (rs.next()) {
            ids.add(rs.getLong("id"));
        }
        rs.close();
        return ids;
    }

    /**
     * 获取对局记录ID
     */
    private Long getGameRecordId(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT id FROM game_record LIMIT 1");
        if (rs.next()) {
            Long id = rs.getLong("id");
            rs.close();
            return id;
        }
        rs.close();
        return null;
    }

    /**
     * 关闭数据源
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 主入口 - 独立运行
     */
    public static void main(String[] args) {
        try {
            System.out.println("正在加载数据库配置...");
            DatabaseConfig config = DatabaseConfig.load();

            System.out.println("正在连接数据库...");
            TestDataFiller filler = new TestDataFiller(config);

            filler.fillAllTestData();

            filler.close();

            System.out.println("\n提示：以下表需要在运行应用后填充：");
            System.out.println("  • friend - 好友关系（需要添加好友）");
            System.out.println("  • chat_message - 聊天消息（需要发送消息）");
            System.out.println("  • observer_record - 观战记录（需要观战）");

        } catch (Exception e) {
            logger.error("执行失败", e);
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }
}
