package raknetserver.udp;

import io.netty.bootstrap.Bootstrap;
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

import io.netty.channel.unix.UnixChannelOption;
import raknetserver.utils.Constants;

import java.net.SocketAddress;
import java.util.function.Function;

public class UdpServerChannel extends AbstractServerChannel {

    public static final int MAX_LISTENERS_PER_PORT;
    public static final Class<? extends DatagramChannel> CHANNEL_CLASS;
    public static final Function<Integer, EventLoopGroup> NEW_EVENT_GROUP;

    static {
        boolean kQueueEnabled = false;
        try {
            kQueueEnabled = KQueue.isAvailable();
        } catch (Throwable e) {}

        if (Epoll.isAvailable()) {
            CHANNEL_CLASS = EpollDatagramChannel.class;
            MAX_LISTENERS_PER_PORT = Constants.UDP_IO_THREADS;
            NEW_EVENT_GROUP = EpollEventLoopGroup::new;
        } else if (kQueueEnabled) {
            CHANNEL_CLASS = KQueueDatagramChannel.class;
            MAX_LISTENERS_PER_PORT = Constants.UDP_IO_THREADS;
            NEW_EVENT_GROUP = KQueueEventLoopGroup::new;
        } else {
            CHANNEL_CLASS = NioDatagramChannel.class;
            MAX_LISTENERS_PER_PORT = 1;
            NEW_EVENT_GROUP = NioEventLoopGroup::new;
        }
    }

    protected final DefaultChannelConfig config = new DefaultChannelConfig(this);
    protected SocketAddress localAddress = null;
    protected Channel listener = null;
    protected volatile boolean open = true;

    protected void doBind(SocketAddress local) {
        localAddress = local;
        //share same loop between io channel and server channel
        final Bootstrap bootstrap = new Bootstrap().group(eventLoop())
                .channel(CHANNEL_CLASS).handler(newReader());
        if (MAX_LISTENERS_PER_PORT > 1) {
            bootstrap.option(UnixChannelOption.SO_REUSEPORT, true);
        }
        listener = bootstrap.bind(local).channel();
        //bind life cycles
        listener.closeFuture().addListener(v -> close());
        closeFuture().addListener(v -> listener.close());
    }

    public ChannelConfig config() {
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

}
