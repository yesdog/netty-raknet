package network.ycc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

import network.ycc.raknet.channel.RakNetUDPChannel;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.server.RakNetServer;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.Assert.*;

public class EndToEndTest {
    final EventLoopGroup ioGroup = RakNetServer.DEFAULT_CHANNEL_EVENT_GROUP.get();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();
    final InetSocketAddress localhost = new InetSocketAddress("localhost", 31745);

    @Test
    public void serverCloseTest() throws InterruptedException {
        for (int i = 0 ; i < 20 ; i++) {
            newServer(null, null).close().sync();
        }
    }

    @Test
    public void connectAndCloseTest() throws InterruptedException {
        for (int i = 0 ; i < 20 ; i++) {
            Channel server = newServer(null, null);
            Channel client = newClient(null);

            server.close().sync();
            client.close().sync();
        }
    }

    @Test
    public void singleBufferTest() throws InterruptedException {
        int bytesSent = 1000;
        AtomicInteger bytesRecvd = new AtomicInteger(0);

        Channel server = newServer(null, simpleHandler((ctx, msg) -> {
            if (msg instanceof ByteBuf) {
                bytesRecvd.addAndGet(((ByteBuf) msg).readableBytes());
            }
        }));
        Channel client = newClient(null);

        client.pipeline().write(Unpooled.wrappedBuffer(new byte[bytesSent]));
        client.pipeline().flush();

        Thread.sleep(1000); //give pings time to run

        server.close().sync();
        client.close().sync();

        Assert.assertEquals(bytesSent, bytesRecvd.get());
    }

    @Test
    public void manyBufferTest() throws InterruptedException {
        dataTest(100, 5000, false, false);
    }

    @Test
    public void manyBufferBadClient() throws InterruptedException {
        dataTest(1000, 1000, false, false);
    }

    @Test
    public void manyBufferBadServer() throws InterruptedException {
        dataTest(1000, 1000, true, true);
    }

    @Test
    public void manyBufferBadBoth() throws InterruptedException {
        dataTest(1000, 1000, true, true);
    }

    public void dataTest(int nSent, int maxSize, boolean brutalizeWrite, boolean brutalizeRead) throws InterruptedException {
        Random rnd = new Random(345983678);
        int bytesSent = 0;
        AtomicInteger bytesRecvd = new AtomicInteger(0);
        PromiseCombiner combiner = new PromiseCombiner();

        Channel server = newServer(null, simpleHandler((ctx, msg) -> {
            if (msg instanceof ByteBuf) {
                bytesRecvd.addAndGet(((ByteBuf) msg).readableBytes());
            }
        }));
        Channel client = newClient(null);
        Brutalizer brutalizer = new Brutalizer();
        client.pipeline().addBefore(RakNetUDPChannel.LISTENER_HANDLER_NAME, "brutalizer", brutalizer);
        brutalizer.rnd = rnd;
        brutalizer.brutalizeRead = brutalizeRead;
        brutalizer.brutalizeWrite = brutalizeWrite;

        for (int i = 0 ; i < nSent ; i++) {
            int size = rnd.nextInt(maxSize);
            combiner.add(
                    client.pipeline().write(Unpooled.wrappedBuffer(new byte[size])));
            bytesSent += size;
        }
        client.pipeline().flush();

        try {
            ChannelPromise donePromise = client.newPromise();
            combiner.finish(donePromise);
            donePromise.await(15, TimeUnit.SECONDS);
            Assert.assertEquals(bytesSent, bytesRecvd.get());
        } finally {
            server.close().sync();
            client.close().sync();
        }
    }

    public Channel newServer(ChannelInitializer ioInit, ChannelInitializer childInit) throws InterruptedException {
        if (ioInit == null) ioInit = new EmptyInit();
        if (childInit == null) childInit = new EmptyInit();
        final ServerBootstrap bootstrap = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channelFactory(() -> new RakNetServer(RakNetServer.DEFAULT_CHANNEL_CLASS))
        .option(UnixChannelOption.SO_REUSEPORT, true)
        .option(RakNet.SERVER_ID, UUID.randomUUID().getMostSignificantBits())
        .childOption(RakNet.USER_DATA_ID, 0xFE)
        .handler(ioInit)
        .childHandler(childInit);
        return bootstrap.bind(localhost).sync().channel();
    }

    public Channel newClient(ChannelInitializer init) throws InterruptedException {
        if (init == null) init = new EmptyInit();
        final Bootstrap bootstrap = new Bootstrap()
        .group(ioGroup)
        .channelFactory(() -> new RakNetClientChannel(RakNetServer.DEFAULT_CHANNEL_CLASS))
        .option(RakNet.USER_DATA_ID, 0xFE)
        .handler(init);
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

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (brutalizeWrite) {
                if (rnd.nextDouble() < lossPercent) {
                    ReferenceCountUtil.release(msg);
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
                if (rnd.nextDouble() < lossPercent) {
                    ReferenceCountUtil.release(msg);
                    return;
                }
                if (rnd.nextDouble() < dupePercent) {
                    super.channelRead(ctx, ReferenceCountUtil.retain(msg));
                }
            }
            super.channelRead(ctx, msg);
        }
    }

    public static class EmptyInit extends ChannelInitializer {
        protected void initChannel(Channel ch) throws Exception { }
    }
}
