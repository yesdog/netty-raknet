package raknetserver.udp;

import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.SocketAddress;
import java.util.function.Supplier;

public class UdpServerChannel extends AbstractServerChannel {

    public static final boolean DEFAULT_CHANNEL_CAN_REUSE;
    public static final Class<? extends DatagramChannel> DEFAULT_CHANNEL_CLASS;
    public static final Supplier<EventLoopGroup> DEFAULT_CHANNEL_EVENT_GROUP;

    static {
        boolean kQueueEnabled = false;
        try {
            kQueueEnabled = KQueue.isAvailable();
        } catch (Throwable e) {}

        if (Epoll.isAvailable()) {
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

    //TODO: keep NIO channel as part of instance and forward configs accordingly, dont use bootstrap

    protected final DatagramChannel listener;
    protected final Config config = new Config();
    protected SocketAddress localAddress = null;
    protected volatile boolean open = true;

    public UdpServerChannel(Class<? extends DatagramChannel> ioChannelType) {
        try {
            listener = ioChannelType.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create instance", e);
        }
        listener.pipeline().addLast(newReader());
        listener.closeFuture().addListener(v -> close());
    }

    @Override
    protected void doRegister() {
        //share same loop between io channel and server channel
        eventLoop().register(listener);
    }

    @Override
    protected void doDeregister() {
        listener.deregister();
    }

    protected void doBind(SocketAddress local) {
        if (localAddress != null) {
            throw new IllegalStateException("already bound");
        }
        if (!open) {
            throw new IllegalStateException("already closed");
        }
        localAddress = local;
        listener.bind(local);
    }

    public Config config() {
        return config;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isActive() {
        return isOpen() && isRegistered();
    }

    protected void doClose() {
        open = false;
        listener.close();
    }

    protected void doBeginRead() {

    }

    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    protected SocketAddress localAddress0() {
        return localAddress;
    }

    protected IoReader newReader() {
        return new IoReader();
    }

    protected class IoReader extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            assert UdpServerChannel.this.eventLoop().inEventLoop();
            UdpServerChannel.this.pipeline()
                    .fireChannelRead(msg)
                    .fireChannelReadComplete();
        }
    }

    public class Config extends RakNetConfig {
        protected Config() {
            super(UdpServerChannel.this);
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

}
