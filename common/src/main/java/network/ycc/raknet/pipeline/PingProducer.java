package network.ycc.raknet.pipeline;

import network.ycc.raknet.packet.Ping;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

public class PingProducer implements ChannelHandler {

    public static final String NAME = "rn-ping-producer";

    ScheduledFuture<?> pingTask = null;
    ScheduledFuture<?> pingTaskReliable = null;

    public void handlerAdded(ChannelHandlerContext ctx) {
        pingTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.write(new Ping()),
                0, 200, TimeUnit.MILLISECONDS
        );
        pingTaskReliable = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.write(Ping.newReliablePing()),
                0, 1, TimeUnit.SECONDS
        );
    }

    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (pingTaskReliable != null) {
            pingTaskReliable.cancel(false);
            pingTaskReliable = null;
        }
    }

    @SuppressWarnings("deprecation")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

}
