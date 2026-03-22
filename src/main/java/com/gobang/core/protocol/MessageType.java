package com.gobang.core.protocol;

/**
 * 消息类型枚举
 * 对应protobuf中定义的MessageType
 */
public enum MessageType {
    UNKNOWN(0),

    // 认证相关
    AUTH_LOGIN(1),
    AUTH_REGISTER(2),
    AUTH_RESPONSE(3),
    TOKEN_AUTH(100),  // WebSocket连接后的token认证

    // 匹配相关
    MATCH_START(10),
    MATCH_CANCEL(11),
    MATCH_SUCCESS(12),
    MATCH_FAILED(13),
    MATCH_TIMEOUT(14),

    // 人机对战
    BOT_MATCH_START(15),

    // 房间相关
    CREATE_ROOM(16),
    JOIN_ROOM(17),
    LEAVE_ROOM(18),
    ROOM_INFO(19),
    ROOM_JOINED(20),
    ROOM_PLAYER_LEFT(29),

    // 游戏相关
    GAME_MOVE(30),
    GAME_MOVE_RESULT(31),
    GAME_STATE(32),
    GAME_OVER(33),
    GAME_RESIGN(34),
    GAME_RECONNECT(25),
    GAME_UNDO_REQUEST(26),
    GAME_UNDO_RESPONSE(27),
    GAME_UNDO_NOTIFY(28),

    // 观战相关
    OBSERVER_LIST(35),
    OBSERVER_JOIN(36),
    OBSERVER_LEAVE(37),
    OBSERVER_COUNT(38),

    // 聊天相关
    CHAT_SEND(40),
    CHAT_RECEIVE(41),
    CHAT_SYSTEM(42),

    // 好友相关
    FRIEND_REQUEST(50),
    FRIEND_ACCEPT(51),
    FRIEND_REJECT(52),
    FRIEND_REMOVE(53),
    FRIEND_LIST(54),
    FRIEND_ONLINE(55),
    FRIEND_OFFLINE(56),
    FRIEND_REMARK(57),      // 设置好友备注
    FRIEND_GROUP_CREATE(58),  // 创建好友分组
    FRIEND_GROUP_LIST(59),  // 获取分组列表
    FRIEND_MOVE_GROUP(60),  // 移动好友到分组

    // 用户相关
    USER_INFO(61),
    USER_STATS(62),
    GAME_REPLAY_REQUEST(70),
    GAME_REPLAY_DATA(71),
    GAME_HISTORY_REQUEST(72),

    // 再来一局相关
    GAME_REMATCH_REQUEST(80),
    GAME_REMATCH_RESPONSE(81),
    GAME_REMATCH_START(82);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MessageType fromValue(int value) {
        for (MessageType type : MessageType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
