package com.gobang.core.netty;

import com.gobang.core.protocol.PacketCodec;
import com.gobang.service.AuthService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket服务器Channel初始化器
 */
public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerInitializer.class);

    private final String websocketPath;
    private final int readTimeout;
    private final int writeTimeout;
    private final ChannelManager channelManager;
    private final AuthService authService;

    public WebSocketServerInitializer(String websocketPath, int readTimeout, int writeTimeout,
                                      ChannelManager channelManager, AuthService authService) {
        this.websocketPath = websocketPath;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.channelManager = channelManager;
        this.authService = authService;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // HTTP编解码
        pipeline.addLast(new HttpServerCodec());

        // HTTP对象聚合
        pipeline.addLast(new HttpObjectAggregator(65536));

        // Token提取Handler - 在WebSocket握手前提取token
        pipeline.addLast(new TokenExtractHandler(authService));

        // 支持大数据流
        pipeline.addLast(new ChunkedWriteHandler());

        // WebSocket压缩 - 已禁用，导致与某些客户端的兼容性问题
        // pipeline.addLast(new WebSocketServerCompressionHandler());

        // WebSocket协议处理
        pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath, null, true, 8192));

        // 空闲状态检测
        pipeline.addLast(new IdleStateHandler(readTimeout, writeTimeout, 0, TimeUnit.SECONDS));

        // 自定义WebSocket处理器（将在NettyServer中设置）
        // WebSocketHandler handler = new WebSocketHandler(channelManager);
        // pipeline.addLast("websocketHandler", handler);

        logger.debug("Channel pipeline initialized");
    }

    /**
     * Token提取Handler - 在WebSocket握手前从URL参数中提取token
     */
    private static class TokenExtractHandler extends SimpleChannelInboundHandler<io.netty.handler.codec.http.HttpRequest> {
        private static final Logger logger = LoggerFactory.getLogger(TokenExtractHandler.class);
        private final AuthService authService;

        public TokenExtractHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpRequest request) throws Exception {
            // 提取URL参数中的token
            io.netty.handler.codec.http.QueryStringDecoder decoder = new io.netty.handler.codec.http.QueryStringDecoder(request.uri());
            String token = decoder.parameters().get("token").stream().findFirst().orElse(null);

            if (token != null) {
                logger.debug("Token found in URL parameters");
                // 保存token到channel属性，供握手完成后使用
                io.netty.util.AttributeKey<String> TOKEN_ATTR = io.netty.util.AttributeKey.valueOf("authToken");
                ctx.channel().attr(TOKEN_ATTR).set(token);
            }

            // 传递给下一个handler
            ctx.fireChannelRead(request);
        }
    }
}
