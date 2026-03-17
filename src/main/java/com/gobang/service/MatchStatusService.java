package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.netty.ChannelManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 匹配状态广播服务
 * 定期向已认证的客户端推送匹配池状态和在线人数
 */
public class MatchStatusService {

    private static final Logger logger = LoggerFactory.getLogger(MatchStatusService.class);

    private final MatchQueueService matchQueueService;
    private final UserService userService;
    private final ChannelManager channelManager;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler;

    // 广播间隔（秒）
    private final int broadcastInterval;

    public MatchStatusService(MatchQueueService matchQueueService, UserService userService,
                              ChannelManager channelManager, int broadcastInterval) {
        this.matchQueueService = matchQueueService;
        this.userService = userService;
        this.channelManager = channelManager;
        this.objectMapper = new ObjectMapper();
        this.broadcastInterval = broadcastInterval;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "match-status-broadcaster");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动广播服务
     */
    public void start() {
        logger.info("MatchStatusService starting, broadcast interval: {}s", broadcastInterval);

        scheduler.scheduleAtFixedRate(
            this::broadcastStatus,
            broadcastInterval,
            broadcastInterval,
            TimeUnit.SECONDS
        );

        logger.info("MatchStatusService started successfully");
    }

    /**
     * 停止广播服务
     */
    public void stop() {
        logger.info("MatchStatusService stopping...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("MatchStatusService stopped");
    }

    /**
     * 广播状态到所有已认证的客户端
     */
    private void broadcastStatus() {
        try {
            // 获取在线人数
            int onlineCount = userService.getOnlineUserCount();

            // 获取匹配队列统计
            MatchQueueService.QueueStats stats = matchQueueService.getQueueStats();
            int casualWaiting = stats.getCasualCount();
            int rankedWaiting = stats.getRankedCount();
            int totalWaiting = casualWaiting + rankedWaiting;

            // 构建在线人数更新消息
            Map<String, Object> onlineCountMsg = new HashMap<>();
            onlineCountMsg.put("type", 15); // ONLINE_COUNT_UPDATE
            onlineCountMsg.put("timestamp", System.currentTimeMillis());
            Map<String, Object> onlineBody = new HashMap<>();
            onlineBody.put("count", onlineCount);
            onlineBody.put("waiting", totalWaiting);
            onlineBody.put("casual_waiting", casualWaiting);
            onlineBody.put("ranked_waiting", rankedWaiting);
            onlineCountMsg.put("body", onlineBody);

            // 获取匹配池中的玩家（用于前端显示）
            // 注意：removeInactive=false，不移除不活跃的玩家，因为这只是用于广播显示
            List<MatchQueueService.MatchPlayer> casualPlayers = matchQueueService.getPlayersInQueue("casual", new HashMap<>(), false);
            List<MatchQueueService.MatchPlayer> rankedPlayers = matchQueueService.getPlayersInQueue("ranked", new HashMap<>(), false);

            // 构建匹配池更新消息
            List<Map<String, Object>> playersList = new ArrayList<>();

            // 添加休闲模式玩家
            for (MatchQueueService.MatchPlayer player : casualPlayers) {
                playersList.add(buildPlayerInfo(player, "casual"));
            }

            // 添加竞技模式玩家
            for (MatchQueueService.MatchPlayer player : rankedPlayers) {
                playersList.add(buildPlayerInfo(player, "ranked"));
            }

            Map<String, Object> poolUpdateMsg = new HashMap<>();
            poolUpdateMsg.put("type", 14); // MATCH_POOL_UPDATE
            poolUpdateMsg.put("timestamp", System.currentTimeMillis());
            Map<String, Object> poolBody = new HashMap<>();
            poolBody.put("players", playersList);
            poolBody.put("casual_count", casualWaiting);
            poolBody.put("ranked_count", rankedWaiting);
            poolUpdateMsg.put("body", poolBody);

            // 序列化消息
            String onlineCountJson = objectMapper.writeValueAsString(onlineCountMsg);
            String poolUpdateJson = objectMapper.writeValueAsString(poolUpdateMsg);

            // 向所有已认证的客户端广播
            int broadcastCount = 0;
            for (Channel channel : channelManager.getAllChannels()) {
                if (channel.isActive()) {
                    // 检查是否已认证
                    io.netty.util.AttributeKey<Boolean> AUTHENTICATED_ATTR = io.netty.util.AttributeKey.valueOf("authenticated");
                    Boolean authenticated = channel.attr(AUTHENTICATED_ATTR).get();

                    if (Boolean.TRUE.equals(authenticated)) {
                        try {
                            channel.writeAndFlush(new TextWebSocketFrame(onlineCountJson));
                            channel.writeAndFlush(new TextWebSocketFrame(poolUpdateJson));
                            broadcastCount++;
                        } catch (Exception e) {
                            logger.debug("Failed to send status update to channel", e);
                        }
                    }
                }
            }

            logger.debug("Broadcasted status to {} authenticated clients: online={}, waiting={} (casual={}, ranked={})",
                broadcastCount, onlineCount, totalWaiting, casualWaiting, rankedWaiting);

        } catch (Exception e) {
            logger.error("Error broadcasting match status", e);
        }
    }

    /**
     * 构建玩家信息
     */
    private Map<String, Object> buildPlayerInfo(MatchQueueService.MatchPlayer player, String mode) {
        Map<String, Object> info = new HashMap<>();
        info.put("user_id", player.getUserId());
        info.put("username", player.getUsername());
        info.put("nickname", player.getNickname());
        info.put("rating", player.getRating());
        info.put("mode", mode);
        info.put("wait_time", player.getWaitSeconds());
        return info;
    }

    /**
     * 手动触发状态广播（用于测试或立即更新）
     */
    public void triggerBroadcast() {
        scheduler.execute(this::broadcastStatus);
    }
}
