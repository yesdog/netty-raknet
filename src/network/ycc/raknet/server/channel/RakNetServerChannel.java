package network.ycc.raknet.server.channel;

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

import network.ycc.raknet.channel.RakNetUDPChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RakNetServerChannel extends RakNetUDPChannel implements ServerChannel {

    public static final boolean DEFAULT_CHANNEL_CAN_REUSE;
    public static final Class<? extends DatagramChannel> DEFAULT_CHANNEL_CLASS;
    public static final Supplier<EventLoopGroup> DEFAULT_CHANNEL_EVENT_GROUP;

    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

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

    protected final Map<SocketAddress, RakNetChildChannel> childMap = new HashMap<>();
    protected volatile SocketAddress localAddress = null;

    public RakNetServerChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
    }

    @SuppressWarnings("unchecked")
    protected void doBind(SocketAddress local) {
        localAddress = local;
        listener.bind(local).addListeners(
                ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    protected void doDisconnect() {
        throw new UnsupportedOperationException();
    }

    protected void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    protected ServerHandler newChannelHandler() {
        return new ServerHandler();
    }

    public ChannelMetadata metadata() {
        return METADATA;
    }

    protected SocketAddress localAddress0() {
        return localAddress;
    }

    public SocketAddress remoteAddress() {
        return null;
    }

    protected SocketAddress remoteAddress0() {
        return null;
    }

    protected AbstractUnsafe newUnsafe() {
        return new ServerUnsafe();
    }

    protected final Object filterOutboundMessage(Object msg) {
        throw new UnsupportedOperationException();
    }

    protected final class ServerUnsafe extends AbstractUnsafe {
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
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

    protected class ServerHandler extends ChannelHandler {
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
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            childMap.values().forEach(ch -> ch.pipeline().fireChannelWritabilityChanged());
            ctx.fireChannelWritabilityChanged();
        }
    }

}
