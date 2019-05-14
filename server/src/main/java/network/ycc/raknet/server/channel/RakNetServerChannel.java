package network.ycc.raknet.server.channel;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;

import network.ycc.raknet.channel.ExtendedDatagramChannel;
import network.ycc.raknet.server.RakNetServer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RakNetServerChannel extends ExtendedDatagramChannel implements ServerChannel {

    protected final Map<SocketAddress, RakNetChildChannel> childMap = new HashMap<>();

    public RakNetServerChannel() {
        this(NioDatagramChannel.class);
    }

    public RakNetServerChannel(Supplier<? extends DatagramChannel> ioChannelSupplier) {
        super(ioChannelSupplier);
        addDefaultPipeline();
    }

    public RakNetServerChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
        addDefaultPipeline();
    }

    protected void addDefaultPipeline() {
        pipeline()
        .addLast(newServerHandler())
        .addLast(RakNetServer.DefaultIoInitializer.INSTANCE);
    }

    protected ChannelHandler newServerHandler() {
        return new ServerHandler();
    }

    protected RakNetChildChannel newChild(InetSocketAddress remoteAddress) {
        return new RakNetChildChannel(this, remoteAddress);
    }

    protected class ServerHandler extends ChannelDuplexHandler {
        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                            SocketAddress localAddress, ChannelPromise promise) throws Exception {
            //TODO: session limit check
            try {
                if (localAddress != null && !RakNetServerChannel.this.localAddress().equals(localAddress)) {
                    throw new IllegalArgumentException("Bound localAddress does not match provided " + localAddress);
                }
                if (!(remoteAddress instanceof InetSocketAddress)) {
                    throw new IllegalArgumentException("Provided remote address is not an InetSocketAddress");
                }
                if (!childMap.containsKey(remoteAddress)) {
                    final RakNetChildChannel child = newChild((InetSocketAddress) remoteAddress);
                    child.closeFuture().addListener(v ->
                            eventLoop().execute(() -> childMap.remove(remoteAddress, child))
                    );
                    child.config.setServerId(config.getServerId());
                    pipeline().fireChannelRead(child).fireChannelReadComplete(); //register
                    childMap.put(remoteAddress, child);
                }
                //TODO: tie promise to connection sequence
                promise.trySuccess();
            } catch (Exception e) {
                promise.tryFailure(e);
                throw e;
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof DatagramPacket) {
                try {
                    final DatagramPacket datagram = (DatagramPacket) msg;
                    final Channel child = childMap.get(datagram.sender());
                    if (child != null && child.isOpen() && child.config().isAutoRead()) {
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

