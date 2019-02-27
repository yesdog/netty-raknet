package raknetserver.pipeline;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.RakNetServer;
import raknetserver.packet.PacketData;
import raknetserver.udp.UdpChildChannel;

public class WriteHandler extends MessageToMessageEncoder<ByteBuf> {

    public static final String NAME = "rn-write";

    protected void encode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> list) {
        if (!buf.isReadable()) {
            return;
        }
        //TODO: default order channel?
        final int userDataId = ((UdpChildChannel) ctx.channel()).config().getUserDataId();
        if (userDataId != -1) {
            list.add(PacketData.create(ctx.alloc(), userDataId, buf));
        }
    }

}
