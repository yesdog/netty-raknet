package network.ycc.raknet.pipeline;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.frame.FrameData;
import network.ycc.raknet.packet.FramedPacket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

@ChannelHandler.Sharable
public class FramedPacketCodec extends MessageToMessageCodec<FrameData, FramedPacket> {

    public static final String NAME = "rn-framed-codec";
    public static final FramedPacketCodec INSTANCE = new FramedPacketCodec();

    @Override
    protected void encode(ChannelHandlerContext ctx, FramedPacket in, List<Object> out) {
        out.add(RakNet.config(ctx).getCodec().encode(in, ctx.alloc()));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FrameData in, List<Object> out) {
        out.add(RakNet.config(ctx).getCodec().decode(in));
    }

}
