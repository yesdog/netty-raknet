package raknet.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import raknet.packet.Disconnect;

public class DisconnectHandler extends SimpleChannelInboundHandler<Disconnect> {

    public static final String NAME = "rn-disconnect";

    protected void channelRead0(ChannelHandlerContext ctx, Disconnect msg) {
        ctx.channel().close();
    }

}
