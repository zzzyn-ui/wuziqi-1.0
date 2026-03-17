package com.gobang.cli;

import com.gobang.util.DatabaseUtil;
import com.gobang.util.DatabaseStatusChecker;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * 数据库查询命令行工具
 * 用于查询和管理游戏数据库
 */
public class DatabaseCLI {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseCLI.class);

    private final DatabaseUtil dbUtil;
    private final DatabaseStatusChecker statusChecker;
    private final Scanner scanner;

    public DatabaseCLI(SqlSessionFactory sqlSessionFactory) {
        this.dbUtil = new DatabaseUtil(sqlSessionFactory);
        this.statusChecker = new DatabaseStatusChecker(sqlSessionFactory);
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        System.out.println("\n=================================");
        System.out.println("    五子棋游戏数据库查询工具");
        System.out.println("=================================\n");

        // 检查连接
        if (!dbUtil.checkConnection()) {
            System.out.println("❌ 数据库连接失败，请检查配置");
            return;
        }

        System.out.println("✅ 数据库连接成功\n");

        // 主循环
        while (true) {
            printMenu();
            System.out.print("请选择操作 (输入序号): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    dbUtil.printStats();
                    break;
                case "2":
                    printLeaderboardMenu();
                    break;
                case "3":
                    queryUserInfo();
                    break;
                case "4":
                    queryUserHistory();
                    break;
                case "5":
                    queryGameDetail();
                    break;
                case "6":
                    checkTableStatus();
                    break;
                case "7":
                    showDataSuggestions();
                    break;
                case "0":
                    System.out.println("\n再见！");
                    return;
                default:
                    System.out.println("❌ 无效的选择，请重新输入\n");
            }
        }
    }

    private void printMenu() {
        System.out.println("\n┌─────────────────────────────────┐");
        System.out.println("│           主菜单                 │");
        System.out.println("├─────────────────────────────────┤");
        System.out.println("│ 1. 查看数据库统计              │");
        System.out.println("│ 2. 查看排行榜                  │");
        System.out.println("│ 3. 查询用户信息                │");
        System.out.println("│ 4. 查询用户对局历史            │");
        System.out.println("│ 5. 查询对局详情                │");
        System.out.println("│ 6. 检查表状态                  │");
        System.out.println("│ 7. 查看数据填充建议            │");
        System.out.println("│ 0. 退出                        │");
        System.out.println("└─────────────────────────────────┘");
    }

    private void printLeaderboardMenu() {
        System.out.print("\n请输入要显示的排名数量 (默认10): ");
        String input = scanner.nextLine().trim();
        int limit = 10;
        if (!input.isEmpty()) {
            try {
                limit = Integer.parseInt(input);
                limit = Math.max(1, Math.min(100, limit));
            } catch (NumberFormatException e) {
                System.out.println("输入无效，使用默认值10");
            }
        }
        dbUtil.printLeaderboard(limit);
    }

    private void queryUserInfo() {
        System.out.print("\n请输入用户ID: ");
        String input = scanner.nextLine().trim();
        try {
            Long userId = Long.parseLong(input);
            dbUtil.printUserInfo(userId);
        } catch (NumberFormatException e) {
            System.out.println("❌ 无效的用户ID\n");
        }
    }

    private void queryUserHistory() {
        System.out.print("\n请输入用户ID: ");
        String userIdInput = scanner.nextLine().trim();
        System.out.print("请输入要显示的场数 (默认20): ");
        String limitInput = scanner.nextLine().trim();

        try {
            Long userId = Long.parseLong(userIdInput);
            int limit = 20;
            if (!limitInput.isEmpty()) {
                limit = Integer.parseInt(limitInput);
                limit = Math.max(1, Math.min(100, limit));
            }
            dbUtil.printUserHistory(userId, limit);
        } catch (NumberFormatException e) {
            System.out.println("❌ 无效的输入\n");
        }
    }

    private void queryGameDetail() {
        System.out.print("\n请输入房间ID: ");
        String roomId = scanner.nextLine().trim();
        if (!roomId.isEmpty()) {
            dbUtil.printGameDetail(roomId);
        } else {
            System.out.println("❌ 房间ID不能为空\n");
        }
    }

    /**
     * 主入口 - 用于独立运行CLI工具
     */
    public static void main(String[] args) {
        System.out.println("正在初始化数据库连接...");

        try {
            // 这里需要传入 SqlSessionFactory
            // 实际使用时应该从主应用程序获取
            System.out.println("⚠️  此工具需要集成到主应用程序中使用");
            System.out.println("请在 GobangServer 中调用 DatabaseCLI");
        } catch (Exception e) {
            logger.error("初始化失败", e);
        }
    }

    /**
     * 检查表状态
     */
    private void checkTableStatus() {
        statusChecker.printStatusReport();
    }

    /**
     * 显示数据填充建议
     */
    private void showDataSuggestions() {
        System.out.println("\n=================================");
        System.out.println("       数据填充建议");
        System.out.println("=================================\n");

        var suggestions = statusChecker.getDataSuggestions();

        if (suggestions.isEmpty()) {
            System.out.println("✅ 所有表都有数据！");
            return;
        }

        System.out.println("以下表需要填充数据：\n");
        suggestions.forEach((table, suggestion) -> {
            System.out.printf("• %s%n", table);
            System.out.printf("  说明: %s%n%n", suggestion);
        });

        System.out.println("\n💡 提示：运行游戏服务器并进行操作后，这些表将自动填充数据");
    }
}
