package com.gobang.core.protocol;

import com.gobang.protocol.protobuf.GobangProto;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Protobuf消息编解码器
 * 格式: [长度(4字节)] [Packet]
 */
public class PacketCodec extends MessageToMessageCodec<ByteBuf, GobangProto.Packet> {

    private static final Logger logger = LoggerFactory.getLogger(PacketCodec.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, GobangProto.Packet packet, List<Object> out) throws Exception {
        try {
            byte[] data = packet.toByteArray();
            ByteBuf buffer = ctx.alloc().buffer();
            buffer.writeInt(data.length);
            buffer.writeBytes(data);
            out.add(buffer);
        } catch (Exception e) {
            logger.error("Encode packet failed", e);
            throw e;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        try {
            // 等待长度字段
            if (buffer.readableBytes() < 4) {
                return;
            }

            buffer.markReaderIndex();
            int length = buffer.readInt();

            // 检查数据是否完整
            if (buffer.readableBytes() < length) {
                buffer.resetReaderIndex();
                return;
            }

            byte[] data = new byte[length];
            buffer.readBytes(data);

            GobangProto.Packet packet = GobangProto.Packet.parseFrom(data);
            out.add(packet);
        } catch (Exception e) {
            logger.error("Decode packet failed", e);
            buffer.clear();
        }
    }
}
