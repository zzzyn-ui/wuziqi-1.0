package com.gobang.core.netty.api;

import com.gobang.core.game.GameState;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.service.AuthService;
import com.gobang.service.GameService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 统计数据API处理器
 * 处理服务器统计信息查询
 */
public class StatsApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(StatsApiHandler.class);
    private final RoomManager roomManager;
    private final GameService gameService;
    private final AuthService authService;

    public StatsApiHandler(RoomManager roomManager, GameService gameService, AuthService authService) {
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/stats";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            if ("GET".equals(method)) {
                handleStats(ctx, request);
                return true;
            }
        } catch (Exception e) {
            logger.error("Stats API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取统计信息失败"));
        }
        return false;
    }

    /**
     * 处理统计信息请求
     */
    private void handleStats(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            // 获取房间统计
            var allRooms = roomManager.getAllRooms();
            int totalRooms = allRooms.size();
            int playingGames = 0;
            for (GameRoom room : allRooms) {
                if (room.getGameState() == GameState.PLAYING) {
                    playingGames++;
                }
            }

            // 获取匹配队列统计
            int waitingCount = 0;
            int casualWaiting = 0;
            int rankedWaiting = 0;
            try {
                if (gameService.getMatchQueueService() != null) {
                    var stats = gameService.getMatchQueueService().getQueueStats();
                    casualWaiting = stats.getCasualCount();
                    rankedWaiting = stats.getRankedCount();
                    waitingCount = casualWaiting + rankedWaiting;
                }
            } catch (Exception e) {
                logger.debug("Failed to get queue stats: {}", e.getMessage());
            }

            // 获取在线玩家数
            int onlinePlayers = roomManager.getOnlinePlayerCount();

            // 构建响应数据结构
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> pvpOnline = new HashMap<>();
            Map<String, Object> pvpLocal = new HashMap<>();

            // 填充在线对战统计数据（全局数据）
            pvpOnline.put("today_matches", 0); // 今日对局数需要从数据库统计，暂不实现
            pvpOnline.put("win_rate", "0.0");  // 胜率需要用户登录后从数据库查询
            pvpOnline.put("online_players", onlinePlayers);

            // 填充本地对战统计数据
            pvpLocal.put("today_matches", 0); // 本地对战暂不统计

            data.put("pvp_online", pvpOnline);
            data.put("pvp_local", pvpLocal);
            data.put("playing_games", playingGames);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            result.put("timestamp", System.currentTimeMillis());

            sendJsonResponse(ctx, HttpResponseStatus.OK, result);
            logger.debug("📊 Stats returned: online_players={}, playing_games={}, waiting={}",
                onlinePlayers, playingGames, waitingCount);
        } catch (Exception e) {
            logger.error("Failed to get stats", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取统计信息失败"));
        }
    }
}
