package com.gobang;

import com.gobang.config.AppConfig;
import com.gobang.config.DatabaseConfig;
import com.gobang.config.NettyConfig;
import com.gobang.config.RedisConfig;
import com.gobang.core.match.MatchMaker;
import com.gobang.core.netty.NettyServer;
import com.gobang.core.room.RoomManager;
import com.gobang.core.security.RateLimitManager;
import com.gobang.core.social.ChatManager;
import com.gobang.core.social.FriendManager;
import com.gobang.core.social.ObserverManager;
import com.gobang.service.AuthService;
import com.gobang.service.ChatService;
import com.gobang.service.FriendService;
import com.gobang.service.FriendGroupService;
import com.gobang.service.GameService;
import com.gobang.service.MatchQueueService;
import com.gobang.service.RecordService;
import com.gobang.service.RoomService;
import com.gobang.service.UserService;
import com.gobang.service.UserSettingsService;
import com.gobang.service.ActivityLogService;
import com.gobang.service.GameFavoriteService;
import com.gobang.service.GameInvitationService;
import com.gobang.service.MatchStatusService;
import com.gobang.service.PuzzleService;
import com.gobang.controller.ApiServer;
import com.gobang.mapper.UserMapper;
import com.gobang.util.JwtUtil;
import com.gobang.util.RedisUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 五子棋游戏服务器主启动类
 */
public class GobangServer {

    private static final Logger logger = LoggerFactory.getLogger(GobangServer.class);

    private final NettyServer nettyServer;
    private final RedisUtil redisUtil;
    private final SqlSessionFactory sqlSessionFactory;
    private final RoomManager roomManager;
    private final MatchMaker matchMaker;
    private final FriendManager friendManager;
    private final ChatManager chatManager;
    private final ObserverManager observerManager;
    private final RateLimitManager rateLimitManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuthService authService;
    private final MatchQueueService matchQueueService;
    private final GameService gameService;
    private final RoomService roomService;
    private final ChatService chatService;
    private final FriendService friendService;
    private final FriendGroupService friendGroupService;
    private final RecordService recordService;
    private final UserSettingsService userSettingsService;
    private final ActivityLogService activityLogService;
    private final GameFavoriteService gameFavoriteService;
    private final GameInvitationService gameInvitationService;
    private final PuzzleService puzzleService;
    private final ApiServer apiServer;

