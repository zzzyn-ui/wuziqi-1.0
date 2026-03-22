package com.gobang.core.netty;

import com.gobang.core.netty.api.*;
import com.gobang.service.*;
import com.gobang.core.room.RoomManager;
import com.gobang.util.JwtUtil;

import java.util.Map;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP API 处理器 (重构版)
 * 使用独立Handler类架构，每个API端点有专门的Handler
 *
 * 架构:
 * HttpApiHandler (入口)
 *     ↓
 * ApiRouter (路由分发)
 *     ↓
 * 各独立ApiHandler (业务处理)
 *     - AuthApiHandler (认证)
 *     - FriendsApiHandler (好友)
 *     - LeaderboardApiHandler (排行榜)
 *     - UserApiHandler (用户)
 *     - StatsApiHandler (统计)
 *     - HealthApiHandler (健康检查)
 *     - ... 更多Handler
 */
public class HttpApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(HttpApiHandler.class);

    private final ApiRouter router;

    public HttpApiHandler(UserService userService, AuthService authService,
                          GameService gameService, RoomService roomService,
                          FriendService friendService, ChatService chatService,
                          RoomManager roomManager, UserSettingsService userSettingsService,
                          ActivityLogService activityLogService,
                          GameFavoriteService gameFavoriteService,
                          GameInvitationService gameInvitationService,
                          RecordService recordService, PuzzleService puzzleService,
                          JwtUtil jwtUtil) {

        this.router = new ApiRouter();

        // 初始化所有独立的API Handler
        initializeHandlers(userService, authService, gameService, roomService,
                          friendService, chatService, roomManager, userSettingsService,
                          activityLogService, gameFavoriteService, gameInvitationService,
                          recordService, puzzleService, jwtUtil, gameService.getSqlSessionFactory());

        // 打印所有已注册的Handler
        router.printHandlers();
    }

    /**
     * 初始化所有API Handler
     */
    private void initializeHandlers(UserService userService, AuthService authService,
                                    GameService gameService, RoomService roomService,
                                    FriendService friendService, ChatService chatService,
                                    RoomManager roomManager, UserSettingsService userSettingsService,
                                    ActivityLogService activityLogService,
                                    GameFavoriteService gameFavoriteService,
                                    GameInvitationService gameInvitationService,
                                    RecordService recordService, PuzzleService puzzleService,
                                    JwtUtil jwtUtil, org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {

        // 1. 认证相关 (/api/auth/*)
        router.registerHandler("/api/auth", new AuthApiHandler(authService, userService));

        // 2. 好友管理 (/api/friends/*)
        router.registerHandler("/api/friends", new FriendsApiHandler(friendService, userService, authService, chatService));

        // 3. 排行榜 (/api/leaderboard, /api/rank)
        router.registerHandler("/api/leaderboard", new LeaderboardApiHandler(userService));

        // 4. 用户信息 (/api/user)
        router.registerHandler("/api/user", new UserApiHandler(userService, authService));

        // 5. 统计数据 (/api/stats)
        router.registerHandler("/api/stats", new StatsApiHandler(roomManager, gameService, authService));

        // 6. 健康检查 (/api/health)
        router.registerHandler("/api/health", new HealthApiHandler());

        // 7. 心跳检测 (/api/heartbeat)
        router.registerPathHandler("/api/heartbeat", new HeartbeatApiHandler(roomManager, authService));

        // 8. 游戏相关 (/api/game)
        router.registerHandler("/api/game", new GameApiHandler(gameService, roomService, userService, authService));

        // 9. 房间相关 (/api/rooms)
        router.registerHandler("/api/rooms", new RoomsApiHandler(roomManager, roomService, userService, authService));

        // 10. 对局记录 (/api/records)
        router.registerHandler("/api/records", new RecordsApiHandler(recordService, userService, authService, sqlSessionFactory));

        // 11. 用户设置 (/api/settings)
        router.registerHandler("/api/settings", new SettingsApiHandler(userSettingsService, authService));

        // 12. 活动日志 (/api/activity)
        router.registerHandler("/api/activity", new ActivityApiHandler(activityLogService, authService));

        // 13. 对局收藏 (/api/favorites)
        router.registerHandler("/api/favorites", new FavoritesApiHandler(gameFavoriteService, authService));

        // 14. 游戏邀请 (/api/invitations)
        router.registerHandler("/api/invitations", new InvitationsApiHandler(gameInvitationService, userService, authService));

        // 15. 清理数据 (/api/cleanup)
        router.registerHandler("/api/cleanup", new CleanupApiHandler(roomManager, gameService));

        // 16. 残局挑战 (/api/puzzles)
        router.registerHandler("/api/puzzles", new PuzzleApiHandler(puzzleService, authService));
    }

    /**
     * 处理 API 请求 (入口方法)
     * @return true 如果请求已处理，false 如果应该传递给下一个处理器
     */
    public boolean handleApiRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        String method = request.method().name();
        String path = uri.split("\\?")[0];

        // 只处理 /api/ 开头的请求
        if (!path.startsWith("/api/")) {
            return false;
        }

        logger.debug("API Request: {} {}", method, path);

        try {
            // 处理 OPTIONS 预检请求
            if ("OPTIONS".equals(method)) {
                sendCorsResponse(ctx);
                return true;
            }

            // 路由到对应的Handler
            return router.route(ctx, request);

        } catch (Exception e) {
            logger.error("API request error: {} {}", method, path, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器内部错误"));
            return true;
        }
    }

    /**
     * 发送CORS响应
     */
    private void sendCorsResponse(ChannelHandlerContext ctx) {
        var response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.EMPTY_BUFFER
        );
        response.headers().set("Access-Control-Allow-Origin", "*");
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.headers().setInt("Content-Length", 0);
        ctx.writeAndFlush(response);
    }

    /**
     * 发送JSON响应 (兼容方法)
     */
    public void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, Map<String, Object> data) {
        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) json.append(",");
                first = false;
                json.append("\"").append(entry.getKey()).append("\":");

                Object value = entry.getValue();
                if (value == null) {
                    json.append("null");
                } else if (value instanceof String) {
                    json.append("\"").append(escapeJson((String) value)).append("\"");
                } else if (value instanceof Number || value instanceof Boolean) {
                    json.append(value);
                } else {
                    json.append("\"").append(escapeJson(value.toString())).append("\"");
                }
            }

            json.append("}");

            var response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(json.toString(), io.netty.util.CharsetUtil.UTF_8)
            );
            response.headers().set("Content-Type", "application/json; charset=UTF-8");
            response.headers().set("Access-Control-Allow-Origin", "*");
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.headers().setInt("Content-Length", response.content().readableBytes());
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            logger.error("Failed to send JSON response", e);
        }
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 获取路由器 (用于测试)
     */
    public ApiRouter getRouter() {
        return router;
    }
}
