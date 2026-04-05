package com.gobang.core.match;

import com.gobang.model.enums.GameMode;
import com.gobang.service.GameServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spring 版本匹配器
 * 处理玩家匹配逻辑，使用 STOMP WebSocket 通信
 *
 * 支持两种匹配模式：
 * - casual (休闲): 不影响积分
 * - ranked (竞技): 使用ELO计算积分变化
 */
@Component
public class SpringMatchMaker {

    private static final Logger logger = LoggerFactory.getLogger(SpringMatchMaker.class);

    // 等待匹配的玩家队列 (mode -> Queue<UserId>)
    private final Map<String, ConcurrentLinkedQueue<MatchRequest>> waitingPlayers = new ConcurrentHashMap<>();

    private final GameServiceImpl gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public SpringMatchMaker(GameServiceImpl gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
        logger.info("✅ SpringMatchMaker 初始化完成");
    }

    /**
     * 匹配请求数据类
     */
    private static class MatchRequest {
        final Long userId;
        final String username;
        final String mode;
        final Long avoidOpponentId; // 排除的对手ID
        final long requestTime;

        MatchRequest(Long userId, String username, String mode, Long avoidOpponentId) {
            this.userId = userId;
            this.username = username;
            this.mode = mode;
            this.avoidOpponentId = avoidOpponentId;
            this.requestTime = System.currentTimeMillis();
        }
    }

    /**
     * 玩家加入匹配队列
     */
    public synchronized void joinQueue(Long userId, String username, String mode, Long avoidOpponentId) {
        GameMode gameMode = GameMode.fromCode(mode);

        logger.info("🎮 玩家 {} ({}) 尝试加入 {} 匹配队列, 排除对手: {}",
                userId, username, gameMode.getDescription(), avoidOpponentId);

        // 检查玩家是否已经在队列中
        removePlayerFromAllQueues(userId);

        ConcurrentLinkedQueue<MatchRequest> queue = waitingPlayers.computeIfAbsent(mode, k -> new ConcurrentLinkedQueue<>());
        MatchRequest request = new MatchRequest(userId, username, mode, avoidOpponentId);
        queue.add(request);

        logger.info("✓ 玩家 {} 已加入 {} 队列，当前队列大小: {}", userId, gameMode.getDescription(), queue.size());

        // 打印当前队列状态
        printQueueStatus();

        // 检查是否有足够玩家开始游戏
        if (queue.size() >= 2) {
            // 尝试找到合适的配对
            MatchRequest player1 = queue.poll();

            // 查找第二个玩家，排除避免匹配的对手
            MatchRequest player2 = null;
            for (MatchRequest candidate : queue) {
                // 检查是否需要排除
                boolean shouldAvoid = false;
                if (player1.avoidOpponentId != null && candidate.userId.equals(player1.avoidOpponentId)) {
                    shouldAvoid = true;
                }
                if (candidate.avoidOpponentId != null && player1.userId.equals(candidate.avoidOpponentId)) {
                    shouldAvoid = true;
                }

                if (!shouldAvoid) {
                    player2 = candidate;
                    queue.remove(candidate);
                    break;
                }
            }

            if (player2 == null) {
                // 没有找到合适的对手，把player1放回队列
                queue.add(player1);
                logger.info("⏳ 玩家 {} 等待合适对手...", userId);
                sendWaitingNotification(userId, queue.size());
                return;
            }

            // 防止自己匹配自己（理论上不应该发生，但作为防御性编程）
            if (player1.userId.equals(player2.userId)) {
                logger.warn("⚠️ 检测到同一玩家尝试匹配自己，将第二个玩家放回队列: userId={}", player1.userId);
                queue.add(player2);
                sendWaitingNotification(player1.userId, queue.size());
                return;
            }

            logger.info("🎯 匹配成功: {} ({}) vs {} ({}), 模式: {}",
                    player1.userId, player1.username,
                    player2.userId, player2.username,
                    gameMode.getDescription());

            // 创建游戏房间
            createAndNotifyGame(player1, player2, mode);
        } else {
            // 通知玩家等待中
            sendWaitingNotification(userId, queue.size());
        }
    }

    /**
     * 玩家加入匹配队列（不带排除参数）
     */
    public synchronized void joinQueue(Long userId, String username, String mode) {
        joinQueue(userId, username, mode, null);
    }

