package com.gobang.core.netty.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import com.gobang.service.AuthService;
import com.gobang.service.RecordService;
import com.gobang.service.UserService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对局记录API处理器
 * 处理对局历史查询、复盘数据获取
 */
public class RecordsApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(RecordsApiHandler.class);
    private static final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RecordService recordService;
    private final UserService userService;
    private final AuthService authService;
    private final SqlSessionFactory sqlSessionFactory;

    public RecordsApiHandler(RecordService recordService, UserService userService, AuthService authService,
                             SqlSessionFactory sqlSessionFactory) {
        this.recordService = recordService;
        this.userService = userService;
        this.authService = authService;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/records";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            logger.info("=== RecordsApiHandler === path: {}, method: {}", path, method);

            // 验证用户身份
            Long userId = extractUserId(ctx, request);
            logger.info("提取的userId: {}", userId);

            if ("GET".equals(method)) {
                if (path.equals("/api/records/recent") || path.equals("/api/records")) {
                    // 获取最近的公共对局记录（不需要登录）
                    logger.info("处理获取最近记录请求");
                    handleGetRecentRecords(ctx, request);
                    return true;
                } else if (path.matches("/api/records/\\d+")) {
                    // 获取单个对局记录详情
                    logger.info("处理获取单个记录请求");
                    handleGetRecordById(ctx, request, path);
                    return true;
                } else if (path.equals("/api/records/my")) {
                    // 获取当前用户的对局记录
                    logger.info("处理获取用户记录请求, userId: {}", userId);
                    if (userId == null) {
                        logger.warn("用户未登录");
                        sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                            Map.of("success", false, "message", "请先登录"));
                        return true;
                    }
                    handleGetMyRecords(ctx, request, userId);
                    return true;
                } else if (path.startsWith("/api/records/replay/")) {
                    // 获取复盘数据
                    String roomId = path.substring("/api/records/replay/".length());
                    logger.info("处理获取复盘数据请求, roomId: {}", roomId);
                    handleGetReplayData(ctx, roomId);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Records API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    /**
     * 从请求中提取用户ID
     */
    private Long extractUserId(ChannelHandlerContext ctx, FullHttpRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && !token.isEmpty()) {
            return authService.validateToken(token);
        }
        return null;
    }

    /**
     * 获取最近的公共对局记录
     */
    private void handleGetRecentRecords(ChannelHandlerContext ctx, FullHttpRequest request) {
        try (var session = sqlSessionFactory.openSession(true)) {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String limitStr = decoder.parameters().getOrDefault("limit", List.of("20")).get(0);
            int limit = Integer.parseInt(limitStr);

            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            List<GameRecord> records = recordMapper.findRecent(limit);

            // 转换为响应格式
            List<Map<String, Object>> recordList = new ArrayList<>();
            for (GameRecord record : records) {
                Map<String, Object> recordData = new HashMap<>();
                recordData.put("id", record.getId());
                recordData.put("roomId", record.getRoomId());
                recordData.put("blackPlayerId", record.getBlackPlayerId());
                recordData.put("whitePlayerId", record.getWhitePlayerId());
                recordData.put("winnerId", record.getWinnerId());
                recordData.put("winColor", record.getWinColor());
                recordData.put("endReason", record.getEndReason());
                recordData.put("moveCount", record.getMoveCount());
                recordData.put("duration", record.getDuration());
                recordData.put("gameMode", record.getGameMode());
                recordData.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : "");

                // 获取玩家信息
                User blackPlayer = userService.getUserById(record.getBlackPlayerId());
                User whitePlayer = userService.getUserById(record.getWhitePlayerId());

                recordData.put("blackPlayer", blackPlayer != null ? Map.of(
                    "id", blackPlayer.getId(),
                    "username", blackPlayer.getUsername(),
                    "nickname", blackPlayer.getNickname(),
                    "rating", blackPlayer.getRating()
                ) : null);

                recordData.put("whitePlayer", whitePlayer != null ? Map.of(
                    "id", whitePlayer.getId(),
                    "username", whitePlayer.getUsername(),
                    "nickname", whitePlayer.getNickname(),
                    "rating", whitePlayer.getRating()
                ) : null);

                recordList.add(recordData);
            }

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "records", recordList));
        } catch (Exception e) {
            logger.error("Failed to get recent records", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取对局记录失败"));
        }
    }

    /**
     * 获取用户的对局记录（三天内）
     */
    private void handleGetMyRecords(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try (var session = sqlSessionFactory.openSession(true)) {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String limitStr = decoder.parameters().getOrDefault("limit", List.of("100")).get(0);
            int limit = Integer.parseInt(limitStr);

            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            // 使用新的查询方法，只返回三天内的记录
            List<GameRecord> records = recordMapper.findRecentByUserId(userId, limit);

            logger.info("用户 {} 的对局记录查询结果: {} 条（三天内）", userId, records.size());

            // 转换为响应格式
            List<Map<String, Object>> recordList = new ArrayList<>();
            for (GameRecord record : records) {
                Map<String, Object> recordData = new HashMap<>();
                recordData.put("id", record.getId());
                recordData.put("roomId", record.getRoomId());
                recordData.put("blackPlayerId", record.getBlackPlayerId());
                recordData.put("whitePlayerId", record.getWhitePlayerId());
                recordData.put("winnerId", record.getWinnerId());
                recordData.put("winColor", record.getWinColor());
                recordData.put("endReason", record.getEndReason());
                recordData.put("moveCount", record.getMoveCount());
                recordData.put("duration", record.getDuration());
                recordData.put("gameMode", record.getGameMode());
                recordData.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : "");

                // 获取对手信息
                Long opponentId = userId.equals(record.getBlackPlayerId()) ? record.getWhitePlayerId() : record.getBlackPlayerId();
                User opponent = userService.getUserById(opponentId);

                recordData.put("opponent", opponent != null ? Map.of(
                    "id", opponent.getId(),
                    "username", opponent.getUsername(),
                    "nickname", opponent.getNickname(),
                    "rating", opponent.getRating()
                ) : null);

                // 判断胜负
                boolean isWin = userId.equals(record.getWinnerId());
                recordData.put("result", isWin ? "win" : (record.getWinnerId() == null ? "draw" : "lose"));
                recordData.put("myColor", userId.equals(record.getBlackPlayerId()) ? "black" : "white");

                recordList.add(recordData);
            }

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "records", recordList, "total", recordList.size()));
        } catch (Exception e) {
            logger.error("Failed to get user records", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取对局记录失败"));
        }
    }

    /**
     * 获取单个对局记录详情
     */
    private void handleGetRecordById(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        try (var session = sqlSessionFactory.openSession(true)) {
            // 从路径中提取记录ID: /api/records/{id}
            String[] parts = path.split("/");
            if (parts.length < 4) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "无效的请求"));
                return;
            }

            Long recordId = Long.parseLong(parts[3]);
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            GameRecord record = recordMapper.findById(recordId);

            if (record == null) {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    Map.of("success", false, "message", "对局记录不存在"));
                return;
            }

            // 解析落子记录
            Type moveListType = new TypeToken<List<int[]>>() {}.getType();
            List<int[]> moves = gson.fromJson(record.getMoves(), moveListType);

            // 转换落子格式
            List<Map<String, Object>> moveList = new ArrayList<>();
            if (moves != null) {
                for (int[] move : moves) {
                    moveList.add(Map.of(
                        "x", move[0],
                        "y", move[1],
                        "color", move[2]
                    ));
                }
            }

            // 构建响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("id", record.getId());
            responseData.put("roomId", record.getRoomId());
            responseData.put("blackPlayerId", record.getBlackPlayerId());
            responseData.put("whitePlayerId", record.getWhitePlayerId());
            responseData.put("winnerId", record.getWinnerId());
            responseData.put("winColor", record.getWinColor());
            responseData.put("endReason", record.getEndReason());
            responseData.put("moveCount", record.getMoveCount());
            responseData.put("duration", record.getDuration());
            responseData.put("gameMode", record.getGameMode());
            responseData.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : "");
            responseData.put("moves", moveList);

            // 获取玩家信息
            User blackPlayer = userService.getUserById(record.getBlackPlayerId());
            User whitePlayer = userService.getUserById(record.getWhitePlayerId());

            responseData.put("blackPlayer", blackPlayer != null ? Map.of(
                "id", blackPlayer.getId(),
                "username", blackPlayer.getUsername(),
                "nickname", blackPlayer.getNickname(),
                "rating", blackPlayer.getRating()
            ) : null);

            responseData.put("whitePlayer", whitePlayer != null ? Map.of(
                "id", whitePlayer.getId(),
                "username", whitePlayer.getUsername(),
                "nickname", whitePlayer.getNickname(),
                "rating", whitePlayer.getRating()
            ) : null);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "record", responseData));
        } catch (Exception e) {
            logger.error("Failed to get record by id", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取对局记录失败"));
        }
    }

    /**
     * 获取复盘数据
     */
    private void handleGetReplayData(ChannelHandlerContext ctx, String roomId) {
        logger.info("=== handleGetReplayData === roomId: {}", roomId);

        try (var session = sqlSessionFactory.openSession(true)) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            GameRecord record = recordMapper.findByRoomId(roomId);

            if (record == null) {
                logger.warn("复盘数据 - 对局记录不存在: roomId={}", roomId);
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    Map.of("success", false, "message", "对局记录不存在"));
                return;
            }

            logger.info("复盘数据 - 找到记录: roomId={}, winnerId={}, endReason={}, gameMode={}",
                roomId, record.getWinnerId(), record.getEndReason(), record.getGameMode());

            // 解析落子记录
            Type moveListType = new TypeToken<List<int[]>>() {}.getType();
            List<int[]> moves = gson.fromJson(record.getMoves(), moveListType);

            // 转换落子格式
            List<Map<String, Object>> moveList = new ArrayList<>();
            if (moves != null) {
                for (int[] move : moves) {
                    moveList.add(Map.of(
                        "x", move[0],
                        "y", move[1],
                        "color", move[2]
                    ));
                }
            }

            logger.info("复盘数据 - 落子数: {}", moveList.size());

            // 构建响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("roomId", record.getRoomId());
            responseData.put("blackPlayerId", record.getBlackPlayerId());
            responseData.put("whitePlayerId", record.getWhitePlayerId());
            responseData.put("winnerId", record.getWinnerId());
            responseData.put("winColor", record.getWinColor());
            responseData.put("endReason", record.getEndReason());
            responseData.put("moveCount", record.getMoveCount());
            responseData.put("duration", record.getDuration());
            responseData.put("gameMode", record.getGameMode());
            responseData.put("moves", moveList);

            // 获取玩家信息
            User blackPlayer = userService.getUserById(record.getBlackPlayerId());
            User whitePlayer = userService.getUserById(record.getWhitePlayerId());

            Map<String, Object> players = new HashMap<>();
            players.put("black", blackPlayer != null ? Map.of(
                "id", blackPlayer.getId(),
                "username", blackPlayer.getUsername(),
                "nickname", blackPlayer.getNickname(),
                "rating", blackPlayer.getRating()
            ) : null);

            players.put("white", whitePlayer != null ? Map.of(
                "id", whitePlayer.getId(),
                "username", whitePlayer.getUsername(),
                "nickname", whitePlayer.getNickname(),
                "rating", whitePlayer.getRating()
            ) : null);

            responseData.put("players", players);

            // 添加胜负信息
            Map<String, Object> winner = new HashMap<>();
            if (record.getWinnerId() != null) {
                winner.put("id", record.getWinnerId());
                winner.put("color", record.getWinColor());
                User winnerUser = userService.getUserById(record.getWinnerId());
                if (winnerUser != null) {
                    winner.put("nickname", winnerUser.getNickname());
                    winner.put("userId", winnerUser.getId());
                }
                logger.info("复盘数据 - 有获胜者: id={}, color={}, nickname={}",
                    record.getWinnerId(), record.getWinColor(), winnerUser != null ? winnerUser.getNickname() : null);
            } else {
                logger.info("复盘数据 - 无获胜者（平局或未完成）, endReason: {}", record.getEndReason());
            }
            responseData.put("winner", winner);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "data", responseData));
        } catch (Exception e) {
            logger.error("Failed to get replay data for room: {}", roomId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取复盘数据失败"));
        }
    }
}
