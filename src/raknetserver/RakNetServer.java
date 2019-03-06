package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

import raknet.pipeline.*;
import raknetserver.pipeline.*;
import raknetserver.channel.RakNetChildChannel;
import raknetserver.channel.RakNetServerChannel;

public class RakNetServer extends RakNetServerChannel {

    public static ChannelFuture createSimple(InetSocketAddress listen, ChannelInitializer childInit, ChannelInitializer ioInit) {
        ServerBootstrap bootstrap = new ServerBootstrap()
        .group(RakNetServer.DEFAULT_CHANNEL_EVENT_GROUP.get(), new DefaultEventLoopGroup())
        .channelFactory(() -> new RakNetServer(RakNetServer.DEFAULT_CHANNEL_CLASS))
        .handler(new DefaultIoInitializer(ioInit))
        .childHandler(new DefaultChildInitializer(childInit));
        return bootstrap.bind(listen);
    }

    public static class DefaultIoInitializer extends ChannelInitializer<RakNetServerChannel> {
        final ChannelInitializer ioInit;

        public DefaultIoInitializer(ChannelInitializer childInit) {
            this.ioInit = childInit;
        }

        protected void initChannel(RakNetServerChannel channel) {
            channel.pipeline()
                    .addLast(ConnectionInitializer.NAME, new ConnectionInitializer())
                    .addLast(ioInit);
                    //TODO: blackhole unhandled Datagram messages. respond with disconnect?
        }
    }

    public static class DefaultChildInitializer extends ChannelInitializer<RakNetChildChannel> {
        final ChannelInitializer childInit;

        public DefaultChildInitializer(ChannelInitializer childInit) {
            this.childInit = childInit;
        }

        protected void initChannel(RakNetChildChannel channel) {
            channel.pipeline()
                    .addLast("rn-timeout",        new ReadTimeoutHandler(5))
                    .addLast(PacketEncoder.NAME,        PacketEncoder.INSTANCE)
                    .addLast(PacketDecoder.NAME,        PacketDecoder.INSTANCE)
                    .addLast(ConnectionHandler.NAME,    new ConnectionHandler())
                    .addLast(ReliabilityHandler.NAME,   new ReliabilityHandler())
                    .addLast(FrameJoiner.NAME,          new FrameJoiner())
                    .addLast(FrameOrderIn.NAME,         new FrameOrderIn())
                    .addLast(FrameSplitter.NAME,        new FrameSplitter())
                    .addLast(FrameOrderOut.NAME,        new FrameOrderOut())
                    .addLast(DisconnectHandler.NAME,    DisconnectHandler.INSTANCE)
                    .addLast(PingHandler.NAME,          PingHandler.INSTANCE)
                    .addLast(PongHandler.NAME,          PongHandler.INSTANCE)
                    .addLast(
                         ConnectionRequestHandler.NAME, ConnectionRequestHandler.INSTANCE)
                    .addLast(WriteHandler.NAME,         WriteHandler.INSTANCE)
                    .addLast(ReadHandler.NAME,          ReadHandler.INSTANCE)
                    .addLast(childInit)
                    .addLast(FlushTickHandler.NAME_OUT, new FlushTickHandler());
        }
    }

    public RakNetServer(Class<? extends DatagramChannel> ioChannelType) {
        super(ioChannelType);
    }

}
