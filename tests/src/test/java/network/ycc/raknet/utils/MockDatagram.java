package network.ycc.raknet.utils;

import static org.mockito.Mockito.mock;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.function.Consumer;

public class MockDatagram extends AbstractChannel implements DatagramChannel {
    final static int fixedMTU = 700;
    static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    final InetSocketAddress localAddress;
    final InetSocketAddress remoteAddress;

    final DatagramChannelConfig config = mock(DatagramChannelConfig.class);

    public Consumer<DatagramPacket> writeOut;
    boolean connected = false;
    boolean closed = false;

    public MockDatagram(Channel parent, InetSocketAddress localAddress,
            InetSocketAddress remoteAddress) {
        super(parent);
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                final ByteBuf buf;
                if (msg instanceof ByteBuf) {
                    buf = (ByteBuf) msg;
                } else {
                    buf = ((DatagramPacket) msg).content();
                }
                if (buf.readableBytes() > fixedMTU) {
                    writeOut.accept(new DatagramPacket(buf.readSlice(fixedMTU), remoteAddress,
                            localAddress));
                } else {
                    writeOut.accept(new DatagramPacket(buf, remoteAddress, localAddress));
                }
                promise.trySuccess();
            }
        });
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public InetSocketAddress localAddress() {
        return localAddress0();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return remoteAddress0();
    }

    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress,
                    ChannelPromise promise) {
                connected = true;
                promise.trySuccess();
            }
        };
    }

    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    protected InetSocketAddress localAddress0() {
        return localAddress;
    }

    protected InetSocketAddress remoteAddress0() {
        return remoteAddress;
    }

    protected void doBind(SocketAddress localAddress) {
        connected = true;
    }

    protected void doDisconnect() {

    }

    protected void doClose() {
        closed = true;
    }

    protected void doBeginRead() {

    }

    protected void doWrite(ChannelOutboundBuffer in) {

    }

    public DatagramChannelConfig config() {
        return config;
    }

    public boolean isOpen() {
        return !closed;
    }

    public boolean isActive() {
        return connected;
    }

    public ChannelMetadata metadata() {
        return METADATA;
    }

    public boolean isConnected() {
        return isOpen();
    }

    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return null;
    }

    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise future) {
        return null;
    }

    public ChannelFuture joinGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        return null;
    }

    public ChannelFuture joinGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, ChannelPromise future) {
        return null;
    }

    public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source) {
        return null;
    }

    public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source, ChannelPromise future) {
        return null;
    }

    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        return null;
    }

    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise future) {
        return null;
    }

    public ChannelFuture leaveGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        return null;
    }

    public ChannelFuture leaveGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, ChannelPromise future) {
        return null;
    }

    public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source) {
        return null;
    }

    public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source, ChannelPromise future) {
        return null;
    }

    public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return null;
    }

    public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock, ChannelPromise future) {
        return null;
    }

    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return null;
    }

    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock,
            ChannelPromise future) {
        return null;
    }
}
