package raknet.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import raknet.packet.Ping;
import raknet.packet.Pong;

public class PingHandler extends SimpleChannelInboundHandler<Ping> {

    public static final String NAME = "rn-ping";

    protected void channelRead0(ChannelHandlerContext ctx, Ping ping) {
        ctx.writeAndFlush(new Pong(ping.getTimestamp(), ping.getReliability()));
    }

}
