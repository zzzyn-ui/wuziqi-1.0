package com.gobang.core.netty.api;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * API路由器
 * 管理所有API Handler并分发请求
 */
public class ApiRouter {

    private static final Logger logger = LoggerFactory.getLogger(ApiRouter.class);

    private final Map<String, IApiHandler> handlers = new HashMap<>();
    private final Map<String, IApiHandler> pathHandlers = new HashMap<>();

    /**
     * 注册API处理器
     * @param path API路径前缀
     * @param handler 处理器实例
     */
    public void registerHandler(String path, IApiHandler handler) {
        handlers.put(path, handler);
        logger.info("Registered API handler: {} -> {}", path, handler.getClass().getSimpleName());
    }

    /**
     * 注册完整路径的处理器
     * @param fullPath 完整API路径
     * @param handler 处理器实例
     */
    public void registerPathHandler(String fullPath, IApiHandler handler) {
        pathHandlers.put(fullPath, handler);
        logger.info("Registered API path handler: {} -> {}", fullPath, handler.getClass().getSimpleName());
    }

    /**
     * 路由请求到对应的Handler
     * @return true 如果请求已处理，false 如果没有匹配的Handler
     */
    public boolean route(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        String method = request.method().name();
        String path = uri.split("\\?")[0];

        logger.debug("Routing request: {} {}", method, path);

        // 1. 首先检查完整路径匹配
        IApiHandler pathHandler = pathHandlers.get(path);
        if (pathHandler != null) {
            logger.debug("Path handler matched: {}", path);
            return pathHandler.handleRequest(ctx, request, path, method);
        }

        // 2. 检查路径前缀匹配
        for (Map.Entry<String, IApiHandler> entry : handlers.entrySet()) {
            String prefix = entry.getKey();
            if (path.startsWith(prefix)) {
                IApiHandler handler = entry.getValue();
                logger.debug("Prefix handler matched: {} (path: {})", prefix, path);
                return handler.handleRequest(ctx, request, path, method);
            }
        }

        // 3. 特殊路径处理
        // /api/rank 和 /api/leaderboard 都使用同一个处理器
        if (path.equals("/api/rank") || path.equals("/api/leaderboard")) {
            IApiHandler handler = handlers.get("/api/leaderboard");
            if (handler != null) {
                return handler.handleRequest(ctx, request, path, method);
            }
        }

        logger.debug("No handler found for path: {}", path);
        return false;
    }

    /**
     * 获取所有已注册的处理器信息
     */
    public Map<String, String> getHandlerInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        for (Map.Entry<String, IApiHandler> entry : handlers.entrySet()) {
            info.put(entry.getKey(), entry.getValue().getClass().getSimpleName());
        }
        for (Map.Entry<String, IApiHandler> entry : pathHandlers.entrySet()) {
            info.put(entry.getKey(), entry.getValue().getClass().getSimpleName() + " (exact)");
        }
        return info;
    }

    /**
     * 打印所有已注册的处理器
     */
    public void printHandlers() {
        logger.info("=== API Handlers ===");
        Map<String, String> info = getHandlerInfo();
        for (Map.Entry<String, String> entry : info.entrySet()) {
            logger.info("  {} -> {}", entry.getKey(), entry.getValue());
        }
        logger.info("===================");
    }
}
