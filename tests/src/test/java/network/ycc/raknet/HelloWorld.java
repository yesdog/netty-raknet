package network.ycc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;

import network.ycc.raknet.client.RakNetClient;
import network.ycc.raknet.pipeline.UserDataCodec;
import network.ycc.raknet.server.RakNetServer;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class HelloWorld {

    final EventLoopGroup ioGroup = new NioEventLoopGroup();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();

    final String testString = "Hello world!";
    String resultStr;

    @Test
    public void helloWorld() throws Throwable {
        final InetSocketAddress localhost = new InetSocketAddress("localhost", 31747);

        final ServerBootstrap serverBootstrap = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channel(RakNetServer.CHANNEL)
        .option(RakNet.SERVER_ID, 1234567L)
        .childHandler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                        resultStr = msg.readCharSequence(msg.readableBytes(), StandardCharsets.UTF_8).toString();
                        System.out.println(resultStr);
                    }
                });
            }
        });

        final Bootstrap clientBootstrap = new Bootstrap()
        .group(ioGroup)
        .channel(RakNetClient.CHANNEL)
        .option(RakNet.CLIENT_ID,6789L)
        .handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
            }
        });

        final Channel serverChannel = serverBootstrap.bind(localhost).sync().channel();
        final Channel clientChannel = clientBootstrap.connect(localhost).sync().channel();

        final ByteBuf buf = Unpooled.buffer();
        buf.writeCharSequence(testString, StandardCharsets.UTF_8);
        clientChannel.writeAndFlush(buf).get(5, TimeUnit.SECONDS);

        serverChannel.close().sync();
        clientChannel.close().sync();

        Assert.assertEquals(resultStr, testString);
    }

}
