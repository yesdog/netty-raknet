package raknetserver.pipeline;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import raknetserver.RakNetServer;

import java.util.concurrent.TimeUnit;

//TODO: inner class that lives at the 'last' part of the pipeline?
public class FlushTickDriver {

    public static final String NAME_IN = "rn-tick-in";
    public static final String NAME_OUT = "rn-tick-out";
    public static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS);

    protected static final long COARSE_TIMER_RESOLUTION = 50; //in ms, limited by netty timer resolution

    public final ChannelInboundHandlerAdapter inboundHandler = new InboundHandler();
    public final ChannelOutboundHandlerAdapter outboundHandler = new OutboundHandler();
    protected long tickAccum = 0;
    protected long lastTickAccum = System.nanoTime();
    protected boolean timerRunning = false;
    protected ChannelHandlerContext ctx = null;

    protected void startCoarseTickTimer() {
        if (timerRunning) {
            return;
        }
        timerRunning = true;
        ctx.channel().eventLoop().schedule(() -> {
            timerRunning = false;
            if (ctx.channel().isOpen()) {
                startCoarseTickTimer();
                maybeTick();
            }
        }, COARSE_TIMER_RESOLUTION, TimeUnit.MILLISECONDS);
    }

    /*
    The tick fires no faster than the set interval, and is driven
    by a slow (100ms) netty timer as well as the traffic flow itself.
    This could benefit from a higher resolution timer, but the
    traffic flow itself generally does fine as a driver.
     */
    protected void maybeTick() {
        if (ctx == null) {
            return;
        }
        final long curTime = System.nanoTime();
        tickAccum += curTime - lastTickAccum;
        lastTickAccum = curTime;
        if (tickAccum > TICK_RESOLUTION) {
            final int nTicks = (int) (tickAccum / TICK_RESOLUTION);
            tickAccum = tickAccum % TICK_RESOLUTION;
            ctx.writeAndFlush(Tick.get(nTicks)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    protected final class InboundHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            super.channelRead(ctx, msg);
            maybeTick();
        }
    }

    protected final class OutboundHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            assert FlushTickDriver.this.ctx == null;
            super.handlerAdded(ctx);
            FlushTickDriver.this.ctx = ctx;
            startCoarseTickTimer();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, msg, promise);
            maybeTick();
        }
    }

    private static final class Tick implements RakNetServer.Tick {

        private static final Tick[] instances = new Tick[32];

        static {
            for (int i = 0 ; i < instances.length ; i++) {
                instances[i] = new Tick(i);
            }
        }

        protected static Tick get(int ticks) {
            if (0 <= ticks && ticks < instances.length) {
                return instances[ticks];
            }
            return new Tick(ticks);
        }

        private final int ticks;

        private Tick(int ticks) {
            this.ticks = ticks;
        }

        public int getTicks() {
            return ticks;
        }

    }

}
