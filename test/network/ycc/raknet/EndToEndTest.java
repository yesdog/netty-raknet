package network.ycc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import network.ycc.raknet.client.RakNetClient;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.server.RakNetServer;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class EndToEndTest {
    final EventLoopGroup ioGroup = RakNetServer.DEFAULT_CHANNEL_EVENT_GROUP.get();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();
    final InetSocketAddress localhost = new InetSocketAddress("localhost", 31745);

    @Test
    public void simpleTest() throws InterruptedException {
        boolean gotData = false;
        Channel server = newServer(null, simpleHandler((ctx, msg) -> {
            System.out.println(msg.getClass());
        }));
        Channel client = newClient(null);

        Thread.sleep(2000);
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
