package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.game.Board;
import com.gobang.core.game.GameState;
import com.gobang.core.game.WinCheckerUtil;
import com.gobang.core.match.Player;
import com.gobang.core.rating.ELOCalculator;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.util.SecureRandomUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 游戏服务 - 完整实现
 *
 * 功能：
 * 1. 匹配管理（使用Redis存储匹配队列）
 * 2. 房间管理
 * 3. 落子处理
 * 4. 胜负判定
 * 5. 积分计算
 * 6. 对局记录
 */
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    // ==================== 依赖 ====================
    private final RoomManager roomManager;
    private final UserService userService;
    private final SqlSessionFactory sqlSessionFactory;
    private final ObjectMapper objectMapper;
    private final MatchQueueService matchQueueService;
    private final com.gobang.util.RedisUtil redisUtil;

    // ==================== 本地匹配队列（当Redis不可用时使用）====================
    private final Map<Long, LocalMatchPlayer> localCasualQueue = new ConcurrentHashMap<>();
    private final Map<Long, LocalMatchPlayer> localRankedQueue = new ConcurrentHashMap<>();

    // ==================== 本地Channel映射（不存储到Redis）====================
    private final Map<Long, Channel> localChannels = new ConcurrentHashMap<>();

    // ==================== 统计 ====================
    private final Map<Long, Long> matchStartTime = new ConcurrentHashMap<>();

    // ==================== 定时任务 ====================
    private final ScheduledExecutorService scheduler;

    // ==================== 配置 ====================
    private final int ratingDiff;
    private final int maxQueueTime;
    private final int checkInterval;
    private final boolean testMode;  // 测试模式：单人可与机器人匹配
    private final int testMatchDelay;

    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory,
                       MatchQueueService matchQueueService, com.gobang.util.RedisUtil redisUtil,
                       int ratingDiff, int maxQueueTime, int checkInterval, boolean testMode, int testMatchDelay) {
        this.roomManager = roomManager;
        this.userService = userService;
        this.sqlSessionFactory = sqlSessionFactory;
        this.objectMapper = new ObjectMapper();
        this.matchQueueService = matchQueueService;
        this.redisUtil = redisUtil;
        this.scheduler = Executors.newScheduledThreadPool(2);

        // 配置参数
        this.ratingDiff = ratingDiff;
        this.maxQueueTime = maxQueueTime;
        this.checkInterval = checkInterval;
        this.testMode = testMode;
        this.testMatchDelay = testMatchDelay;

        // 启动匹配任务
        startMatchingTask();

        // 自动运行数据库迁移
        runDatabaseMigrations();

        logger.info("GameService initialized with testMode={}, testMatchDelay={}ms", testMode, testMatchDelay);
    }

    /**
     * 运行数据库迁移
     */
    private void runDatabaseMigrations() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            // 检查game_mode列是否存在
            java.sql.Connection conn = session.getConnection();
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            java.sql.ResultSet rs = meta.getColumns(null, null, "game_record", "game_mode");

            if (!rs.next()) {
                // 列不存在，添加列
                logger.info("检测到game_mode列不存在，正在添加...");
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE game_record ADD COLUMN game_mode VARCHAR(20) DEFAULT 'pvp_online' COMMENT '游戏模式: pve(人机), pvp_online(在线对战), pvp_local(本地对战)'");
                    logger.info("成功添加game_mode列");
                }
            } else {
                logger.info("game_mode列已存在，跳过迁移");
            }
            rs.close();
        } catch (Exception e) {
            logger.error("数据库迁移失败", e);
        }
    }

    // 兼容旧构造函数（不使用MatchQueueService）
    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory, com.gobang.util.RedisUtil redisUtil) {
        this(roomManager, userService, sqlSessionFactory, null, redisUtil, 100, 300, 100, false, 3000);
    }

    // 兼容旧构造函数
    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory,
                       int ratingDiff, int maxQueueTime, int checkInterval, boolean testMode, int testMatchDelay,
                       com.gobang.util.RedisUtil redisUtil) {
        this(roomManager, userService, sqlSessionFactory, null, redisUtil, ratingDiff, maxQueueTime, checkInterval, testMode, testMatchDelay);
    }

    // 兼容旧构造函数（不使用RedisUtil）
    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory,
                       MatchQueueService matchQueueService,
                       int ratingDiff, int maxQueueTime, int checkInterval, boolean testMode, int testMatchDelay) {
        this(roomManager, userService, sqlSessionFactory, matchQueueService, null, ratingDiff, maxQueueTime, checkInterval, testMode, testMatchDelay);
    }

    // 兼容旧构造函数（不使用MatchQueueService和RedisUtil）
    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory) {
        this(roomManager, userService, sqlSessionFactory, null, null, 100, 300, 100, false, 3000);
    }

    // ==================== 匹配功能 ====================

    /**
     * 开始匹配
     * @param mode 游戏模式: "casual"=休闲, "ranked"=竞技
     */
    public boolean startMatch(Long userId, String username, String nickname, Integer rating, Channel channel, String mode) {
        // 先清理该用户可能存在的旧匹配记录
        cancelMatch(userId);

        // 存储Channel引用到本地
        localChannels.put(userId, channel);

        User user = userService.getUserById(userId);
        // 游客用户可能没有数据库记录
        if (userId >= 0 && user == null) {
            sendError(channel, "用户不存在", 13);
            return false;
        }

        if (roomManager.getRoomByUserId(userId) != null) {
            sendError(channel, "您已在游戏中，请先结束当前对局", 13);
            return false;
        }

        boolean success;
        if (matchQueueService != null) {
            // 使用Redis匹配队列
            success = matchQueueService.addToQueue(userId, username, nickname, rating, channel, mode);
        } else {
            // 使用本地内存匹配队列
            logger.info("使用本地匹配队列: userId={}, mode={}", userId, mode);
            LocalMatchPlayer player = new LocalMatchPlayer(userId, username, nickname, rating, channel, mode);
            Map<Long, LocalMatchPlayer> targetQueue = "casual".equals(mode) ? localCasualQueue : localRankedQueue;
            targetQueue.put(userId, player);
            logger.info("已添加到本地{}队列: userId={}, 队列大小={}", mode, userId, targetQueue.size());
            success = true;
        }

        if (!success) {
            sendError(channel, "匹配失败，您可能已在匹配队列中", 13);
            return false;
        }

        // 记录匹配开始时间
        matchStartTime.put(userId, System.currentTimeMillis());

        // 发送成功响应（延迟发送，让客户端先进入匹配状态）
        sendSuccess(channel, "已加入匹配队列，请稍候...", 10);

        logger.info("用户 {} ({}, 积分:{}, 模式:{}) 加入匹配队列", userId, nickname, rating, mode);
        return true;
    }

    /**
     * 清理用户从匹配队列
     */
    private void cleanupUserFromQueue(Long userId) {
        localChannels.remove(userId);
        matchStartTime.remove(userId);
        if (matchQueueService != null) {
            matchQueueService.removeFromQueue(userId);
        }
        logger.info("清理用户 {} 的旧匹配记录", userId);
    }

    /**
     * 取消匹配
     */
    public boolean cancelMatch(Long userId) {
        localChannels.remove(userId);
        matchStartTime.remove(userId);

        boolean success = true;
        if (matchQueueService != null) {
            success = matchQueueService.removeFromQueue(userId);
        } else {
            // 从本地队列中移除
            localCasualQueue.remove(userId);
            localRankedQueue.remove(userId);
            logger.info("用户 {} 从本地匹配队列移除", userId);
        }

        if (success) {
            logger.info("用户 {} 取消匹配", userId);
        }
        return success;
    }

    /**
     * 开始人机对战（直接匹配机器人，不进入匹配队列）
     */
    public boolean startBotMatch(Long userId, String username, String nickname, Integer rating, Channel channel) {
        // 检查是否已在房间中
        if (roomManager.getRoomByUserId(userId) != null) {
            sendError(channel, "您已在游戏中，请先结束当前对局", 13);
            return false;
        }

        // 先清理匹配队列中的记录
        cancelMatch(userId);

        try {
            GameRoom room = roomManager.createRoom();

            // 创建机器人
            long botId = -(userId + 1000000);
            Channel botChannel = null;

            room.startGame(userId, channel, botId, botChannel);
            room.setGameMode("pve"); // 人机对战模式

            // 发送匹配成功消息
            Map<String, Object> botInfo = new HashMap<>();
            botInfo.put("user_id", String.valueOf(botId));
            botInfo.put("username", "Bot_" + (Math.abs(botId) % 1000));
            botInfo.put("nickname", "五子棋机器人");
            botInfo.put("rating", rating);

            Map<String, Object> body = new HashMap<>();
            body.put("room_id", room.getRoomId());
            body.put("is_first", true);
            body.put("my_color", 1);
            body.put("opponent", botInfo);

            Map<String, Object> response = new HashMap<>();
            response.put("type", 12);
            response.put("sequenceId", 0);
            response.put("timestamp", System.currentTimeMillis());
            response.put("body", body);

            String json = objectMapper.writeValueAsString(response);
            channel.writeAndFlush(new TextWebSocketFrame(json));

            broadcastGameState(room);

            logger.info("[人机对战] {} ({}) vs Bot_{}", userId, nickname, botId);
            return true;

        } catch (Exception e) {
            logger.error("人机对战启动失败", e);
            sendError(channel, "启动人机对战失败", 13);
            return false;
        }
    }

    /**
     * 处理匹配
     */
    private void processMatching() {
        try {
            if (matchQueueService != null) {
                // 使用Redis匹配队列
                processMatchingWithRedis();
            } else {
                // 使用本地内存匹配队列
                processMatchingLocal();
            }

            // 定期清理过期玩家
            if (matchQueueService != null) {
                matchQueueService.cleanupExpiredPlayers();
            }

        } catch (Exception e) {
            logger.error("处理匹配时发生错误", e);
        }
    }

    /**
     * 使用Redis队列处理匹配
     */
    private void processMatchingWithRedis() {
        logger.info("=== processMatchingWithRedis 开始 ===");
        logger.info("localChannels 大小: {}", localChannels.size());
        logger.info("localChannels 内容: {}", localChannels.keySet());

        // 获取队列统计
        MatchQueueService.QueueStats stats = matchQueueService.getQueueStats();
        logger.info("队列统计 - 休闲: {}人, 竞技: {}人", stats.getCasualCount(), stats.getRankedCount());

        // 处理休闲模式匹配（简单两两配对）
        // 休闲模式只能和休闲模式匹配
        List<MatchQueueService.MatchPair> casualPairs = matchQueueService.matchCasual(localChannels);
        logger.info("休闲模式匹配结果: {} 对", casualPairs.size());
        for (MatchQueueService.MatchPair pair : casualPairs) {
            logger.info("休闲匹配对: {}(mode={}) vs {}(mode={})",
                pair.getPlayer1().getUserId(), pair.getPlayer1().getMode(),
                pair.getPlayer2().getUserId(), pair.getPlayer2().getMode());
        }

        for (MatchQueueService.MatchPair pair : casualPairs) {
            try {
                createAndStartGame(pair.getPlayer1(), pair.getPlayer2());
                // 匹配成功后，从 localChannels 移除玩家
                localChannels.remove(pair.getPlayer1().getUserId());
                localChannels.remove(pair.getPlayer2().getUserId());
                logger.info("休闲匹配成功后，从 localChannels 移除: {} 和 {}",
                    pair.getPlayer1().getUserId(), pair.getPlayer2().getUserId());
            } catch (Exception e) {
                logger.error("休闲模式匹配失败", e);
                // 失败时将玩家放回队列
                matchQueueService.addToQueue(
                    pair.getPlayer1().getUserId(),
                    pair.getPlayer1().getUsername(),
                    pair.getPlayer1().getNickname(),
                    pair.getPlayer1().getRating(),
                    pair.getPlayer1().getChannel(),
                    "casual"
                );
            }
        }

        // 处理竞技模式匹配（根据积分配对）
        // 竞技模式只能和竞技模式匹配
        List<MatchQueueService.MatchPair> rankedPairs = matchQueueService.matchRanked(localChannels, ratingDiff);
        logger.info("竞技模式匹配结果: {} 对", rankedPairs.size());
        for (MatchQueueService.MatchPair pair : rankedPairs) {
            logger.info("竞技匹配对: {}(mode={}) vs {}(mode={})",
                pair.getPlayer1().getUserId(), pair.getPlayer1().getMode(),
                pair.getPlayer2().getUserId(), pair.getPlayer2().getMode());
        }

        for (MatchQueueService.MatchPair pair : rankedPairs) {
            try {
                createAndStartGame(pair.getPlayer1(), pair.getPlayer2());
                // 匹配成功后，从 localChannels 移除玩家
                localChannels.remove(pair.getPlayer1().getUserId());
                localChannels.remove(pair.getPlayer2().getUserId());
                logger.info("竞技匹配成功后，从 localChannels 移除: {} 和 {}",
                    pair.getPlayer1().getUserId(), pair.getPlayer2().getUserId());
            } catch (Exception e) {
                logger.error("竞技模式匹配失败", e);
                // 失败时将玩家放回队列
                matchQueueService.addToQueue(
                    pair.getPlayer1().getUserId(),
                    pair.getPlayer1().getUsername(),
                    pair.getPlayer1().getNickname(),
                    pair.getPlayer1().getRating(),
                    pair.getPlayer1().getChannel(),
                    "ranked"
                );
            }
        }
    }

    /**
     * 使用本地内存队列处理匹配（当Redis不可用时使用）
     */
    private void processMatchingLocal() {
        // 处理休闲模式匹配
        processLocalQueue(localCasualQueue, "casual");

        // 处理竞技模式匹配
        processLocalQueue(localRankedQueue, "ranked");
    }

    /**
     * 处理本地匹配队列
     */
    private void processLocalQueue(Map<Long, LocalMatchPlayer> queue, String mode) {
        // 清理不活跃或超时的玩家
        long now = System.currentTimeMillis();
        queue.entrySet().removeIf(entry -> {
            LocalMatchPlayer player = entry.getValue();
            boolean timeout = now - player.enqueueTime > maxQueueTime * 1000L;
            boolean inactive = !player.channel.isActive();
            if (timeout || inactive) {
                logger.info("清理{}队列玩家: userId={}, timeout={}, inactive={}", mode, player.userId, timeout, inactive);
                localChannels.remove(player.userId);
                matchStartTime.remove(player.userId);
                return true;
            }
            return false;
        });

        // 获取活跃玩家列表
        List<LocalMatchPlayer> activePlayers = new ArrayList<>(queue.values());
        logger.info("本地{}队列: {} 人等待匹配", mode, activePlayers.size());

        // 两两配对
        for (int i = 0; i < activePlayers.size() - 1; i += 2) {
            LocalMatchPlayer p1 = activePlayers.get(i);
            LocalMatchPlayer p2 = activePlayers.get(i + 1);

            logger.info("本地{}匹配: {}({}) vs {}({})", mode, p1.userId, p1.nickname, p2.userId, p2.nickname);

            try {
                createAndStartGameLocal(p1, p2);

                // 从队列中移除
                queue.remove(p1.userId);
                queue.remove(p2.userId);
                localChannels.remove(p1.userId);
                localChannels.remove(p2.userId);

                logger.info("本地匹配成功，已移除: {} 和 {}", p1.userId, p2.userId);
            } catch (Exception e) {
                logger.error("本地{}匹配失败", mode, e);
            }
        }
    }

    /**
     * 处理匹配（旧版本，兼容用 - 已禁用）
     */
    @SuppressWarnings("unused")
    private void processMatchingOld() {
        logger.warn("旧版本匹配逻辑已禁用，请使用Redis匹配队列");
    }

    /**
     * 创建并开始游戏
     */
    private String createAndStartGame(MatchQueueService.MatchPlayer p1, MatchQueueService.MatchPlayer p2) {
        GameRoom room = roomManager.createRoom();

        boolean p1First = SecureRandomUtil.nextBoolean();
        Long blackId = p1First ? p1.getUserId() : p2.getUserId();
        Long whiteId = p1First ? p2.getUserId() : p1.getUserId();
        Channel blackCh = p1First ? p1.getChannel() : p2.getChannel();
        Channel whiteCh = p1First ? p2.getChannel() : p1.getChannel();

        room.startGame(blackId, blackCh, whiteId, whiteCh);

        // 根据玩家模式设置游戏模式（casual=休闲不计算积分，ranked=竞技计算积分）
        String gameMode = p1.getMode(); // 两个玩家应该有相同的模式
        room.setGameMode(gameMode);

        // 将玩家添加到房间管理器的映射中（用于重连时查找房间）
        roomManager.joinRoom(room.getRoomId(), blackId, blackCh);
        roomManager.joinRoom(room.getRoomId(), whiteId, whiteCh);

        // 发送匹配成功消息
        sendMatchSuccess(p1, p2, room, p1First);
        sendMatchSuccess(p2, p1, room, !p1First);

        // 发送初始游戏状态
        broadcastGameState(room);

        return room.getRoomId();
    }

    /**
     * 创建并开始游戏（旧版本，兼容用）
     */
    @SuppressWarnings("unused")
    private String createAndStartGameOld(MatchPlayer p1, MatchPlayer p2) {
        GameRoom room = roomManager.createRoom();

        boolean p1First = SecureRandomUtil.nextBoolean();
        Long blackId = p1First ? p1.getUserId() : p2.getUserId();
        Long whiteId = p1First ? p2.getUserId() : p1.getUserId();
        Channel blackCh = p1First ? p1.getChannel() : p2.getChannel();
        Channel whiteCh = p1First ? p2.getChannel() : p1.getChannel();

        room.startGame(blackId, blackCh, whiteId, whiteCh);

        // 根据玩家模式设置游戏模式（casual=休闲不计算积分，ranked=竞技计算积分）
        String gameMode = p1.getMode(); // 两个玩家应该有相同的模式
        room.setGameMode(gameMode);

        // 将玩家添加到房间管理器的映射中（用于重连时查找房间）
        roomManager.joinRoom(room.getRoomId(), blackId, blackCh);
        roomManager.joinRoom(room.getRoomId(), whiteId, whiteCh);

        // 发送匹配成功消息
        sendMatchSuccessOld(p1, p2, room, p1First);
        sendMatchSuccessOld(p2, p1, room, !p1First);

        // 发送初始游戏状态
        broadcastGameState(room);

        return room.getRoomId();
    }

    /**
     * 发送匹配成功消息
     */
    private void sendMatchSuccess(MatchQueueService.MatchPlayer player, MatchQueueService.MatchPlayer opponent, GameRoom room, boolean isFirst) {
        try {
            Map<String, Object> opponentInfo = new HashMap<>();
            opponentInfo.put("user_id", String.valueOf(opponent.getUserId()));
            opponentInfo.put("username", opponent.getUsername());
            opponentInfo.put("nickname", opponent.getNickname());
            opponentInfo.put("rating", opponent.getRating());

            Map<String, Object> body = new HashMap<>();
            body.put("room_id", room.getRoomId());
            body.put("is_first", isFirst);
            body.put("my_color", isFirst ? 1 : 2);
            body.put("opponent", opponentInfo);

            Map<String, Object> response = new HashMap<>();
            response.put("type", 12); // MATCH_SUCCESS
            response.put("sequenceId", 0);
            response.put("timestamp", System.currentTimeMillis());
            response.put("body", body);

            String json = objectMapper.writeValueAsString(response);
            player.getChannel().writeAndFlush(new TextWebSocketFrame(json));

            logger.info("✓ 发送匹配成功消息给用户 {} 房间{} 执{}",
                player.getUserId(), room.getRoomId(), isFirst ? "黑" : "白");

        } catch (Exception e) {
            logger.error("发送匹配成功消息失败", e);
        }
    }

    /**
     * 发送匹配成功消息（旧版本，兼容用）
     */
    @SuppressWarnings("unused")
    private void sendMatchSuccessOld(MatchPlayer player, MatchPlayer opponent, GameRoom room, boolean isFirst) {
        try {
            Map<String, Object> opponentInfo = new HashMap<>();
            opponentInfo.put("user_id", String.valueOf(opponent.getUserId()));
            opponentInfo.put("username", opponent.getUsername());
            opponentInfo.put("nickname", opponent.getNickname());
            opponentInfo.put("rating", opponent.getRating());

            Map<String, Object> body = new HashMap<>();
            body.put("room_id", room.getRoomId());
            body.put("is_first", isFirst);
            body.put("my_color", isFirst ? 1 : 2);
            body.put("opponent", opponentInfo);

            Map<String, Object> response = new HashMap<>();
            response.put("type", 12); // MATCH_SUCCESS
            response.put("sequenceId", 0);
            response.put("timestamp", System.currentTimeMillis());
            response.put("body", body);

            String json = objectMapper.writeValueAsString(response);
            player.getChannel().writeAndFlush(new TextWebSocketFrame(json));

            logger.info("✓ 发送匹配成功消息给用户 {} 房间{} 执{}",
                player.getUserId(), room.getRoomId(), isFirst ? "黑" : "白");

        } catch (Exception e) {
            logger.error("发送匹配成功消息失败", e);
        }
    }

    /**
     * 匹配机器人
     */
    private void matchWithBot(MatchPlayer player) {
        try {
            GameRoom room = roomManager.createRoom();

            // 创建机器人
            long botId = -(player.getUserId() + 1000000);
            Channel botChannel = null; // 机器人不需要真实的Channel

            room.startGame(player.getUserId(), player.getChannel(), botId, botChannel);
            room.setGameMode("pve"); // 人机对战模式

            // 发送匹配成功消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", 12);
            response.put("sequenceId", 0);
            response.put("timestamp", System.currentTimeMillis());
            response.put("room_id", room.getRoomId());
            response.put("is_first", true);
            response.put("my_color", 1);

            Map<String, Object> botInfo = new HashMap<>();
            botInfo.put("user_id", String.valueOf(botId));
            botInfo.put("username", "Bot_" + (Math.abs(botId) % 1000));
            botInfo.put("nickname", "五子棋机器人");
            botInfo.put("rating", player.getRating());
            response.put("opponent", botInfo);

            String json = objectMapper.writeValueAsString(response);
            player.getChannel().writeAndFlush(new TextWebSocketFrame(json));

            broadcastGameState(room);

            logger.info("[TEST MODE] 机器人匹配: {} ({}) vs Bot_{}", player.getUserId(), player.getMode(), botId);

        } catch (Exception e) {
            logger.error("机器人匹配失败", e);
        }
    }

    // ==================== 游戏操作 ====================

    /**
     * 落子
     */
    public MoveResult makeMove(Long userId, String roomId, int x, int y) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return MoveResult.error("房间不存在");
        }

        logger.info("🎮 玩家 {} 在房间 {} 落子 ({}, {})", userId, roomId, x, y);

        int result = room.makeMove(userId, x, y);

        if (result == 0) {
            // 保存游戏状态（用于断线重连）
            saveGameState(room);

            // 更新玩家活动时间
            updatePlayerActivity(roomId, userId);

            broadcastGameState(room);

            logger.info("🎮 落子成功，房间 {} 游戏状态: {}", roomId, room.getGameState());

            if (room.getGameState() == GameState.FINISHED) {
                logger.info("🎮 游戏 {} 结束，调用 handleGameOver", roomId);
                handleGameOver(room);
            } else {
                // 检查是否轮到机器人落子
                scheduleBotMove(room);
            }
            return MoveResult.success();
        }

        String[] errorMessages = {
            "", // 0 = 成功
            "位置无效", // 1
            "不是你的回合", // 2
            "游戏已结束", // 3
            "该位置已有棋子" // 4
        };

        return MoveResult.error(errorMessages[result]);
    }

    /**
     * 认输
     */
    public void resign(Long userId, String roomId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) return;

        Long winnerId = userId.equals(room.getBlackPlayerId())
            ? room.getWhitePlayerId()
            : room.getBlackPlayerId();

        room.resign(userId);
        handleGameOver(room, winnerId, 3);
    }

    /**
     * 处理游戏结束（公开方法，供WebSocketHandler调用）
     * @param room 游戏房间
     * @param winnerId 获胜者ID
     * @param endReason 结束原因：0=正常胜负, 1=认输, 2=平局, 3=超时
     */
    public void endGame(GameRoom room, Long winnerId, int endReason) {
        handleGameOver(room, winnerId, endReason);
    }

    /**
     * 获取用户的未完成游戏
     */
    public Map<String, Object> getUnfinishedGame(Long userId) {
        logger.info("检查未完成对局: userId={}", userId);
        GameRoom room = roomManager.getRoomByUserId(userId);
        logger.info("getRoomByUserId返回: room={}", room);
        if (room != null) {
            logger.info("房间状态: gameState={}, roomId={}", room.getGameState(), room.getRoomId());
            if (room.getGameState() == com.gobang.core.game.GameState.PLAYING) {
                Map<String, Object> gameInfo = new HashMap<>();
                gameInfo.put("room_id", room.getRoomId());
                gameInfo.put("game_state", room.getGameState().name());
                gameInfo.put("board", room.getBoard().toArray());
                gameInfo.put("current_player", room.getCurrentPlayer());
                gameInfo.put("move_count", room.getMoves().size());
                gameInfo.put("game_mode", room.getGameMode());

                // 添加玩家信息
                Long opponentId = userId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();
                if (opponentId != null) {
                    User opponent = userService.getUserById(opponentId);
                    if (opponent != null) {
                        Map<String, Object> opponentInfo = new HashMap<>();
                        opponentInfo.put("user_id", String.valueOf(opponent.getId()));
                        opponentInfo.put("username", opponent.getUsername());
                        opponentInfo.put("nickname", opponent.getNickname());
                        opponentInfo.put("rating", opponent.getRating());
                        gameInfo.put("opponent", opponentInfo);
                    }
                }

                // 添加玩家颜色信息
                int playerColor = userId.equals(room.getBlackPlayerId()) ? 1 : 2;
                gameInfo.put("my_color", playerColor);

                // 添加双方玩家ID
                gameInfo.put("black_player_id", room.getBlackPlayerId());
                gameInfo.put("white_player_id", room.getWhitePlayerId());

                logger.info("找到未完成对局: roomId={}, gameMode={}", room.getRoomId(), room.getGameMode());
                return gameInfo;
            } else {
                logger.info("房间状态不是PLAYING: {}", room.getGameState());
            }
        } else {
            logger.info("用户不在任何房间中");
        }
        return null;
    }

    /**
     * 根据房间ID获取房间
     */
    public GameRoom getRoomById(String roomId) {
        return roomManager.getRoom(roomId);
    }

    /**
     * 获取所有活跃游戏列表（用于管理员或调试）
     */
    public List<Map<String, Object>> getActiveGames() {
        List<Map<String, Object>> activeGames = new ArrayList<>();
        for (GameRoom room : roomManager.getPlayingRooms()) {
            Map<String, Object> gameInfo = new HashMap<>();
            gameInfo.put("room_id", room.getRoomId());
            gameInfo.put("black_player_id", room.getBlackPlayerId());
            gameInfo.put("white_player_id", room.getWhitePlayerId());
            gameInfo.put("current_player", room.getCurrentPlayer());
            gameInfo.put("move_count", room.getMoves().size());
            gameInfo.put("game_mode", room.getGameMode());
            gameInfo.put("game_state", room.getGameState().name());
            activeGames.add(gameInfo);
        }
        return activeGames;
    }

    /**
     * 处理游戏结束
     */
    private void handleGameOver(GameRoom room) {
        // 获取最后落子的玩家ID（这个玩家赢了）
        Long winnerId = room.getLastMovePlayerId();

        if (winnerId == null) {
            // 平局情况
            handleGameOver(room, null, 2);
        } else {
            // 有胜负
            handleGameOver(room, winnerId, 0);
        }
    }

    /**
     * 处理游戏结束
     */
    private void handleGameOver(GameRoom room, Long winnerId, int endReason) {
        // 获取游戏模式
        String gameMode = room.getGameMode();
        boolean isCasual = "casual".equals(gameMode);

        logger.info("🏁 handleGameOver 被调用 - 房间: {}, 模式: {}, 胜者: {}, 原因: {}",
            room.getRoomId(), gameMode, winnerId, endReason);

        // 获取用户信息
        User blackUser = userService.getUserById(room.getBlackPlayerId());
        User whiteUser = userService.getUserById(room.getWhitePlayerId());

        // 如果用户不存在（可能是游客），使用默认积分
        int blackRatingBefore = (blackUser != null) ? blackUser.getRating() : 1200;
        int whiteRatingBefore = (whiteUser != null) ? whiteUser.getRating() : 1200;

        int[] ratingChanges = new int[4]; // [newBlackRating, newWhiteRating, blackChange, whiteChange]

        if (isCasual) {
            // 休闲模式：不计算积分变化，但记录场次和经验值
            ratingChanges[0] = blackRatingBefore;
            ratingChanges[1] = whiteRatingBefore;
            ratingChanges[2] = 0;
            ratingChanges[3] = 0;

            // 休闲模式也增加经验值，用于统计活跃度
            if (blackUser != null) {
                int exp = endReason == 2 ? 5 : (winnerId != null && winnerId.equals(room.getBlackPlayerId()) ? 10 : 2);
                userService.updateUserRating(room.getBlackPlayerId(), blackRatingBefore, exp);
            }

            if (whiteUser != null) {
                int exp = endReason == 2 ? 5 : (winnerId != null && winnerId.equals(room.getWhitePlayerId()) ? 10 : 2);
                userService.updateUserRating(room.getWhitePlayerId(), whiteRatingBefore, exp);
            }
        } else {
            // 竞技模式：计算积分变化
            // endReason: 0=胜利, 1=失败, 2=平局, 3=认输, 4=超时
            if (endReason == 2) {
                // 平局
                ratingChanges = ELOCalculator.calculateDrawRatingChange(blackRatingBefore, whiteRatingBefore);
            } else {
                // 有胜负
                boolean blackWins = winnerId != null && winnerId.equals(room.getBlackPlayerId());
                if (blackWins) {
                    ratingChanges = ELOCalculator.calculateRatingChange(blackRatingBefore, whiteRatingBefore);
                } else {
                    ratingChanges = ELOCalculator.calculateRatingChange(whiteRatingBefore, blackRatingBefore);
                    // 交换顺序，使得 ratingChanges[0] 是黑方变化，ratingChanges[1] 是白方变化
                    int temp = ratingChanges[0];
                    ratingChanges[0] = ratingChanges[1];
                    ratingChanges[1] = temp;
                }
            }

            // 更新积分（仅竞技模式）
            if (blackUser != null) {
                int blackRatingAfter = blackRatingBefore + ratingChanges[2];
                int exp = endReason == 2 ? 5 : (winnerId != null && winnerId.equals(room.getBlackPlayerId()) ? 10 : 2);
                userService.updateUserRating(room.getBlackPlayerId(), blackRatingAfter, exp);
            }

            if (whiteUser != null) {
                int whiteRatingAfter = whiteRatingBefore + ratingChanges[3];
                int exp = endReason == 2 ? 5 : (winnerId != null && winnerId.equals(room.getWhitePlayerId()) ? 10 : 2);
                userService.updateUserRating(room.getWhitePlayerId(), whiteRatingAfter, exp);
            }
        }

        // 发送游戏结束消息
        sendGameOver(room, winnerId, endReason, ratingChanges, isCasual);

        // 保存对局记录
        saveGameRecord(room, winnerId, endReason, ratingChanges);

        // 更新用户状态
        if (room.getBlackPlayerId() >= 0) {
            userService.updateUserStatus(room.getBlackPlayerId(), 0);
        }
        if (room.getWhitePlayerId() >= 0) {
            userService.updateUserStatus(room.getWhitePlayerId(), 0);
        }

        // 清除游戏状态（Redis中的数据）
        clearGameState(room);

        // 清理用户到房间的映射，让玩家可以立即开始新的匹配
        roomManager.removeRoom(room.getRoomId());

        logger.info("游戏结束 - 房间: {}, 模式: {}, 胜者: {}, 原因: {}, 积分变化: {}",
            room.getRoomId(), gameMode, winnerId, endReason, Arrays.toString(ratingChanges));
    }

    /**
     * 发送游戏状态
     */
    private void broadcastGameState(GameRoom room) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("room_id", room.getRoomId());
            body.put("board", room.getBoard().toArray());
            body.put("current_player", room.getCurrentPlayer());
            body.put("move_count", room.getMoves().size());
            body.put("game_state", room.getGameState().name());
            body.put("black_remaining_time", room.getBlackPlayerRemainingTime());
            body.put("white_remaining_time", room.getWhitePlayerRemainingTime());

            Map<String, Object> response = new HashMap<>();
            response.put("type", 22); // GAME_STATE
            response.put("sequenceId", 0);
            response.put("timestamp", System.currentTimeMillis());
            response.put("body", body);

            String json = objectMapper.writeValueAsString(response);

            // 为每个玩家创建新的消息帧
            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                room.getBlackChannel().writeAndFlush(new TextWebSocketFrame(json));
                logger.info("✓ 发送游戏状态给黑棋玩家 房间{} 当前执棋: {}",
                    room.getRoomId(), room.getCurrentPlayer());
            }
            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                room.getWhiteChannel().writeAndFlush(new TextWebSocketFrame(json));
                logger.info("✓ 发送游戏状态给白棋玩家 房间{} 当前执棋: {}",
                    room.getRoomId(), room.getCurrentPlayer());
            }

        } catch (Exception e) {
            logger.error("广播游戏状态失败", e);
        }
    }

    /**
     * 发送游戏结束消息
     */
    private void sendGameOver(GameRoom room, Long winnerId, int endReason, int[] ratingChanges, boolean isCasual) {
        try {
            for (Long userId : Arrays.asList(room.getBlackPlayerId(), room.getWhitePlayerId())) {
                boolean isWinner = userId.equals(winnerId);
                // ratingChanges: [newRating1, newRating2, change1, change2]
                // 对于黑方使用 change1 (索引2)，白方使用 change2 (索引3)
                int ratingChange = userId.equals(room.getBlackPlayerId()) ? ratingChanges[2] : ratingChanges[3];

                Map<String, Object> body = new HashMap<>();
                body.put("room_id", room.getRoomId());
                body.put("winner_id", winnerId != null ? String.valueOf(winnerId) : null);
                body.put("reason", endReason);
                body.put("is_winner", isWinner);
                body.put("rating_change", ratingChange);
                body.put("is_casual", isCasual); // 标识是否为休闲模式
                body.put("board", room.getBoard().toArray());
                body.put("moves", room.getMoves());

                Map<String, Object> response = new HashMap<>();
                response.put("type", 23); // GAME_OVER
                response.put("sequenceId", 0);
                response.put("timestamp", System.currentTimeMillis());
                response.put("body", body);

                String json = objectMapper.writeValueAsString(response);

                Channel channel = userId.equals(room.getBlackPlayerId())
                    ? room.getBlackChannel()
                    : room.getWhiteChannel();

                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            }

        } catch (Exception e) {
            logger.error("发送游戏结束消息失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private void startMatchingTask() {
        logger.info("=== 启动匹配任务 ===");
        logger.info("匹配配置: checkInterval={}ms, ratingDiff={}, maxQueueTime={}s",
            checkInterval, ratingDiff, maxQueueTime);
        logger.info("使用Redis匹配队列: {}", matchQueueService != null);
        scheduler.scheduleAtFixedRate(this::processMatching, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
        logger.info("匹配任务已启动，将每{}ms执行一次", checkInterval);
    }

    private void sendSuccess(Channel channel, String message, int type) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("success", true);
            response.put("message", message);

            String json = objectMapper.writeValueAsString(response);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(json));
            }
        } catch (Exception e) {
            logger.error("发送成功消息失败", e);
        }
    }

    private void sendError(Channel channel, String message, int type) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("success", false);
            response.put("message", message);

            String json = objectMapper.writeValueAsString(response);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(json));
            }
        } catch (Exception e) {
            logger.error("发送错误消息失败", e);
        }
    }

    private void saveGameRecord(GameRoom room, Long winnerId, int endReason, int[] ratingChanges) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            // 获取用户信息
            User blackUser = userService.getUserById(room.getBlackPlayerId());
            User whiteUser = userService.getUserById(room.getWhitePlayerId());

            int blackRatingBefore = blackUser != null ? blackUser.getRating() : 1200;
            int whiteRatingBefore = whiteUser != null ? whiteUser.getRating() : 1200;

            // ratingChanges: [newBlackRating, newWhiteRating, blackChange, whiteChange]
            int blackRatingAfter = blackRatingBefore + ratingChanges[2];
            int whiteRatingAfter = whiteRatingBefore + ratingChanges[3];

            // 创建游戏记录
            GameRecord record = room.createRecord(
                winnerId,
                endReason,
                blackRatingBefore,
                whiteRatingBefore,
                ratingChanges[2],
                ratingChanges[3]
            );

            logger.info("准备保存游戏记录 - 房间: {}, 黑棋: {}, 白棋: {}, 胜者: {}, 游戏模式: {}, 原因: {}",
                room.getRoomId(), room.getBlackPlayerId(), room.getWhitePlayerId(),
                winnerId, record.getGameMode(), endReason);

            // 保存到数据库
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            recordMapper.insert(record);

            // 更新用户统计
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);
            int moveCount = record.getMoveCount() != null ? record.getMoveCount() : 0;
            int duration = record.getDuration() != null ? record.getDuration() : 0;
            boolean isCasual = "casual".equals(record.getGameMode()) || "pvp_local".equals(record.getGameMode());

            // 更新黑方统计
            if (room.getBlackPlayerId() != null && room.getBlackPlayerId() > 0) {
                updatePlayerStats(session, statsMapper, room.getBlackPlayerId(), winnerId, 1,
                    record.getWinColor(), moveCount, duration, isCasual);
            }
            // 更新白方统计
            if (room.getWhitePlayerId() != null && room.getWhitePlayerId() > 0) {
                updatePlayerStats(session, statsMapper, room.getWhitePlayerId(), winnerId, 2,
                    record.getWinColor(), moveCount, duration, isCasual);
            }

            logger.info("✅ 游戏记录已保存 - 房间: {}, 胜者: {}, 游戏模式: {}, 原因: {}",
                room.getRoomId(), winnerId, record.getGameMode(), endReason);
        } catch (Exception e) {
            logger.error("❌ 保存游戏记录失败 - 房间: {}, 胜者: {}, 游戏模式: {}",
                room.getRoomId(), winnerId, room.getGameMode(), e);
        }
    }

    /**
     * 更新玩家统计（用于在线对战）
     */
    private void updatePlayerStats(SqlSession session, UserStatsMapper statsMapper,
                                   Long userId, Long winnerId, int playerColor,
                                   Integer winColor, int moveCount, int duration, boolean isCasual) {
        try {
            UserStats stats = statsMapper.findByUserId(userId);
            if (stats == null) {
                stats = new UserStats(userId);
                statsMapper.insert(stats);
            }

            // 计算胜负
            int winAdd = 0;
            int lossAdd = 0;
            int drawAdd = 0;
            int streak = stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0;

            if (winnerId == null || (winColor != null && winColor == 0)) {
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
            if (user != null && (stats.getMaxRating() == null || stats.getMaxRating() < user.getRating())) {
                stats.setMaxRating(user.getRating());
                statsMapper.update(stats);
            }

            logger.info("✅ 更新用户统计: userId={}, winAdd={}, lossAdd={}, drawAdd={}, streak={}",
                userId, winAdd, lossAdd, drawAdd, streak);
        } catch (Exception e) {
            logger.error("Failed to update player stats", e);
        }
    }

    // ==================== 机器人AI ====================

    /**
     * 检查并安排机器人落子
     */
    private void scheduleBotMove(GameRoom room) {
        if (room.getGameState() != com.gobang.core.game.GameState.PLAYING) {
            return;
        }

        // 获取当前应该落子的玩家ID
        int currentPlayer = room.getCurrentPlayer();
        Long botUserId = (currentPlayer == 1) ? room.getBlackPlayerId() : room.getWhitePlayerId();

        // 检查当前玩家是否是机器人（userId < 0）
        if (botUserId == null || botUserId >= 0) {
            return; // 不是机器人
        }

        // 延迟500-1500毫秒后机器人落子，模拟思考
        int delay = 500 + (int)(Math.random() * 1000);

        scheduler.schedule(() -> {
            try {
                if (room.getGameState() != com.gobang.core.game.GameState.PLAYING) {
                    return;
                }

                // 再次检查轮到谁
                int currentTurn = room.getCurrentPlayer();
                Long currentBotId = (currentTurn == 1) ? room.getBlackPlayerId() : room.getWhitePlayerId();

                if (currentBotId == null || !currentBotId.equals(botUserId)) {
                    return; // 不是机器人的回合了
                }

                // 计算机器人落子位置
                int[] move = calculateBotMove(room);
                if (move == null) {
                    return; // 无处可下
                }

                // 机器人落子
                int result = room.makeMove(botUserId, move[0], move[1]);
                if (result == 0) {
                    broadcastGameState(room);

                    if (room.getGameState() == com.gobang.core.game.GameState.FINISHED) {
                        handleGameOver(room);
                    } else {
                        // 检查是否需要继续机器人落子（不太可能，但防止连续两个机器人）
                        scheduleBotMove(room);
                    }

                    logger.info("[机器人] Bot_{} 落子 ({}, {})", botUserId, move[0], move[1]);
                }
            } catch (Exception e) {
                logger.error("机器人落子失败", e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 计算机器人落子位置（简单AI）
     */
    private int[] calculateBotMove(GameRoom room) {
        com.gobang.core.game.Board board = room.getBoard();
        int[] boardArray = board.toArray();
        int size = 15;

        // 策略1: 检查是否能赢（有4子连珠）
        int[] winMove = findWinningMove(boardArray, size, 2); // 2=白(机器人)
        if (winMove != null) {
            return winMove;
        }

        // 策略2: 阻止对手赢（对手有4子连珠）
        int[] blockMove = findWinningMove(boardArray, size, 1); // 1=黑(对手)
        if (blockMove != null) {
            return blockMove;
        }

        // 策略3: 攻击位置（已有3子连珠）
        int[] attackMove = findBestAttackMove(boardArray, size, 2);
        if (attackMove != null) {
            return attackMove;
        }

        // 策略4: 防守位置（对手有3子连珠）
        int[] defendMove = findBestAttackMove(boardArray, size, 1);
        if (defendMove != null) {
            return defendMove;
        }

        // 策略5: 中心位置优先
        int center = size / 2;
        if (boardArray[center * size + center] == 0) {
            return new int[]{center, center};
        }

        // 策略6: 随机选择空位（优先选择靠近中心的位置）
        return findRandomMove(boardArray, size);
    }

    /**
     * 查找能赢的位置（有4子连珠）
     */
    private int[] findWinningMove(int[] board, int size, int player) {
        int[] directions = {1, size, size + 1, size - 1};

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                if (board[idx] != 0) continue;

                // 尝试在这个位置落子
                for (int dir : directions) {
                    int count = 1;
                    // 正向计数
                    for (int i = 1; i < 5; i++) {
                        int pos = idx + dir * i;
                        if (pos < 0 || pos >= board.length) break;
                        if (board[pos] != player) break;
                        count++;
                    }
                    // 反向计数
                    for (int i = 1; i < 5; i++) {
                        int pos = idx - dir * i;
                        if (pos < 0 || pos >= board.length) break;
                        if (board[pos] != player) break;
                        count++;
                    }
                    if (count >= 4) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    /**
     * 查找最佳攻击位置（有3子连珠）
     */
    private int[] findBestAttackMove(int[] board, int size, int player) {
        int[] directions = {1, size, size + 1, size - 1};
        int bestScore = -1;
        int[] bestMove = null;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                if (board[idx] != 0) continue;

                int maxCount = 0;
                for (int dir : directions) {
                    int count = 1;
                    for (int i = 1; i < 5; i++) {
                        int pos = idx + dir * i;
                        if (pos < 0 || pos >= board.length) break;
                        if (board[pos] != player) break;
                        count++;
                    }
                    for (int i = 1; i < 5; i++) {
                        int pos = idx - dir * i;
                        if (pos < 0 || pos >= board.length) break;
                        if (board[pos] != player) break;
                        count++;
                    }
                    maxCount = Math.max(maxCount, count);
                }

                if (maxCount > bestScore) {
                    bestScore = maxCount;
                    bestMove = new int[]{x, y};
                }
            }
        }

        return bestScore >= 3 ? bestMove : null;
    }

    /**
     * 随机选择空位（靠近中心优先）
     */
    private int[] findRandomMove(int[] board, int size) {
        int center = size / 2;
        java.util.List<int[]> candidates = new java.util.ArrayList<>();

        // 收集所有空位
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                if (board[idx] == 0) {
                    // 计算与中心的距离
                    int dist = Math.abs(x - center) + Math.abs(y - center);
                    candidates.add(new int[]{x, y, dist});
                }
            }
        }

        if (candidates.isEmpty()) {
            return new int[]{center, center};
        }

        // 按距离排序，优先选择靠近中心的
        candidates.sort((a, b) -> a[2] - b[2]);

        // 从前10个最优位置中随机选择
        int range = Math.min(10, candidates.size());
        int[] selected = candidates.get((int)(Math.random() * range));
        return new int[]{selected[0], selected[1]};
    }

    // ==================== 内部类 ====================

    private static class MatchPlayer {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final Integer rating;
        private final Channel channel;
        private final long enqueueTime;
        private final String mode;  // 游戏模式: "casual"=休闲, "ranked"=竞技

        public MatchPlayer(Long userId, String username, String nickname, Integer rating, Channel channel, String mode) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.channel = channel;
            this.enqueueTime = System.currentTimeMillis();
            this.mode = mode != null ? mode : "casual";  // 默认休闲模式
        }

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public Integer getRating() { return rating; }
        public Channel getChannel() { return channel; }
        public long getEnqueueTime() { return enqueueTime; }
        public String getMode() { return mode; }
        public long getWaitSeconds() { return (System.currentTimeMillis() - enqueueTime) / 1000; }
        public boolean isActive() {
            // 机器人玩家（userId < 0）始终活跃
            if (userId < 0) {
                return true;
            }
            return channel != null && channel.isActive();
        }
        public boolean isTimeout(long maxMillis) { return System.currentTimeMillis() - enqueueTime > maxMillis; }
    }

    /**
     * 本地匹配玩家（用于本地内存队列）
     */
    private static class LocalMatchPlayer {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final Integer rating;
        private final Channel channel;
        private final long enqueueTime;
        private final String mode;

        public LocalMatchPlayer(Long userId, String username, String nickname, Integer rating, Channel channel, String mode) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.channel = channel;
            this.enqueueTime = System.currentTimeMillis();
            this.mode = mode != null ? mode : "casual";
        }

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public Integer getRating() { return rating; }
        public Channel getChannel() { return channel; }
        public long getEnqueueTime() { return enqueueTime; }
        public String getMode() { return mode; }
    }

    /**
     * 创建并开始游戏（本地队列版本）
     */
    private String createAndStartGameLocal(LocalMatchPlayer p1, LocalMatchPlayer p2) {
        GameRoom room = roomManager.createRoom();

        boolean p1First = SecureRandomUtil.nextBoolean();
        Long blackId = p1First ? p1.getUserId() : p2.getUserId();
        Long whiteId = p1First ? p2.getUserId() : p1.getUserId();
        Channel blackCh = p1First ? p1.getChannel() : p2.getChannel();
        Channel whiteCh = p1First ? p2.getChannel() : p1.getChannel();

        room.startGame(blackId, blackCh, whiteId, whiteCh);

        // 根据玩家模式设置游戏模式（casual=休闲不计算积分，ranked=竞技计算积分）
        String gameMode = p1.getMode(); // 两个玩家应该有相同的模式
        room.setGameMode(gameMode);

        // 将玩家添加到房间管理器
        roomManager.joinRoom(room.getRoomId(), blackId, blackCh);
        roomManager.joinRoom(room.getRoomId(), whiteId, whiteCh);

        // 发送匹配成功消息
        sendMatchSuccessLocal(p1, p2, room, p1First);
        sendMatchSuccessLocal(p2, p1, room, !p1First);

        // 发送初始游戏状态
        broadcastGameState(room);

        logger.info("本地匹配成功 - 房间: {}, 黑棋: {}, 白棋: {}", room.getRoomId(), blackId, whiteId);
        return room.getRoomId();
    }

    /**
     * 发送匹配成功消息（本地队列版本）
     */
    private void sendMatchSuccessLocal(LocalMatchPlayer player, LocalMatchPlayer opponent, GameRoom room, boolean isFirst) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", 12); // MATCH_SUCCESS
            response.put("sequenceId", System.currentTimeMillis());
            response.put("timestamp", System.currentTimeMillis());

            Map<String, Object> body = new HashMap<>();
            body.put("room_id", room.getRoomId());
            body.put("is_first", isFirst);
            body.put("opponent", Map.of(
                "user_id", opponent.getUserId(),
                "nickname", opponent.getNickname(),
                "rating", opponent.getRating()
            ));
            response.put("body", body);

            String json = objectMapper.writeValueAsString(response);
            player.getChannel().writeAndFlush(new TextWebSocketFrame(json));

            logger.info("✓ 发送匹配成功消息给用户 {} 房间{} 执{}",
                player.getUserId(), room.getRoomId(), isFirst ? "黑" : "白");

        } catch (Exception e) {
            logger.error("发送匹配成功消息失败", e);
        }
    }

    /**
     * 获取匹配队列服务（用于API服务器访问统计信息）
     */
    public MatchQueueService getMatchQueueService() {
        return matchQueueService;
    }

    /**
     * 获取今日对战数
     */
    public int getTodayMatchesCount() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);

            // 计算今天的开始时间（00:00:00）
            java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().toLocalDate().atStartOfDay();

            logger.info("查询今日对战数: todayStart={}, 当前时间={}", todayStart, java.time.LocalDateTime.now());

            // 使用countSince方法
            Integer count = recordMapper.countSince(todayStart);
            logger.info("今日对战数查询结果: count={}", count);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("Failed to get today matches count", e);
            return 0;
        }
    }

    /**
     * 获取今日指定模式的对局数（所有用户）
     */
    public int getTodayMatchesCountByMode(String gameMode) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().toLocalDate().atStartOfDay();
            Integer count = recordMapper.countByModeSince(gameMode, todayStart);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("Failed to get today matches count by mode: {}", gameMode, e);
            return 0;
        }
    }

    /**
     * 获取用户今日指定模式的对局数
     */
    public int getTodayMatchesCountByUserAndMode(Long userId, String gameMode) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().toLocalDate().atStartOfDay();
            Integer count = recordMapper.countByUserAndModeSince(userId, gameMode, todayStart);
            int result = count != null ? count : 0;
            logger.info("📊 今日对局数统计 - 用户: {}, 模式: {}, 场次: {}", userId, gameMode, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to get today matches count by user and mode: userId={}, mode={}", userId, gameMode, e);
            return 0;
        }
    }

    /**
     * 获取今日指定模式的胜场数
     */
    public int getTodayWinsCountByMode(String gameMode, Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().toLocalDate().atStartOfDay();
            Integer count = recordMapper.countWinsByModeSince(gameMode, userId, todayStart);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("Failed to get today wins count by mode: {}, userId: {}", gameMode, userId, e);
            return 0;
        }
    }

    /**
     * 获取今日指定模式的胜率
     */
    public double getTodayWinRateByMode(String gameMode, Long userId) {
        int total = getTodayMatchesCountByUserAndMode(userId, gameMode);
        int wins = getTodayWinsCountByMode(gameMode, userId);
        double winRate = (total == 0) ? 0.0 : (double) wins / total * 100.0;
        logger.info("📊 今日胜率计算 - 用户: {}, 模式: {}, 总场次: {}, 胜场: {}, 胜率: {}%",
            userId, gameMode, total, wins, String.format("%.1f", winRate));
        return winRate;
    }

    /**
     * 获取最近的游戏记录
     */
    public java.util.List<Map<String, Object>> getRecentGameRecords(int limit) {
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            var records = recordMapper.findRecent(limit);

            for (GameRecord record : records) {
                Map<String, Object> info = new java.util.HashMap<>();
                info.put("id", record.getId());
                info.put("room_id", record.getRoomId());
                info.put("black_player_id", record.getBlackPlayerId());
                info.put("white_player_id", record.getWhitePlayerId());
                info.put("winner_id", record.getWinnerId());
                info.put("end_reason", record.getEndReason());
                info.put("move_count", record.getMoveCount());
                info.put("duration", record.getDuration());
                info.put("created_at", record.getCreatedAt());
                result.add(info);
            }

            logger.info("获取最近游戏记录: 查询到{}条", records.size());
        } catch (Exception e) {
            logger.error("Failed to get recent game records", e);
        }
        return result;
    }

    /**
     * 保存游戏记录（用于客户端游戏）
     * 同时更新用户统计数据
     */
    public boolean saveGameRecord(GameRecord record) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);

            // 保存游戏记录
            int result = recordMapper.insert(record);
            logger.info("保存游戏记录: roomId={}, gameMode={}, result={}",
                record.getRoomId(), record.getGameMode(), result);

            // 更新用户统计（PvE和本地PvP需要手动更新统计）
            if (result > 0 && record.getBlackPlayerId() != null && record.getBlackPlayerId() > 0) {
                updateUserStatsAfterGame(session, statsMapper, record);
            }

            return result > 0;
        } catch (Exception e) {
            logger.error("Failed to save game record", e);
            return false;
        }
    }

    /**
     * 更新用户统计数据（用于PvE和本地PvP）
     */
    private void updateUserStatsAfterGame(SqlSession session, UserStatsMapper statsMapper, GameRecord record) {
        try {
            Long userId = record.getBlackPlayerId();
            // PvE中玩家执黑，需要根据winColor判断胜负
            // winColor: 1=黑胜(玩家胜), 2=白胜(玩家负), null=平局
            Integer winColor = record.getWinColor();

            UserStats stats = statsMapper.findByUserId(userId);
            if (stats == null) {
                stats = new UserStats(userId);
                statsMapper.insert(stats);
            }

            // 计算胜负
            int winAdd = 0;
            int lossAdd = 0;
            int drawAdd = 0;
            int streak = stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0;

            // 根据winColor判断胜负（PvE: 玩家执黑）
            if (winColor == null || winColor == 0) {
                // 平局
                drawAdd = 1;
                streak = 0;
            } else if (winColor == 1) {
                // 黑胜（玩家胜）
                winAdd = 1;
                streak = streak > 0 ? streak + 1 : 1;
                if (stats.getMaxStreak() == null || streak > stats.getMaxStreak()) {
                    stats.setMaxStreak(streak);
                }
            } else {
                // 白胜（玩家负）
                lossAdd = 1;
                streak = streak < 0 ? streak - 1 : -1;
            }

            // 更新统计
            int moveCount = record.getMoveCount() != null ? record.getMoveCount() : 0;
            statsMapper.updateGameStats(userId, winAdd, lossAdd, drawAdd, moveCount, streak);

            // 更新最高积分
            User user = session.getMapper(UserMapper.class).findById(userId);
            if (user != null && (stats.getMaxRating() == null || stats.getMaxRating() < user.getRating())) {
                stats.setMaxRating(user.getRating());
                statsMapper.update(stats);
            }

            logger.info("✅ 更新用户统计: userId={}, winColor={}, winAdd={}, lossAdd={}, drawAdd={}, streak={}",
                userId, winColor, winAdd, lossAdd, drawAdd, streak);
        } catch (Exception e) {
            logger.error("Failed to update user stats", e);
        }
    }

    public static class MoveResult {
        private final boolean success;
        private final String message;

        public MoveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static MoveResult success() { return new MoveResult(true, "success"); }
        public static MoveResult error(String message) { return new MoveResult(false, message); }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    // ==================== 游戏状态保存和重连 ====================

    /**
     * 保存游戏状态到Redis
     */
    public void saveGameState(GameRoom room) {
        if (redisUtil == null) {
            logger.warn("RedisUtil未初始化，无法保存游戏状态");
            return;
        }

        if (room.getGameState() != GameState.PLAYING) {
            return; // 只保存进行中的游戏
        }

        try {
            String gameStateJson = room.serializeGameState();
            if (gameStateJson != null) {
                // 保存5分钟（300秒）
                redisUtil.saveGameState(room.getRoomId(), gameStateJson, 300);

                // 保存用户房间映射
                if (room.getBlackPlayerId() != null) {
                    redisUtil.saveUserRoomMapping(room.getBlackPlayerId(), room.getRoomId(), 300);
                }
                if (room.getWhitePlayerId() != null) {
                    redisUtil.saveUserRoomMapping(room.getWhitePlayerId(), room.getRoomId(), 300);
                }

                logger.info("✅ 游戏状态已保存到Redis: roomId={}", room.getRoomId());
            }
        } catch (Exception e) {
            logger.error("保存游戏状态失败: roomId={}", room.getRoomId(), e);
        }
    }

    /**
     * 从Redis恢复游戏状态（简化版 - 仅用于检查）
     */
    public String checkGameState(String roomId) {
        if (redisUtil == null) {
            return null;
        }
        return redisUtil.loadGameState(roomId);
    }

    /**
     * 处理玩家重连
     */
    public Map<String, Object> handleReconnect(Long userId, Channel channel) {
        Map<String, Object> result = new HashMap<>();

        // 检查内存中的房间
        GameRoom room = roomManager.getRoomByUserId(userId);
        if (room != null && room.getGameState() == GameState.PLAYING) {
            // 更新channel
            if (userId.equals(room.getBlackPlayerId())) {
                room.setBlackChannel(channel);
            } else if (userId.equals(room.getWhitePlayerId())) {
                room.setWhiteChannel(channel);
            }

            // 保存最新的channel映射
            localChannels.put(userId, channel);

            logger.info("玩家重连: userId={}, roomId={}", userId, room.getRoomId());
            return buildGameInfo(room, userId);
        }

        result.put("found", false);
        result.put("message", "未找到进行中的游戏");
        return result;
    }

    /**
     * 构建游戏信息
     */
    private Map<String, Object> buildGameInfo(GameRoom room, Long userId) {
        Map<String, Object> gameInfo = new HashMap<>();
        gameInfo.put("found", true);
        gameInfo.put("room_id", room.getRoomId());
        gameInfo.put("game_state", room.getGameState().name());
        gameInfo.put("board", room.getBoard().toArray());
        gameInfo.put("current_player", room.getCurrentPlayer());
        gameInfo.put("move_count", room.getMoves().size());
        gameInfo.put("game_mode", room.getGameMode());
        gameInfo.put("black_remaining_time", room.getBlackPlayerRemainingTime());
        gameInfo.put("white_remaining_time", room.getWhitePlayerRemainingTime());

        // 添加玩家信息
        Long opponentId = userId.equals(room.getBlackPlayerId())
            ? room.getWhitePlayerId() : room.getBlackPlayerId();
        if (opponentId != null) {
            User opponent = userService.getUserById(opponentId);
            if (opponent != null) {
                Map<String, Object> opponentInfo = new HashMap<>();
                opponentInfo.put("user_id", String.valueOf(opponent.getId()));
                opponentInfo.put("username", opponent.getUsername());
                opponentInfo.put("nickname", opponent.getNickname());
                opponentInfo.put("rating", opponent.getRating());
                gameInfo.put("opponent", opponentInfo);
            }
        }

        // 添加玩家颜色信息
        int playerColor = userId.equals(room.getBlackPlayerId()) ? 1 : 2;
        gameInfo.put("my_color", playerColor);

        // 添加双方玩家ID
        gameInfo.put("black_player_id", room.getBlackPlayerId());
        gameInfo.put("white_player_id", room.getWhitePlayerId());

        return gameInfo;
    }

    /**
     * 清除游戏状态（游戏结束时调用）
     */
    public void clearGameState(GameRoom room) {
        if (redisUtil == null) {
            return;
        }

        try {
            redisUtil.deleteGameState(room.getRoomId());
            if (room.getBlackPlayerId() != null) {
                redisUtil.deleteUserRoomMapping(room.getBlackPlayerId());
            }
            if (room.getWhitePlayerId() != null) {
                redisUtil.deleteUserRoomMapping(room.getWhitePlayerId());
            }
            logger.info("游戏状态已清除: roomId={}", room.getRoomId());
        } catch (Exception e) {
            logger.error("清除游戏状态失败: roomId={}", room.getRoomId(), e);
        }
    }

    /**
     * 启动超时检查任务
     */
    public void startTimeoutChecker() {
        if (redisUtil == null) {
            logger.warn("RedisUtil未初始化，无法启动超时检查任务");
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkTimeoutPlayers();
            } catch (Exception e) {
                logger.error("超时检查任务执行失败", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒检查一次

        logger.info("✅ 超时检查任务已启动");
    }

    /**
     * 检查超时玩家
     */
    private void checkTimeoutPlayers() {
        logger.debug("开始检查超时玩家...");

        for (GameRoom room : roomManager.getAllRooms()) {
            if (room.getGameState() != GameState.PLAYING) {
                continue;
            }

            // 使用GameRoom的checkTimeout方法检查超时（2分钟=120秒）
            Long timeoutPlayerId = room.checkTimeout();
            if (timeoutPlayerId != null) {
                logger.info("⏰ 玩家超时判负: roomId={}, timeoutPlayerId={}", room.getRoomId(), timeoutPlayerId);
                // 判超时玩家负，对方获胜
                Long winnerId = timeoutPlayerId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();
                handleGameOver(room, winnerId, 4); // 4=超时
            }
        }
    }

    /**
     * 更新玩家活动时间
     */
    public void updatePlayerActivity(String roomId, Long userId) {
        if (redisUtil != null) {
            redisUtil.saveUserLastActivity(roomId, userId, System.currentTimeMillis());
        }
    }

    /**
     * 获取SqlSessionFactory
     */
    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }
}
