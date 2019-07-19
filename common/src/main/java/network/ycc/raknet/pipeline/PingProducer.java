package network.ycc.raknet.pipeline;

import network.ycc.raknet.packet.Ping;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

public class PingProducer implements ChannelHandler {
    public static final String NAME = "rn-ping-producer";

    protected ScheduledFuture<?> pingTask = null;
    protected ScheduledFuture<?> pingTaskReliable = null;

    public void handlerAdded(ChannelHandlerContext ctx) {
        pingTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.channel().write(new Ping()),
                0, 250, TimeUnit.MILLISECONDS
        );
        pingTaskReliable = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.channel().write(Ping.newReliablePing()),
                0, 1, TimeUnit.SECONDS
        );
        ctx.channel().closeFuture().addListener(x -> {
            pingTask.cancel(false);
            pingTaskReliable.cancel(false);
        });
    }

    public void handlerRemoved(ChannelHandlerContext ctx) {
        pingTask.cancel(false);
        pingTaskReliable.cancel(false);
        pingTask = null;
        pingTaskReliable = null;
    }

    @SuppressWarnings("deprecation")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }
}
