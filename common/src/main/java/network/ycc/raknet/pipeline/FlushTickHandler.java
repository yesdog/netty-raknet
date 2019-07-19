package network.ycc.raknet.pipeline;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

/**
 * This handler produces an automatic flush cycle that is driven by the channel IO itself. The ping
 * produced in {@link AbstractConnectionInitializer} serves as a timed driver if no IO is present.
 * The channel write signal is driven by {@link ReliabilityHandler} using {@link
 * FlushTickHandler#checkFlushTick(Channel)}.
 */
public class FlushTickHandler extends ChannelDuplexHandler {

    public static final String NAME = "rn-flush-tick";
    public static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS
            .convert(5, TimeUnit.MILLISECONDS);
    protected static final Object FLUSH_CHECK_SIGNAL = new Object();
    protected long tickAccum = 0;
    protected long lastTickAccum = System.nanoTime();
    protected ScheduledFuture<?> flushTask = null;

    public static void checkFlushTick(Channel channel) {
        channel.pipeline().fireUserEventTriggered(FLUSH_CHECK_SIGNAL);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        assert flushTask == null;
        flushTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> ctx.channel().flush(),
                0, 100, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        flushTask.cancel(false);
        flushTask = null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        maybeFlush(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == FLUSH_CHECK_SIGNAL) {
            maybeFlush(ctx.channel());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (tickAccum >= TICK_RESOLUTION) {
            tickAccum -= TICK_RESOLUTION;
            ctx.channel().eventLoop().execute(() ->
                    ctx.channel().pipeline().fireUserEventTriggered(FLUSH_CHECK_SIGNAL));
        } else {
            tickAccum = 0;
        }
        super.flush(ctx);
    }

    protected void maybeFlush(Channel channel) {
        final long curTime = System.nanoTime();
        tickAccum += curTime - lastTickAccum;
        lastTickAccum = curTime;
        if (tickAccum >= TICK_RESOLUTION) {
            channel.flush();
        }
    }

}
