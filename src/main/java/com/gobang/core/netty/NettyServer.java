package com.gobang.core.netty;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.handler.ChatHandler;
import com.gobang.core.handler.FriendHandler;
import com.gobang.core.handler.GameHandler;
import com.gobang.core.handler.MatchHandler;
import com.gobang.core.handler.ObserverHandler;
import com.gobang.core.handler.RoomHandler;
import com.gobang.core.netty.JsonMessageHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.room.RoomManager;
import com.gobang.core.security.RateLimitManager;
import com.gobang.core.social.ChatManager;
import com.gobang.core.social.FriendManager;
import com.gobang.core.social.ObserverManager;
import com.gobang.service.AuthService;
import com.gobang.service.ChatService;
import com.gobang.service.FriendService;
import com.gobang.service.GameService;
import com.gobang.service.RoomService;
import com.gobang.service.UserService;
import com.gobang.util.JwtUtil;
import com.gobang.util.RedisUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket服务器
 */
public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private final String host;
    private final int port;
    private final int bossThreads;
    private final int workerThreads;
    private final int readTimeout;
    private final int writeTimeout;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final ChannelManager channelManager;
    private WebSocketHandler webSocketHandler;

    // 服务层组件
    private final AuthService authService;
    private final UserService userService;
    private final GameService gameService;
    private final RoomService roomService;
    private final ChatService chatService;
    private final FriendService friendService;
    private final RoomManager roomManager;
    private final FriendManager friendManager;
    private final ChatManager chatManager;
    private final ObserverManager observerManager;
    private final RateLimitManager rateLimitManager;
    private final JwtUtil jwtUtil;

    public NettyServer(String host, int port, int bossThreads, int workerThreads,
                       int readTimeout, int writeTimeout, AuthService authService,
                       UserService userService, GameService gameService, RoomService roomService,
                       ChatService chatService, FriendService friendService,
                       RoomManager roomManager, FriendManager friendManager,
                       ChatManager chatManager, ObserverManager observerManager,
                       RateLimitManager rateLimitManager, JwtUtil jwtUtil) {
        this.host = host;
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.authService = authService;
        this.userService = userService;
        this.gameService = gameService;
        this.roomService = roomService;
        this.chatService = chatService;
        this.friendService = friendService;
        this.roomManager = roomManager;
        this.friendManager = friendManager;
        this.chatManager = chatManager;
        this.observerManager = observerManager;
        this.rateLimitManager = rateLimitManager;
        this.jwtUtil = jwtUtil;
        this.channelManager = new ChannelManager();
    }

    /**
     * 启动服务器
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // HTTP编解码
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));

                            // 自定义WebSocket处理器（必须在WebSocketServerProtocolHandler之前，用于处理HTTP请求）
                            webSocketHandler = new WebSocketHandler(channelManager, userService, authService, roomManager, friendService, jwtUtil, friendManager);

                            // 创建JsonMessageHandler并设置到WebSocketHandler
                            JsonMessageHandler jsonMessageHandler = new JsonMessageHandler(
                                authService, userService, gameService, roomService, chatService,
                                roomManager, chatManager);
                            webSocketHandler.setJsonMessageHandler(jsonMessageHandler);

                            pipeline.addLast(webSocketHandler);

                            pipeline.addLast(new ChunkedWriteHandler());

                            // WebSocket压缩 - 已禁用，导致与某些客户端的兼容性问题
                            // pipeline.addLast(new WebSocketServerCompressionHandler());

                            // WebSocket协议处理（路径检查在升级前进行，查询参数不影响路径匹配）
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 8192, false));

                            // 空闲检测（移到WebSocketHandler之后，避免干扰握手）
                            pipeline.addLast(new IdleStateHandler(readTimeout, writeTimeout, 0, TimeUnit.SECONDS));

                            // 响应编码器（自动转换Protobuf为JSON）
                            pipeline.addLast(new ResponseEncoder());

                            // 注册消息处理器
                            registerMessageHandlers();
                        }
                    });

            // 绑定端口并启动
            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();

            logger.info("Netty WebSocket server started on {}:{}", host, port);
            logger.info("WebSocket endpoint: ws://{}:{}/ws", host, port);

        } catch (Exception e) {
            logger.error("Failed to start Netty server", e);
            shutdown();
            throw e;
        }
    }

    /**
     * 注册所有消息处理器
     */
    private void registerMessageHandlers() {
        // 更新WebSocketHandler的HttpRequestHandler依赖
        webSocketHandler.updateHttpRequestHandlerDependencies(friendService, userService, jwtUtil);

        // 认证处理器
        MessageHandler authHandler = new AuthHandler(authService, userService, rateLimitManager, friendManager, friendService);
        webSocketHandler.registerHandler(authHandler);
        webSocketHandler.registerHandler(new AuthHandler(authService, userService, rateLimitManager, friendManager, friendService) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.AUTH_REGISTER;
            }
        });

        // 匹配处理器
        MessageHandler matchHandler = new MatchHandler(gameService, userService, roomManager, rateLimitManager);
        webSocketHandler.registerHandler(matchHandler);
        webSocketHandler.registerHandler(new MatchHandler(gameService, userService, roomManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.MATCH_CANCEL;
            }
        });

        // 房间处理器 - 处理创建房间和加入房间
        RoomHandler roomHandler = new RoomHandler(roomManager, userService);
        webSocketHandler.setRoomHandler(roomHandler);

        // 游戏处理器
        MessageHandler gameHandler = new GameHandler(gameService, roomService, userService, roomManager, rateLimitManager);
        webSocketHandler.registerHandler(gameHandler);
        webSocketHandler.registerHandler(new GameHandler(gameService, roomService, userService, roomManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.GAME_RESIGN;
            }
        });
        webSocketHandler.registerHandler(new GameHandler(gameService, roomService, userService, roomManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.GAME_RECONNECT;
            }
        });

        // 聊天处理器
        MessageHandler chatHandler = new ChatHandler(chatService, userService, roomManager, rateLimitManager);
        webSocketHandler.registerHandler(chatHandler);

        // 观战处理器
        MessageHandler observerHandler = new ObserverHandler(observerManager, userService, roomManager);
        webSocketHandler.registerHandler(observerHandler);
        webSocketHandler.registerHandler(new ObserverHandler(observerManager, userService, roomManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.OBSERVER_LEAVE;
            }
        });
        webSocketHandler.registerHandler(new ObserverHandler(observerManager, userService, roomManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.OBSERVER_LIST;
            }
        });

        // 好友处理器
        MessageHandler friendHandler = new FriendHandler(friendService, userService, friendManager, rateLimitManager);
        webSocketHandler.registerHandler(friendHandler);
        webSocketHandler.registerHandler(new FriendHandler(friendService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_ACCEPT;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_REJECT;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_REMOVE;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_LIST;
            }
        });

        logger.info("All message handlers registered (with rate limiting)");
    }

    /**
     * 关闭服务器
     */
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        channelManager.clear();
        logger.info("Netty server shutdown");
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineCount() {
        return channelManager.getOnlineCount();
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }
}
