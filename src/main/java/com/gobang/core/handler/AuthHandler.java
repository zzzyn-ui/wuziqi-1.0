package com.gobang.core.handler;

import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.security.RateLimitManager;
import com.gobang.core.social.FriendManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.AuthService;
import com.gobang.service.FriendService;
import com.gobang.service.UserService;
import com.gobang.core.netty.ResponseUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证消息处理器
 * 处理登录、注册等认证相关消息
 */
public class AuthHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    private static final AttributeKey<Long> USER_ID_ATTR = AttributeKey.valueOf("userId");
    private static final AttributeKey<Boolean> AUTHENTICATED_ATTR = AttributeKey.valueOf("authenticated");

    private final AuthService authService;
    private final UserService userService;
    private final RateLimitManager rateLimitManager;
    private final FriendManager friendManager;
    private final FriendService friendService;

    public AuthHandler(AuthService authService, UserService userService, RateLimitManager rateLimitManager,
                       FriendManager friendManager, FriendService friendService) {
        this.authService = authService;
        this.userService = userService;
        this.rateLimitManager = rateLimitManager;
        this.friendManager = friendManager;
        this.friendService = friendService;
    }

    public AuthHandler(AuthService authService, UserService userService, RateLimitManager rateLimitManager) {
        this(authService, userService, rateLimitManager, null, null);
    }

    public AuthHandler(AuthService authService, UserService userService) {
        this(authService, userService, null, null, null);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());
        logger.info("=== AuthHandler.handle() called ===");
        logger.info("Message type: {}", messageType);
        logger.info("Sequence ID: {}", packet.getSequenceId());
        boolean hasBody = packet.getBody() != null && !packet.getBody().isEmpty();
        logger.info("Has body: {}", hasBody);
        if (hasBody) {
            logger.info("Body size: {} bytes", packet.getBody().size());
        }

        switch (messageType) {
            case AUTH_LOGIN:
                logger.info("Processing AUTH_LOGIN request...");
                handleLogin(ctx, packet);
                break;
            case AUTH_REGISTER:
                logger.info("Processing AUTH_REGISTER request...");
                handleRegister(ctx, packet);
                break;
            default:
                logger.warn("Unsupported message type for AuthHandler: {}", messageType);
                break;
        }
    }

    /**
     * 处理登录请求
     */
    private void handleLogin(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        try {
            logger.info("=== handleLogin() called ===");

            // 限流检查
            if (rateLimitManager != null) {
                if (!rateLimitManager.tryAcquire(RateLimitManager.LimitType.LOGIN, ctx.channel())) {
                    sendErrorResponse(ctx, packet, rateLimitManager.getErrorMessage(RateLimitManager.LimitType.LOGIN));
                    logger.warn("Login rate limit exceeded from: {}", ctx.channel().remoteAddress());
                    return;
                }
            }

            GobangProto.LoginRequest request = GobangProto.LoginRequest.parseFrom(packet.getBody());
            logger.info("Login request from: {}", request.getUsername());

            // 验证用户
            logger.info("Calling authService.login()...");
            User user = authService.login(request.getUsername(), request.getPassword());
            logger.info("authService.login() returned: {}", user != null ? "SUCCESS" : "FAILED");

            GobangProto.AuthResponse.Builder responseBuilder = GobangProto.AuthResponse.newBuilder();

            if (user != null) {
                // 生成JWT令牌
                String token = authService.generateToken(user.getId());

                // 构建用户信息
                GobangProto.UserInfo userInfo = GobangProto.UserInfo.newBuilder()
                        .setUserId(String.valueOf(user.getId()))
                        .setUsername(user.getUsername())
                        .setNickname(user.getNickname())
                        .setAvatar(user.getAvatar())
                        .setRating(user.getRating())
                        .setLevel(user.getLevel())
                        .setExp(user.getExp())
                        .setOnline(true)
                        .build();

                responseBuilder.setSuccess(true)
                        .setToken(token)
                        .setMessage("登录成功")
                        .setUserInfo(userInfo);

                // 保存用户信息到Channel属性
                ctx.channel().attr(USER_ID_ATTR).set(user.getId());
                ctx.channel().attr(AUTHENTICATED_ATTR).set(true);

                // 通知好友管理器用户上线
                if (friendManager != null && friendService != null) {
                    friendManager.userOnline(user.getId(), ctx.channel());
                    // 加载用户的好友列表
                    try {
                        var friendList = friendService.getFriendList(user.getId());
                        var friendIds = friendList.stream()
                                .map(com.gobang.model.entity.User::getId)
                                .toList();
                        friendManager.loadUserFriends(user.getId(), friendIds);
                    } catch (Exception e) {
                        logger.warn("Failed to load friends for user {}", user.getId(), e);
                    }
                }

                logger.info("User {} logged in successfully", user.getUsername());
            } else {
                responseBuilder.setSuccess(false)
                        .setMessage("用户名或密码错误");
                logger.warn("Login failed for: {}", request.getUsername());
            }

            // 发送响应（自动选择JSON或Protobuf格式）
            GobangProto.Packet responsePacket = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.AUTH_RESPONSE)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(responseBuilder.build().toByteString())
                    .build();

            // 使用ResponseUtil发送响应（自动检测channel模式）
            ResponseUtil.sendResponse(ctx.channel(), responsePacket,
                new ResponseUtil.AuthResponseJsonBuilder(
                    responseBuilder.getSuccess(),
                    responseBuilder.getMessage(),
                    responseBuilder.getUserInfo(),
                    responseBuilder.getToken()
                ));

        } catch (Exception e) {
            logger.error("Error handling login", e);
            sendErrorResponse(ctx, packet, "登录处理失败");
        }
    }

    /**
     * 处理注册请求
     */
    private void handleRegister(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        try {
            // 限流检查
            if (rateLimitManager != null) {
                if (!rateLimitManager.tryAcquire(RateLimitManager.LimitType.REGISTER, ctx.channel())) {
                    sendErrorResponse(ctx, packet, rateLimitManager.getErrorMessage(RateLimitManager.LimitType.REGISTER));
                    logger.warn("Register rate limit exceeded from: {}", ctx.channel().remoteAddress());
                    return;
                }
            }

            GobangProto.RegisterRequest request = GobangProto.RegisterRequest.parseFrom(packet.getBody());

            logger.info("Register request from: {}", request.getUsername());

            // 检查参数
            if (request.getUsername() == null || request.getUsername().isEmpty() ||
                request.getPassword() == null || request.getPassword().isEmpty()) {
                sendErrorResponse(ctx, packet, "用户名和密码不能为空");
                return;
            }

            if (request.getUsername().length() < 3 || request.getUsername().length() > 16) {
                sendErrorResponse(ctx, packet, "用户名长度必须在3-16位之间");
                return;
            }

            if (request.getPassword().length() < 6) {
                sendErrorResponse(ctx, packet, "密码长度不能少于6位");
                return;
            }

            // 注册用户
            String nickname = request.getNickname() != null && !request.getNickname().isEmpty()
                    ? request.getNickname() : request.getUsername();
            User user = authService.register(request.getUsername(), request.getPassword(), nickname);

            GobangProto.AuthResponse.Builder responseBuilder = GobangProto.AuthResponse.newBuilder();

            if (user != null) {
                // 注册成功，自动登录
                String token = authService.generateToken(user.getId());

                GobangProto.UserInfo userInfo = GobangProto.UserInfo.newBuilder()
                        .setUserId(String.valueOf(user.getId()))
                        .setUsername(user.getUsername())
                        .setNickname(user.getNickname())
                        .setAvatar(user.getAvatar())
                        .setRating(user.getRating())
                        .setLevel(user.getLevel())
                        .setExp(user.getExp())
                        .setOnline(true)
                        .build();

                responseBuilder.setSuccess(true)
                        .setToken(token)
                        .setMessage("注册成功")
                        .setUserInfo(userInfo);

                ctx.channel().attr(USER_ID_ATTR).set(user.getId());
                ctx.channel().attr(AUTHENTICATED_ATTR).set(true);

                logger.info("User {} registered successfully", user.getUsername());
            } else {
                responseBuilder.setSuccess(false)
                        .setMessage("用户名已存在");
                logger.warn("Registration failed: username already exists - {}", request.getUsername());
            }

            GobangProto.Packet responsePacket = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.AUTH_RESPONSE)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(responseBuilder.build().toByteString())
                    .build();

            // 使用ResponseUtil发送响应
            ResponseUtil.sendResponse(ctx.channel(), responsePacket,
                new ResponseUtil.AuthResponseJsonBuilder(
                    responseBuilder.getSuccess(),
                    responseBuilder.getMessage(),
                    responseBuilder.getUserInfo(),
                    responseBuilder.getToken()
                ));

        } catch (Exception e) {
            logger.error("Error handling register", e);
            sendErrorResponse(ctx, packet, "注册处理失败");
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        logger.info("=== sendErrorResponse() called ===");
        logger.info("Error message: {}", message);

        GobangProto.AuthResponse response = GobangProto.AuthResponse.newBuilder()
                .setSuccess(false)
                .setMessage(message)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.AUTH_RESPONSE)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(response.toByteString())
                .build();

        // 使用 ResponseUtil 发送响应（自动检测 JSON 模式）
        ResponseUtil.sendResponse(ctx.channel(), packet,
            new ResponseUtil.AuthResponseJsonBuilder(false, message, null));
        logger.info("Error response sent");
    }

    /**
     * 检查Channel是否已认证
     */
    public static boolean isAuthenticated(Channel channel) {
        Boolean authenticated = channel.attr(AUTHENTICATED_ATTR).get();
        return Boolean.TRUE.equals(authenticated);
    }

    /**
     * 获取Channel关联的用户ID
     */
    public static Long getUserId(Channel channel) {
        return channel.attr(USER_ID_ATTR).get();
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.AUTH_LOGIN; // 主要处理登录，注册也在此处理
    }
}
