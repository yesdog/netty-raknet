package network.ycc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

import network.ycc.raknet.channel.RakNetUDPChannel;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.packet.FramedPacket;
import network.ycc.raknet.frame.FrameData;
import network.ycc.raknet.pipeline.UserDataCodec;
import network.ycc.raknet.server.channel.RakNetServerChannel;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

public class EndToEndTest {
    final static int fixedMTU = 700;

    final EventLoopGroup ioGroup = new NioEventLoopGroup();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();
    final InetSocketAddress localhost = new InetSocketAddress("localhost", 31745);
    final InetSocketAddress localSender = new InetSocketAddress("localhost", 31745);

    @Test
    public void serverCloseTest() throws Throwable {
        for (int i = 0 ; i < 20 ; i++) {
            newServer(null, null, null).close().get();
        }
    }

    @Test
    public void connectAndCloseTest() throws Throwable {
        for (int i = 0 ; i < 20 ; i++) {
            Channel server = newServer(null, null, null);
            Channel client = newClient(null, null);

            server.close().get();
            client.close().get();
        }
    }

    @Test
    public void singleBufferTest() throws Throwable {
        int bytesSent = 1000;
        AtomicInteger bytesRecvd = new AtomicInteger(0);

        Channel server = newServer(null, simpleHandler((ctx, msg) -> {
            if (msg instanceof ByteBuf) {
                bytesRecvd.addAndGet(((ByteBuf) msg).readableBytes());
            }
        }), null);
        Channel client = newClient(null, null);

        client.pipeline().write(Unpooled.wrappedBuffer(new byte[bytesSent]));
        client.pipeline().flush();

        Thread.sleep(1000); //give pings time to run

        server.close().get();
        client.close().get();

        Assert.assertEquals(bytesSent, bytesRecvd.get());
    }

    @Test
    public void manyBufferTest() throws Throwable {
        dataTest(100, 5000, false, false, false);
    }

    @Test
    public void manyBufferBadClient() throws Throwable {
        dataTest(100, 1000, false, false, false);
    }

    @Test
    public void manyBufferBadServer() throws Throwable {
        dataTest(100, 1000, true, true, false);
    }

    @Test
    public void manyBufferBadBoth() throws Throwable {
        dataTest(100, 5000, true, true, false);
    }

    @Test
    public void manyBufferTestMocked() throws Throwable {
        dataTest(1000, 5000, false, false, true);
    }

    @Test
    public void manyBufferBadClientMocked() throws Throwable {
        dataTest(1000, 5000, false, false, true);
    }

    @Test
    public void manyBufferBadServerMocked() throws Throwable {
        dataTest(1000, 5000, true, true, true);
    }

    @Test
    public void manyBufferBadBothMocked() throws Throwable {
        dataTest(2000, 3000, true, true, true);
    }

    @Test
    public void fireGC() {
        System.gc();
    }

    public void dataTest(int nSend, int maxSize, boolean brutalizeWrite, boolean brutalizeRead, boolean mockTransport) throws Throwable {
        Random rnd = new Random(34598);
        AtomicInteger bytesSent = new AtomicInteger(0);
        AtomicInteger bytesRecvd = new AtomicInteger(0);
        AtomicInteger numRecvd = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(0);
        ConcurrentHashMap<Long, Object> unreliableSet = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, Object> reliableSet = new ConcurrentHashMap<>();
        EventLoop testLoop = childGroup.next();
        PromiseCombiner combiner = new PromiseCombiner(testLoop);
        MockDatagramPair mockPair = mockTransport ? new MockDatagramPair() : null;
        Brutalizer brutalizer = new Brutalizer();

        Channel server = newServer(null, simpleHandler((ctx, msg) -> {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                if (buf.readableBytes() == 8) {
                    long value = buf.readLong();
                    reliableSet.remove(value);
                } else {
                    numRecvd.incrementAndGet();
                    bytesRecvd.addAndGet(buf.readableBytes());
                }
            }
        }), mockPair);
        Channel client = newClient(null, mockPair);
        ChannelPromise donePromise = client.newPromise();

        client.pipeline().addAfter(RakNetUDPChannel.LISTENER_HANDLER_NAME, "brutalizer", brutalizer);
        brutalizer.rnd = rnd;
        brutalizer.brutalizeRead = brutalizeRead;
        brutalizer.brutalizeWrite = brutalizeWrite;