    // 匹配状态服务需要在 NettyServer 初始化后创建
    private MatchStatusService matchStatusService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public GobangServer() {
        // 加载配置
        NettyConfig nettyConfig = NettyConfig.load();
        RedisConfig redisConfig = RedisConfig.load();
        DatabaseConfig dbConfig = DatabaseConfig.load();
        AppConfig appConfig = AppConfig.load();

        // 初始化Redis
        this.redisUtil = new RedisUtil(
                redisConfig.getHost(),
                redisConfig.getPort(),
                redisConfig.getPassword(),
                redisConfig.getDatabase(),
                redisConfig.getTimeout()
        );

        // 初始化数据库
        this.sqlSessionFactory = createSqlSessionFactory(dbConfig);

        // 初始化工具
        this.jwtUtil = new JwtUtil(
                appConfig.getJwt().getSecret(),
                appConfig.getJwt().getExpiration(),
                appConfig.getJwt().getIssuer()
        );

        // 初始化管理器（先创建 roomManager，但不设置回调）
        this.roomManager = new RoomManager(
                redisUtil,
                appConfig.getGame().getRoomExpireTime(),
                appConfig.getGame().getReconnectWindow(),
                appConfig.getGame().getMatchTimeout() * 1000L // 落子超时时间（毫秒）
        );

        this.matchMaker = new MatchMaker(
                appConfig.getMatch().getRatingDiff(),
                appConfig.getMatch().getMaxQueueTime(),
                appConfig.getMatch().getCheckInterval(),
                appConfig.getMatch().getTestMatchDelay()
        );

        // 设置测试模式
        this.matchMaker.setTestMode(appConfig.getMatch().isTestMode());

        this.friendManager = new FriendManager();
        this.chatManager = new ChatManager();
        this.observerManager = new ObserverManager(roomManager);
        this.rateLimitManager = new RateLimitManager();

        // 初始化服务（所有服务共享SqlSessionFactory）
        this.userService = new UserService(sqlSessionFactory);
        this.authService = new AuthService(sqlSessionFactory, jwtUtil);
        this.chatService = new ChatService(sqlSessionFactory);
        this.friendService = new FriendService(sqlSessionFactory);
        // 获取 Mapper 实例用于 FriendGroupService
        com.gobang.mapper.FriendGroupMapper friendGroupMapper;
        com.gobang.mapper.FriendMapper friendMapper;
        try (org.apache.ibatis.session.SqlSession session = sqlSessionFactory.openSession()) {
            friendGroupMapper = session.getMapper(com.gobang.mapper.FriendGroupMapper.class);
            friendMapper = session.getMapper(com.gobang.mapper.FriendMapper.class);
        }
        this.friendGroupService = new FriendGroupService(friendGroupMapper, friendMapper);
        this.recordService = new RecordService(sqlSessionFactory);
        this.userSettingsService = new UserSettingsService(sqlSessionFactory);
        this.activityLogService = new ActivityLogService(sqlSessionFactory);
        this.gameFavoriteService = new GameFavoriteService(sqlSessionFactory);
        this.gameInvitationService = new GameInvitationService(sqlSessionFactory);

        // 初始化残局服务
        this.puzzleService = new PuzzleService(sqlSessionFactory);

        // 自动检查并初始化残局数据
        try {
            DataSource dataSource = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
            logger.info("检查残局数据...");
            if (com.gobang.util.DatabaseInitUtil.initPuzzleData(dataSource)) {
                logger.info("✓ 残局数据已就绪");
            } else {
                logger.warn("⚠️ 残局数据初始化失败，残局功能可能无法使用");
            }
        } catch (Exception e) {
            logger.warn("残局数据检查失败: {}", e.getMessage());
        }

        // 从配置加载匹配参数
        var matchConfig = appConfig.getMatch();

        // 尝试初始化 MatchQueueService（需要 Redis）
        MatchQueueService tempMatchQueueService = null;
        try {
            logger.info("正在测试 Redis 连接...");
            // 使用专门的测试方法，会抛出异常如果连接失败
            redisUtil.testConnection();
            tempMatchQueueService = new MatchQueueService(redisUtil);
            logger.info("✓ Redis 连接正常，已启用分布式匹配队列");
        } catch (Exception e) {
            logger.warn("⚠️ Redis 连接失败，将使用本地内存匹配队列");
            logger.warn("Redis 错误: {}", e.getMessage());
            tempMatchQueueService = null;
        }
        this.matchQueueService = tempMatchQueueService;

        this.gameService = new GameService(
                roomManager,
                userService,
                sqlSessionFactory,
                matchQueueService,
                redisUtil,  // 添加RedisUtil用于断线重连和超时检测
                matchConfig.getRatingDiff(),
                matchConfig.getMaxQueueTime(),
                matchConfig.getCheckInterval(),
                matchConfig.isTestMode(),
                matchConfig.getTestMatchDelay()
        );

        // 初始化 HTTP API 服务器
        this.apiServer = new ApiServer(
            9090,
            userService,
            userSettingsService,
            activityLogService,
            gameFavoriteService,
            gameInvitationService,
            authService,
            gameService,
            friendService,
            roomManager,
            friendManager,
            sqlSessionFactory
        );

        this.roomService = new RoomService(roomManager, userService, recordService);

        // 设置房间超时回调
        this.roomManager.setTimeoutCallback((room, timeoutPlayerId) -> {
            // 超时玩家判负，使用 RoomService 的 handleTimeout 方法
            roomService.handleTimeout(timeoutPlayerId, room.getRoomId());
        });

        // 初始化Netty服务器
        this.nettyServer = new NettyServer(
                nettyConfig.getHost(),
                nettyConfig.getPort(),
                nettyConfig.getBossThreads(),
                nettyConfig.getWorkerThreads(),
                nettyConfig.getReadTimeout(),
                nettyConfig.getWriteTimeout(),
                authService,
                userService,
                gameService,
                roomService,
                chatService,
                friendService,
                friendGroupService,
                roomManager,
                friendManager,
                chatManager,
                observerManager,
                rateLimitManager,
                jwtUtil,
                userSettingsService,
                activityLogService,
                gameFavoriteService,
                gameInvitationService,
                recordService,
                puzzleService
        );

        // 初始化匹配状态广播服务（需要 NettyServer 的 ChannelManager）
        this.matchStatusService = new MatchStatusService(
                matchQueueService,
                userService,
                nettyServer.getChannelManager(),
                5  // 每5秒广播一次状态
        );

        // 启动定时任务
        startScheduledTasks();
    }

