package com.gobang.service;

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
    String createRoom(Long userId, String mode);

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
}
