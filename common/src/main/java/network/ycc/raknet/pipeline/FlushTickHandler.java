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
    public static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS);

    /** Fired near the end of a pipeline to trigger a flush check. */
    protected static final Object FLUSH_CHECK_SIGNAL = new Object();

    protected long tickAccum = 0;
    protected long lastTickAccum = System.nanoTime();
    protected ScheduledFuture<?> flushTask = null;

    /** Helper method to trigger a flush check. */
    public static void checkFlushTick(Channel channel) {
        channel.pipeline().fireUserEventTriggered(FLUSH_CHECK_SIGNAL);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        assert flushTask == null;
        flushTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> checkFlushTick(ctx.channel()),
                0, 50, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        flushTask.cancel(false);
        flushTask = null;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
        maybeFlush(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == FLUSH_CHECK_SIGNAL) {
            maybeFlush(ctx.channel());
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (tickAccum >= TICK_RESOLUTION) {
            tickAccum -= TICK_RESOLUTION;
        } else {
            tickAccum = 0;
        }
        ctx.flush();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        maybeFlush(ctx.channel());
        ctx.fireChannelWritabilityChanged();
    }

    protected void maybeFlush(Channel channel) {
        final long curTime = System.nanoTime();
        tickAccum += curTime - lastTickAccum;
        lastTickAccum = curTime;

        if (tickAccum >= TICK_RESOLUTION) {
            channel.flush();

            final int nFlushes = (int) (tickAccum / TICK_RESOLUTION);
            if (nFlushes > 0) {
                tickAccum -= nFlushes * TICK_RESOLUTION;
                channel.pipeline().fireUserEventTriggered(new MissedFlushes(nFlushes));
            }
        }
    }

    /** Fired from this handler to alert handlers of a missed flush tick. */
    public class MissedFlushes {
        public final int nFlushes;

        public MissedFlushes(int nFlushes) {
            this.nFlushes = nFlushes;
        }
    }

}
