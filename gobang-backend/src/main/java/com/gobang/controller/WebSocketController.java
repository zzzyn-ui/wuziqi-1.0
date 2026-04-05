package com.gobang.controller;

import com.gobang.core.match.MatchMaker;
import com.gobang.model.dto.*;
import com.gobang.service.GameServiceImpl;
import com.gobang.service.UserServiceImpl;
import com.gobang.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 消息控制器
 * 使用 @MessageMapping 处理 STOMP 消息
 */
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    private final UserServiceImpl userService;
    private final GameServiceImpl gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchMaker matchMaker;

    // ==================== 认证相关 ====================

    /**
     * 登录请求
     */
    @MessageMapping("/auth/login")
    public void handleLogin(@Payload LoginDto loginDto) {
        logger.info("收到登录请求: username={}", loginDto.getUsername());

        try {
            String token = userService.login(loginDto);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "AUTH_SUCCESS");
            response.put("token", token);

            // TODO: 获取用户信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", 1);
            userInfo.put("username", loginDto.getUsername());
            userInfo.put("nickname", "测试用户");
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

    // ==================== 游戏相关 ====================

    /**
     * 落子
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
     */
    @MessageMapping("/game/resign")
    public void handleResign(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 认输: roomId={}", userId, roomId);

        if (roomId != null) {
            try {
                gameService.resign(userId, roomId);
            } catch (Exception e) {
                logger.error("认输失败", e);
                sendError(roomId, e.getMessage());
            }
        } else {
            logger.warn("认输失败: roomId 为空");
        }
    }

    /**
     * 聊天消息
     */
    @MessageMapping("/game/chat")
    public void handleChat(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        String content = (String) payload.get("content");
        String sender = getUsernameFromHeader(headerAccessor);

        logger.info("用户 {} 发送聊天消息: roomId={}, content={}", userId, roomId, content);

        if (roomId != null && content != null && !content.trim().isEmpty()) {
            Map<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("type", "CHAT_MESSAGE");
            chatMessage.put("roomId", roomId);
            chatMessage.put("sender", sender);
            chatMessage.put("content", content.trim());
            chatMessage.put("timestamp", System.currentTimeMillis());

            // 广播聊天消息到房间内的所有玩家
            messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage);
        }
    }

    /**
     * 超时处理
     */
    @MessageMapping("/game/timeout")
    public void handleTimeout(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 超时: roomId={}", userId, roomId);

        if (roomId != null) {
            try {
                gameService.timeout(userId, roomId);
            } catch (Exception e) {
                logger.error("超时处理失败", e);
            }
        }
    }

    /**
     * 悔棋请求
     */
    @MessageMapping("/game/undo")
    public void handleUndo(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        Boolean request = (Boolean) payload.get("request");
        Boolean accept = (Boolean) payload.get("accept");

        logger.info("用户 {} 悔棋操作: roomId={}, request={}, accept={}", userId, roomId, request, accept);

        if (roomId == null) return;

        if (request != null && request) {
            // 发送悔棋请求给对手
            Map<String, Object> undoRequest = new HashMap<>();
            undoRequest.put("type", "UNDO_REQUEST");
            undoRequest.put("requesterId", userId);
            undoRequest.put("requesterName", getUsernameFromHeader(headerAccessor));

            messagingTemplate.convertAndSend("/topic/room/" + roomId, undoRequest);
        } else if (accept != null) {
            // 响应悔棋请求
            Map<String, Object> undoResponse = new HashMap<>();
            undoResponse.put("type", "UNDO_RESPONSE");
            undoResponse.put("accepted", accept);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, undoResponse);

            // 如果同意，执行悔棋操作
            if (accept) {
                try {
                    gameService.undoMove(roomId);
                } catch (Exception e) {
                    logger.error("悔棋失败", e);
                    sendError(roomId, e.getMessage());
                }
            }
        }
    }

    /**
     * 和棋请求
     */
    @MessageMapping("/game/draw")
    public void handleDraw(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        Boolean request = (Boolean) payload.get("request");
        Boolean accept = (Boolean) payload.get("accept");

        logger.info("用户 {} 和棋操作: roomId={}, request={}, accept={}", userId, roomId, request, accept);

        if (roomId == null) return;

        if (request != null && request) {
            // 发送和棋请求给对手
            Map<String, Object> drawRequest = new HashMap<>();
            drawRequest.put("type", "DRAW_REQUEST");
            drawRequest.put("requesterId", userId);
            drawRequest.put("requesterName", getUsernameFromHeader(headerAccessor));

            messagingTemplate.convertAndSend("/topic/room/" + roomId, drawRequest);
        } else if (accept != null) {
            // 响应和棋请求
            Map<String, Object> drawResponse = new HashMap<>();
            drawResponse.put("type", "DRAW_RESPONSE");
            drawResponse.put("accepted", accept);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, drawResponse);

            if (accept) {
                // 和棋成功，结束游戏
                logger.info("和棋成功，结束游戏");
                try {
                    gameService.draw(roomId);
                } catch (Exception e) {
                    logger.error("和棋处理失败", e);
                }
            }
        }
    }

    /**
     * 再来一局
     */
    @MessageMapping("/game/play-again")
    public void handlePlayAgain(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");
        Boolean request = (Boolean) payload.get("request");
        Boolean accept = (Boolean) payload.get("accept");
        Boolean cancel = (Boolean) payload.get("cancel");

        logger.info("用户 {} 再来一局操作: roomId={}, request={}, accept={}, cancel={}", userId, roomId, request, accept, cancel);

        if (roomId == null) return;

        if (cancel != null && cancel) {
            // 取消请求
            gameService.setPlayAgainAgreement(roomId, userId, false);
            return;
        }

        if (request != null && request) {
            // 发送再来一局请求
            gameService.setPlayAgainAgreement(roomId, userId, true);

            Map<String, Object> playAgainRequest = new HashMap<>();
            playAgainRequest.put("type", "PLAY_AGAIN_REQUEST");
            playAgainRequest.put("userId", userId);
            playAgainRequest.put("username", getUsernameFromHeader(headerAccessor));

            messagingTemplate.convertAndSend("/topic/room/" + roomId, playAgainRequest);

            // 检查是否双方都同意（之前已经有人点过了）
            if (gameService.checkBothPlayersAgreePlayAgain(roomId)) {
                // 双方都同意，清空棋盘重新开始
                gameService.resetGame(roomId);

                Map<String, Object> gameStart = new HashMap<>();
                gameStart.put("type", "PLAY_AGAIN_ACCEPT");
                gameStart.put("roomId", roomId);

                messagingTemplate.convertAndSend("/topic/room/" + roomId, gameStart);
                logger.info("双方同意再来一局，游戏已重置: roomId={}", roomId);
            }
        } else if (accept != null && accept) {
            // 接收方同意再来一局
            gameService.setPlayAgainAgreement(roomId, userId, true);

            // 检查是否双方都同意
            if (gameService.checkBothPlayersAgreePlayAgain(roomId)) {
                // 双方都同意，清空棋盘重新开始
                gameService.resetGame(roomId);

                Map<String, Object> gameStart = new HashMap<>();
                gameStart.put("type", "PLAY_AGAIN_ACCEPT");
                gameStart.put("roomId", roomId);

                messagingTemplate.convertAndSend("/topic/room/" + roomId, gameStart);
                logger.info("双方同意再来一局，游戏已重置: roomId={}", roomId);
            }
        }
    }

    /**
     * 换桌
     */
    @MessageMapping("/game/change-table")
    public void handleChangeTable(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String roomId = (String) payload.get("roomId");

        logger.info("用户 {} 换桌: roomId={}", userId, roomId);

        if (roomId != null) {
            // 广播换桌消息
            Map<String, Object> changeTable = new HashMap<>();
            changeTable.put("type", "CHANGE_TABLE");
            changeTable.put("userId", userId);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, changeTable);
        }
    }

    /**
     * 创建房间
     */
    @MessageMapping("/room/create")
    public void handleCreateRoom(@Payload CreateRoomDto dto, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 创建房间: mode={}", userId, dto.getMode());

        try {
            String roomId = gameService.createRoom(userId, dto.getMode());

            Map<String, Object> response = new HashMap<>();
            response.put("type", "ROOM_CREATED");
            response.put("roomId", roomId);

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/room", response);
        } catch (Exception e) {
            logger.error("创建房间失败", e);
        }
    }

    /**
     * 加入房间
     */
    @MessageMapping("/room/join")
    public void handleJoinRoom(@Payload JoinRoomDto dto, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        logger.info("用户 {} 加入房间: roomId={}", userId, dto.getRoomId());

        try {
            gameService.joinRoom(dto.getRoomId(), userId);
        } catch (Exception e) {
            logger.error("加入房间失败", e);
        }
    }

    // ==================== 匹配相关 ====================

    /**
     * 开始匹配
     */
    @MessageMapping("/match/start")
    public void handleStartMatch(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        logger.info("========================================");
        logger.info("收到匹配请求");
        logger.info("Payload: {}", payload);
        logger.info("Headers: {}", headerAccessor.toMap());
        logger.info("========================================");

        Long userId = getUserIdFromHeader(headerAccessor);
        String mode = (String) payload.get("mode");

        // 获取用户名
        String username = getUsernameFromHeader(headerAccessor);

        logger.info("提取的信息 - userId: {}, username: {}, mode: {}", userId, username, mode);

        if (userId == null) {
            logger.error("无法获取用户ID，连接可能未认证");
            logger.error("用户信息: userId={}, username={}, mode={}", userId, username, mode);
            return;
        }

        logger.info("用户 {} ({}) 准备加入 {} 匹配队列", userId, username, mode != null ? mode : "casual");

        // 加入匹配队列
        matchMaker.joinQueue(userId, username, mode != null ? mode : "casual");
        logger.info("匹配请求处理完成");
    }

    /**
     * 取消匹配
     */
    @MessageMapping("/match/cancel")
    public void handleCancelMatch(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromHeader(headerAccessor);
        String mode = (String) payload.get("mode");

        logger.info("用户 {} 取消匹配: mode={}", userId, mode);

        // 从匹配队列移除
        matchMaker.cancelMatch(userId, mode != null ? mode : "casual");
    }

    // ==================== 辅助方法 ====================

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserIdFromHeader(SimpMessageHeaderAccessor headerAccessor) {
        // 首先尝试从 Principal 获取
        Principal principal = headerAccessor.getUser();
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // 从 session attributes 获取（在 CONNECT 时存储）
        Object userIdObj = headerAccessor.getSessionAttributes().get("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }

        logger.warn("无法从 Principal 或 session 获取用户 ID");
        return null;
    }

    private String getUsernameFromHeader(SimpMessageHeaderAccessor headerAccessor) {
        // 从 session attributes 获取
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username != null) {
            return username;
        }

        // 回退到从用户服务获取
        Long userId = getUserIdFromHeader(headerAccessor);
        if (userId != null) {
            try {
                com.gobang.model.entity.User user = userService.getUserById(userId);
                return user != null ? user.getUsername() : "User" + userId;
            } catch (Exception e) {
                logger.warn("获取用户名失败: userId={}", userId, e);
                return "User" + userId;
            }
        }

        return "Unknown";
    }

    private void sendError(String roomId, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("message", message);

        if (roomId != null) {
            messagingTemplate.convertAndSend("/topic/room/" + roomId, error);
        }
    }
}
