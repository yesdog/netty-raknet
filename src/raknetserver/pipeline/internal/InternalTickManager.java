package raknetserver.pipeline.internal;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import raknetserver.RakNetServer;

import java.util.concurrent.TimeUnit;

//TODO: bad namespace >.<
public class InternalTickManager extends ChannelDuplexHandler {

    public static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS);

    protected static final long COARSE_TIMER_RESOLUTION = 50; //in ms, limited by netty timer resolution

    public static void checkTick(ChannelHandlerContext ctx) {
        //TODO: fire on tick context directly
        //dummy object will trigger maybeTick when it gets to InternalTickManager#channelRead
        ctx.fireChannelRead(CheckTick.INSTANCE);
    }

    protected int references = 0;
    protected long tickAccum = 0;
    protected long lastTickAccum = System.nanoTime();
    protected boolean timerRunning = false;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        references++;
        assert references == 1; //only one context
        startCoarseTickTimer(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        references--;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        maybeTick(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        maybeTick(ctx);
    }

    protected void startCoarseTickTimer(ChannelHandlerContext ctx) {
        if (timerRunning) {
            return;
        }
        timerRunning = true;
        ctx.channel().eventLoop().schedule(() -> {
            timerRunning = false;
            if (ctx.channel().isOpen() && references >= 0) {
                startCoarseTickTimer(ctx);
                maybeTick(ctx);
            }
        }, COARSE_TIMER_RESOLUTION, TimeUnit.MILLISECONDS);
    }

    /*
    The tick fires no faster than the set interval, and is driven
    by a slow (100ms) netty timer as well as the traffic flow itself.
    This could benefit from a higher resolution timer, but the
    traffic flow itself generally does fine as a driver.
     */
    protected void maybeTick(ChannelHandlerContext ctx) {
        final long curTime = System.nanoTime();
        tickAccum += curTime - lastTickAccum;
        lastTickAccum = curTime;
        if (tickAccum > TICK_RESOLUTION) {
            final int nTicks = (int) (tickAccum / TICK_RESOLUTION);
            tickAccum = tickAccum % TICK_RESOLUTION;
            ctx.writeAndFlush(Tick.get(nTicks)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    public static final class CheckTick {

        static protected final CheckTick INSTANCE = new CheckTick();

        private CheckTick() {

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
