package network.ycc.raknet.channel;

import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.PromiseCombiner;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultConfig;

import java.nio.channels.ClosedChannelException;
import java.util.function.Supplier;

//TODO: implement DatagramChannel?
public abstract class RakNetUDPChannel extends AbstractChannel {

    public static final boolean DEFAULT_CHANNEL_CAN_REUSE;
    public static final Class<? extends DatagramChannel> DEFAULT_CHANNEL_CLASS;
    public static final Supplier<EventLoopGroup> DEFAULT_CHANNEL_EVENT_GROUP;
    public static final String LISTENER_HANDLER_NAME = "rn-udp-listener-handler";

    protected static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    static {
        boolean kQueueEnabled = false;
        boolean ePollEnabled = false;

        try {
            kQueueEnabled = KQueue.isAvailable();
        } catch (Throwable e) {}

        try {
            ePollEnabled = Epoll.isAvailable();
        } catch (Throwable e) {}

        if (ePollEnabled) {
            DEFAULT_CHANNEL_CLASS = EpollDatagramChannel.class;
            DEFAULT_CHANNEL_CAN_REUSE = true;
            DEFAULT_CHANNEL_EVENT_GROUP = EpollEventLoopGroup::new;
        } else if (kQueueEnabled) {
            DEFAULT_CHANNEL_CLASS = KQueueDatagramChannel.class;
            DEFAULT_CHANNEL_CAN_REUSE = true;
            DEFAULT_CHANNEL_EVENT_GROUP = KQueueEventLoopGroup::new;
        } else {
            DEFAULT_CHANNEL_CLASS = NioDatagramChannel.class;
            DEFAULT_CHANNEL_CAN_REUSE = false;
            DEFAULT_CHANNEL_EVENT_GROUP = NioEventLoopGroup::new;
        }
    }

    protected final DatagramChannel listener;
    protected final Config config = new Config();
    protected volatile boolean open = true;

    public RakNetUDPChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(null);
        try {
            listener = ioChannelType.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create instance", e);
        }
        initChannels();
    }

    abstract protected ChannelHandler newChannelHandler();

    @Override
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this) {
            @Override
            protected void onUnhandledInboundException(Throwable cause) {
                if (cause instanceof ClosedChannelException) {
                    return;
                }
                super.onUnhandledInboundException(cause);
            }
        };
    }

    @Override
    public boolean isWritable() {
        return listener.isWritable();
    }

    @Override
    public long bytesBeforeUnwritable() {
        return listener.bytesBeforeUnwritable();
    }

    @Override
    public long bytesBeforeWritable() {
        return listener.bytesBeforeWritable();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doRegister() {
        //share same loop between listener and server channel
        eventLoop().register(listener).addListeners(
                ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doDeregister() {
        listener.deregister().addListeners(
                ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    protected void doClose() {
        open = false;
        try {
            listener.close().sync(); //TODO: not happy about this sync
        } catch (InterruptedException e) {}
    }

    protected void doBeginRead() {}

    public RakNet.Config config() {
        return config;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isActive() {
        return isOpen() && listener.isActive();
    }

    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    protected boolean inEventLoop() {
        return eventLoop().inEventLoop();
    }

    protected void initChannels() {
        pipeline().addLast(LISTENER_HANDLER_NAME, newChannelHandler());
        listener.pipeline().addLast(new ListenerHandler());
        listener.closeFuture().addListener(v -> close());
    }

    protected class Config extends DefaultConfig {
        protected Config() {
            super(RakNetUDPChannel.this);
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            final boolean thisOption = super.setOption(option, value);
            final boolean listenOption = listener.config().setOption(option, value);
            return thisOption || listenOption;
        }

        @Override
        @SuppressWarnings({ "unchecked", "deprecation" })
        public <T> T getOption(ChannelOption<T> option) {
            final T thisOption = super.getOption(option);
            if (thisOption == null) {
                return listener.config().getOption(option);
            }
            return thisOption;
        }
    }

    protected class ListenerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            assert inEventLoop();
            pipeline().fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            assert inEventLoop();
            pipeline().fireChannelReadComplete();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            assert inEventLoop();
            pipeline().fireChannelWritabilityChanged();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            assert inEventLoop();
            pipeline().fireExceptionCaught(cause);
        }
    }

    protected abstract class ChannelHandler extends ChannelDuplexHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof DatagramPacket) {
                listener.write(msg).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                promise.trySuccess();
            } else {
                super.write(ctx, msg, promise);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            listener.flush();
            ctx.flush();
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            try {
                final PromiseCombiner combiner = new PromiseCombiner();
                combiner.addAll(ctx.close(), listener.close());
                combiner.finish(promise);
            } catch (Throwable t) {
                promise.tryFailure(t);
            }
        }
    }
}
