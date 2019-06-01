package network.ycc.raknet.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
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
        final ByteBuf buf = ctx.alloc().ioBuffer(in.sizeHint());
        try {
            RakNet.config(ctx).getCodec().encode(in, buf);
            RakNet.metrics(ctx).bytesOut(buf.readableBytes());
            out.add(buf.retain());
        } finally {
            buf.release();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() != 0) {
            RakNet.metrics(ctx).bytesIn(in.readableBytes());
            try {
                out.add(RakNet.config(ctx).getCodec().decode(in));
            } catch (CorruptedFrameException e) {
                RakNet.metrics(ctx).frameError(1); //tolerate frame errors, they'll get resent.
            }
        }
    }

}
