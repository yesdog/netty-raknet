package network.ycc.raknet.pipeline;

import network.ycc.raknet.frame.FrameData;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

/**
 * Configure a user data packet ID that will be used for ByteBuf messages in the channel.
 */
@ChannelHandler.Sharable
public class UserDataCodec extends MessageToMessageCodec<FrameData, ByteBuf> {

    public static final String NAME = "rn-user-data-codec";

    private final int packetId;

    public UserDataCodec(int packetId) {
        this.packetId = packetId;
    }

    protected void encode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) {
        if (buf.isReadable()) {
            out.add(FrameData.create(ctx.alloc(), packetId, buf));
        }
    }

    protected void decode(ChannelHandlerContext ctx, FrameData packet, List<Object> out) {
        assert !packet.isFragment();
        if (packet.getDataSize() > 0) {
            if (packetId == packet.getPacketId()) {
                out.add(packet.createData().skipBytes(1));
            } else {
                out.add(packet.retain());
            }
        }
    }

}
