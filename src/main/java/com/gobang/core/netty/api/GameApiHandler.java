package com.gobang.core.netty.api;

import com.gobang.service.AuthService;
import com.gobang.service.GameService;
import com.gobang.service.RoomService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 游戏API处理器
 * 处理游戏相关操作
 */
public class GameApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameApiHandler.class);
    private final GameService gameService;
    private final RoomService roomService;
    private final UserService userService;
    private final AuthService authService;

    public GameApiHandler(GameService gameService, RoomService roomService,
                         UserService userService, AuthService authService) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.userService = userService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/game";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            Long userId = extractUserId(ctx, request);
            if (userId == null) {
                sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                    Map.of("success", false, "message", "未授权"));
                return true;
            }

            // 根据子路径处理不同的游戏操作
            if (path.endsWith("/resign")) {
                handleResign(ctx, userId);
                return true;
            }

            // 检查未完成的游戏
            if (path.endsWith("/unfinished")) {
                handleUnfinishedGame(ctx, userId);
                return true;
            }

            // 默认响应
            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "message", "游戏API正常"));
            return true;

        } catch (Exception e) {
            logger.error("Game API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    private Long extractUserId(ChannelHandlerContext ctx, FullHttpRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && !token.isEmpty()) {
            return authService.validateToken(token);
        }
        return null;
    }

    private void handleResign(ChannelHandlerContext ctx, Long userId) {
        // 实现认输逻辑
        sendJsonResponse(ctx, HttpResponseStatus.OK,
            Map.of("success", true, "message", "已认输"));
    }

    /**
     * 处理检查未完成游戏的请求
     */
    private void handleUnfinishedGame(ChannelHandlerContext ctx, Long userId) {
        try {
            Map<String, Object> unfinishedGame = gameService.getUnfinishedGame(userId);
            if (unfinishedGame != null) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "data", unfinishedGame));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "data", Map.of("has_unfinished", false)));
            }
        } catch (Exception e) {
            logger.error("检查未完成游戏失败", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "检查失败"));
        }
    }
}
