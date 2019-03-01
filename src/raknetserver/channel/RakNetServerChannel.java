package raknetserver.channel;

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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

import raknet.RakNet;
import raknet.config.DefaultConfig;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RakNetServerChannel extends AbstractServerChannel {

    public static final boolean DEFAULT_CHANNEL_CAN_REUSE;
    public static final Class<? extends DatagramChannel> DEFAULT_CHANNEL_CLASS;
    public static final Supplier<EventLoopGroup> DEFAULT_CHANNEL_EVENT_GROUP;

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
    protected final Map<SocketAddress, RakNetChildChannel> childMap = new HashMap<>();
    protected SocketAddress localAddress = null;
    protected volatile boolean open = true;

    public RakNetServerChannel(Class<? extends DatagramChannel> ioChannelType) {
        try {
            listener = ioChannelType.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create instance", e);
        }
        initChannels();
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

    @SuppressWarnings("unchecked")
    protected void doBind(SocketAddress local) {
        localAddress = local;
        listener.bind(local).addListeners(
                ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    protected void doClose() {
        open = false;
    }

    protected void doBeginRead() {}

    public RakNet.Config config() {
        return config;
    }

    public boolean isOpen() {
        return open && listener.isOpen();
    }

    public boolean isActive() {
        return isOpen() && isRegistered();
    }

    protected void initChannels() {
        pipeline().addLast(new ServerHandler());
        listener.pipeline().addLast(new ListenerHandler());
        listener.closeFuture().addListener(v -> close());
    }

    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    protected SocketAddress localAddress0() {
        return localAddress;
    }

    private boolean inEventLoop() {
        return eventLoop().inEventLoop();
    }

    protected class Config extends DefaultConfig {
        protected Config() {
            super(RakNetServerChannel.this);
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
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            assert inEventLoop();
            pipeline().fireExceptionCaught(cause);
        }
    }

    protected class ServerHandler extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof DatagramPacket) {
                try {
                    final DatagramPacket datagram = (DatagramPacket) msg;
                    final Channel child = childMap.get(datagram.sender());
                    if (child != null && child.isActive() && child.config().isAutoRead()) {
                        child.pipeline()
                                .fireChannelRead(datagram.content().retain())
                                .fireChannelReadComplete();
                    } else if (child == null) {
                        ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

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
            final PromiseCombiner combiner = new PromiseCombiner();
            combiner.addAll(ctx.close(), listener.close());
            combiner.finish(promise);
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                            SocketAddress localAddress, ChannelPromise promise) {
            //TODO: session limit check
            try {
                if (localAddress != null && !localAddress.equals(localAddress)) {
                    throw new IllegalArgumentException("Bound localAddress does not match provided " + localAddress);
                }
                if (!(remoteAddress instanceof InetSocketAddress)) {
                    throw new IllegalArgumentException("Provided remote address is not an InetSocketAddress");
                }
                if (!childMap.containsKey(remoteAddress)) {
                    final RakNetChildChannel child = new RakNetChildChannel(
                            RakNetServerChannel.this, (InetSocketAddress) remoteAddress);
                    child.closeFuture().addListener(v ->
                            eventLoop().execute(() -> childMap.remove(remoteAddress, child))
                    );
                    pipeline().fireChannelRead(child).fireChannelReadComplete(); //register
                    childMap.put(remoteAddress, child);
                }
                promise.trySuccess();
            } catch (Exception e) {
                promise.tryFailure(e);
                throw e;
            }
        }
    }

}