        //TODO: server side writes?

        for (int i = 0 ; i < nSend ; i++) {
            int size = rnd.nextInt(maxSize) + 1;
            if (size == 8) size = 9; //reserve 8 size for the other tests
            while (!client.isWritable() || pending.get() > 2046) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                //TODO: deadline check
            }
            ChannelFuture fut;
            switch (rnd.nextInt(2)) {
                case 0:
                    fut = client.pipeline().write(Unpooled.wrappedBuffer(new byte[size]));
                    break;
                default:
                    FrameData data = FrameData.create(client.alloc(), 0xFE, Unpooled.wrappedBuffer(new byte[size]));
                    if (rnd.nextBoolean()) {
                        data.setReliability(FramedPacket.Reliability.RELIABLE_ORDERED);
                        data.setOrderChannel(rnd.nextInt(4));
                    }
                    fut = client.pipeline().write(data);
            }
            testLoop.execute(() -> combiner.add(fut));
            bytesSent.addAndGet(size);
            pending.incrementAndGet();
            fut.addListener(x -> pending.decrementAndGet());
        }

        for (int i = 0 ; i < 200 ; i++) {
            long value = rnd.nextLong();
            reliableSet.put(value, true);
            ByteBuf buf = Unpooled.wrappedBuffer(new byte[8]);
            buf.clear();
            buf.writeLong(value);
            FrameData packet = FrameData.create(client.alloc(), 0xFE, buf);
            packet.setReliability(FramedPacket.Reliability.RELIABLE);
            testLoop.execute(() -> combiner.add(client.pipeline().write(packet)));
        }

        client.pipeline().flush();

        testLoop.execute(() -> combiner.finish(donePromise));

        //TODO: new test loop for UNRELIABLE + RELIABLE

        try {
            donePromise.get(15, TimeUnit.SECONDS);
            Thread.sleep(1000);
        } finally {
            server.close().get();
            client.close().get();
        }
        System.gc();
        Assert.assertTrue(reliableSet.isEmpty());
        Assert.assertEquals(0, pending.get());
        Assert.assertEquals(nSend, numRecvd.get());
        Assert.assertEquals(bytesSent.get(), bytesRecvd.get());
    }

    public Channel newServer(ChannelInitializer ioInit, final ChannelInitializer childInit, MockDatagramPair dgPair) throws InterruptedException {
        if (ioInit == null) ioInit = new EmptyInit();
        final ServerBootstrap bootstrap = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channelFactory(() -> new RakNetServerChannel(() -> {
            if (dgPair != null) {
                return dgPair.server;
            } else {
                return new NioDatagramChannel();
            }
        }))
        .option(RakNet.SERVER_ID, 12345L)
        .handler(ioInit)
        .childHandler(new ChannelInitializer() {
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                if (childInit != null) {
                    ch.pipeline().addLast(childInit);
                }
            }
        });
        return bootstrap.bind(localhost).sync().channel();
    }

    public Channel newClient(ChannelInitializer init, MockDatagramPair dgPair) throws InterruptedException {
        final Bootstrap bootstrap = new Bootstrap()
        .group(ioGroup)
        .channelFactory(() -> new RakNetClientChannel(() -> {
            if (dgPair != null) {
                return dgPair.client;
            } else {
                return new NioDatagramChannel();
            }
        }))
        .option(RakNet.CLIENT_ID,6789L)
        .handler(new ChannelInitializer() {
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                if (init != null) {
                    ch.pipeline().addLast(init);
                }
            }
        });
        return bootstrap.connect(localhost).sync().channel();
    }

    public static ChannelInitializer simpleHandler(BiConsumer<ChannelHandlerContext, Object> func) {
        return new ChannelInitializer() {
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        func.accept(ctx, msg);
                    }
                });
            }
        };
    }

    public static class Brutalizer extends ChannelDuplexHandler {
        Random rnd;
        boolean brutalizeWrite = false;
        boolean brutalizeRead = false;
        double lossPercent = 0.20;
        double dupePercent = 0.20;
        double orderPercent = 0.20;

        Object writeStash = null;
        Object readStash = null;

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            ReferenceCountUtil.safeRelease(writeStash);
            ReferenceCountUtil.safeRelease(readStash);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (brutalizeWrite) {
                if (rnd.nextDouble() < orderPercent && writeStash != null) {
                    ctx.write(writeStash);
                    writeStash = null;
                }
                if (rnd.nextDouble() < lossPercent) {
                    if (rnd.nextDouble() < orderPercent) {
                        writeStash = msg;
                    } else {
                        ReferenceCountUtil.release(msg);
                    }
                    promise.trySuccess();
                    return;
                }
                if (rnd.nextDouble() < dupePercent) {
                    super.write(ctx, ReferenceCountUtil.retain(msg), ctx.voidPromise());
                }
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (brutalizeRead) {
                if (rnd.nextDouble() < orderPercent && readStash != null) {
                    ctx.write(readStash);
                    readStash = null;
                }
                if (rnd.nextDouble() < lossPercent) {
                    if (rnd.nextDouble() < orderPercent) {
                        readStash = msg;
                    } else {
                        ReferenceCountUtil.release(msg);
                    }
                    return;
                }
                if (rnd.nextDouble() < dupePercent) {
                    super.channelRead(ctx, ReferenceCountUtil.retain(msg));
                }
            }
            super.channelRead(ctx, msg);
        }
    }

    public class MockDatagramPair {
        final MockDatagram server = new MockDatagram(null, localhost, localSender);
        final MockDatagram client = new MockDatagram(null, localSender, localhost);

        {
            server.writeOut = dg -> client.pipeline().fireChannelRead(dg).fireChannelReadComplete();
            client.writeOut = dg -> server.pipeline().fireChannelRead(dg).fireChannelReadComplete();
        }
    }

    public static class MockDatagram extends AbstractChannel implements DatagramChannel {
        static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

        final InetSocketAddress localAddress;
        final InetSocketAddress remoteAddress;

        final DatagramChannelConfig config = mock(DatagramChannelConfig.class);

        Consumer<DatagramPacket> writeOut;
        boolean connected = false;
        boolean closed = false;

        public MockDatagram(Channel parent, InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
            super(parent);
            this.localAddress = localAddress;
            this.remoteAddress = remoteAddress;
            pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    ByteBuf buf;
                    if (msg instanceof ByteBuf) {
                        buf = (ByteBuf) msg;
                    } else {
                        buf = ((DatagramPacket) msg).content();
                    }
                    if (buf.readableBytes() > fixedMTU) {
                        writeOut.accept(new DatagramPacket(buf.readSlice(fixedMTU), remoteAddress, localAddress));
                    } else {
                        writeOut.accept(new DatagramPacket(buf, remoteAddress, localAddress));
                    }
                    promise.trySuccess();
                }
            });
        }

        protected AbstractUnsafe newUnsafe() {
            return new AbstractUnsafe() {
                public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                    connected = true;
                    promise.trySuccess();
                }
            };
        }

        protected boolean isCompatible(EventLoop loop) {
            return true;
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

        protected InetSocketAddress localAddress0() {
            return localAddress;
        }

        protected InetSocketAddress remoteAddress0() {
            return remoteAddress;
        }

        protected void doBind(SocketAddress localAddress) throws Exception {
            connected = true;
        }

        protected void doDisconnect() throws Exception {

        }

        protected void doClose() throws Exception {
            closed = true;
        }

        protected void doBeginRead() throws Exception {

        }

        protected void doWrite(ChannelOutboundBuffer in) throws Exception {

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

        public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
            return null;
        }

        public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, ChannelPromise future) {
            return null;
        }

        public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
            return null;
        }

        public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, ChannelPromise future) {
            return null;
        }

        public ChannelFuture leaveGroup(InetAddress multicastAddress) {
            return null;
        }

        public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise future) {
            return null;
        }

        public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
            return null;
        }

        public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, ChannelPromise future) {
            return null;
        }

        public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
            return null;
        }

        public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, ChannelPromise future) {
            return null;
        }

        public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock) {
            return null;
        }

        public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock, ChannelPromise future) {
            return null;
        }

        public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
            return null;
        }

        public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise future) {
            return null;
        }
    }

    public static class EmptyInit extends ChannelInitializer {
        protected void initChannel(Channel ch) throws Exception { }
    }

}
