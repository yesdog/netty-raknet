package network.ycc.raknet.pipeline;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.Pong;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class PongHandler extends SimpleChannelInboundHandler<Pong> {

    public static final String NAME = "rn-pong";
    public static final PongHandler INSTANCE = new PongHandler();

    protected void channelRead0(ChannelHandlerContext ctx, Pong pong) {
        if (!pong.getReliability().isReliable) {
            final RakNet.Config config = RakNet.config(ctx);
            config.updateRTTNanos(pong.getRTT());
        }
    }

}
