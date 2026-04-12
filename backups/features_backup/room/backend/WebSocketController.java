package com.gobang.controller;

import com.gobang.core.match.SpringMatchMaker;
import com.gobang.core.room.RoomManager;
import com.gobang.model.dto.*;
import com.gobang.model.entity.User;
import com.gobang.service.GameService;
import com.gobang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 消息控制器
 * 使用 @MessageMapping 处理 STOMP 消息
 */
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    private final UserService userService;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SpringMatchMaker matchMaker;
    private final RoomManager roomManager;
    private final com.gobang.service.GameInvitationService gameInvitationService;
    private final com.gobang.service.FriendService friendService;
    private final com.gobang.service.ObserverService observerService;

    // ==================== 认证相关 ====================

    /**
     * 登录请求
     * @MessageMapping("/app/auth/login")
     */
    @MessageMapping("/auth/login")
    public void handleLogin(@Payload LoginDto loginDto) {
        logger.info("收到登录请求: username={}", loginDto.getUsername());

        try {
            String token = userService.login(loginDto);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "AUTH_SUCCESS");
            response.put("token", token);

            // 获取用户信息
            User user = userService.getUserByToken(token);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("nickname", user.getNickname());
            response.put("user", userInfo);

            // 发送响应
            messagingTemplate.convertAndSend("/topic/auth/response", response);
        } catch (Exception e) {
            logger.error("登录失败", e);

            Map<String, Object> error = new HashMap<>();
            error.put("type", "AUTH_ERROR");
            error.put("message", e.getMessage());

            messagingTemplate.convertAndSend("/topic/auth/error", error);
        }
    }

    /**
     * 注册请求
     * @MessageMapping("/app/auth/register")
     */
    @MessageMapping("/auth/register")
    public void handleRegister(@Payload RegisterDto registerDto) {
        logger.info("收到注册请求: username={}", registerDto.getUsername());

        try {
            userService.register(registerDto);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "REGISTER_SUCCESS");
            response.put("message", "注册成功");

            messagingTemplate.convertAndSend("/topic/auth/response", response);
        } catch (Exception e) {
            logger.error("注册失败", e);

            Map<String, Object> error = new HashMap<>();
            error.put("type", "REGISTER_ERROR");
            error.put("message", e.getMessage());

            messagingTemplate.convertAndSend("/topic/auth/error", error);
        }
    }

    // ==================== 匹配相关 ====================

    /**
     * 开始匹配
     * @MessageMapping("/app/match/start")
     */
    @MessageMapping("/match/start")
    public void handleStartMatch(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        logger.info("收到匹配请求: payload={}, headers={}", payload, headerAccessor.toMap());

        Long userId = getUserIdFromHeader(headerAccessor);
        String mode = (String) payload.get("mode");

        // 获取用户名
        String username = getUsernameFromHeader(headerAccessor);

        // 获取排除的对手ID
        Long avoidOpponentId = null;
        if (payload.containsKey("avoidOpponentId")) {
            Object avoidObj = payload.get("avoidOpponentId");
            if (avoidObj instanceof Number) {
                avoidOpponentId = ((Number) avoidObj).longValue();
            }
        }

        logger.info("用户 {} ({}) 开始匹配: mode={}, 排除对手: {}", userId, username, mode, avoidOpponentId);

        if (userId == null) {
            logger.error("无法获取用户ID，连接可能未认证");
            return;
        }

        // 加入匹配队列
        matchMaker.joinQueue(userId, username, mode != null ? mode : "casual", avoidOpponentId);
    }

    /**
     * 取消匹配
     * @MessageMapping("/app/match/cancel")
     */
    @MessageMapping("/match/cancel")
    public void handleCancelMatch(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String mode = (String) payload.get("mode");

        logger.info("用户 {} 取消匹配: mode={}", userId, mode);

        // 从匹配队列移除
        matchMaker.cancelMatch(userId, mode != null ? mode : "casual");
    }

    // ==================== 游戏相关 ====================

    /**
     * 落子
     * @MessageMapping("/app/game/move")
     */
    @MessageMapping("/game/move")
    public void handleMove(@Payload GameMoveDto dto, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 落子: roomId={}, x={}, y={}", userId, dto.getRoomId(), dto.getX(), dto.getY());

        try {
            gameService.makeMove(userId, dto.getRoomId(), dto.getX(), dto.getY());
        } catch (Exception e) {
            logger.error("落子失败", e);
            sendError(dto.getRoomId(), e.getMessage());
        }
    }

    /**
     * 认输
     * @MessageMapping("/app/game/resign")
     */
    @MessageMapping("/game/resign")
    public void handleResign(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        logger.info("用户 {} 认输: roomId={}", userId, roomId);

        try {
            gameService.resign(userId, roomId);
        } catch (Exception e) {
            logger.error("认输失败", e);
            sendError(roomId, e.getMessage());
        }
    }

    /**
     * 悔棋
     * @MessageMapping("/app/game/undo")
     */
    @MessageMapping("/game/undo")
    public void handleUndo(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        Boolean accept = payload.get("accept") != null ? Boolean.valueOf(payload.get("accept").toString()) : null;

        logger.info("用户 {} 悔棋请求: roomId={}, accept={}", userId, roomId, accept);

        try {
            if (accept == null) {
                // 发送悔棋请求给对手
                gameService.sendUndoRequest(roomId, userId);
            } else {
                // 响应悔棋请求
                gameService.respondUndoRequest(roomId, userId, accept);
            }
        } catch (Exception e) {
            logger.error("处理悔棋请求失败", e);
            sendError(roomId, e.getMessage());
        }
    }

    /**
     * 超时
     * @MessageMapping("/app/game/timeout")
     */
    @MessageMapping("/game/timeout")
    public void handleTimeout(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        logger.info("用户 {} 超时: roomId={}", userId, roomId);

        try {
            // TODO: 实现超时处理逻辑
            // gameService.timeout(userId, roomId);
            logger.info("超时功能待实现");
        } catch (Exception e) {
            logger.error("超时处理失败", e);
            sendError(roomId, e.getMessage());
        }
    }

    /**
     * 获取游戏状态
     * @MessageMapping("/app/game/state")
     */
    @MessageMapping("/game/state")
    public void handleGetState(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        logger.info("用户 {} 请求游戏状态: roomId={}", userId, roomId);

        try {
            gameService.broadcastGameState(roomId);
        } catch (Exception e) {
            logger.error("获取游戏状态失败", e);
            sendError(roomId, e.getMessage());
        }
    }

    /**
     * 和棋请求
     * @MessageMapping("/app/game/draw")
     */
    @MessageMapping("/game/draw")
    public void handleDraw(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        Boolean accept = payload.get("accept") != null ? Boolean.valueOf(payload.get("accept").toString()) : null;

        logger.info("用户 {} 和棋请求: roomId={}, accept={}", userId, roomId, accept);

        try {
            if (accept == null) {
                // 发送和棋请求给对手
                gameService.sendDrawRequest(roomId, userId);
            } else {
                // 响应和棋请求
                gameService.respondDrawRequest(roomId, userId, accept);
            }
        } catch (Exception e) {
            logger.error("处理和棋请求失败", e);
            sendError(roomId, e.getMessage());
        }
    }

    /**
     * 聊天消息
     * @MessageMapping("/app/game/chat")
     */
    @MessageMapping("/game/chat")
    public void handleChat(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        String content = (String) payload.get("content");

        logger.info("用户 {} 发送聊天消息: roomId={}, content={}", userId, roomId, content);

        try {
            gameService.sendChatMessage(roomId, userId, content);
        } catch (Exception e) {
            logger.error("发送聊天消息失败", e);
        }
    }

    /**
     * 再来一局
     * @MessageMapping("/app/game/play-again")
     */
    @MessageMapping("/game/play-again")
    public void handlePlayAgain(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        Boolean request = payload.get("request") != null ? Boolean.valueOf(payload.get("request").toString()) : null;
        Boolean accept = payload.get("accept") != null ? Boolean.valueOf(payload.get("accept").toString()) : null;
        Boolean cancel = payload.get("cancel") != null ? Boolean.valueOf(payload.get("cancel").toString()) : false;

        logger.info("用户 {} 再来一局: roomId={}, request={}, accept={}, cancel={}", userId, roomId, request, accept, cancel);

        try {
            if (Boolean.TRUE.equals(cancel)) {
                // 取消再来一局请求
                gameService.cancelPlayAgainRequest(roomId, userId);
            } else if (Boolean.TRUE.equals(accept)) {
                // 同意再来一局
                gameService.acceptPlayAgain(roomId, userId);
            } else if (Boolean.TRUE.equals(request)) {
                // 发送再来一局请求
                gameService.sendPlayAgainRequest(roomId, userId);
            }
        } catch (Exception e) {
            logger.error("处理再来一局失败", e);
        }
    }

    /**
     * 玩家准备（游戏结束后点击"再来一局"）
     * @MessageMapping("/app/game/player-ready")
     */
    @MessageMapping("/game/player-ready")
    public void handlePlayerReady(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 准备再来一局: roomId={}", userId, roomId);

        try {
            gameService.sendPlayerReady(roomId, userId);
        } catch (Exception e) {
            logger.error("处理玩家准备失败", e);
        }
    }

    /**
     * 换桌
     * @MessageMapping("/app/game/change-table")
     */
    @MessageMapping("/game/change-table")
    public void handleChangeTable(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 换桌: roomId={}", userId, roomId);

        try {
            gameService.sendChangeTableMessage(roomId, userId);
        } catch (Exception e) {
            logger.error("处理换桌失败", e);
        }
    }

    /**
     * 离开房间（返回首页）
     * @MessageMapping("/app/game/leave")
     */
    @MessageMapping("/game/leave")
    public void handleLeave(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        // 在方法最开始就记录日志
        logger.info("============================================");
        logger.info("🚪🚪🚪 [LEAVE] 收到 /app/game/leave 消息！");
        logger.info("🚪 [LEAVE] 原始 payload: {}", payload);

        logger.info("🚪 [LEAVE] 获取 session attributes");
        if (headerAccessor.getSessionAttributes() != null) {
            logger.info("🚪 [LEAVE] Session attributes: {}", headerAccessor.getSessionAttributes().keySet());
            logger.info("🚪 [LEAVE] UserId in session: {}", headerAccessor.getSessionAttributes().get("userId"));
        }

        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("🚪 [LEAVE] 解析后: userId={}, roomId={}", userId, roomId);
        logger.info("🚪 [LEAVE] 完整payload: {}", payload);

        if (userId == null) {
            logger.error("🚪 [LEAVE] 无法获取用户ID");
            return;
        }

        if (roomId == null || roomId.isEmpty()) {
            logger.error("🚪 [LEAVE] roomId为空: {}", roomId);
            return;
        }

        try {
            logger.info("🚪 [LEAVE] 准备调用 sendPlayerLeaveMessage: roomId={}, userId={}", roomId, userId);
            gameService.sendPlayerLeaveMessage(roomId, userId);
            logger.info("✅ [LEAVE] sendPlayerLeaveMessage 调用完成");
            logger.info("============================================");
        } catch (Exception e) {
            logger.error("🚪 [LEAVE] 处理离开房间失败", e);
            logger.error("🚪 [LEAVE] 错误详情: {}", e.getMessage());
            logger.error("🚪 [LEAVE] 堆栈跟踪: ", e);
            logger.info("============================================");
        }
    }

    // ==================== 房间相关 ====================

    /**
     * 创建房间
     * @MessageMapping("/app/room/create")
     */
    @MessageMapping("/room/create")
    public void handleCreateRoom(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomName = (String) payload.get("roomName");
        String gameMode = (String) payload.get("gameMode");
        String password = (String) payload.get("password");

        logger.info("🏠 [CREATE] 用户 {} 创建房间: name={}, mode={}, password={}", userId, roomName, gameMode, password);

        if (userId == null) {
            logger.error("🏠 [CREATE] 无法获取用户ID");
            return;
        }

        try {
            String roomId = gameService.createRoom(userId, gameMode != null ? gameMode : "casual", roomName, password);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_CREATED");
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("message", "房间创建成功");

            // 直接发送到用户的topic（更可靠的方式）
            String userDestination = "/topic/user/" + userId + "/room";
            logger.info("🏠 [CREATE] 发送响应: userId={}, roomId={}, destination={}", userId, roomId, userDestination);
            messagingTemplate.convertAndSend(userDestination, response);
            logger.info("🏠 [CREATE] 响应已发送到 {}", userDestination);
        } catch (Exception e) {
            logger.error("🏠 [CREATE] 创建房间失败", e);

            Map<String, Object> error = new HashMap<>();
            error.put("type", "ROOM_ERROR");
            error.put("success", false);
            error.put("message", e.getMessage() != null ? e.getMessage() : "创建房间失败");

            // 发送错误响应
            String userDestination = "/topic/user/" + userId + "/room";
            messagingTemplate.convertAndSend(userDestination, error);
            logger.info("🏠 [CREATE] 错误响应已发送到 {}", userDestination);
        }
    }

    /**
     * 加入房间
     * @MessageMapping("/app/room/join")
     */
    @MessageMapping("/room/join")
    public void handleJoinRoom(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        String password = (String) payload.get("password");

        logger.info("🚪 [JOIN] 用户 {} 尝试加入房间: roomId={}, password={}", userId, roomId, password);

        if (roomId == null || roomId.trim().isEmpty()) {
            logger.warn("🚪 [JOIN] 房间ID为空");
            Map<String, Object> error = new HashMap<>();
            error.put("type", "ROOM_ERROR");
            error.put("success", false);
            error.put("message", "房间ID不能为空");
            String userDestination = "/topic/user/" + userId + "/room";
            messagingTemplate.convertAndSend(userDestination, error);
            return;
        }

        try {
            gameService.joinRoom(roomId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_JOINED");
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("message", "加入房间成功");

            // 发送到用户的topic（与创建房间保持一致）
            String userDestination = "/topic/user/" + userId + "/room";
            messagingTemplate.convertAndSend(userDestination, response);

            logger.info("✅ [JOIN] 加入房间成功: roomId={}, userId={}, destination={}", roomId, userId, userDestination);
        } catch (Exception e) {
            logger.error("❌ [JOIN] 加入房间失败: roomId={}, userId={}", roomId, userId, e);

            Map<String, Object> error = new HashMap<>();
            error.put("type", "ROOM_ERROR");
            error.put("success", false);
            error.put("message", e.getMessage());

            // 发送错误到用户的topic
            String userDestination = "/topic/user/" + userId + "/room";
            messagingTemplate.convertAndSend(userDestination, error);
        }
    }

    /**
     * 获取公开房间列表
     * @MessageMapping("/app/room/list")
     */
    @MessageMapping("/room/list")
    public void handleGetRoomList(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("📋 用户 {} 请求房间列表", userId);

        try {
            // 🧹 只清理空房间（不删除有玩家的房间）
            roomManager.cleanAllEmptyRooms();

            // 从RoomManager获取公开房间列表
            List<RoomManager.RoomInfo> publicRooms = roomManager.getPublicRooms();
            logger.info("📋 获取到 {} 个公开房间", publicRooms.size());

            // 转换为前端需要的格式
            List<Map<String, Object>> roomList = new ArrayList<>();
            for (RoomManager.RoomInfo room : publicRooms) {
                Map<String, Object> roomData = new HashMap<>();
                // 房间ID就是房间名称
                roomData.put("id", room.getRoomId());
                roomData.put("name", room.getRoomId());  // 显示的名称也用房间ID
                roomData.put("mode", room.getGameMode());
                roomData.put("playerCount", room.getPlayerCount());
                roomList.add(roomData);
                logger.info("📋 房间: id={}, name={}, mode={}, players={}",
                        room.getRoomId(), room.getRoomId(), room.getGameMode(), room.getPlayerCount());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_LIST");
            response.put("rooms", roomList);

            // 广播给所有用户
            messagingTemplate.convertAndSend("/topic/rooms/public", response);
            logger.info("📋 已发送房间列表: {} 个房间", roomList.size());
        } catch (Exception e) {
            logger.error("❌ 获取房间列表失败", e);
        }
    }

    // ==================== 好友系统相关 ====================

    /**
     * 订阅好友状态更新
     * @MessageMapping("/app/friend/subscribe")
     */
    @MessageMapping("/friend/subscribe")
    public void handleFriendSubscribe(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 请求订阅好友状态", userId);

        if (userId == null) {
            logger.error("无法获取用户ID");
            return;
        }

        try {
            // 获取好友列表并返回当前状态
            List<Map<String, Object>> friends = friendService.getFriendList(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "FRIEND_LIST");
            response.put("friends", friends);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/friend/status",
                    response
            );

            logger.info("已发送好友列表给用户 {}: {} 个好友", userId, friends.size());
        } catch (Exception e) {
            logger.error("获取好友列表失败", e);
            sendErrorToUser(userId, "获取好友列表失败: " + e.getMessage());
        }
    }

    /**
     * 发送游戏邀请给好友
     * @MessageMapping("/app/friend/invite")
     */
    @MessageMapping("/friend/invite")
    public void handleFriendInvite(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        Long friendId = getLongFromPayload(payload, "friendId");
        String gameMode = (String) payload.getOrDefault("gameMode", "casual");

        logger.info("用户 {} 邀请好友 {} 进行游戏: mode={}", userId, friendId, gameMode);

        if (userId == null || friendId == null) {
            logger.error("无法获取用户ID或好友ID");
            return;
        }

        try {
            com.gobang.model.entity.GameInvitation invitation = gameInvitationService.sendInvitation(userId, friendId, gameMode);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "INVITATION_SENT");
            response.put("invitationId", invitation.getId());
            response.put("friendId", friendId);
            response.put("expiresAt", invitation.getExpiresAt());

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/friend/invitation/response",
                    response
            );

            logger.info("游戏邀请已发送: id={}, inviter={}, invitee={}", invitation.getId(), userId, friendId);
        } catch (Exception e) {
            logger.error("发送游戏邀请失败", e);
            sendErrorToUser(userId, "发送邀请失败: " + e.getMessage());
        }
    }

    /**
     * 响应游戏邀请（接受/拒绝）
     * @MessageMapping("/app/friend/invite/respond")
     */
    @MessageMapping("/friend/invite/respond")
    public void handleFriendInviteRespond(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        Long invitationId = getLongFromPayload(payload, "invitationId");
        Boolean accept = payload.get("accept") != null ? Boolean.valueOf(payload.get("accept").toString()) : null;
        String reason = (String) payload.get("reason");

        logger.info("用户 {} 响应游戏邀请: invitationId={}, accept={}", userId, invitationId, accept);

        if (userId == null || invitationId == null || accept == null) {
            logger.error("缺少必要参数");
            return;
        }

        try {
            if (accept) {
                String roomId = gameInvitationService.acceptInvitation(invitationId, userId);
                logger.info("用户 {} 接受了邀请，加入房间: {}", userId, roomId);
            } else {
                gameInvitationService.rejectInvitation(invitationId, userId, reason != null ? reason : "对方拒绝了邀请");
                logger.info("用户 {} 拒绝了邀请: {}", userId, invitationId);
            }
        } catch (Exception e) {
            logger.error("响应游戏邀请失败", e);
            sendErrorToUser(userId, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 取消游戏邀请
     * @MessageMapping("/app/friend/invite/cancel")
     */
    @MessageMapping("/friend/invite/cancel")
    public void handleFriendInviteCancel(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        Long invitationId = getLongFromPayload(payload, "invitationId");

        logger.info("用户 {} 取消游戏邀请: invitationId={}", userId, invitationId);

        if (userId == null || invitationId == null) {
            logger.error("缺少必要参数");
            return;
        }

        try {
            gameInvitationService.cancelInvitation(invitationId, userId);
            logger.info("用户 {} 取消了邀请: {}", userId, invitationId);
        } catch (Exception e) {
            logger.error("取消游戏邀请失败", e);
            sendErrorToUser(userId, "取消邀请失败: " + e.getMessage());
        }
    }

    /**
     * 获取待处理的邀请列表
     * @MessageMapping("/app/friend/invitations")
     */
    @MessageMapping("/friend/invitations")
    public void handleGetInvitations(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 请求待处理的邀请列表", userId);

        if (userId == null) {
            logger.error("无法获取用户ID");
            return;
        }

        try {
            List<Map<String, Object>> invitations = gameInvitationService.getPendingInvitations(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "PENDING_INVITATIONS");
            response.put("invitations", invitations);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/friend/invitations",
                    response
            );

            logger.info("已发送待处理邀请列表给用户 {}: {} 个邀请", userId, invitations.size());
        } catch (Exception e) {
            logger.error("获取待处理邀请列表失败", e);
            sendErrorToUser(userId, "获取邀请列表失败: " + e.getMessage());
        }
    }

    // ==================== 观战系统相关 ====================

    /**
     * 加入观战
     * @MessageMapping("/app/observer/join")
     */
    @MessageMapping("/observer/join")
    public void handleObserverJoin(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 加入观战: roomId={}", userId, roomId);

        if (userId == null || roomId == null) {
            logger.error("缺少必要参数");
            return;
        }

        try {
            com.gobang.model.dto.ObserverRoomDto roomDto = observerService.joinObserver(roomId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "OBSERVER_JOINED");
            response.put("roomId", roomId);
            response.put("observerCount", roomDto.getObserverCount());

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/observer/response",
                    response
            );

            // 广播观战者数量变化给房间内的所有人
            Map<String, Object> broadcastMsg = new HashMap<>();
            broadcastMsg.put("type", "OBSERVER_COUNT_CHANGE");
            broadcastMsg.put("roomId", roomId);
            broadcastMsg.put("observerCount", roomDto.getObserverCount());

            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/observer", broadcastMsg);

            logger.info("用户 {} 成功加入观战: roomId={}", userId, roomId);
        } catch (Exception e) {
            logger.error("加入观战失败", e);
            sendErrorToUser(userId, "加入观战失败: " + e.getMessage());
        }
    }

    /**
     * 离开观战
     * @MessageMapping("/app/observer/leave")
     */
    @MessageMapping("/observer/leave")
    public void handleObserverLeave(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 离开观战: roomId={}", userId, roomId);

        if (userId == null || roomId == null) {
            logger.error("缺少必要参数");
            return;
        }

        try {
            observerService.leaveObserver(roomId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "OBSERVER_LEFT");
            response.put("roomId", roomId);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/observer/response",
                    response
            );

            // 广播观战者数量变化
            Map<String, Object> broadcastMsg = new HashMap<>();
            broadcastMsg.put("type", "OBSERVER_COUNT_CHANGE");
            broadcastMsg.put("roomId", roomId);
            broadcastMsg.put("observerCount", gameService.getRoomObserverCount(roomId));

            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/observer", broadcastMsg);

            logger.info("用户 {} 成功离开观战: roomId={}", userId, roomId);
        } catch (Exception e) {
            logger.error("离开观战失败", e);
            sendErrorToUser(userId, "离开观战失败: " + e.getMessage());
        }
    }

    /**
     * 获取可观战房间列表
     * @MessageMapping("/app/observer/rooms")
     */
    @MessageMapping("/observer/rooms")
    public void handleGetObservableRooms(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 请求可观战房间列表", userId);

        if (userId == null) {
            logger.error("无法获取用户ID");
            return;
        }

        try {
            List<Map<String, Object>> rooms = roomManager.getPublicRooms().stream()
                    .map(room -> {
                        Map<String, Object> roomData = new HashMap<>();
                        roomData.put("id", room.getRoomId());
                        roomData.put("name", room.getRoomName());
                        roomData.put("mode", room.getGameMode());
                        roomData.put("playerCount", room.getPlayerCount());
                        roomData.put("observerCount", gameService.getRoomObserverCount(room.getRoomId()));
                        return roomData;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("type", "OBSERVABLE_ROOMS");
            response.put("rooms", rooms);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/observer/rooms",
                    response
            );

            logger.info("已发送可观战房间列表给用户 {}: {} 个房间", userId, rooms.size());
        } catch (Exception e) {
            logger.error("获取可观战房间列表失败", e);
            sendErrorToUser(userId, "获取房间列表失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从消息头中获取用户ID
     * 优先从Principal获取，如果为空则从session attributes获取
     */
    private Long getUserIdFromHeader(SimpMessageHeaderAccessor headerAccessor) {
        // 首先尝试从Principal获取
        Principal principal = headerAccessor.getUser();
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                logger.warn("Principal name不是有效的用户ID: {}", principal.getName());
            }
        }

        // 如果Principal为空，尝试从session attributes获取
        Object userIdObj = headerAccessor.getSessionAttributes().get("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj != null) {
            try {
                return Long.parseLong(userIdObj.toString());
            } catch (NumberFormatException e) {
                logger.warn("session attributes中的userId不是有效的数字: {}", userIdObj);
            }
        }

        logger.error("无法从消息头获取用户ID: Principal={}, session userId={}",
                principal, userIdObj);
        return null;
    }

    /**
     * 从消息头中获取用户名
     */
    private String getUsernameFromHeader(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        if (userId != null) {
            try {
                // 首先尝试从session attributes获取
                Object usernameObj = headerAccessor.getSessionAttributes().get("username");
                if (usernameObj != null) {
                    return usernameObj.toString();
                }
                // 从用户服务获取用户名
                User user = userService.getUserById(userId);
                return user != null ? user.getUsername() : "User" + userId;
            } catch (Exception e) {
                logger.warn("获取用户名失败: userId={}", userId, e);
                return "User" + userId;
            }
        }
        return "Unknown";
    }

    /**
     * 发送错误消息
     */
    private void sendError(String roomId, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("message", message);

        if (roomId != null) {
            messagingTemplate.convertAndSend("/topic/room/" + roomId, error);
        }
    }

    /**
     * 发送错误消息给指定用户
     */
    private void sendErrorToUser(Long userId, String message) {
        if (userId == null) return;

        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("message", message);

        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                "/queue/error",
                error
        );
    }

    /**
     * 从payload中获取Long值
     */
    private Long getLongFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;

        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
