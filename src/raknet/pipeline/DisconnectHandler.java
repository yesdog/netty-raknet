package raknet.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import raknet.packet.Disconnect;

@ChannelHandler.Sharable
public class DisconnectHandler extends SimpleChannelInboundHandler<Disconnect> {

    public static final String NAME = "rn-disconnect";
    public static final DisconnectHandler INSTANCE = new DisconnectHandler();

    protected void channelRead0(ChannelHandlerContext ctx, Disconnect msg) {
        ctx.channel().close();
    }

}
