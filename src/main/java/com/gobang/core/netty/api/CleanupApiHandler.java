package com.gobang.core.netty.api;

import com.gobang.core.room.RoomManager;
import com.gobang.service.GameService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 数据清理API处理器
 * 处理服务器数据清理操作
 */
public class CleanupApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(CleanupApiHandler.class);
    private final RoomManager roomManager;
    private final GameService gameService;

    public CleanupApiHandler(RoomManager roomManager, GameService gameService) {
        this.roomManager = roomManager;
        this.gameService = gameService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/cleanup";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            if ("POST".equals(method)) {
                if (path.equals("/api/cleanup/all")) {
                    handleCleanupAll(ctx);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Cleanup API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "清理失败"));
        }
        return false;
    }

    private void handleCleanupAll(ChannelHandlerContext ctx) {
        try {
            int clearedRooms = 0;

            // 清理所有房间
            for (var roomId : roomManager.getAllRooms().stream().map(room -> room.getRoomId()).toList()) {
                roomManager.removeRoom(roomId);
                clearedRooms++;
            }

            sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                "success", true,
                "message", "清理完成",
                "clearedRooms", clearedRooms
            ));
            logger.info("Cleanup completed: {} rooms cleared", clearedRooms);
        } catch (Exception e) {
            logger.error("Cleanup failed", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "清理失败"));
        }
    }
}
