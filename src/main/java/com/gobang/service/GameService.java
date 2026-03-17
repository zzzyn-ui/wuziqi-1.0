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
                       MatchQueueService matchQueueService,
                       int ratingDiff, int maxQueueTime, int checkInterval, boolean testMode, int testMatchDelay) {
        this.roomManager = roomManager;
        this.userService = userService;
        this.sqlSessionFactory = sqlSessionFactory;
        this.objectMapper = new ObjectMapper();
        this.matchQueueService = matchQueueService;
        this.scheduler = Executors.newScheduledThreadPool(2);

        // 配置参数
        this.ratingDiff = ratingDiff;
        this.maxQueueTime = maxQueueTime;
        this.checkInterval = checkInterval;
        this.testMode = testMode;
        this.testMatchDelay = testMatchDelay;

        // 启动匹配任务
        startMatchingTask();
        logger.info("GameService initialized with testMode={}, testMatchDelay={}ms", testMode, testMatchDelay);
    }

    // 兼容旧构造函数（不使用MatchQueueService）
    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory) {
        this(roomManager, userService, sqlSessionFactory, null, 100, 300, 100, false, 3000);
    }

    // 兼容旧构造函数
    public GameService(RoomManager roomManager, UserService userService, SqlSessionFactory sqlSessionFactory,
                       int ratingDiff, int maxQueueTime, int checkInterval, boolean testMode, int testMatchDelay) {
        this(roomManager, userService, sqlSessionFactory, null, ratingDiff, maxQueueTime, checkInterval, testMode, testMatchDelay);
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
            // 降级到本地匹配（兼容旧版本）
            logger.warn("MatchQueueService未初始化，使用本地匹配队列");
            success = true; // 临时返回true
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

        boolean success;
        if (matchQueueService != null) {
            success = matchQueueService.removeFromQueue(userId);
        } else {
            success = true;
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
            room.setGameMode("casual"); // 人机对战默认为休闲模式

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
        logger.info("=== 开始处理匹配 ===");

        try {
            if (matchQueueService != null) {
                // 使用Redis匹配队列
                processMatchingWithRedis();
            } else {
                // 降级到本地匹配
                logger.warn("MatchQueueService未初始化，跳过匹配处理");
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

        // 设置游戏模式（使用p1的模式，因为匹配时已经确保模式相同）
        room.setGameMode(p1.getMode());

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

        // 设置游戏模式（使用p1的模式，因为匹配时已经确保模式相同）
        room.setGameMode(p1.getMode());

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
            room.setGameMode(player.getMode()); // 设置游戏模式

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

        int result = room.makeMove(userId, x, y);

        if (result == 0) {
            broadcastGameState(room);

            if (room.getGameState() == GameState.FINISHED) {
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
     * 获取用户的未完成游戏
     */
    public Map<String, Object> getUnfinishedGame(Long userId) {
        GameRoom room = roomManager.getRoomByUserId(userId);
        if (room != null && room.getGameState() == com.gobang.core.game.GameState.PLAYING) {
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

            return gameInfo;
        }
        return null;
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

        // 获取用户信息
        User blackUser = userService.getUserById(room.getBlackPlayerId());
        User whiteUser = userService.getUserById(room.getWhitePlayerId());

        // 如果用户不存在（可能是游客），使用默认积分
        int blackRatingBefore = (blackUser != null) ? blackUser.getRating() : 1200;
        int whiteRatingBefore = (whiteUser != null) ? whiteUser.getRating() : 1200;

        int[] ratingChanges = new int[4]; // [newBlackRating, newWhiteRating, blackChange, whiteChange]

        if (isCasual) {
            // 休闲模式：不计算积分变化
            ratingChanges[0] = blackRatingBefore;
            ratingChanges[1] = whiteRatingBefore;
            ratingChanges[2] = 0;
            ratingChanges[3] = 0;
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

            // 保存到数据库
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            recordMapper.insert(record);

            logger.info("游戏记录已保存 - 房间: {}, 胜者: {}, 原因: {}",
                room.getRoomId(), winnerId, endReason);
        } catch (Exception e) {
            logger.error("保存游戏记录失败", e);
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
}
