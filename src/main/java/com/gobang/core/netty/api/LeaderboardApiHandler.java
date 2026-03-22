package com.gobang.core.netty.api;

import com.gobang.model.entity.User;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 排行榜API处理器
 * 处理排行榜查询
 */
public class LeaderboardApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardApiHandler.class);
    private final UserService userService;

    public LeaderboardApiHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/leaderboard";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        logger.info("LeaderboardApiHandler.handleRequest: {} {}", method, path);
        try {
            if ("GET".equals(method)) {
                handleLeaderboard(ctx, request);
                return true;
            }
        } catch (Exception e) {
            logger.error("Leaderboard API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取排行榜失败"));
        }
        return false;
    }

    /**
     * 处理排行榜请求
     */
    private void handleLeaderboard(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            logger.info("处理排行榜请求: {}", request.uri());
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String limitStr = decoder.parameters().getOrDefault("limit", List.of("50")).get(0);
            int limit = Integer.parseInt(limitStr);

            logger.info("获取排行榜数据 - limit={}", limit);
            List<User> leaderboard = userService.getLeaderboard(limit);
            logger.info("查询到 {} 位玩家", leaderboard.size());

            Map<String, Object> response = Map.of(
                "success", true,
                "data", leaderboard
            );

            logger.info("发送排行榜响应");
            sendJsonResponse(ctx, HttpResponseStatus.OK, response);
            logger.info("排行榜响应已发送 - {} 位玩家", leaderboard.size());
        } catch (Exception e) {
            logger.error("Failed to get leaderboard", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取排行榜失败"));
        }
    }
}
