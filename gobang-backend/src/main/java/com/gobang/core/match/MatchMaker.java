package com.gobang.core.match;

import com.gobang.service.GameServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 匹配器 - 处理玩家匹配逻辑
 */
@Component
public class MatchMaker {

    private static final Logger logger = LoggerFactory.getLogger(MatchMaker.class);

    // 等待匹配的玩家队列 (mode -> List<UserId>)
    private final Map<String, java.util.Queue<Long>> waitingPlayers = new ConcurrentHashMap<>();

    // 玩家ID -> 用户名映射 (用于调试)
    private final Map<Long, String> playerNames = new ConcurrentHashMap<>();

    private final GameServiceImpl gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchMaker(GameServiceImpl gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 玩家加入匹配队列
     */
    public synchronized void joinQueue(Long userId, String username, String mode) {
        logger.info("========== MatchMaker.joinQueue ==========");
        logger.info("玩家加入匹配: userId={}, username={}, mode={}", userId, username, mode);

        playerNames.put(userId, username);

        java.util.Queue<Long> queue = waitingPlayers.computeIfAbsent(mode, k -> new java.util.LinkedList<>());
        queue.add(userId);

        logger.info("玩家 {} 已加入队列，当前队列大小: {}", userId, queue.size());

        // 检查是否有足够玩家开始游戏
        if (queue.size() >= 2) {
            // 取出两个玩家
            Long player1 = queue.poll();
            Long player2 = queue.poll();

            logger.info("========== 找到2个玩家，开始匹配 ==========");
            logger.info("玩家1: {} ({})", player1, playerNames.get(player1));
            logger.info("玩家2: {} ({})", player2, playerNames.get(player2));
            logger.info("模式: {}", mode);

            // 创建游戏房间
            createAndNotifyGame(player1, player2, mode);
        } else {
            logger.info("队列中只有 {} 个玩家，等待更多玩家加入...", queue.size());
            // 通知玩家等待中
            sendWaitingNotification(userId, queue.size());
        }
        logger.info("========================================");
    }

    /**
     * 玩家取消匹配
     */
    public synchronized void cancelMatch(Long userId, String mode) {
        java.util.Queue<Long> queue = waitingPlayers.get(mode);
        if (queue != null && queue.remove(userId)) {
            playerNames.remove(userId);
            logger.info("玩家 {} 取消 {} 匹配", userId, mode);
        }
    }

    /**
     * 创建游戏房间并通知玩家
     */
    private void createAndNotifyGame(Long player1, Long player2, String mode) {
        try {
            // 使用第一个玩家创建房间
            String roomId = gameService.createRoom(player1, mode);

            // 让第二个玩家加入房间
            gameService.joinRoom(roomId, player2);

            // 通知两个玩家匹配成功 - player1 是黑棋，player2 是白棋
            notifyMatchSuccess(player1, roomId, 1, player2);  // 黑棋
            notifyMatchSuccess(player2, roomId, 2, player1);  // 白棋

            logger.info("房间创建成功: roomId={}, 玩家1={}, 玩家2={}", roomId, player1, player2);
        } catch (Exception e) {
            logger.error("创建游戏房间失败", e);
            // 创建失败，让玩家重新匹配
            if (e.getMessage() != null) {
                notifyMatchError(player1, e.getMessage());
                notifyMatchError(player2, e.getMessage());
            }
        }
    }

    /**
     * 通知匹配成功
     */
    private void notifyMatchSuccess(Long userId, String roomId, int color, Long opponentId) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "MATCH_SUCCESS");
        response.put("roomId", roomId);
        response.put("color", color);  // 1=黑, 2=白
        response.put("opponentName", playerNames.get(opponentId));
        response.put("opponentId", opponentId);

        // 发送到用户特定的 topic（前端订阅 /topic/match/{userId}）
        messagingTemplate.convertAndSend("/topic/match/" + userId, response);

        logger.info("发送 MATCH_SUCCESS 给用户 {}: roomId={}, color={}", userId, roomId, color);
    }

    /**
     * 通知等待中
     */
    private void sendWaitingNotification(Long userId, int queueSize) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "MATCH_WAITING");
        response.put("queueSize", queueSize);
        response.put("message", "正在寻找对手...");

        messagingTemplate.convertAndSend("/topic/match/" + userId, response);
    }

    /**
     * 通知匹配错误
     */
    private void notifyMatchError(Long userId, String error) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "MATCH_ERROR");
        response.put("message", error);

        messagingTemplate.convertAndSend("/topic/match/" + userId, response);
    }

    /**
     * 获取当前队列大小
     */
    public synchronized int getQueueSize(String mode) {
        java.util.Queue<Long> queue = waitingPlayers.get(mode);
        return queue != null ? queue.size() : 0;
    }

    /**
     * 清理超时玩家
     */
    public void cleanupTimeoutPlayers() {
        // TODO: 实现超时清理逻辑
    }
}
