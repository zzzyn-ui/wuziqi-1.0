package com.gobang.core.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.handler.AuthHandler;
import com.gobang.core.room.RoomManager;
import com.gobang.mapper.FriendMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.User;
import com.gobang.service.FriendService;
import com.gobang.service.UserService;
import com.gobang.util.JwtUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP静态文件处理器
 * 处理静态文件请求（HTML、CSS、JS等）和API请求
 */
public class HttpRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);

    private final String webRoot;
    private final String wsPath;
    private final RoomManager roomManager;
    private final FriendService friendService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public HttpRequestHandler(String webRoot, String wsPath, RoomManager roomManager,
                              FriendService friendService, UserService userService, JwtUtil jwtUtil) {
        this.webRoot = webRoot;
        this.wsPath = wsPath;
        this.roomManager = roomManager;
        this.friendService = friendService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = new ObjectMapper();

        // 查找static目录
        File staticDir = findStaticDirectory();
        if (staticDir != null) {
            this.actualWebRoot = staticDir.getAbsolutePath();
            logger.info("Static files directory: {}", this.actualWebRoot);
        } else {
            this.actualWebRoot = null;
            logger.warn("Static files directory not found!");
        }
    }

    private String actualWebRoot;

    /**
     * 处理HTTP请求
     * @return true如果请求已被处理并已释放request，false如果应该继续传递给下一个处理器
     */
    public boolean handle(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();

        logger.info("HttpRequestHandler.handle: {} {}", request.method(), uri);

        // 处理API请求
        if (uri.startsWith("/api/")) {
            handleApiRequest(ctx, request, uri);
            return true; // API请求已处理
        }

        // 提取URL参数中的token并保存到channel属性
        if (uri.contains("?")) {
            try {
                QueryStringDecoder decoder = new QueryStringDecoder(uri);
                var tokens = decoder.parameters().get("token");
                if (tokens != null && !tokens.isEmpty()) {
                    String token = tokens.get(0);
                    logger.info("Token found in URL for {}: length={}", uri, token.length());
                    // 保存token到channel属性
                    ctx.channel().attr(io.netty.util.AttributeKey.<String>valueOf("authToken")).set(token);
                }
            } catch (Exception e) {
                logger.error("Failed to extract token from URI", e);
            }
        }

        // WebSocket升级请求不处理，交给WebSocketServerProtocolHandler
        if (uri.startsWith(wsPath)) {
            return false;
        }

        // 处理静态文件请求
        if (uri.equals("/")) {
            uri = "/login.html";
        }

        // 移除查询参数，只保留路径部分用于文件查找
        String path = uri.split("\\?")[0];
        logger.debug("HTTP request: {} (path: {})", uri, path);

        byte[] content = null;
        String contentType = null;

        // 尝试从文件系统加载
        if (actualWebRoot != null) {
            String filePath = actualWebRoot + path;
            File file = new File(filePath);

            if (file.exists() && !file.isDirectory()) {
                try {
                    content = Files.readAllBytes(file.toPath());
                    contentType = getContentType(path);
                    logger.debug("从文件系统加载: {}", path);
                } catch (IOException e) {
                    logger.warn("从文件系统读取失败: {}", path, e);
                }
            }
        }

        // 如果文件系统加载失败，尝试从classpath加载
        if (content == null) {
            // 移除开头的斜杠
            String resourcePath = path.startsWith("/") ? path.substring(1) : path;
            content = readStaticFileFromClasspath(resourcePath);
            if (content == null) {
                // 尝试添加 static/ 前缀（Spring Boot jar 结构）
                content = readStaticFileFromClasspath("static/" + resourcePath);
            }
            if (content != null) {
                contentType = getContentType(path);
                logger.info("从classpath加载静态文件: {}", path);
            }
        }

        // 如果是目录请求，尝试返回index.html
        if (content == null && path.endsWith("/")) {
            String indexPath = path + "index.html";
            if (actualWebRoot != null) {
                File indexFile = new File(actualWebRoot + indexPath);
                if (indexFile.exists()) {
                    content = Files.readAllBytes(indexFile.toPath());
                    contentType = getContentType(indexPath);
                }
            }
            if (content == null) {
                content = readStaticFileFromClasspath(indexPath.substring(1));
                if (content != null) {
                    contentType = getContentType(indexPath);
                }
            }
        }

        if (content == null) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            // 错误响应已发送，释放请求
            return true;
        }

        if (contentType == null) {
            contentType = getContentType(path);
        }

        // 构建响应
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            io.netty.buffer.Unpooled.wrappedBuffer(content)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        // 禁用缓存，确保获取最新内容
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        response.headers().set("Pragma", "no-cache");
        response.headers().set("Expires", "0");

        // 保持连接
        if (!HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, "close");
        }

        // 写入响应并关闭请求
        ctx.writeAndFlush(response).addListener(future -> {
            request.release();
        });

        return true;
    }

    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            io.netty.buffer.Unpooled.copiedBuffer("Error: " + status.toString(), io.netty.util.CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        ctx.writeAndFlush(response);
    }

    /**
     * 根据文件名获取Content-Type
     */
    private String getContentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (fileName.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (fileName.endsWith(".ico")) {
            return "image/x-icon";
        } else if (fileName.endsWith(".woff")) {
            return "font/woff";
        } else if (fileName.endsWith(".woff2")) {
            return "font/woff2";
        } else if (fileName.endsWith(".ttf")) {
            return "font/ttf";
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * 查找static目录
     */
    private File findStaticDirectory() {
        // 尝试多个可能的路径，优先使用 src 目录（开发时直接修改 src 目录）
        String[] possiblePaths = {
            "src/main/resources/static",     // 开发目录，优先使用
            "target/classes/static",         // 编译后的目录
            "static"                         // 备用目录
        };

        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                logger.info("使用静态文件目录: {}", path);
                return dir;
            }
        }

        logger.error("未找到任何静态文件目录！");
        return null;
    }

    /**
     * 从classpath读取静态文件（用于从jar内部加载）
     */
    private byte[] readStaticFileFromClasspath(String resourcePath) {
        InputStream is = null;
        try {
            // 尝试从classpath读取
            is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                logger.debug("Resource not found in classpath: {}", resourcePath);
                return null;
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = is.read(data)) > 0) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            logger.error("Failed to read resource from classpath: {}", resourcePath, e);
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * 处理API请求
     */
    private void handleApiRequest(ChannelHandlerContext ctx, FullHttpRequest request, String uri) {
        try {
            // 提取路径（移除查询参数）
            String path = uri.split("\\?")[0];

            logger.info("处理API请求: {} {}", request.method(), path);

            // 处理OPTIONS预检请求
            if ("OPTIONS".equals(request.method().name())) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");
                ctx.writeAndFlush(response);
                return;
            }

            String jsonResponse = "";
            HttpResponseStatus status = HttpResponseStatus.OK;

            if (path.equals("/api/rooms/playing")) {
                // 获取正在进行的对局数量
                int playingCount = 0;
                if (roomManager != null) {
                    playingCount = roomManager.getPlayingRoomsCount();
                }
                jsonResponse = "{\"count\":" + playingCount + ",\"rooms\":[]}";
                logger.debug("API请求: /api/rooms/playing - 返回: " + jsonResponse);
            } else if (path.equals("/api/leaderboard") || path.equals("/api/rank")) {
                // 获取排行榜
                jsonResponse = handleLeaderboard(ctx, request);
                if (jsonResponse == null) {
                    return; // 错误响应已发送
                }
            } else if (path.equals("/api/friends/requests")) {
                // 获取待处理的好友请求
                jsonResponse = handleGetFriendRequests(ctx, request, path);
                if (jsonResponse == null) {
                    return; // 错误响应已发送
                }
            } else if (path.equals("/api/friends/list")) {
                // 获取好友列表
                jsonResponse = handleGetFriendList(ctx, request, path);
                if (jsonResponse == null) {
                    return; // 错误响应已发送
                }
            } else {
                jsonResponse = "{\"success\":false,\"message\":\"Unknown API endpoint\"}";
                status = HttpResponseStatus.NOT_FOUND;
            }

            // 构建响应
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(jsonResponse, io.netty.util.CharsetUtil.UTF_8)
            );

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }

            logger.info("发送API响应: status={}, length={}", status, jsonResponse.length());
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            logger.error("处理API请求失败: " + uri, e);
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 处理获取待处理好友请求
     */
    private String handleGetFriendRequests(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        try {
            logger.info("收到好友请求API调用");

            // 验证JWT token
            String authHeader = request.headers().get("Authorization");
            logger.info("Authorization header: {}", authHeader);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("未授权：缺少或无效的Authorization header");
                return sendJsonError(401, "未授权");
            }

            String token = authHeader.substring(7);
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                logger.warn("token无效：无法提取用户ID");
                return sendJsonError(401, "token无效");
            }

            logger.info("用户 {} 请求待处理好友列表", userId);

            // 获取待处理请求
            List<Friend> requests = friendService.getPendingRequests(userId);
            logger.info("找到 {} 条待处理好友请求", requests.size());

            List<Map<String, Object>> result = new ArrayList<>();
            for (Friend req : requests) {
                // 获取发送者信息
                User sender = null;
                if (userService != null) {
                    sender = userService.getUserById(req.getUserId());
                }

                Map<String, Object> reqData = new HashMap<>();
                reqData.put("id", req.getId());
                reqData.put("user_id", req.getUserId());
                reqData.put("username", sender != null ? sender.getUsername() : "用户" + req.getUserId());
                reqData.put("nickname", sender != null ? sender.getNickname() : reqData.get("username"));
                reqData.put("request_message", req.getRequestMessage());
                reqData.put("status", req.getStatus());
                reqData.put("created_at", req.getCreatedAt() != null ? req.getCreatedAt().toString() : new java.sql.Date(System.currentTimeMillis()).toString());
                result.add(reqData);
                logger.info("好友请求: id={}, user_id={}, username={}, message={}",
                    req.getId(), req.getUserId(), reqData.get("username"), req.getRequestMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requests", result);

            logger.info("返回待处理好友请求: {} 条", result.size());
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("处理好友请求API失败", e);
            return sendJsonError(500, "服务器错误");
        }
    }

    /**
     * 处理获取好友列表
     */
    private String handleGetFriendList(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        try {
            // 验证JWT token
            String authHeader = request.headers().get("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return sendJsonError(401, "未授权");
            }

            String token = authHeader.substring(7);
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                return sendJsonError(401, "token无效");
            }

            // 获取好友列表
            List<User> friends = friendService.getFriendList(userId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (User friend : friends) {
                Map<String, Object> friendData = new HashMap<>();
                friendData.put("user_id", friend.getId());
                friendData.put("username", friend.getUsername());
                friendData.put("nickname", friend.getNickname());
                friendData.put("avatar", friend.getAvatar());
                friendData.put("online", friend.getStatus() == 1);
                result.add(friendData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("friends", result);

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("处理好友列表API失败", e);
            return sendJsonError(500, "服务器错误");
        }
    }

    /**
     * 返回JSON错误
     */
    private String sendJsonError(int code, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", message);
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + message + "\"}";
        }
    }

    /**
     * 处理排行榜API请求
     */
    private String handleLeaderboard(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String limitStr = decoder.parameters().getOrDefault("limit", List.of("50")).get(0);
            int limit = Integer.parseInt(limitStr);

            logger.info("获取排行榜 - limit={}", limit);

            List<User> leaderboard = userService.getLeaderboard(limit);
            logger.info("查询到 {} 位玩家", leaderboard.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", leaderboard);

            String json = objectMapper.writeValueAsString(response);
            logger.info("排行榜JSON: {}", json);
            return json;
        } catch (Exception e) {
            logger.error("处理排行榜API失败", e);
            return sendJsonError(500, "获取排行榜失败");
        }
    }
}