    /**
     * 创建MyBatis SqlSessionFactory
     */
    private static SqlSessionFactory createSqlSessionFactory(DatabaseConfig config) {
        // 使用HikariCP连接池
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriver());
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getPoolSize());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setPoolName("GobangHikariPool");

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        TransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, dataSource);

        Configuration configuration = new Configuration(environment);
        // 启用驼峰命名自动映射：created_at -> createdAt
        configuration.setMapUnderscoreToCamelCase(true);
        // 启用懒加载
        configuration.setLazyLoadingEnabled(true);
        // 设置日志实现类
        configuration.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);

        configuration.addMapper(com.gobang.mapper.UserMapper.class);
        configuration.addMapper(com.gobang.mapper.GameRecordMapper.class);
        configuration.addMapper(com.gobang.mapper.UserStatsMapper.class);
        configuration.addMapper(com.gobang.mapper.FriendMapper.class);
        configuration.addMapper(com.gobang.mapper.FriendGroupMapper.class);
        configuration.addMapper(com.gobang.mapper.ChatMessageMapper.class);
        // 新增功能Mapper
        configuration.addMapper(com.gobang.mapper.UserSettingsMapper.class);
        configuration.addMapper(com.gobang.mapper.UserActivityLogMapper.class);
        configuration.addMapper(com.gobang.mapper.GameFavoriteMapper.class);
        configuration.addMapper(com.gobang.mapper.GameInvitationMapper.class);
        configuration.addMapper(com.gobang.mapper.PuzzleMapper.class);
        configuration.addMapper(com.gobang.mapper.PuzzleRecordMapper.class);
        configuration.addMapper(com.gobang.mapper.PuzzleStatsMapper.class);

        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        // 检查数据库连接
        checkDatabaseConnection();

        // 每小时清理一次旧聊天消息
        scheduler.scheduleAtFixedRate(() -> {
            try {
                chatService.cleanupOldMessages(7);
            } catch (Exception e) {
                logger.error("Error in scheduled task", e);
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 检查数据库连接和数据状态
     */
    private void checkDatabaseConnection() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            Long userCount = userMapper.countUsers();

            logger.info("========================================");
            logger.info("  数据库连接检查");
            logger.info("========================================");
            logger.info("✓ 数据库连接正常");
            logger.info("✓ 用户表存在，用户总数: {}", userCount);

            if (userCount == 0) {
                logger.warn("⚠ 数据库中没有用户，请先注册或使用测试数据填充");
                logger.info("  提示: 运行 'java -jar gobang.jar --fill-data' 填充测试数据");
            }

            logger.info("========================================");

        } catch (Exception e) {
            logger.error("========================================");
            logger.error("  数据库连接失败！");
            logger.error("========================================");
            logger.error("错误信息: {}", e.getMessage());
            logger.error("请检查:");
            logger.error("  1. MySQL 服务是否启动");
            logger.error("  2. 数据库 'gobang' 是否存在");
            logger.error("  3. 用户名和密码是否正确");
            logger.error("========================================");
        }
    }

    /**
     * 启动服务器
     */
    public void start() throws InterruptedException {
        logger.info("========================================");
        logger.info("  五子棋在线对战服务器启动中...");
        logger.info("========================================");

        // 不启动 HTTP API 服务器（9090端口），所有功能已迁移到 Netty (8083)
        logger.info("HTTP API 功能已集成到 Netty 服务器 (8083端口)");

        // 启动 Netty 服务器（支持 HTTP + WebSocket）
        nettyServer.start();

        // 启动超时检查任务
        gameService.startTimeoutChecker();
        logger.info("超时检查任务已启动");

        // 启动匹配状态广播服务
        matchStatusService.start();
        logger.info("匹配状态广播服务已启动");

        logger.info("========================================");
        logger.info("  服务器启动完成！");
        logger.info("  服务端点: http://0.0.0.0:8083");
        logger.info("  WebSocket: ws://0.0.0.0:8083/ws");
        logger.info("  HTTP API: http://0.0.0.0:8083/api");
        logger.info("========================================");
    }

    /**
     * 关闭服务器
     */
    public void shutdown() {
        logger.info("Shutting down server...");

        scheduler.shutdown();
        apiServer.stop();
        nettyServer.shutdown();
        matchMaker.shutdown();
        roomManager.shutdown();
        friendManager.cleanup();
        chatManager.cleanup();
        rateLimitManager.clear();

        // 停止匹配状态广播服务
        if (matchStatusService != null) {
            matchStatusService.stop();
        }

        redisUtil.close();

        // 关闭HikariCP连接池
        Environment environment = sqlSessionFactory.getConfiguration().getEnvironment();
        if (environment.getDataSource() instanceof HikariDataSource) {
            ((HikariDataSource) environment.getDataSource()).close();
        }

        logger.info("Server shutdown complete");
    }

    /**
     * 主函数
     * 支持以下命令行参数：
     * --cli              启动数据库查询CLI工具
     * --stats            打印数据库统计信息
     * --check-db         检查数据库表状态
     * --empty-tables      显示空表列表
     * --data-suggestions 显示数据填充建议
     * --fill-data        填充测试数据
     * --leaderboard      打印排行榜
     * --help, -h         显示帮助信息
     */
    public static void main(String[] args) {
        // 检查命令行参数
        for (String arg : args) {
            if ("--cli".equals(arg) || "--database-cli".equals(arg)) {
                // 启动CLI工具
                startCliTool();
                return;
            } else if ("--check-db".equals(arg)) {
                // 检查数据库状态
                startCheckDb();
                return;
            } else if ("--empty-tables".equals(arg)) {
                // 显示空表
                startEmptyTables();
                return;
            } else if ("--data-suggestions".equals(arg)) {
                // 显示数据填充建议
                startDataSuggestions();
                return;
            } else if ("--fill-data".equals(arg)) {
                // 填充测试数据
                startFillData();
                return;
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return;
            }
        }

        // 正常启动服务器
        GobangServer server = new GobangServer();

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
        }));

        try {
            server.start();

            // 保持运行
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.info("Server interrupted", e);
            server.shutdown();
        } catch (Exception e) {
            logger.error("Server error", e);
            server.shutdown();
            System.exit(1);
        }
    }

    /**
     * 启动数据库CLI工具
     */
    private static void startCliTool() {
        try {
            // 加载配置
            DatabaseConfig dbConfig = DatabaseConfig.load();

            // 创建SqlSessionFactory
            SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(dbConfig);

            // 启动CLI
            com.gobang.cli.DatabaseCLI cli = new com.gobang.cli.DatabaseCLI(sqlSessionFactory);
            cli.run();

        } catch (Exception e) {
            logger.error("启动CLI工具失败", e);
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 检查数据库状态
     */
    private static void startCheckDb() {
        try {
            DatabaseConfig dbConfig = DatabaseConfig.load();
            SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(dbConfig);

            com.gobang.util.DatabaseStatusChecker checker =
                new com.gobang.util.DatabaseStatusChecker(sqlSessionFactory);
            checker.printStatusReport();

        } catch (Exception e) {
            logger.error("检查数据库状态失败", e);
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 显示空表列表
     */
    private static void startEmptyTables() {
        try {
            DatabaseConfig dbConfig = DatabaseConfig.load();
            SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(dbConfig);

            com.gobang.util.DatabaseStatusChecker checker =
                new com.gobang.util.DatabaseStatusChecker(sqlSessionFactory);

            var emptyTables = checker.getEmptyTables();

            if (emptyTables.isEmpty()) {
                System.out.println("\n✅ 所有表都有数据！");
            } else {
                System.out.println("\n空表列表：");
                emptyTables.forEach(t -> {
                    System.out.println("  • " + t.getTableName() + " - " + t.getDescription());
                });
            }

        } catch (Exception e) {
            logger.error("查询空表失败", e);
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 显示数据填充建议
     */
    private static void startDataSuggestions() {
        try {
            DatabaseConfig dbConfig = DatabaseConfig.load();
            SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(dbConfig);

            com.gobang.util.DatabaseStatusChecker checker =
                new com.gobang.util.DatabaseStatusChecker(sqlSessionFactory);

            var suggestions = checker.getDataSuggestions();

            System.out.println("\n=================================");
            System.out.println("       数据填充建议");
            System.out.println("=================================\n");

            if (suggestions.isEmpty()) {
                System.out.println("✅ 所有表都有数据！");
            } else {
                System.out.println("以下表需要填充数据：\n");
                suggestions.forEach((table, suggestion) -> {
                    System.out.println("• " + table);
                    System.out.println("  " + suggestion + "\n");
                });
            }

        } catch (Exception e) {
            logger.error("生成数据填充建议失败", e);
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 填充测试数据
     */
    private static void startFillData() {
        try {
            DatabaseConfig dbConfig = DatabaseConfig.load();
            com.gobang.util.TestDataFiller filler = new com.gobang.util.TestDataFiller(dbConfig);
            filler.fillAllTestData();
            filler.close();

        } catch (Exception e) {
            logger.error("填充测试数据失败", e);
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("\n五子棋游戏服务器");
        System.out.println("\n用法: java -jar gobang-server.jar [选项]");
        System.out.println("\n选项:");
        System.out.println("  --cli, --database-cli    启动数据库查询CLI工具");
        System.out.println("  --check-db               检查数据库表状态");
        System.out.println("  --empty-tables           显示空表列表");
        System.out.println("  --data-suggestions       显示数据填充建议");
        System.out.println("  --fill-data              填充测试数据");
        System.out.println("  --help, -h               显示此帮助信息");
        System.out.println("\n示例:");
        System.out.println("  java -jar gobang-server.jar                      # 启动服务器");
        System.out.println("  java -jar gobang-server.jar --cli                  # 启动数据库查询工具");
        System.out.println("  java -jar gobang-server.jar --check-db             # 检查数据库状态");
        System.out.println("  java -jar gobang-server.jar --empty-tables         # 查看空表");
        System.out.println("  java -jar gobang-server.jar --fill-data            # 填充测试数据");
        System.out.println();
    }
}
