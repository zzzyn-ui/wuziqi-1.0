package com.gobang.core.match;

import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.util.IdGenerator;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 匹配器
 * 使用无锁队列实现玩家匹配
 * 支持测试模式：单人匹配时可与机器人对战
 */
public class MatchMaker {

    private static final Logger logger = LoggerFactory.getLogger(MatchMaker.class);

    private final Queue<Player> matchQueue = new ConcurrentLinkedQueue<>();
    private final Map<Long, Player> playerMap = new HashMap<>(); // userId -> Player

    private final int maxRatingDiff;
    private final int maxQueueTime;
    private final int checkInterval;
    private final IdGenerator idGenerator = new IdGenerator(2, 1);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 测试模式开关
    private boolean testMode = false;
    private final int testMatchDelay; // 测试模式下自动匹配延迟（毫秒）

    // 匹配回调接口
    public interface MatchCallback {
        void onMatchSuccess(Player p1, Player p2, String roomId);
        void onMatchTimeout(Player player);
    }

    private MatchCallback callback;

    public MatchMaker(int maxRatingDiff, int maxQueueTime, int checkInterval) {
        this(maxRatingDiff, maxQueueTime, checkInterval, 3000); // 默认3秒后与机器人匹配
    }

    public MatchMaker(int maxRatingDiff, int maxQueueTime, int checkInterval, int testMatchDelay) {
        this.maxRatingDiff = maxRatingDiff;
        this.maxQueueTime = maxQueueTime * 1000; // 转换为毫秒
        this.checkInterval = checkInterval;
        this.testMatchDelay = testMatchDelay;

        // 启动匹配任务
        startMatchTask();
    }

    public void setCallback(MatchCallback callback) {
        this.callback = callback;
    }

    /**
     * 设置测试模式
     * @param enabled 是否启用测试模式
     */
    public void setTestMode(boolean enabled) {
        this.testMode = enabled;
        logger.info("Test mode: {}", enabled ? "ENABLED" : "DISABLED");
    }

    /**
     * 获取测试模式状态
     */
    public boolean isTestMode() {
        return testMode;
    }

    /**
     * 加入匹配队列
     */
    public boolean enqueue(Player player) {
        if (playerMap.containsKey(player.getUserId())) {
            logger.warn("Player {} already in match queue", player.getUserId());
            return false;
        }

        matchQueue.offer(player);
        playerMap.put(player.getUserId(), player);
        logger.info("Player {} (rating: {}) joined match queue, queue size: {}",
                player.getUserId(), player.getRating(), matchQueue.size());
        return true;
    }

    /**
     * 离开匹配队列
     */
    public boolean dequeue(Long userId) {
        Player player = playerMap.remove(userId);
        if (player != null) {
            matchQueue.remove(player);
            logger.info("Player {} left match queue", userId);
            return true;
        }
        return false;
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return matchQueue.size();
    }

    /**
     * 启动匹配任务
     */
    private void startMatchTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processMatching();
            } catch (Exception e) {
                logger.error("Error in match task", e);
            }
        }, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理匹配逻辑
     */
    private void processMatching() {
        if (matchQueue.isEmpty()) {
            return;
        }

        // 转换为列表以便处理
        List<Player> players = new ArrayList<>();
        Iterator<Player> iter = matchQueue.iterator();
        while (iter.hasNext()) {
            Player p = iter.next();
            if (!p.isActive()) {
                iter.remove();
                playerMap.remove(p.getUserId());
            } else {
                players.add(p);
            }
        }

        // 检查超时
        checkTimeouts(players);

        // 尝试匹配
        List<Player> matched = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (matched.contains(players.get(i))) {
                continue;
            }

            Player p1 = players.get(i);
            if (!p1.isActive() || !playerMap.containsKey(p1.getUserId())) {
                continue;
            }

            // 测试模式：单人匹配时自动匹配机器人
            if (testMode && players.size() == 1) {
                long waitTime = System.currentTimeMillis() - p1.getEnqueueTime();
                if (waitTime >= testMatchDelay) {
                    matched.add(p1);
                    matchQueue.remove(p1);
                    playerMap.remove(p1.getUserId());

                    Player botPlayer = createBotPlayer(p1);
                    String roomId = generateRoomId();
                    if (callback != null) {
                        callback.onMatchSuccess(p1, botPlayer, roomId);
                    }

                    logger.info("[TEST MODE] Matched player {} with bot", p1.getUserId());
                    break;
                }
            }

            // 正常匹配：需要至少2个玩家
            if (players.size() < 2 && !testMode) {
                continue;
            }

            for (int j = i + 1; j < players.size(); j++) {
                Player p2 = players.get(j);
                if (matched.contains(p2) || !p2.isActive() || !playerMap.containsKey(p2.getUserId())) {
                    continue;
                }

                if (p1.canMatch(p2, maxRatingDiff)) {
                    // 匹配成功
                    matched.add(p1);
                    matched.add(p2);

                    matchQueue.remove(p1);
                    matchQueue.remove(p2);
                    playerMap.remove(p1.getUserId());
                    playerMap.remove(p2.getUserId());

                    String roomId = generateRoomId();
                    if (callback != null) {
                        callback.onMatchSuccess(p1, p2, roomId);
                    }

                    logger.info("Matched: {} (rating:{}) vs {} (rating:{})",
                            p1.getUserId(), p1.getRating(),
                            p2.getUserId(), p2.getRating());
                    break;
                }
            }
        }
    }

    /**
     * 创建机器人玩家（用于测试）
     */
    private Player createBotPlayer(Player realPlayer) {
        // 机器人的ID使用负数，与真实用户区分
        long botId = -(realPlayer.getUserId() + 1000000);

        // 机器人不需要真实的Channel，使用null
        Channel botChannel = null;

        // 机器人使用与真实玩家相同的模式
        return new Player(botId, "Bot_" + (Math.abs(botId) % 1000), "五子棋机器人",
                realPlayer.getRating(), botChannel, realPlayer.getMode());
    }

    /**
     * 检查超时玩家
     */
    private void checkTimeouts(List<Player> players) {
        for (Player player : players) {
            if (!player.isActive()) {
                matchQueue.remove(player);
                playerMap.remove(player.getUserId());
                continue;
            }

            if (player.isTimeout(maxQueueTime)) {
                matchQueue.remove(player);
                playerMap.remove(player.getUserId());

                if (callback != null) {
                    callback.onMatchTimeout(player);
                }

                logger.info("Player {} match timeout", player.getUserId());
            }
        }
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return String.format("%06d", Math.abs(idGenerator.nextId()) % 1000000);
    }

    /**
     * 关闭匹配器
     */
    public void shutdown() {
        scheduler.shutdown();
        matchQueue.clear();
        playerMap.clear();
    }
}
