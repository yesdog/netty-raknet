package network.ycc.raknet.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import network.ycc.raknet.packet.PacketData;
import network.ycc.raknet.RakNet;

import java.nio.channels.ClosedChannelException;

@ChannelHandler.Sharable
public class ReadHandler extends SimpleChannelInboundHandler<PacketData> {

    public static final String NAME = "rn-read";
    public static final ReadHandler INSTANCE = new ReadHandler();

    protected void channelRead0(ChannelHandlerContext ctx, PacketData packet) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        final int userDataId = config.getUserDataId();
        assert !packet.isFragment();
        if (packet.getDataSize() <= 1) {
            return;
        } else if (userDataId != -1 && userDataId == packet.getPacketId()) {
            ctx.fireChannelRead(packet.createData().skipBytes(1));
        } else {
            ctx.fireChannelRead(packet.retain());
        }
    }

}
