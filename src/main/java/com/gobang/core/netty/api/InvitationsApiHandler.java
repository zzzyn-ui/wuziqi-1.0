package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.GameInvitation;
import com.gobang.service.AuthService;
import com.gobang.service.GameInvitationService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 游戏邀请API处理器
 * 处理游戏邀请操作
 */
public class InvitationsApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(InvitationsApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameInvitationService gameInvitationService;
    private final UserService userService;
    private final AuthService authService;

    public InvitationsApiHandler(GameInvitationService gameInvitationService,
                                 UserService userService, AuthService authService) {
        this.gameInvitationService = gameInvitationService;
        this.userService = userService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/invitations";
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

            if ("GET".equals(method)) {
                if (path.equals("/api/invitations/pending")) {
                    handleGetPendingInvitations(ctx, userId);
                    return true;
                } else if (path.equals("/api/invitations/sent")) {
                    handleGetSentInvitations(ctx, request, userId);
                    return true;
                }
            } else if ("POST".equals(method)) {
                if (path.equals("/api/invitations")) {
                    handleSendInvitation(ctx, request, userId);
                    return true;
                } else if (path.matches("/api/invitations/\\d+/accept")) {
                    handleAcceptInvitation(ctx, request, userId, path);
                    return true;
                } else if (path.matches("/api/invitations/\\d+/reject")) {
                    handleRejectInvitation(ctx, request, userId, path);
                    return true;
                }
            } else if ("DELETE".equals(method)) {
                if (path.matches("/api/invitations/\\d+")) {
                    handleCancelInvitation(ctx, path, userId);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Invitations API error", e);
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

    private void handleGetPendingInvitations(ChannelHandlerContext ctx, Long userId) {
        try {
            List<GameInvitation> invitations = gameInvitationService.getPendingInvitations(userId);
            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "invitations", invitations));
        } catch (Exception e) {
            logger.error("Failed to get pending invitations for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取邀请失败"));
        }
    }

    private void handleGetSentInvitations(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            List<GameInvitation> invitations = gameInvitationService.getSentInvitations(userId, 20);
            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "invitations", invitations));
        } catch (Exception e) {
            logger.error("Failed to get sent invitations for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取邀请失败"));
        }
    }

    private void handleSendInvitation(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            Number inviteeIdNum = (Number) data.get("inviteeId");
            if (inviteeIdNum == null) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "缺少被邀请人ID"));
                return;
            }

            Long inviteeId = inviteeIdNum.longValue();
            String invitationType = (String) data.getOrDefault("invitationType", "casual");

            Long invitationId = gameInvitationService.sendInvitation(userId, inviteeId, invitationType);

            if (invitationId != null) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "invitationId", invitationId));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "发送邀请失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to send invitation from user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "发送邀请失败"));
        }
    }

    private void handleAcceptInvitation(ChannelHandlerContext ctx, FullHttpRequest request, Long userId, String path) {
        try {
            String[] parts = path.split("/");
            Long invitationId = Long.parseLong(parts[3]);

            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);
            String roomId = (String) data.get("roomId");

            boolean success = gameInvitationService.acceptInvitation(invitationId, roomId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已接受邀请"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "接受邀请失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to accept invitation for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "接受邀请失败"));
        }
    }

    private void handleRejectInvitation(ChannelHandlerContext ctx, FullHttpRequest request, Long userId, String path) {
        try {
            String[] parts = path.split("/");
            Long invitationId = Long.parseLong(parts[3]);

            boolean success = gameInvitationService.rejectInvitation(invitationId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已拒绝邀请"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "拒绝邀请失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to reject invitation for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "拒绝邀请失败"));
        }
    }

    private void handleCancelInvitation(ChannelHandlerContext ctx, String path, Long userId) {
        try {
            String[] parts = path.split("/");
            Long invitationId = Long.parseLong(parts[3]);

            boolean success = gameInvitationService.cancelInvitation(invitationId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已取消邀请"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "取消邀请失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to cancel invitation for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "取消邀请失败"));
        }
    }
}
