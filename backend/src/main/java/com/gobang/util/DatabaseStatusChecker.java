package com.gobang.util;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 数据库表状态检查工具
 * 查询各表的数据量，识别空表
 */
public class DatabaseStatusChecker {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseStatusChecker.class);

    private final SqlSessionFactory sqlSessionFactory;

    public DatabaseStatusChecker(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 获取所有表的状态
     */
    public List<TableStatus> getAllTableStatus() {
        List<TableStatus> statuses = new ArrayList<>();

        try (SqlSession session = sqlSessionFactory.openSession()) {
            // 核心表
            statuses.add(checkTable(session, "user", "用户表"));
            statuses.add(checkTable(session, "user_stats", "用户统计表"));
            statuses.add(checkTable(session, "game_record", "对局记录表"));
            statuses.add(checkTable(session, "friend", "好友关系表"));
            statuses.add(checkTable(session, "chat_message", "聊天消息表"));
            statuses.add(checkTable(session, "observer_record", "观战记录表"));

            // 新增功能表
            statuses.add(checkTable(session, "user_settings", "用户设置表"));
            statuses.add(checkTable(session, "user_activity_log", "用户活动日志表"));
            statuses.add(checkTable(session, "game_favorite", "对局收藏表"));
            statuses.add(checkTable(session, "game_invitation", "游戏邀请表"));
        }

        return statuses;
    }

    /**
     * 检查单个表的状态
     */
    private TableStatus checkTable(SqlSession session, String tableName, String description) {
        try {
            Long count = session.selectOne("SELECT COUNT(*) FROM " + tableName);
            boolean isEmpty = count == null || count == 0;

            return new TableStatus(tableName, description, count != null ? count : 0L, isEmpty);

        } catch (Exception e) {
            logger.warn("检查表 {} 失败: {}", tableName, e.getMessage());
            return new TableStatus(tableName, description, 0L, true, true); // 表不存在或查询失败
        }
    }

    /**
     * 打印表状态报告
     */
    public void printStatusReport() {
        List<TableStatus> statuses = getAllTableStatus();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("         数据库表状态报告");
        System.out.println("=".repeat(60));

        // 分组显示
        printSection("核心表", statuses, Arrays.asList("user", "user_stats", "game_record", "friend", "chat_message", "observer_record"));
        printSection("新增功能表", statuses, Arrays.asList("user_settings", "user_activity_log", "game_favorite", "game_invitation"));

        // 汇总统计
        printSummary(statuses);

        System.out.println("=".repeat(60) + "\n");
    }

    /**
     * 打印分组
     */
    private void printSection(String title, List<TableStatus> allStatuses, List<String> tableNames) {
        System.out.println("\n【" + title + "】");

        for (String tableName : tableNames) {
            Optional<TableStatus> status = allStatuses.stream()
                .filter(s -> s.getTableName().equals(tableName))
                .findFirst();

            if (status.isPresent()) {
                TableStatus s = status.get();
                System.out.printf("  %-25s %s%n", s.getTableName(), s.getStatusText());
            }
        }
    }

    /**
     * 打印汇总统计
     */
    private void printSummary(List<TableStatus> statuses) {
        long totalTables = statuses.size();
        long emptyTables = statuses.stream().filter(TableStatus::isEmpty).count();
        long totalRecords = statuses.stream().mapToLong(TableStatus::getRecordCount).sum();

        System.out.println("\n【统计汇总】");
        System.out.println("  总表数: " + totalTables);
        System.out.println("  空表数: " + emptyTables);
        System.out.println("  总记录数: " + totalRecords);

        if (emptyTables > 0) {
            System.out.println("\n【空表列表】");
            statuses.stream()
                .filter(TableStatus::isEmpty)
                .forEach(s -> System.out.println("  - " + s.getTableName() + " (" + s.getDescription() + ")"));
        }
    }

    /**
     * 获取空表列表
     */
    public List<TableStatus> getEmptyTables() {
        return getAllTableStatus().stream()
            .filter(TableStatus::isEmpty)
            .toList();
    }

    /**
     * 获取需要填充数据的表
     */
    public Map<String, String> getDataSuggestions() {
        Map<String, String> suggestions = new LinkedHashMap<>();

        List<TableStatus> emptyTables = getEmptyTables();

        for (TableStatus status : emptyTables) {
            String tableName = status.getTableName();
            String suggestion = getSuggestion(tableName);
            if (suggestion != null) {
                suggestions.put(tableName, suggestion);
            }
        }

        return suggestions;
    }

    /**
     * 获取填充建议
     */
    private String getSuggestion(String tableName) {
        switch (tableName) {
            case "game_record":
                return "进行游戏对局后自动保存";
            case "friend":
                return "用户添加好友后产生数据";
            case "chat_message":
                return "用户发送聊天消息后产生数据";
            case "observer_record":
                return "用户观战后产生数据";
            case "user_settings":
                return "用户首次访问设置页面时自动创建";
            case "user_activity_log":
                return "用户登录、游戏等活动时自动记录";
            case "game_favorite":
                return "用户收藏对局后产生数据";
            case "game_invitation":
                return "用户发送游戏邀请后产生数据";
            default:
                return null;
        }
    }

    /**
     * 表状态信息
     */
    public static class TableStatus {
        private final String tableName;
        private final String description;
        private final long recordCount;
        private final boolean isEmpty;
        private final boolean hasError;

        public TableStatus(String tableName, String description, long recordCount, boolean isEmpty) {
            this(tableName, description, recordCount, isEmpty, false);
        }

        public TableStatus(String tableName, String description, long recordCount, boolean isEmpty, boolean hasError) {
            this.tableName = tableName;
            this.description = description;
            this.recordCount = recordCount;
            this.isEmpty = isEmpty;
            this.hasError = hasError;
        }

        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public long getRecordCount() { return recordCount; }
        public boolean isEmpty() { return isEmpty; }
        public boolean hasError() { return hasError; }

        public String getStatusText() {
            if (hasError) {
                return "❌ 表不存在或查询失败";
            } else if (isEmpty) {
                return "📭 空表 (0 条记录)";
            } else {
                return "✓ 有数据 (" + recordCount + " 条记录)";
            }
        }
    }

    /**
     * 主入口 - 用于独立运行检查
     */
    public static void main(String[] args) {
        System.out.println("数据库状态检查工具需要集成到主程序中使用");
        System.out.println("请使用 DatabaseCLI 或 GobangServer 集成调用");
    }
}
