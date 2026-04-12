package com.gobang.service;

import java.util.List;
import java.util.Map;

/**
 * 游戏服务接口
 */
public interface GameService {

    /**
     * 落子
     */
    void makeMove(Long userId, String roomId, int x, int y);

    /**
     * 创建房间
     */
    String createRoom(Long userId, String mode, String roomName, String password);

    /**
     * 加入房间
     */
    void joinRoom(String roomId, Long userId);

    /**
     * 认输
     */
    void resign(Long userId, String roomId);

    /**
     * 广播游戏状态
     */
    void broadcastGameState(String roomId);

    /**
     * 发送和棋请求
     */
    void sendDrawRequest(String roomId, Long userId);

    /**
     * 响应和棋请求
     */
    void respondDrawRequest(String roomId, Long userId, boolean accept);

    /**
     * 发送悔棋请求
     */
    void sendUndoRequest(String roomId, Long userId);

    /**
     * 响应悔棋请求
     */
    void respondUndoRequest(String roomId, Long userId, boolean accept);

    /**
     * 发送聊天消息
     */
    void sendChatMessage(String roomId, Long userId, String content);

    /**
     * 发送再来一局请求
     */
    void sendPlayAgainRequest(String roomId, Long userId);

    /**
     * 发送玩家准备状态（游戏结束后点击"再来一局"）
     */
    void sendPlayerReady(String roomId, Long userId);

    /**
     * 同意再来一局
     */
    void acceptPlayAgain(String roomId, Long userId);

    /**
     * 取消再来一局请求
     */
    void cancelPlayAgainRequest(String roomId, Long userId);

    /**
     * 发送换桌消息
     */
    void sendChangeTableMessage(String roomId, Long userId);

    /**
     * 处理玩家离开（断开连接）
     */
    void handlePlayerLeave(Long userId);

    /**
     * 发送玩家离开消息
     */
    void sendPlayerLeaveMessage(String roomId, Long userId);

    // ==================== 观战相关 ====================

    /**
     * 添加观战者到房间
     * @param roomId 房间ID
     * @param userId 观战者用户ID
     */
    void addObserverToRoom(String roomId, Long userId);

    /**
     * 从房间移除观战者
     * @param roomId 房间ID
     * @param userId 观战者用户ID
     */
    void removeObserverFromRoom(String roomId, Long userId);

    /**
     * 获取房间观战者列表
     * @param roomId 房间ID
     * @return 观战者ID列表
     */
    List<Long> getRoomObservers(String roomId);

    /**
     * 获取房间观战者数量
     * @param roomId 房间ID
     * @return 观战者数量
     */
    int getRoomObserverCount(String roomId);

    /**
     * 获取游戏房间对象
     * @param roomId 房间ID
     * @return GameRoom对象
     */
    Object getGameRoom(String roomId);
}
