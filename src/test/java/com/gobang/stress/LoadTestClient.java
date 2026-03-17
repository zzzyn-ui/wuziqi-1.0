package com.gobang.stress;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 五子棋压力测试客户端
 * 模拟100+用户同时在线，进行登录、匹配、游戏等操作
 */
public class LoadTestClient {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestClient.class);
    private static final String SERVER_URL = "ws://localhost:9091/ws";

    // 测试配置
    private static final int TOTAL_USERS = 100; // 总用户数
    private static final int CONNECTION_BATCH_SIZE = 10; // 每批连接用户数
    private static final int CONNECTION_BATCH_DELAY_MS = 500; // 批次间延迟(毫秒)
    private static final int TEST_DURATION_SECONDS = 300; // 测试持续时间(秒)

    // 性能统计
    private static final AtomicInteger onlineUsers = new AtomicInteger(0);
    private static final AtomicInteger successfulLogins = new AtomicInteger(0);
    private static final AtomicInteger failedLogins = new AtomicInteger(0);
    private static final AtomicInteger matchedGames = new AtomicInteger(0);
    private static final AtomicInteger activeGames = new AtomicInteger(0);
    private static final AtomicLong totalMessagesSent = new AtomicLong(0);
    private static final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private static final AtomicLong totalLatency = new AtomicLong(0);

    // 响应时间统计
    private static final ConcurrentHashMap<Integer, Long> loginStartTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Long> matchStartTimes = new ConcurrentHashMap<>();

    private static final List<TestClient> clients = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService statsExecutor = Executors.newScheduledThreadPool(1);
    private static final ExecutorService gameExecutor = Executors.newFixedThreadPool(20);

    /**
     * 测试客户端类
     */
    static class TestClient extends WebSocketClient {
        private final int userId;
        private final String username;
        private final String password;
        private final Random random = new Random();

        private String authToken;
        private String roomId;
        private boolean isLoggedIn = false;
        private boolean isMatching = false;
        private boolean isInGame = false;
        private boolean isBlackPlayer = false;
        private int lastMoveX = -1;
        private int lastMoveY = -1;
        private final int[][] board = new int[15][15];

        private final AtomicLong messagesSent = new AtomicLong(0);
        private final AtomicLong messagesReceived = new AtomicLong(0);

        public TestClient(int userId, String serverUri) throws Exception {
            super(new URI(serverUri));
            this.userId = userId;
            this.username = "testuser" + userId;
            this.password = "testpass123";
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            onlineUsers.incrementAndGet();
            logger.info("用户 {} 连接成功", username);

            // 连接成功后立即登录
            sendLoginRequest();
        }

        @Override
        public void onMessage(String message) {
            messagesReceived.incrementAndGet();
            totalMessagesReceived.incrementAndGet();

            // 解析JSON消息
            handleJsonMessage(message);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            messagesReceived.incrementAndGet();
            totalMessagesReceived.incrementAndGet();

            // 解析二进制消息
            handleBinaryMessage(bytes);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            onlineUsers.decrementAndGet();
            if (isInGame) {
                activeGames.decrementAndGet();
            }
            logger.info("用户 {} 断开连接: {}", username, reason);
        }

        @Override
        public void onError(Exception ex) {
            logger.error("用户 {} 发生错误: {}", username, ex.getMessage());
        }

        private void sendLoginRequest() {
            loginStartTimes.put(userId, System.currentTimeMillis());

            // 构造登录请求
            String loginRequest = String.format(
                "{\"type\":1,\"sequence_id\":%d,\"timestamp\":%d,\"body\":\"\"}",
                System.nanoTime(),
                System.currentTimeMillis()
            );

            // 实际应该发送包含用户名密码的protobuf消息
            // 这里简化为发送URL编码的认证信息
            String authMessage = String.format(
                "{\"type\":100,\"sequence_id\":%d,\"timestamp\":%d,\"body\":\"%s\"}",
                System.nanoTime(),
                System.currentTimeMillis(),
                username
            );

            send(authMessage);
            messagesSent.incrementAndGet();
            totalMessagesSent.incrementAndGet();
        }

        private void sendMatchRequest() {
            if (isMatching || isInGame) return;

            isMatching = true;
            matchStartTimes.put(userId, System.currentTimeMillis());

            String matchRequest = String.format(
                "{\"type\":10,\"sequence_id\":%d,\"timestamp\":%d,\"body\":\"{\\\"rating\\\":1200,\\\"mode\\\":\\\"casual\\\"}\"}",
                System.nanoTime(),
                System.currentTimeMillis()
            );

            send(matchRequest);
            messagesSent.incrementAndGet();
            totalMessagesSent.incrementAndGet();

            logger.debug("用户 {} 请求匹配", username);
        }

        private void sendMoveRequest(int x, int y) {
            String moveRequest = String.format(
                "{\"type\":20,\"sequence_id\":%d,\"timestamp\":%d,\"body\":\"{\\\"x\\\":%d,\\\"y\\\":%d}\"}",
                System.nanoTime(),
                System.currentTimeMillis(),
                x, y
            );

            send(moveRequest);
            messagesSent.incrementAndGet();
            totalMessagesSent.incrementAndGet();
        }

        private void handleJsonMessage(String message) {
            try {
                // 简化的JSON解析
                if (message.contains("\"type\":3")) { // AUTH_RESPONSE
                    handleAuthResponse(message);
                } else if (message.contains("\"type\":12")) { // MATCH_SUCCESS
                    handleMatchSuccess(message);
                } else if (message.contains("\"type\":13")) { // MATCH_FAILED
                    handleMatchFailed(message);
                } else if (message.contains("\"type\":21")) { // MOVE_RESULT
                    handleMoveResult(message);
                } else if (message.contains("\"type\":22")) { // GAME_STATE
                    handleGameState(message);
                } else if (message.contains("\"type\":23")) { // GAME_OVER
                    handleGameOver(message);
                }
            } catch (Exception e) {
                logger.debug("解析消息失败: {}", e.getMessage());
            }
        }

        private void handleBinaryMessage(ByteBuffer bytes) {
            // 处理protobuf二进制消息
            // 这里简化处理
        }

        private void handleAuthResponse(String message) {
            long latency = System.currentTimeMillis() - loginStartTimes.get(userId);
            totalLatency.addAndGet(latency);

            if (message.contains("\"success\":true") || message.contains("true")) {
                isLoggedIn = true;
                successfulLogins.incrementAndGet();
                logger.info("用户 {} 登录成功，延迟: {}ms", username, latency);

                // 登录成功后延迟一段时间开始匹配
                gameExecutor.submit(() -> {
                    try {
                        Thread.sleep(random.nextInt(3000) + 1000);
                        sendMatchRequest();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } else {
                failedLogins.incrementAndGet();
                logger.warn("用户 {} 登录失败", username);
            }
        }

        private void handleMatchSuccess(String message) {
            long latency = System.currentTimeMillis() - matchStartTimes.get(userId);
            isMatching = false;
            isInGame = true;
            activeGames.incrementAndGet();
            matchedGames.incrementAndGet();

            // 解析房间信息
            if (message.contains("\"is_first\":true")) {
                isBlackPlayer = true;
            }

            logger.info("用户 {} 匹配成功，匹配延迟: {}ms", username, latency);

            // 如果是黑棋，先手
            if (isBlackPlayer) {
                gameExecutor.submit(() -> {
                    try {
                        Thread.sleep(500);
                        makeRandomMove();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }

        private void handleMatchFailed(String message) {
            isMatching = false;
            logger.debug("用户 {} 匹配失败", username);

            // 重新尝试匹配
            gameExecutor.submit(() -> {
                try {
                    Thread.sleep(5000);
                    sendMatchRequest();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        private void handleMoveResult(String message) {
            // 落子结果处理
        }

        private void handleGameState(String message) {
            // 游戏状态更新，如果轮到己方下棋
            if (message.contains("\"current_player\":1") && isBlackPlayer ||
                message.contains("\"current_player\":2") && !isBlackPlayer) {
                gameExecutor.submit(() -> {
                    try {
                        Thread.sleep(random.nextInt(1000) + 500);
                        makeRandomMove();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }

        private void handleGameOver(String message) {
            isInGame = false;
            activeGames.decrementAndGet();
            logger.info("用户 {} 游戏结束", username);

            // 游戏结束后重新开始匹配
            gameExecutor.submit(() -> {
                try {
                    Thread.sleep(random.nextInt(5000) + 2000);
                    sendMatchRequest();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        private void makeRandomMove() {
            if (!isInGame) return;

            // 随机选择一个空位
            List<int[]> emptyPositions = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                for (int j = 0; j < 15; j++) {
                    if (board[i][j] == 0) {
                        emptyPositions.add(new int[]{i, j});
                    }
                }
            }

            if (!emptyPositions.isEmpty()) {
                int[] pos = emptyPositions.get(random.nextInt(emptyPositions.size()));
                sendMoveRequest(pos[0], pos[1]);
                board[pos[0]][pos[1]] = isBlackPlayer ? 1 : 2;
            }
        }

        public void sendMessage(String message) {
            if (isOpen()) {
                send(message);
                messagesSent.incrementAndGet();
                totalMessagesSent.incrementAndGet();
            }
        }

    }

    /**
     * 启动压力测试
     */
    public static void startLoadTest() throws Exception {
        logger.info("========================================");
        logger.info("  五子棋压力测试开始");
        logger.info("  测试用户数: {}", TOTAL_USERS);
        logger.info("  服务器地址: {}", SERVER_URL);
        logger.info("  测试持续时间: {}秒", TEST_DURATION_SECONDS);
        logger.info("========================================");

        // 定期输出统计信息
        statsExecutor.scheduleAtFixedRate(LoadTestClient::printStats, 5, 5, TimeUnit.SECONDS);

        // 分批连接用户，避免瞬时压力过大
        int connectedUsers = 0;
        while (connectedUsers < TOTAL_USERS) {
            int batchSize = Math.min(CONNECTION_BATCH_SIZE, TOTAL_USERS - connectedUsers);

            for (int i = 0; i < batchSize; i++) {
                int userId = connectedUsers + i + 1;
                try {
                    TestClient client = new TestClient(userId, SERVER_URL);
                    client.connect();
                    clients.add(client);
                    Thread.sleep(50); // 避免同时连接
                } catch (Exception e) {
                    logger.error("创建客户端失败: {}", e.getMessage());
                }
            }

            connectedUsers += batchSize;
            logger.info("已连接用户: {}/{}", connectedUsers, TOTAL_USERS);
            Thread.sleep(CONNECTION_BATCH_DELAY_MS);
        }

        logger.info("所有用户连接完成，开始压力测试...");

        // 等待测试完成
        Thread.sleep(TEST_DURATION_SECONDS * 1000L);

        // 输出最终统计
        printFinalStats();

        // 关闭所有连接
        shutdown();
    }

    /**
     * 打印实时统计信息
     */
    private static void printStats() {
        int online = onlineUsers.get();
        int logins = successfulLogins.get();
        int games = matchedGames.get();
        int active = activeGames.get();
        long sent = totalMessagesSent.get();
        long received = totalMessagesReceived.get();
        double avgLatency = totalLatency.get() / (double) Math.max(1, logins);

        logger.info("========================================");
        logger.info("  实时统计:");
        logger.info("  在线用户: {}/{}", online, TOTAL_USERS);
        logger.info("  登录成功: {}, 失败: {}", logins, failedLogins.get());
        logger.info("  已完成对局: {}, 进行中: {}", games, active);
        logger.info("  发送消息: {}, 接收消息: {}", sent, received);
        logger.info("  平均登录延迟: {:.2f}ms", avgLatency);
        logger.info("========================================");
    }

    /**
     * 打印最终统计信息
     */
    private static void printFinalStats() {
        int online = onlineUsers.get();
        int logins = successfulLogins.get();
        int games = matchedGames.get();
        long sent = totalMessagesSent.get();
        long received = totalMessagesReceived.get();
        double avgLatency = totalLatency.get() / (double) Math.max(1, logins);
        double successRate = (logins * 100.0) / TOTAL_USERS;

        logger.info("========================================");
        logger.info("  压力测试完成 - 最终统计");
        logger.info("========================================");
        logger.info("  总用户数: {}", TOTAL_USERS);
        logger.info("  在线用户: {} ({:.1f}%)", online, (online * 100.0) / TOTAL_USERS);
        logger.info("  登录成功: {} ({:.1f}%)", logins, successRate);
        logger.info("  登录失败: {}", failedLogins.get());
        logger.info("  已完成对局: {}", games);
        logger.info("  总发送消息: {}", sent);
        logger.info("  总接收消息: {}", received);
        logger.info("  平均登录延迟: {:.2f}ms", avgLatency);
        logger.info("  消息发送速率: {:.2f} msg/s", sent / (double) TEST_DURATION_SECONDS);
        logger.info("  消息接收速率: {:.2f} msg/s", received / (double) TEST_DURATION_SECONDS);
        logger.info("========================================");
    }

    /**
     * 关闭所有连接和资源
     */
    private static void shutdown() {
        logger.info("正在关闭所有连接...");

        for (TestClient client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                logger.error("关闭客户端失败: {}", e.getMessage());
            }
        }

        statsExecutor.shutdown();
        gameExecutor.shutdown();

        try {
            if (!statsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                statsExecutor.shutdownNow();
            }
            if (!gameExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                gameExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("所有连接已关闭");
    }

    /**
     * 主函数
     */
    public static void main(String[] args) {
        try {
            // 可以从命令行参数读取配置
            if (args.length > 0) {
                try {
                    int users = Integer.parseInt(args[0]);
                    if (users > 0 && users <= 1000) {
                        // 通过反射修改TOTAL_USERS
                        // 这里简化处理，直接使用默认值
                    }
                } catch (NumberFormatException e) {
                    logger.warn("无效的用户数参数，使用默认值: {}", TOTAL_USERS);
                }
            }

            startLoadTest();
        } catch (Exception e) {
            logger.error("压力测试失败", e);
            System.exit(1);
        }
    }
}
