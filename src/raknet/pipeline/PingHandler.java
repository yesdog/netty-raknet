package raknet.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import raknet.packet.Ping;
import raknet.packet.Pong;

@ChannelHandler.Sharable
public class PingHandler extends SimpleChannelInboundHandler<Ping> {

    public static final String NAME = "rn-ping";
    public static final PingHandler INSTANCE = new PingHandler();

    protected void channelRead0(ChannelHandlerContext ctx, Ping ping) {
        ctx.writeAndFlush(new Pong(ping.getTimestamp(), ping.getReliability()));
    }

}
