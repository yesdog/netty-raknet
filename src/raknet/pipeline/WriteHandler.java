package raknet.pipeline;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import raknet.RakNet;
import raknet.packet.PacketData;

@ChannelHandler.Sharable
public class WriteHandler extends MessageToMessageEncoder<ByteBuf> {

    public static final String NAME = "rn-write";
    public static final WriteHandler INSTANCE = new WriteHandler();

    protected void encode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> list) {
        if (!buf.isReadable()) {
            return;
        }
        //TODO: default order channel?
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        final int userDataId = config.getUserDataId();
        if (userDataId != -1) {
            list.add(PacketData.create(ctx.alloc(), userDataId, buf));
        }
    }

}
