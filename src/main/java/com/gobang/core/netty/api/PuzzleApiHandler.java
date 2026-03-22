package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.Puzzle;
import com.gobang.model.entity.PuzzleRecord;
import com.gobang.service.AuthService;
import com.gobang.service.PuzzleService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 残局API处理器
 * 处理残局相关操作
 */
public class PuzzleApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(PuzzleApiHandler.class);
    private final PuzzleService puzzleService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public PuzzleApiHandler(PuzzleService puzzleService, AuthService authService) {
        this.puzzleService = puzzleService;
        this.authService = authService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/puzzles";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            // GET /api/puzzles - 获取残局列表
            // GET /api/puzzles/list?difficulty=easy - 按难度获取
            // GET /api/puzzles/:id - 获取残局详情
            // GET /api/puzzles/my - 获取我的残局记录
            // POST /api/puzzles/:id/attempt - 记录尝试
            // POST /api/puzzles/:id/complete - 记录完成
            // GET /api/puzzles/stats/leaderboard - 排行榜

            Long userId = extractUserId(ctx, request);

            if ("GET".equals(method)) {
                if (path.equals("/api/puzzles") || path.equals("/api/puzzles/list")) {
                    handleGetPuzzleList(ctx, request);
                    return true;
                } else if (path.matches("/api/puzzles/\\d+")) {
                    Long puzzleId = extractIdFromPath(path);
                    handleGetPuzzleDetail(ctx, puzzleId);
                    return true;
                } else if (path.equals("/api/puzzles/my")) {
                    if (userId == null) {
                        sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                            Map.of("success", false, "message", "未授权"));
                        return true;
                    }
                    handleGetMyRecords(ctx, userId, request);
                    return true;
                } else if (path.equals("/api/puzzles/stats/leaderboard")) {
                    handleGetLeaderboard(ctx);
                    return true;
                } else if (path.equals("/api/puzzles/stats/summary")) {
                    handleGetStatsSummary(ctx);
                    return true;
                }
            } else if ("POST".equals(method)) {
                if (userId == null) {
                    sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                        Map.of("success", false, "message", "未授权"));
                    return true;
                }

                if (path.matches("/api/puzzles/\\d+/attempt")) {
                    Long puzzleId = extractIdFromPath(path);
                    handleRecordAttempt(ctx, userId, puzzleId);
                    return true;
                } else if (path.matches("/api/puzzles/\\d+/complete")) {
                    Long puzzleId = extractIdFromPath(path);
                    handleRecordCompletion(ctx, userId, puzzleId, request);
                    return true;
                }
            }

            // 默认响应
            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "message", "残局API正常"));
            return true;

        } catch (Exception e) {
            logger.error("Puzzle API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    /**
     * 获取残局列表
     */
    private void handleGetPuzzleList(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String difficulty = decoder.parameters().getOrDefault("difficulty", List.of("")).get(0);

            List<Puzzle> puzzles = puzzleService.getPuzzleList(difficulty);

            Map<String, Object> data = new HashMap<>();
            data.put("puzzles", puzzles);
            data.put("count", puzzles.size());

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "data", data));

        } catch (Exception e) {
            logger.error("获取残局列表失败", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取残局列表失败"));
        }
    }

    /**
     * 获取残局详情
     */
    private void handleGetPuzzleDetail(ChannelHandlerContext ctx, Long puzzleId) {
        try {
            Puzzle puzzle = puzzleService.getPuzzleDetail(puzzleId);

            if (puzzle == null) {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    Map.of("success", false, "message", "残局不存在"));
                return;
            }

            // 解析解法和提示
            Map<String, Object> data = new HashMap<>();
            data.put("puzzle", puzzle);
            data.put("solution", puzzleService.parseSolution(puzzle.getSolution()));
            data.put("hintMoves", puzzleService.parseHintMoves(puzzle.getHintMoves()));

            // 添加棋子统计信息
            Map<String, Integer> pieceCount = puzzleService.getPuzzlePieceCount(puzzle);
            data.put("pieceCount", pieceCount);

            // 验证残局棋盘
            boolean isValid = puzzleService.validatePuzzleBoard(puzzle);
            data.put("boardValid", isValid);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "data", data));

        } catch (Exception e) {
            logger.error("获取残局详情失败, puzzleId={}", puzzleId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取残局详情失败"));
        }
    }

    /**
     * 获取我的残局记录
     */
    private void handleGetMyRecords(ChannelHandlerContext ctx, Long userId, FullHttpRequest request) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String difficulty = decoder.parameters().getOrDefault("difficulty", List.of("")).get(0);

            List<PuzzleRecord> records = puzzleService.getUserPuzzleRecords(userId, difficulty);

            Map<String, Object> data = new HashMap<>();
            data.put("records", records);
            data.put("count", records.size());

            // 获取用户统计
            Map<String, Object> stats = puzzleService.getUserStats(userId);
            data.put("stats", stats);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "data", data));

        } catch (Exception e) {
            logger.error("获取残局记录失败, userId={}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取残局记录失败"));
        }
    }

    /**
     * 记录尝试
     */
    private void handleRecordAttempt(ChannelHandlerContext ctx, Long userId, Long puzzleId) {
        try {
            puzzleService.recordAttempt(userId, puzzleId);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "message", "记录成功"));

        } catch (Exception e) {
            logger.error("记录尝试失败, userId={}, puzzleId={}", userId, puzzleId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "记录失败"));
        }
    }

    /**
     * 记录完成
     */
    private void handleRecordCompletion(ChannelHandlerContext ctx, Long userId, Long puzzleId, FullHttpRequest request) {
        try {
            String body = request.content().toString(java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(body, Map.class);

            int moves = ((Number) params.getOrDefault("moves", 0)).intValue();
            int time = ((Number) params.getOrDefault("time", 0)).intValue();
            @SuppressWarnings("unchecked")
            List<List<Integer>> solutionPath = (List<List<Integer>>) params.get("solutionPath");

            String solutionPathJson = objectMapper.writeValueAsString(solutionPath);

            // 获取残局信息计算星级
            Puzzle puzzle = puzzleService.getPuzzleDetail(puzzleId);
            if (puzzle == null) {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    Map.of("success", false, "message", "残局不存在"));
                return;
            }

            int stars = puzzleService.calculateStars(puzzle, moves, time, true);
            puzzleService.recordCompletion(userId, puzzleId, moves, time, stars, solutionPathJson);

            Map<String, Object> data = new HashMap<>();
            data.put("stars", stars);
            data.put("optimalMoves", puzzle.getOptimalMoves());
            data.put("isNewRecord", moves <= puzzle.getOptimalMoves());

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "message", "恭喜通关！", "data", data));

        } catch (Exception e) {
            logger.error("记录完成失败, userId={}, puzzleId={}", userId, puzzleId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "记录失败"));
        }
    }

    /**
     * 获取排行榜
     */
    private void handleGetLeaderboard(ChannelHandlerContext ctx) {
        try {
            List<Map<String, Object>> leaderboard = puzzleService.getLeaderboard(50);

            Map<String, Object> data = new HashMap<>();
            data.put("leaderboard", leaderboard);
            data.put("count", leaderboard.size());

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "data", data));

        } catch (Exception e) {
            logger.error("获取排行榜失败", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取排行榜失败"));
        }
    }

    /**
     * 获取统计摘要
     */
    private void handleGetStatsSummary(ChannelHandlerContext ctx) {
        try {
            Map<String, Integer> difficultyCounts = puzzleService.getDifficultyCounts();

            Map<String, Object> data = new HashMap<>();
            data.put("difficultyCounts", difficultyCounts);
            data.put("totalPuzzles", difficultyCounts.values().stream().mapToInt(Integer::intValue).sum());

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "data", data));

        } catch (Exception e) {
            logger.error("获取统计摘要失败", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取统计摘要失败"));
        }
    }

    private Long extractUserId(ChannelHandlerContext ctx, FullHttpRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && !token.isEmpty()) {
            return authService.validateToken(token);
        }
        return null;
    }

    private Long extractIdFromPath(String path) {
        String[] parts = path.split("/");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
