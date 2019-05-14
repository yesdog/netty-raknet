package network.ycc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

import network.ycc.raknet.client.RakNetClient;
import network.ycc.raknet.config.DefaultMagic;
import network.ycc.raknet.packet.InvalidVersion;
import network.ycc.raknet.server.RakNetServer;
import network.ycc.raknet.utils.EmptyInit;

import org.junit.Test;

import java.net.InetSocketAddress;

public class BehaviorTest {
    final EventLoopGroup ioGroup = new NioEventLoopGroup();
    final EventLoopGroup childGroup = new DefaultEventLoopGroup();
    final int port = 31747;
    final InetSocketAddress localhost = new InetSocketAddress("localhost", port);
    final RakNet.Magic badMagic = new DefaultMagic(new byte[16]);

    @Test
    public void connectIPv6() throws Throwable {
        final InetSocketAddress localhostIPv6 = new InetSocketAddress("::1", port);

        final Channel serverChannel = new ServerBootstrap()
                .group(ioGroup, childGroup)
                .channel(RakNetServer.CHANNEL)
                .childHandler(new EmptyInit())
                .bind(localhostIPv6).sync().channel();

        final Channel clientChannel = new Bootstrap()
                .group(ioGroup)
                .channel(RakNetClient.CHANNEL)
                .handler(new EmptyInit())
                .connect(localhostIPv6).sync().channel();

        serverChannel.close().sync();
        clientChannel.close().sync();
    }

    @Test(expected = RakNet.Magic.MagicMismatchException.class)
    public void badMagicClient() throws Throwable {
        final Channel serverChannel = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channel(RakNetServer.CHANNEL)
        .childHandler(new EmptyInit())
        .bind(localhost).sync().channel();

        final ChannelFuture clientConnect = new Bootstrap()
        .group(ioGroup)
        .channel(RakNetClient.CHANNEL)
        .option(RakNet.MAGIC, badMagic)
        .handler(new EmptyInit())
        .connect(localhost);

        final Channel clientChannel = clientConnect.channel();

        try {
            clientConnect.sync();
        } finally {
            serverChannel.close().sync();
            clientChannel.close().sync();
        }
    }

    @Test(expected = RakNet.Magic.MagicMismatchException.class)
    public void badMagicServer() throws Throwable {
        final Channel serverChannel = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channel(RakNetServer.CHANNEL)
        .option(RakNet.MAGIC, badMagic)
        .childHandler(new EmptyInit())
        .bind(localhost).sync().channel();

        final ChannelFuture clientConnect = new Bootstrap()
        .group(ioGroup)
        .channel(RakNetClient.CHANNEL)
        .handler(new EmptyInit())
        .connect(localhost);

        final Channel clientChannel = clientConnect.channel();

        try {
            clientConnect.sync();
        } finally {
            serverChannel.close().sync();
            clientChannel.close().sync();
        }
    }

    @Test(expected = ConnectTimeoutException.class)
    public void badConnect() throws Throwable {
        final Channel serverChannel = new Bootstrap()
        .group(ioGroup)
        .channel(NioDatagramChannel.class)
        .handler(new EmptyInit())
        .bind(localhost).sync().channel();

        final ChannelFuture clientConnect = new Bootstrap()
        .group(ioGroup)
        .channel(RakNetClient.CHANNEL)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
        .handler(new EmptyInit())
        .connect(localhost);

        final Channel clientChannel = clientConnect.channel();

        try {
            clientConnect.sync();
        } finally {
            serverChannel.close().sync();
            clientChannel.close().sync();
        }
    }

    @Test(expected = InvalidVersion.InvalidVersionException.class)
    public void badVersionClient() throws Throwable {
        final Channel serverChannel = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channel(RakNetServer.CHANNEL)
        .childHandler(new EmptyInit())
        .bind(localhost).sync().channel();

        final ChannelFuture clientConnect = new Bootstrap()
        .group(ioGroup)
        .channel(RakNetClient.CHANNEL)
        .option(RakNet.PROTOCOL_VERSION, 1)
        .handler(new EmptyInit())
        .connect(localhost);

        final Channel clientChannel = clientConnect.channel();

        try {
            clientConnect.sync();
        } finally {
            serverChannel.close().sync();
            clientChannel.close().sync();
        }
    }

    @Test(expected = InvalidVersion.InvalidVersionException.class)
    public void badVersionServer() throws Throwable {
        final Channel serverChannel = new ServerBootstrap()
        .group(ioGroup, childGroup)
        .channel(RakNetServer.CHANNEL)
        .option(RakNet.PROTOCOL_VERSION, 1)
        .childHandler(new EmptyInit())
        .bind(localhost).sync().channel();

        final ChannelFuture clientConnect = new Bootstrap()
        .group(ioGroup)
        .channel(RakNetClient.CHANNEL)
        .handler(new EmptyInit())
        .connect(localhost);

        final Channel clientChannel = clientConnect.channel();

        try {
            clientConnect.sync();
        } finally {
            serverChannel.close().sync();
            clientChannel.close().sync();
        }
    }
}
