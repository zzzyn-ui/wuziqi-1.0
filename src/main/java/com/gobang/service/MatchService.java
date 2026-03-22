package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.match.Player;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.model.entity.User;
import com.gobang.util.SecureRandomUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 匹配服务 - 完整实现
 *
 * 功能：
 * 1. 无锁队列：ConcurrentLinkedQueue
 * 2. 定时扫描匹配
 * 3. 时间膨胀算法
 * 4. JSON 响应格式
 */
public class MatchService {

    private static final Logger logger = LoggerFactory.getLogger(MatchService.class);

    // ==================== 配置参数 ====================
    private static final int INITIAL_RATING_DIFF = 100;      // 初始积分差
    private static final int MAX_RATING_DIFF = 500;           // 最大积分差
    private static final int EXPANSION_RATE = 20;            // 每秒扩大积分差
    private static final int MAX_QUEUE_TIME = 300;           // 最大等待时间（秒）
    private static final int MATCH_CHECK_INTERVAL = 100;     // 匹配检查间隔（毫秒）
    private static final int TEST_MODE_DELAY = 3000;         // 测试模式匹配延迟（毫秒）
    private static final boolean TEST_MODE = false;          // 测试模式开关（已关闭）

    // ==================== 无锁队列 ====================
    /** 匹配队列 - 无锁队列 */
    private final Queue<MatchPlayer> matchQueue = new ConcurrentLinkedQueue<>();

    /** 玩家索引 - 快速查找 userId -> MatchPlayer */
    private final Map<Long, MatchPlayer> playerIndex = new ConcurrentHashMap<>();

    /** 最近对手记录 - userId -> 上一个对手的userId（避免连续匹配相同对手）*/
    private final Map<Long, Long> lastOpponents = new ConcurrentHashMap<>();

