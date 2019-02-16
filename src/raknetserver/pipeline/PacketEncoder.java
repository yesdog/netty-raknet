package raknetserver.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.Packet;

import java.util.List;

public class PacketEncoder extends MessageToMessageEncoder<Packet> {

    public static final String NAME = "rn-encoder";

    protected void encode(ChannelHandlerContext ctx, Packet msg, List<Object> out) {
        out.add(msg.createData(ctx.alloc()));
    }

}
