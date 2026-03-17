package com.gobang.core.protocol;

import com.gobang.protocol.protobuf.GobangProto;
import io.netty.channel.ChannelHandlerContext;

/**
 * 消息处理接口
 * 所有业务消息处理器都需要实现此接口
 */
public interface MessageHandler {

    /**
     * 处理消息
     *
     * @param ctx     Channel上下文
     * @param packet  消息包
     */
    void handle(ChannelHandlerContext ctx, GobangProto.Packet packet);

    /**
     * 获取支持的消息类型
     *
     * @return 消息类型
     */
    MessageType getSupportedType();
}
