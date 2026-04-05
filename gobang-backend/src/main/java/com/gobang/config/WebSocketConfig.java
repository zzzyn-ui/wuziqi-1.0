package com.gobang.config;

import com.gobang.util.JwtUtil;
import com.gobang.websocket.interceptor.JwtChannelInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 配置类
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理（用于发送消息给客户端）
        // /topic - 广播消息（一对多）
        // /queue - 点对点消息（一对一）
        config.enableSimpleBroker("/topic", "/queue");

        // 设置应用程序目标前缀（客户端发送消息的前缀）
        config.setApplicationDestinationPrefixes("/app");

        // 设置用户目标前缀（用于点对点消息）
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点，添加握手处理器
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // 允许所有来源（生产环境需要限制）
                .setHandshakeHandler((request, response, channel, wsHandler) -> {
                    // 从请求中提取 token
                    String token = extractTokenFromRequest(request);

                    if (token != null && jwtUtil.validateToken(token)) {
                        Long userId = jwtUtil.getUserIdFromToken(token);
                        String username = jwtUtil.getUsernameFromToken(token);
                        logger.info("WebSocket 握手成功: userId={}, username={}", userId, username);
                        return true;  // 允许连接
                    }

                    logger.warn("WebSocket 握手失败: 无效的 token");
                    return true;  // 仍然允许连接，稍后在 CONNECT 时验证
                })
                .withSockJS();  // 支持 SockJS 降级
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 配置客户端入站通道拦截器
        registration.interceptors(new JwtChannelInterceptor(jwtUtil));
    }

    /**
     * 从请求中提取 token
     */
    private String extractTokenFromRequest(org.springframework.http.server.ServerHttpRequest request) {
        // 从查询参数获取
        String query = request.getURI().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }
}
