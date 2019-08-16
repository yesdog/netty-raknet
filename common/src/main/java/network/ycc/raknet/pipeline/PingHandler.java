package network.ycc.raknet.pipeline;

import network.ycc.raknet.packet.Ping;
import network.ycc.raknet.packet.Pong;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class PingHandler extends SimpleChannelInboundHandler<Ping> {

    public static final String NAME = "rn-ping";
    public static final PingHandler INSTANCE = new PingHandler();

    protected void channelRead0(ChannelHandlerContext ctx, Ping ping) {
        ctx.write(new Pong(ping.getTimestamp(), ping.getReliability()));
    }

}
