package network.ycc.raknet.pipeline;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.TimeUnit;

//TODO: keep a channel attr that stores a long # of ticks?
public class FlushTickHandler extends ChannelInboundHandlerAdapter {

    public static final String NAME = "rn-tick-in";
    public static final String NAME_OUT = "rn-tick-out";
    public static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS);

    protected long tickAccum = 0;
    protected long lastTickAccum = System.nanoTime();
    protected Channel channel;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        channel = ctx.channel();
        channel.eventLoop().execute(() -> channel.pipeline().addLast(NAME_OUT, new OutboundHandler()));
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        channel = null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        maybeFlush();
    }

    protected void maybeFlush() {
        if (channel == null) {
            return;
        }
        final long curTime = System.nanoTime();
        tickAccum += curTime - lastTickAccum;
        lastTickAccum = curTime;
        while (tickAccum >= TICK_RESOLUTION) {
            tickAccum -= TICK_RESOLUTION;
            channel.flush();
        }
    }

    protected final class OutboundHandler extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, msg, promise);
            maybeFlush();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            //force flush, lets adjust tickAccum
            if (tickAccum >= TICK_RESOLUTION) {
                tickAccum -= TICK_RESOLUTION;
            } else {
                tickAccum = 0;
            }
            super.flush(ctx);
        }

    }

}
