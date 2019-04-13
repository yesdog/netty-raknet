package network.ycc.raknet.client.channel;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;
import network.ycc.raknet.channel.RakNetUDPChannel;
import network.ycc.raknet.client.RakNetClient;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

public class RakNetClientChannel extends RakNetUDPChannel {

    public final ChannelPromise connectPromise;
    protected volatile InetSocketAddress remoteAddress = null;

    public RakNetClientChannel() {
        this(DEFAULT_CHANNEL_CLASS);
    }

    public RakNetClientChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
        connectPromise = this.newPromise();
    }

    protected void addDefaultPipeline() {
        pipeline().addLast(new RakNetClient.DefaultInitializer(connectPromise));
    }

    protected ChannelHandler newChannelHandler() {
        return new ClientHandler();
    }

    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                try {
                    if (!(remoteAddress instanceof InetSocketAddress)) {
                        throw new IllegalArgumentException("Provided remote address is not an InetSocketAddress");
                    }
                    if (listener.isActive()) {
                        throw new IllegalStateException("Channel connection already started");
                    }
                    RakNetClientChannel.this.remoteAddress = (InetSocketAddress) remoteAddress;
                    final ChannelFuture listenerConnect = listener.connect(remoteAddress, localAddress);
                    listenerConnect.addListener(x -> {
                       if (x.isSuccess()) {
                           addDefaultPipeline();
                           connectPromise.addListener(x2 -> {
                               if (!x2.isSuccess()) {
                                   RakNetClientChannel.this.close();
                               }
                           });
                       }
                    });
                    final PromiseCombiner combiner = new PromiseCombiner();
                    combiner.add(listenerConnect);
                    combiner.add((ChannelFuture) connectPromise);
                    combiner.finish(promise);
                } catch (Throwable t) {
                    promise.tryFailure(t);
                }
            }
        };
    }

    @Override
    protected void doClose() {
        super.doClose();
        connectPromise.tryFailure(new ClosedChannelException());
    }

    protected SocketAddress localAddress0() {
        return listener.localAddress();
    }

    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    protected void doBind(SocketAddress local) {
        throw new UnsupportedOperationException();
    }

    protected void doDisconnect() {
        close();
    }

    protected void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    public ChannelMetadata metadata() {
        return METADATA;
    }

    protected class ClientHandler extends ChannelHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            listener.write(msg).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            promise.trySuccess();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof DatagramPacket) {
                try {
                    final DatagramPacket datagram = (DatagramPacket) msg;
                    if (datagram.sender().equals(remoteAddress())) {
                        ctx.fireChannelRead(datagram.content().retain());
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }

}
