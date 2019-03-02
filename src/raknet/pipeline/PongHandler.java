package raknet.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import raknet.RakNet;
import raknet.packet.Pong;

import java.util.concurrent.TimeUnit;

public class PongHandler extends SimpleChannelInboundHandler<Pong> {

    public static final String NAME = "rn-pong";

    protected void channelRead0(ChannelHandlerContext ctx, Pong pong) {
        if (!pong.getReliability().isReliable) {
            final RakNet.Config config = (RakNet.Config) ctx.channel().config();
            final long pongRTT = TimeUnit.NANOSECONDS.convert(pong.getRTT(), TimeUnit.MILLISECONDS);
            config.updateRTT(pongRTT);
        }
    }

}
