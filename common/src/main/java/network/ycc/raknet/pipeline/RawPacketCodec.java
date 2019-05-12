package network.ycc.raknet.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.Packet;

import java.util.List;

@ChannelHandler.Sharable
public class RawPacketCodec extends MessageToMessageCodec<ByteBuf, Packet> {

    public static final String NAME = "rn-raw-codec";
    public static final RawPacketCodec INSTANCE = new RawPacketCodec();

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet in, List<Object> out) throws Exception {
        final RakNet.Config config = RakNet.config(ctx);
        final ByteBuf buf = ctx.alloc().ioBuffer(in.sizeHint());
        try {
            config.getCodec().encode(in, buf);
            out.add(buf.retain());
            RakNet.metrics(ctx).bytesOut(buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        final RakNet.Config config = RakNet.config(ctx);
        if (in.readableBytes() != 0) {
            RakNet.metrics(ctx).bytesIn(in.readableBytes());
            out.add(config.getCodec().decode(in));
        }
    }

}
