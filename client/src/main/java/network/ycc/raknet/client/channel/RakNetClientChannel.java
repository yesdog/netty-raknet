package network.ycc.raknet.client.channel;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.channel.DatagramChannelProxy;
import network.ycc.raknet.client.RakNetClient;
import network.ycc.raknet.client.pipeline.ConnectionInitializer;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Supplier;

public class RakNetClientChannel extends DatagramChannelProxy {
    protected final ChannelPromise connectPromise;

    public RakNetClientChannel() {
        this(NioDatagramChannel.class);
    }

    public RakNetClientChannel(Supplier<? extends DatagramChannel> ioChannelSupplier) {
        super(ioChannelSupplier);
        connectPromise = newPromise();
        addDefaultPipeline();
    }

    public RakNetClientChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
        connectPromise = newPromise();
        addDefaultPipeline();
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

    protected void addDefaultPipeline() {
        pipeline()
                .addLast(newClientHandler())
                .addLast(RakNetClient.DefaultClientInitializer.INSTANCE);
    }

    protected ChannelHandler newClientHandler() {
        return new ClientHandler();
    }

    protected class ClientHandler extends ChannelDuplexHandler {
        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                SocketAddress localAddress, ChannelPromise promise) {
            try {
                if (!(remoteAddress instanceof InetSocketAddress)) {
                    throw new IllegalArgumentException(
                            "Provided remote address is not an InetSocketAddress");
                }
                if (listener.isActive()) {
                    throw new IllegalStateException("Channel connection already started");
                }
                final ChannelFuture listenerConnect = listener.connect(remoteAddress, localAddress);
                listenerConnect.addListener(udpConnectResult -> {
                    if (udpConnectResult.isSuccess()) {
                        //start connection process
                        pipeline().replace(ConnectionInitializer.NAME, ConnectionInitializer.NAME,
                                new ConnectionInitializer(connectPromise));
                        connectPromise.addListener(rnConnectResult -> {
                            if (!rnConnectResult.isSuccess()) {
                                pipeline.fireExceptionCaught(rnConnectResult.cause());
                                RakNetClientChannel.this.close();
                            }
                        });
                    }
                });
                final PromiseCombiner combiner = new PromiseCombiner(eventLoop());
                combiner.add(listenerConnect);
                combiner.add((ChannelFuture) connectPromise);
                combiner.finish(promise);
            } catch (Exception t) {
                promise.tryFailure(t);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            listener.write(msg, wrapPromise(promise));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof DatagramPacket) {
                try {
                    final DatagramPacket datagram = (DatagramPacket) msg;
                    if (datagram.sender() == null || datagram.sender().equals(remoteAddress())) {
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