    /**
     * 玩家取消匹配
     */
    public synchronized void cancelMatch(Long userId, String mode) {
        logger.info("❌ 玩家 {} 尝试取消 {} 匹配", userId, mode);

        ConcurrentLinkedQueue<MatchRequest> queue = waitingPlayers.get(mode);
        if (queue != null) {
            queue.removeIf(req -> req.userId.equals(userId));
            logger.info("✓ 玩家 {} 已从 {} 队列移除", userId, mode);
        }

        printQueueStatus();
    }

    /**
     * 从所有队列中移除玩家（用于处理重复匹配请求）
     */
    private void removePlayerFromAllQueues(Long userId) {
        for (Map.Entry<String, ConcurrentLinkedQueue<MatchRequest>> entry : waitingPlayers.entrySet()) {
            ConcurrentLinkedQueue<MatchRequest> queue = entry.getValue();
            queue.removeIf(req -> req.userId.equals(userId));
        }
    }

    /**
     * 创建游戏房间并通知玩家
     */
    private void createAndNotifyGame(MatchRequest player1, MatchRequest player2, String mode) {
        try {
            GameMode gameMode = GameMode.fromCode(mode);

            // 使用第一个玩家创建房间
            String roomId = gameService.createRoom(player1.userId, mode);

            // 让第二个玩家加入房间
            gameService.joinRoom(roomId, player2.userId);

            // 通知两个玩家匹配成功 - player1 是黑棋，player2 是白棋
            notifyMatchSuccess(player1.userId, roomId, 1, player2.userId, player2.username);
            notifyMatchSuccess(player2.userId, roomId, 2, player1.userId, player1.username);

            logger.info("🏠 房间创建成功: roomId={}, 玩家1={}, 玩家2={}, 模式={}, 积分影响={}",
                    roomId, player1.userId, player2.userId,
                    gameMode.getDescription(), gameMode.affectsRating());
        } catch (Exception e) {
            logger.error("❌ 创建游戏房间失败", e);
            // 创建失败，让玩家重新匹配
            if (e.getMessage() != null) {
                notifyMatchError(player1.userId, e.getMessage());
                notifyMatchError(player2.userId, e.getMessage());
            }
        }
    }

    /**
     * 通知匹配成功
     */
    private void notifyMatchSuccess(Long userId, String roomId, int color, Long opponentId, String opponentName) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "MATCH_SUCCESS");
        response.put("roomId", roomId);
        response.put("color", color);  // 1=黑, 2=白
        response.put("opponentName", opponentName);
        response.put("opponentId", opponentId);

        logger.info("→ 发送匹配成功通知: userId={}, roomId={}, color={}", userId, roomId, color);

        // 发送到用户专属队列
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/match", response);
            logger.info("✓ 已通过 convertAndSendToUser 发送给用户 {}", userId);
        } catch (Exception e) {
            logger.error("❌ convertAndSendToUser 发送失败: userId={}", userId, e);
        }

        // 同时也尝试广播到 topic（备用方案）
        try {
            messagingTemplate.convertAndSend("/topic/match/" + userId, response);
            logger.info("✓ 已通过 topic 广播发送给用户 {}", userId);
        } catch (Exception e) {
            logger.error("❌ topic 广播失败: userId={}", userId, e);
        }
    }

    /**
     * 通知等待中
     */
    private void sendWaitingNotification(Long userId, int queueSize) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "MATCH_WAITING");
        response.put("queueSize", queueSize);
        response.put("message", "正在寻找对手...");

        logger.debug("→ 发送等待通知: userId={}, 队列大小={}", userId, queueSize);
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/match", response);
    }

    /**
     * 通知匹配错误
     */
    private void notifyMatchError(Long userId, String error) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "MATCH_ERROR");
        response.put("message", error);

        logger.error("→ 发送匹配错误通知: userId={}, error={}", userId, error);
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/match", response);
    }

    /**
     * 获取当前队列大小
     */
    public synchronized int getQueueSize(String mode) {
        ConcurrentLinkedQueue<MatchRequest> queue = waitingPlayers.get(mode);
        return queue != null ? queue.size() : 0;
    }

    /**
     * 打印队列状态（用于调试）
     */
    private void printQueueStatus() {
        logger.info("📊 当前匹配队列状态:");
        for (Map.Entry<String, ConcurrentLinkedQueue<MatchRequest>> entry : waitingPlayers.entrySet()) {
            logger.info("  - {} 模式: {} 人等待", entry.getKey(), entry.getValue().size());
        }
    }
}
