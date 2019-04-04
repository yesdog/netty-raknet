package network.ycc.raknet.server.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultConfig;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RakNetChildChannel extends AbstractChannel {

    private static final ChannelMetadata metadata = new ChannelMetadata(false);
    protected final RakNet.Config config;
    protected final InetSocketAddress remoteAddress;

    protected volatile boolean open = true;

    protected RakNetChildChannel(RakNetServerChannel parent, InetSocketAddress remoteAddress) {
        super(parent);
        this.remoteAddress = remoteAddress;
        config = new DefaultConfig(this);
        config.setMetrics(parent.config().getMetrics());
        config.setServerId(parent.config().getServerId());
        pipeline().addLast(new WriteHandler());
    }

    @Override
    public RakNetServerChannel parent() {
        return (RakNetServerChannel) super.parent();
    }

    protected boolean isCompatible(EventLoop eventloop) {
        return true;
    }

    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            public void connect(SocketAddress addr1, SocketAddress addr2, ChannelPromise pr) {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected SocketAddress localAddress0() {
        return parent().localAddress();
    }

    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    protected void doBind(SocketAddress addr) {
        throw new UnsupportedOperationException();
    }

    protected void doDisconnect() {
        close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    protected void doClose() {
        open = false;
    }

    protected void doBeginRead() {}

    protected void doWrite(ChannelOutboundBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWritable() {
        final Boolean result = attr(RakNet.WRITABLE).get();
        return (result == null || result) && parent().isWritable();
    }

    @Override
    public long bytesBeforeUnwritable() {
        return parent().bytesBeforeUnwritable();
    }

    @Override
    public long bytesBeforeWritable() {
        return parent().bytesBeforeWritable();
    }

    public RakNet.Config config() {
        return config;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isActive() {
        return isOpen() && parent().isActive();
    }

    public ChannelMetadata metadata() {
        return metadata;
    }

    protected class WriteHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf) {
                //TODO: want to do real promise resolution here, but is it worth it?
                promise.trySuccess();
                parent().write(new DatagramPacket((ByteBuf) msg, remoteAddress))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                super.write(ctx, msg, promise);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            parent().flush();
        }
    }

}
