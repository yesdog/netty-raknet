package network.ycc.raknet.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import network.ycc.raknet.packet.Packet;
import network.ycc.raknet.RakNet;

@ChannelHandler.Sharable
public class PacketEncoder extends MessageToByteEncoder<Packet> {

    public static final String NAME = "rn-encoder";
    public static final PacketEncoder INSTANCE = new PacketEncoder();

    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) {
        msg.write(out);
        //TODO: MTU write sanity check
        RakNet.metrics(ctx).bytesOut(out.readableBytes());
    }

}
