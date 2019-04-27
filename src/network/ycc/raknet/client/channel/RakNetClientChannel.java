package network.ycc.raknet.client.channel;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.channel.RakNetUDPChannel;
import network.ycc.raknet.client.RakNetClient;
import network.ycc.raknet.client.pipeline.ConnectionInitializer;
import network.ycc.raknet.packet.Disconnect;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

public class RakNetClientChannel extends RakNetUDPChannel {

    protected final ChannelPromise connectPromise;
    protected volatile InetSocketAddress remoteAddress = null;

    public RakNetClientChannel() {
        this(DEFAULT_CHANNEL_CLASS);
    }

    public RakNetClientChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
        connectPromise = newPromise();
        addDefaultPipeline();
    }

    protected void addDefaultPipeline() {
        pipeline().addLast(RakNetClient.DefaultInitializer.INSTANCE);
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
                           //start connection process
                           pipeline().replace(ConnectionInitializer.NAME, ConnectionInitializer.NAME,
                                   new ConnectionInitializer(connectPromise));
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
        pipeline().writeAndFlush(new Disconnect());
        super.doClose();
        connectPromise.tryFailure(new ClosedChannelException());
    }

    @Override
    public boolean isActive() {
        return super.isActive() && connectPromise.isSuccess();
    }

    @Override
    public boolean isWritable() {
        final Boolean result = attr(RakNet.WRITABLE).get();
        return (result == null || result) && super.isWritable();
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
