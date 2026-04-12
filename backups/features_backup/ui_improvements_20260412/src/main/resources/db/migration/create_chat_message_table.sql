-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT DEFAULT NULL COMMENT '接收者ID（私聊时使用）',
    room_id VARCHAR(50) DEFAULT NULL COMMENT '房间ID（公屏聊天时使用）',
    content VARCHAR(1000) NOT NULL COMMENT '消息内容',
    message_type TINYINT DEFAULT 0 COMMENT '消息类型：0-文字，1-系统，2-图片',
    is_read BOOLEAN DEFAULT FALSE COMMENT '是否已读',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX idx_sender_receiver (sender_id, receiver_id),
    INDEX idx_room_id (room_id),
    INDEX idx_created_at (created_at),
    INDEX idx_receiver_read (receiver_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';
