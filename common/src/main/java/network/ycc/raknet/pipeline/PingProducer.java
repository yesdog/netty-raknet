package network.ycc.raknet.pipeline;

import network.ycc.raknet.packet.Ping;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

@Sharable
public class PingProducer implements ChannelHandler {

    public static final PingProducer INSTANCE = new PingProducer();
    public static final String NAME = "rn-ping-producer";

    public void handlerAdded(ChannelHandlerContext ctx) {
        final ScheduledFuture<?> pingTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.writeAndFlush(new Ping()),
                0, 250, TimeUnit.MILLISECONDS
        );
        final ScheduledFuture<?> pingTaskReliable = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.writeAndFlush(Ping.newReliablePing()),
                100, 1000, TimeUnit.MILLISECONDS
        );
        ctx.channel().closeFuture().addListener(x -> {
            pingTask.cancel(false);
            pingTaskReliable.cancel(false);
        });
    }

    public void handlerRemoved(ChannelHandlerContext ctx) {
        // NOOP
    }

    @SuppressWarnings("deprecation")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

}
