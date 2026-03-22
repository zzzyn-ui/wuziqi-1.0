package com.gobang.core.netty;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.handler.ChatHandler;
import com.gobang.core.handler.FriendHandler;
import com.gobang.core.handler.GameHandler;
import com.gobang.core.handler.MatchHandler;
import com.gobang.core.handler.ObserverHandler;
import com.gobang.core.handler.RoomHandler;
import com.gobang.core.netty.HttpApiHandler;
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
import com.gobang.service.FriendGroupService;
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
    private final com.gobang.service.UserSettingsService userSettingsService;
    private final com.gobang.service.ActivityLogService activityLogService;
    private final com.gobang.service.GameFavoriteService gameFavoriteService;
    private final com.gobang.service.GameInvitationService gameInvitationService;
    private final com.gobang.service.RecordService recordService;
    private final com.gobang.service.PuzzleService puzzleService;
    private final com.gobang.service.FriendGroupService friendGroupService;

    public NettyServer(String host, int port, int bossThreads, int workerThreads,
                       int readTimeout, int writeTimeout, AuthService authService,
                       UserService userService, GameService gameService, RoomService roomService,
                       ChatService chatService, FriendService friendService,
                       FriendGroupService friendGroupService,
                       RoomManager roomManager, FriendManager friendManager,
                       ChatManager chatManager, ObserverManager observerManager,
                       RateLimitManager rateLimitManager, JwtUtil jwtUtil,
                       com.gobang.service.UserSettingsService userSettingsService,
                       com.gobang.service.ActivityLogService activityLogService,
                       com.gobang.service.GameFavoriteService gameFavoriteService,
                       com.gobang.service.GameInvitationService gameInvitationService,
                       com.gobang.service.RecordService recordService,
                       com.gobang.service.PuzzleService puzzleService) {
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
        this.friendGroupService = friendGroupService;
        this.roomManager = roomManager;
        this.friendManager = friendManager;
        this.chatManager = chatManager;
        this.observerManager = observerManager;
        this.rateLimitManager = rateLimitManager;
        this.jwtUtil = jwtUtil;
        this.userSettingsService = userSettingsService;
        this.activityLogService = activityLogService;
        this.gameFavoriteService = gameFavoriteService;
        this.gameInvitationService = gameInvitationService;
        this.recordService = recordService;
        this.puzzleService = puzzleService;
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

                            // HTTP对象聚合器（必须在 WebSocketServerProtocolHandler 之前）
                            pipeline.addLast(new HttpObjectAggregator(65536));

                            // 分块写入支持（在 WebSocket 之前）
                            pipeline.addLast(new ChunkedWriteHandler());

                            // WebSocket协议处理
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, false, 8192, false));

                            // 自定义WebSocket处理器
                            webSocketHandler = new WebSocketHandler(channelManager, userService, authService, roomManager, friendService, jwtUtil, friendManager, gameService);

                            // 创建JsonMessageHandler并设置到WebSocketHandler
                            JsonMessageHandler jsonMessageHandler = new JsonMessageHandler(
                                authService, userService, gameService, roomService, chatService,
                                roomManager, chatManager, friendManager);
                            webSocketHandler.setJsonMessageHandler(jsonMessageHandler);

                            pipeline.addLast(webSocketHandler);

                            // 空闲检测
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

        // 创建并设置 HttpApiHandler（包含所有 API）
        com.gobang.core.netty.HttpApiHandler httpApiHandler = new com.gobang.core.netty.HttpApiHandler(
            userService, authService, gameService, roomService, friendService, chatService,
            roomManager,
            userSettingsService,
            activityLogService,
            gameFavoriteService,
            gameInvitationService,
            recordService,
            puzzleService,
            jwtUtil
        );
        webSocketHandler.setHttpApiHandler(httpApiHandler);

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
        MessageHandler chatHandler = new ChatHandler(chatService, userService, roomManager, rateLimitManager, friendManager);
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
        MessageHandler friendHandler = new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager);
        webSocketHandler.registerHandler(friendHandler);
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_ACCEPT;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_REJECT;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_REMOVE;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_LIST;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_REMARK;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_GROUP_CREATE;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_GROUP_LIST;
            }
        });
        webSocketHandler.registerHandler(new FriendHandler(friendService, friendGroupService, userService, friendManager, rateLimitManager) {
            @Override
            public MessageType getSupportedType() {
                return MessageType.FRIEND_MOVE_GROUP;
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
