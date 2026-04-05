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

        logger.info("用户 {} 创建房间: name={}, mode={}", userId, roomName, gameMode);

        try {
            String roomId = gameService.createRoom(userId, gameMode != null ? gameMode : "CLASSIC");

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_CREATED");
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("message", "房间创建成功");

            // 发送到用户专属队列
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/room/created", response);

            logger.info("房间创建成功: roomId={}, userId={}", roomId, userId);
        } catch (Exception e) {
            logger.error("创建房间失败", e);

            Map<String, Object> error = new HashMap<>();
            error.put("type", "ROOM_ERROR");
            error.put("success", false);
            error.put("message", e.getMessage());

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/room/error", error);
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

        logger.info("用户 {} 加入房间: roomId={}", userId, roomId);

        try {
            gameService.joinRoom(roomId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_JOINED");
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("message", "加入房间成功");

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/room/joined", response);

            logger.info("加入房间成功: roomId={}, userId={}", roomId, userId);
        } catch (Exception e) {
            logger.error("加入房间失败", e);

            Map<String, Object> error = new HashMap<>();
            error.put("type", "ROOM_ERROR");
            error.put("success", false);
            error.put("message", e.getMessage());

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/room/error", error);
        }
    }

    /**
     * 获取公开房间列表
     * @MessageMapping("/app/room/list")
     */
    @MessageMapping("/room/list")
    public void handleGetRoomList(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 请求房间列表", userId);

        try {
            // 从RoomManager获取公开房间列表
            List<RoomManager.RoomInfo> publicRooms = roomManager.getPublicRooms();

            // 转换为前端需要的格式
            List<Map<String, Object>> roomList = new ArrayList<>();
            for (RoomManager.RoomInfo room : publicRooms) {
                Map<String, Object> roomData = new HashMap<>();
                roomData.put("id", room.getRoomId());
                roomData.put("name", room.getRoomName());
                roomData.put("mode", room.getGameMode());
                roomData.put("playerCount", room.getPlayerCount());
                roomList.add(roomData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_LIST");
            response.put("rooms", roomList);

            // 广播给所有用户
            messagingTemplate.convertAndSend("/topic/rooms/public", response);

            logger.info("返回房间列表: {} 个房间", roomList.size());
        } catch (Exception e) {
            logger.error("获取房间列表失败", e);
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
}
