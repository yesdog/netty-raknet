package network.ycc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.util.concurrent.PromiseCombiner;

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
    public void dataTest1() throws InterruptedException {
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

        Thread.sleep(3000);

        server.close().sync();
        client.close().sync();

        Assert.assertEquals(bytesSent, bytesRecvd.get());
    }

    @Test
    public void dataTest2() throws InterruptedException {
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

        for (int i = 0 ; i < 100 ; i++) {
            int size = rnd.nextInt(5000);
            combiner.add(
                    client.pipeline().write(Unpooled.wrappedBuffer(new byte[size])));
            bytesSent += size;
        }
        client.pipeline().flush();

        try {
            ChannelPromise donePromise = client.newPromise();
            combiner.finish(donePromise);
            donePromise.await(5, TimeUnit.SECONDS);
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

    public static class EmptyInit extends ChannelInitializer {
        protected void initChannel(Channel ch) throws Exception { }
    }
}
