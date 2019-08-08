package network.ycc.raknet;

import network.ycc.raknet.channel.DatagramChannelProxy;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.config.DefaultCodec;
import network.ycc.raknet.frame.FrameData;
import network.ycc.raknet.packet.FramedPacket;
import network.ycc.raknet.packet.Ping;
import network.ycc.raknet.pipeline.UserDataCodec;
import network.ycc.raknet.server.channel.RakNetServerChannel;
import network.ycc.raknet.utils.EmptyInit;
import network.ycc.raknet.utils.MockDatagram;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.junit.Assert;
import org.junit.Test;

public class EndToEndTest {
    final EventLoopGroup ioGroup = new NioEventLoopGroup();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();
    final InetSocketAddress localhost = new InetSocketAddress("localhost", 31745);
    final InetSocketAddress localSender = new InetSocketAddress("localhost", 31745);

    public static ChannelInitializer<Channel> simpleHandler(
            BiConsumer<ChannelHandlerContext, Object> func) {
        return new ChannelInitializer<Channel>() {
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        func.accept(ctx, msg);
                    }
                });
            }
        };
    }

    @Test
    public void serverCloseTest() throws Throwable {
        for (int i = 0; i < 20; i++) {
            newServer(null, null, null).close().sync();
        }
    }

    @Test
    public void connectAndCloseTestServerFirst() throws Throwable {
        for (int i = 0; i < 20; i++) {
            Channel server = newServer(null, null, null);
            Channel client = newClient(null, null);

            server.close().sync();
            client.close().sync();
        }
    }

    @Test
    public void connectAndCloseTestClientFirst() throws Throwable {
        for (int i = 0; i < 20; i++) {
            Channel server = newServer(null, null, null);
            Channel client = newClient(null, null);

            client.close().sync();
            server.close().sync();
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
            ReferenceCountUtil.safeRelease(msg);
        }), null);
        Channel client = newClient(null, null);

        //add some bad frame data, should be ignore safely
        client.pipeline().fireChannelRead(Unpooled.wrappedBuffer(
                new byte[]{(byte) DefaultCodec.FRAME_DATA_START, 1, 2, 3, 4, 5, 6, 7, 8, 9}));

        client.pipeline().write(Unpooled.wrappedBuffer(new byte[bytesSent]));
        client.pipeline().flush();

        Thread.sleep(1000); //give pings time to run

        server.close().sync();
        client.close().sync();
        System.gc();
        System.gc();

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

    public void dataTest(int nSend, int maxSize, boolean brutalizeWrite, boolean brutalizeRead,
            boolean mockTransport) throws Throwable {
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
            ReferenceCountUtil.safeRelease(msg);
        }), mockPair);
        Channel client = newClient(null, mockPair);
        ChannelPromise donePromise = client.newPromise();

        client.pipeline()
                .addAfter(DatagramChannelProxy.LISTENER_HANDLER_NAME, "brutalizer", brutalizer);
        brutalizer.rnd = rnd;
        brutalizer.brutalizeRead = brutalizeRead;
        brutalizer.brutalizeWrite = brutalizeWrite;

        //TODO: server side writes?

        for (int i = 0; i < nSend; i++) {
            int size = rnd.nextInt(maxSize) + 1;
            if (size == 8) {
                size = 9; //reserve 8 size for the other tests
            }
            while (!client.isWritable() || pending.get() > 3000) {
                Thread.yield();
                //TODO: deadline check
            }
            ChannelFuture fut;
            switch (rnd.nextInt(2)) {
                case 0:
                    fut = client.pipeline().write(Unpooled.wrappedBuffer(new byte[size]));
                    break;
                default:
                    FrameData data = FrameData
                            .create(client.alloc(), 0xFE, Unpooled.wrappedBuffer(new byte[size]));
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

        for (int i = 0; i < 200; i++) {
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
        client.write(new Ping()).sync();

        testLoop.execute(() -> combiner.finish(donePromise));

        //TODO: new test loop for UNRELIABLE + RELIABLE

        try {
            donePromise.get(90, TimeUnit.SECONDS);
        } finally {
            server.close().sync();
            client.close().sync();
        }
        System.gc();
        System.gc();
        System.gc();
        Assert.assertTrue(reliableSet.isEmpty());
        Assert.assertEquals(0, pending.get());
        Assert.assertEquals(nSend, numRecvd.get());
        Assert.assertEquals(bytesSent.get(), bytesRecvd.get());
    }

    public Channel newServer(ChannelInitializer<Channel> ioInit,
            final ChannelInitializer<Channel> childInit, MockDatagramPair dgPair)
            throws InterruptedException {
        if (ioInit == null) {
            ioInit = new EmptyInit();
        }
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
                .option(RakNet.RETRY_DELAY_NANOS,
                        TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS))
                .handler(ioInit)
                .childHandler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                        if (childInit != null) {
                            ch.pipeline().addLast(childInit);
                        }
                    }
                });
        return bootstrap.bind(localhost).sync().channel();
    }

    public Channel newClient(ChannelInitializer<Channel> init, MockDatagramPair dgPair)
            throws InterruptedException {
        final Bootstrap bootstrap = new Bootstrap()
                .group(ioGroup)
                .channelFactory(() -> new RakNetClientChannel(() -> {
                    if (dgPair != null) {
                        return dgPair.client;
                    } else {
                        return new NioDatagramChannel();
                    }
                }))
                .option(RakNet.CLIENT_ID, 6789L)
                .option(RakNet.RETRY_DELAY_NANOS,
                        TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS))
                .handler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                        if (init != null) {
                            ch.pipeline().addLast(init);
                        }
                    }
                });
        return bootstrap.connect(localhost).sync().channel();
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
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                throws Exception {
            if (brutalizeWrite) {
                if (rnd.nextDouble() < orderPercent && writeStash != null) {
                    ctx.write(writeStash);
                    writeStash = null;
                }
                if (rnd.nextDouble() < lossPercent) {
                    if (rnd.nextDouble() < orderPercent) {
                        ReferenceCountUtil.safeRelease(writeStash);
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
                        ReferenceCountUtil.safeRelease(readStash);
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

}
