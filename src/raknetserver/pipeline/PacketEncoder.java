package raknetserver.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import raknetserver.packet.Packet;

public class PacketEncoder extends MessageToByteEncoder<Packet> {

    public static final String NAME = "rn-encoder";

    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) {
        msg.write(out);
    }

}
