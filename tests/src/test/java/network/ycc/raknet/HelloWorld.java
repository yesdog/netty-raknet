package network.ycc.raknet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import network.ycc.raknet.client.RakNetClient;
import network.ycc.raknet.pipeline.UserDataCodec;
import network.ycc.raknet.server.RakNetServer;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HelloWorld {

    final EventLoopGroup ioGroup = new NioEventLoopGroup();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();
    final InetSocketAddress localhost = new InetSocketAddress("localhost", 31747);
    final String helloWorld = "Hello world!";

    String resultStr;

    @Test
    public void helloWorld() throws Throwable {
        final Channel serverChannel = new ServerBootstrap()
                .group(ioGroup, childGroup)
                .channel(RakNetServer.CHANNEL)
                .option(RakNet.SERVER_ID,
                        1234567L) //will be set randomly if not specified (optional)
                .childHandler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                resultStr = msg.readCharSequence(msg.readableBytes(),
                                        StandardCharsets.UTF_8).toString();
                                System.out.println(resultStr); //"Hello world!"
                            }
                        });
                    }
                }).bind(localhost).sync().channel();

        final Channel clientChannel = new Bootstrap()
                .group(ioGroup)
                .channel(RakNetClient.CHANNEL)
                .option(RakNet.MTU, 150) //can configure an initial MTU if desired (optional)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        5000) //supports most normal netty ChannelOptions (optional)
                .option(ChannelOption.SO_REUSEADDR, true) //can also set socket options (optional)
                .handler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(UserDataCodec.NAME, new UserDataCodec(0xFE));
                    }
                }).connect(localhost).sync().channel();

        final ByteBuf helloWorldBuf = Unpooled.buffer();
        helloWorldBuf.writeCharSequence(helloWorld, StandardCharsets.UTF_8);
        clientChannel.writeAndFlush(helloWorldBuf).sync();

        serverChannel.close().sync();
        clientChannel.close().sync();

        Assertions.assertEquals(resultStr, helloWorld);
    }

}
