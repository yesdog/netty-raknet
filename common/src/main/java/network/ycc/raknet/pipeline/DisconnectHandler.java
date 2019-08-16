package network.ycc.raknet.pipeline;

import network.ycc.raknet.packet.Disconnect;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class DisconnectHandler extends ChannelDuplexHandler {

    public static final String NAME = "rn-disconnect";
    public static final DisconnectHandler INSTANCE = new DisconnectHandler();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Disconnect) {
            ReferenceCountUtil.release(msg);
            ctx.pipeline().remove(this);
            ctx.channel().flush().close(); //send ACKs
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (ctx.channel().isActive()) {
            final ChannelPromise disconnectPromise = ctx.newPromise();
            final ScheduledFuture<?> timeout = ctx.channel().eventLoop().schedule(
                    () -> disconnectPromise.trySuccess(), 1, TimeUnit.SECONDS); //TODO: config
            ctx.channel().writeAndFlush(new Disconnect())
                    .addListener(f -> disconnectPromise.trySuccess());
            disconnectPromise.addListener(f -> {
                timeout.cancel(false);
                ctx.close(promise);
            });
        } else {
            ctx.close(promise);
        }
    }

}
