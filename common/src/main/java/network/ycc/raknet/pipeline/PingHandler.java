package network.ycc.raknet.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import network.ycc.raknet.packet.Ping;
import network.ycc.raknet.packet.Pong;

@ChannelHandler.Sharable
public class PingHandler extends SimpleChannelInboundHandler<Ping> {

    public static final String NAME = "rn-ping";
    public static final PingHandler INSTANCE = new PingHandler();

    protected void channelRead0(ChannelHandlerContext ctx, Ping ping) {
        ctx.writeAndFlush(new Pong(ping.getTimestamp(), ping.getReliability()));
    }

}