    // ==================== 统计信息 ====================
    private final AtomicLong totalMatches = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);
    private final AtomicLong queueJoinCount = new AtomicLong(0);

    // ==================== 依赖服务 ====================
    private final RoomManager roomManager;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    // ==================== 定时任务 ====================
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean matchingInProgress = new AtomicBoolean(false);

    // ==================== 构造函数 ====================
    public MatchService(RoomManager roomManager, UserService userService) {
        this.roomManager = roomManager;
        this.userService = userService;
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "match-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== 初始化与销毁 ====================
    /**
     * 启动匹配服务
     */
    public void start() {
        logger.info("MatchService initializing... TEST_MODE={}", TEST_MODE);

        // 启动定时匹配任务
        scheduler.scheduleAtFixedRate(
            this::processMatching,
            MATCH_CHECK_INTERVAL,
            MATCH_CHECK_INTERVAL,
            TimeUnit.MILLISECONDS
        );

        // 启动统计输出任务
        scheduler.scheduleAtFixedRate(
            this::logStats,
            30,
            30,
            TimeUnit.SECONDS
        );

        logger.info("MatchService initialized successfully");
    }

    /**
     * 关闭匹配服务
     */
    public void shutdown() {
        logger.info("MatchService shutting down... Total matches: {}", totalMatches.get());
        scheduler.shutdown();
        matchQueue.clear();
        playerIndex.clear();
    }

    // ==================== 公共 API ====================

    /**
     * 开始匹配
     * @param userId 用户ID
     * @param channel WebSocket 连接
     * @return 是否成功加入队列
     */
    public MatchResult startMatch(Long userId, Channel channel) {
        // 检查用户是否存在
        User user = userService.getUserById(userId);
        if (user == null) {
            return MatchResult.error("用户不存在");
        }

        // 检查是否已在队列中
        if (playerIndex.containsKey(userId)) {
            return MatchResult.error("您已在匹配队列中");
        }

        // 检查是否已在游戏中
        if (roomManager.getRoomByUserId(userId) != null) {
            return MatchResult.error("您已在游戏中，请先结束当前对局");
        }

        // 清除之前的对手记录（换桌功能）
        // 不仅清除自己的记录，还要清除指向自己的记录
        lastOpponents.remove(userId);

        // 查找并清除指向当前用户的记录
        Iterator<Map.Entry<Long, Long>> iter = lastOpponents.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, Long> entry = iter.next();
            if (entry.getValue().equals(userId)) {
                iter.remove();
                logger.info("清除对手 {} 指向用户 {} 的记录", entry.getKey(), userId);
            }
        }

        logger.info("用户 {} 开始匹配，已清除所有相关的对手记录", userId);

        logger.info("开始匹配 - 用户: {}, 上一个对手已清除", userId);

        // 创建匹配玩家
        MatchPlayer player = new MatchPlayer(
            userId,
            user.getUsername(),
            user.getNickname(),
            user.getRating(),
            channel
        );

        // 加入无锁队列
        matchQueue.offer(player);
        playerIndex.put(userId, player);
        queueJoinCount.incrementAndGet();

        logger.info("用户 {} ({}, 积分:{}) 加入匹配队列，当前队列: {}",
            userId, user.getNickname(), user.getRating(), matchQueue.size());

        return MatchResult.success();
    }

    /**
     * 取消匹配
     * @param userId 用户ID
     * @return 是否成功取消
     */
    public boolean cancelMatch(Long userId) {
        MatchPlayer player = playerIndex.remove(userId);
        if (player == null) {
            return false;
        }

        matchQueue.remove(player);
        logger.info("用户 {} 取消匹配", userId);
        return true;
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return matchQueue.size();
    }

    /**
     * 获取统计信息
     */
    public MatchStats getStats() {
        return new MatchStats(
            totalMatches.get(),
            totalTimeouts.get(),
            matchQueue.size(),
            queueJoinCount.get()
        );
    }

    // ==================== 核心匹配逻辑 ====================

    /**
     * 处理匹配 - 定时调用
     */
    private void processMatching() {
        // 防止重入
        if (!matchingInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            // 1. 清理无效玩家
            cleanupInactivePlayers();

            // 2. 检查超时玩家
            checkTimeouts();

            // 3. 转换队列为列表进行处理
            List<MatchPlayer> players = new ArrayList<>();
            Iterator<MatchPlayer> iter = matchQueue.iterator();
            while (iter.hasNext()) {
                MatchPlayer p = iter.next();
                if (p.isActive() && playerIndex.containsKey(p.getUserId())) {
                    players.add(p);
                }
            }

            if (players.isEmpty()) {
                return;
            }

            // 4. 测试模式：单人匹配机器人
            if (TEST_MODE && players.size() == 1) {
                MatchPlayer player = players.get(0);
                long waitTime = System.currentTimeMillis() - player.getEnqueueTime();
                if (waitTime >= TEST_MODE_DELAY) {
                    // 匹配机器人
                    matchWithBot(player);
                    return;
                }
            }

            // 5. 正常匹配：两两配对
            Set<MatchPlayer> matched = new HashSet<>();
            for (int i = 0; i < players.size(); i++) {
                MatchPlayer p1 = players.get(i);
                if (matched.contains(p1) || !p1.isActive()) {
                    continue;
                }

                // 计算允许的积分差（时间膨胀）
                long waitSeconds = p1.getWaitSeconds();
                int allowedDiff = calculateExpandedRatingDiff(waitSeconds);

                for (int j = i + 1; j < players.size(); j++) {
                    MatchPlayer p2 = players.get(j);
                    if (matched.contains(p2) || !p2.isActive()) {
                        continue;
                    }

                    // 检查是否可以匹配（包括检查是否是上一个对手）
                    if (p1.canMatch(p2, allowedDiff, lastOpponents)) {
                        // 执行匹配
                        if (doMatch(p1, p2)) {
                            matched.add(p1);
                            matched.add(p2);
                            break;
                        }
                    }
                }
            }

        } finally {
            matchingInProgress.set(false);
        }
    }

    /**
     * 执行匹配
     */
    private boolean doMatch(MatchPlayer p1, MatchPlayer p2) {
        try {
            // 从队列中移除
            matchQueue.remove(p1);
            matchQueue.remove(p2);
            playerIndex.remove(p1.getUserId());
            playerIndex.remove(p2.getUserId());

            // 记录对手关系（避免下次匹配到相同对手）
            lastOpponents.put(p1.getUserId(), p2.getUserId());
            lastOpponents.put(p2.getUserId(), p1.getUserId());

            // 生成房间ID
            String roomId = generateRoomId();

            // 随机决定先手
            boolean p1First = SecureRandomUtil.nextBoolean();

            // 发送匹配成功消息
            sendMatchSuccess(p1, p2, roomId, p1First);
            sendMatchSuccess(p2, p1, roomId, !p1First);

            // 创建游戏房间
            createGameRoom(p1, p2, roomId, p1First);

            totalMatches.incrementAndGet();

            logger.info("匹配成功: {}({}分) vs {}({}分), 房间: {}, 先手: {}",
                p1.getUserId(), p1.getRating(),
                p2.getUserId(), p2.getRating(),
                roomId, p1First ? p1.getNickname() : p2.getNickname());

            return true;

        } catch (Exception e) {
            logger.error("匹配执行失败", e);
            // 失败时重新加入队列
            matchQueue.offer(p1);
            matchQueue.offer(p2);
            playerIndex.put(p1.getUserId(), p1);
            playerIndex.put(p2.getUserId(), p2);
            return false;
        }
    }

    /**
     * 匹配机器人
     */
    private void matchWithBot(MatchPlayer player) {
        try {
            // 从队列中移除
            matchQueue.remove(player);
            playerIndex.remove(player.getUserId());

            // 生成房间ID
            String roomId = generateRoomId();

            // 创建机器人
            MatchPlayer bot = createBotPlayer(player);

            // 发送匹配成功消息（玩家执黑先手）
            sendMatchSuccess(player, bot, roomId, true);

            // 创建游戏房间
            createGameRoom(player, bot, roomId, true);

            totalMatches.incrementAndGet();

            logger.info("[TEST MODE] 匹配机器人: {} vs Bot_{}", player.getUserId(), bot.getUserId());

        } catch (Exception e) {
            logger.error("机器人匹配失败", e);
            // 失败时重新加入队列
            matchQueue.offer(player);
            playerIndex.put(player.getUserId(), player);
        }
    }

    /**
     * 清理无效玩家
     */
    private void cleanupInactivePlayers() {
        Iterator<MatchPlayer> iter = matchQueue.iterator();
        while (iter.hasNext()) {
            MatchPlayer player = iter.next();
            if (!player.isActive()) {
                iter.remove();
                playerIndex.remove(player.getUserId());
                logger.debug("清理断线玩家: {}", player.getUserId());
            }
        }
    }

    /**
     * 检查超时玩家
     */
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        Iterator<MatchPlayer> iter = matchQueue.iterator();
        while (iter.hasNext()) {
            MatchPlayer player = iter.next();
            if (player.isTimeout(MAX_QUEUE_TIME * 1000L)) {
                iter.remove();
                playerIndex.remove(player.getUserId());
                totalTimeouts.incrementAndGet();

                // 发送超时通知
                sendMatchFailed(player, "匹配超时，请稍后重试");

                logger.info("玩家 {} 匹配超时，等待时间: {}秒",
                    player.getUserId(), player.getWaitSeconds());
            }
        }
    }

    // ==================== 响应发送 ====================

    /**
     * 发送匹配成功消息
     */
    private void sendMatchSuccess(MatchPlayer player, MatchPlayer opponent,
                                   String roomId, boolean isFirst) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "MATCH_SUCCESS");
            response.put("roomId", roomId);
            response.put("color", isFirst ? "black" : "white");

            // 对手信息
            Map<String, Object> opponentInfo = new HashMap<>();
            opponentInfo.put("userId", opponent.getUserId());
            opponentInfo.put("nickname", opponent.getNickname());
            opponentInfo.put("username", opponent.getUsername());
            opponentInfo.put("rating", opponent.getRating());
            response.put("opponent", opponentInfo);

            // 棋盘初始状态（全0）
            int[][] board = new int[15][15];
            response.put("board", board);

            String json = objectMapper.writeValueAsString(response);
            player.getChannel().writeAndFlush(new TextWebSocketFrame(json));

            logger.debug("发送匹配成功消息给: {}", player.getUserId());

        } catch (Exception e) {
            logger.error("发送匹配成功消息失败", e);
        }
    }

    /**
     * 发送匹配失败消息
     */
    private void sendMatchFailed(MatchPlayer player, String reason) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "MATCH_FAIL");
            response.put("reason", reason);

            String json = objectMapper.writeValueAsString(response);
            if (player.getChannel().isActive()) {
                player.getChannel().writeAndFlush(new TextWebSocketFrame(json));
            }

        } catch (Exception e) {
            logger.error("发送匹配失败消息失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算时间膨胀后的积分差
     */
    private int calculateExpandedRatingDiff(long waitSeconds) {
        int expansion = (int) (waitSeconds * EXPANSION_RATE);
        int expanded = INITIAL_RATING_DIFF + expansion;
        return Math.min(expanded, MAX_RATING_DIFF);
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return String.format("%06d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000);
    }

    /**
     * 创建游戏房间
     */
    private void createGameRoom(MatchPlayer p1, MatchPlayer p2, String roomId, boolean p1First) {
        GameRoom room = roomManager.createRoom();

        Long blackId = p1First ? p1.getUserId() : p2.getUserId();
        Long whiteId = p1First ? p2.getUserId() : p1.getUserId();
        Channel blackCh = p1First ? p1.getChannel() : p2.getChannel();
        Channel whiteCh = p1First ? p2.getChannel() : p1.getChannel();

        room.startGame(blackId, blackCh, whiteId, whiteCh);
        roomManager.joinRoom(roomId, blackId, blackCh);
        roomManager.joinRoom(roomId, whiteId, whiteCh);

        // 更新用户状态
        userService.updateUserStatus(blackId, 2); // 2 = 游戏中
        userService.updateUserStatus(whiteId, 2);

        logger.info("房间 {} 创建完成: 黑={}, 白={}", roomId, blackId, whiteId);
    }

    /**
     * 创建机器人玩家
     */
    private MatchPlayer createBotPlayer(MatchPlayer realPlayer) {
        long botId = -(realPlayer.getUserId() + 1000000);
        // 机器人不需要真实的Channel
        Channel botChannel = null;

        return new MatchPlayer(
            botId,
            "Bot_" + (Math.abs(botId) % 1000),
            "五子棋机器人",
            realPlayer.getRating(),
            botChannel
        );
    }

    /**
     * 输出统计日志
     */
    private void logStats() {
        logger.info("MatchService统计 - 总匹配: {}, 超时: {}, 当前队列: {}",
            totalMatches.get(), totalTimeouts.get(), matchQueue.size());
    }

    // ==================== 内部类 ====================

    /**
     * 匹配玩家 - 包装类
     */
    private static class MatchPlayer {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final Integer rating;
        private final Channel channel;
        private final long enqueueTime;

        public MatchPlayer(Long userId, String username, String nickname, Integer rating, Channel channel) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.channel = channel;
            this.enqueueTime = System.currentTimeMillis();
        }

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public Integer getRating() { return rating; }
        public Channel getChannel() { return channel; }
        public long getEnqueueTime() { return enqueueTime; }

        public long getWaitSeconds() {
            return (System.currentTimeMillis() - enqueueTime) / 1000;
        }

        public boolean isActive() {
            // 机器人玩家（userId < 0）始终活跃
            if (userId < 0) {
                return true;
            }
            return channel != null && channel.isActive();
        }

        public boolean isTimeout(long maxMillis) {
            return System.currentTimeMillis() - enqueueTime > maxMillis;
        }

        public boolean canMatch(MatchPlayer other, int maxDiff, Map<Long, Long> lastOpponents) {
            if (!this.isActive() || !other.isActive()) {
                return false;
            }
            // 检查是否是上一个对手（避免连续匹配相同对手）
            Long myLastOpponent = lastOpponents.get(this.userId);
            Long otherLastOpponent = lastOpponents.get(other.userId);

            // 调试日志
            if (myLastOpponent != null || otherLastOpponent != null) {
                logger.info("检查对手匹配 - 我方ID: {}, 上一个对手: {}, 对方ID: {}, 对方上一个对手: {}",
                    this.userId, myLastOpponent, other.userId, otherLastOpponent);
            }

            if (this.userId.equals(otherLastOpponent) || other.userId.equals(myLastOpponent)) {
                logger.info("跳过匹配 - 上一个对手，我方ID: {}, 对方ID: {}", this.userId, other.userId);
                return false;
            }
            return Math.abs(this.rating - other.rating) <= maxDiff;
        }
    }

    /**
     * 匹配结果
     */
    public static class MatchResult {
        private final boolean success;
        private final String message;

        public MatchResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static MatchResult success() {
            return new MatchResult(true, "success");
        }

        public static MatchResult error(String message) {
            return new MatchResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /**
     * 匹配统计
     */
    public static class MatchStats {
        private final long totalMatches;
        private final long totalTimeouts;
        private final int queueSize;
        private final long totalJoins;

        public MatchStats(long totalMatches, long totalTimeouts, int queueSize, long totalJoins) {
            this.totalMatches = totalMatches;
            this.totalTimeouts = totalTimeouts;
            this.queueSize = queueSize;
            this.totalJoins = totalJoins;
        }

        public long getTotalMatches() { return totalMatches; }
        public long getTotalTimeouts() { return totalTimeouts; }
        public int getQueueSize() { return queueSize; }
        public long getTotalJoins() { return totalJoins; }

        public double getSuccessRate() {
            return totalJoins > 0 ? (double) totalMatches / totalJoins * 100 : 0;
        }
    }
}
