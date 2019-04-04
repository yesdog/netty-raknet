package network.ycc.raknet.client.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;
import network.ycc.raknet.channel.RakNetUDPChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RakNetClientChannel extends RakNetUDPChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    protected volatile InetSocketAddress remoteAddress = null;

    public RakNetClientChannel(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
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
                    RakNetClientChannel.this.remoteAddress = (InetSocketAddress) remoteAddress;
                    final PromiseCombiner combiner = new PromiseCombiner();
                    combiner.add(listener.connect(remoteAddress, localAddress));
                    combiner.finish(promise);
                } catch (Throwable t) {
                    promise.tryFailure(t);
                }
            }
        };
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
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            final Object out;
            if (msg instanceof ByteBuf) {
                out = new DatagramPacket((ByteBuf) msg, remoteAddress);
            } else {
                out = msg;
            }
            super.write(ctx, out, promise);
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
