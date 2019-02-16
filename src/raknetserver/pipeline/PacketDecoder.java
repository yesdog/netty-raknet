package raknetserver.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import raknetserver.packet.Packets;

import java.util.List;

public class PacketDecoder extends ByteToMessageDecoder {

    public static final String NAME = "rn-decoder";

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        out.add(Packets.decodeRaw(in));
    }

}
