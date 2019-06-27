package network.ycc.raknet.server;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.pipeline.FlushTickHandler;
import network.ycc.raknet.pipeline.RawPacketCodec;
import network.ycc.raknet.server.channel.RakNetServerChannel;
import network.ycc.raknet.server.pipeline.ConnectionInitializer;
import network.ycc.raknet.server.pipeline.ConnectionListener;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

public final class RakNetServer extends RakNet {

    public static final Class<RakNetServerChannel> CHANNEL = RakNetServerChannel.class;

    public static class DefaultDatagramInitializer extends ChannelInitializer<Channel> {
        public static final ChannelInitializer<Channel> INSTANCE = new DefaultDatagramInitializer();

        protected void initChannel(Channel channel) {
            //TODO: blackhole unhandled Datagram messages. respond with disconnect?
            channel.pipeline()
                    .addLast(ConnectionListener.NAME, new ConnectionListener());
        }
    }

    public static class DefaultChildInitializer extends ChannelInitializer<Channel> {
        public static final ChannelInitializer<Channel> INSTANCE = new DefaultChildInitializer();

        protected void initChannel(Channel channel) {
            channel.pipeline()
                    .addLast(FlushTickHandler.NAME, new FlushTickHandler())
                    .addLast(RawPacketCodec.NAME, RawPacketCodec.INSTANCE)
                    .addLast(ReliableFrameHandling.INSTANCE)
                    .addLast(PacketHandling.INSTANCE)
                    .addLast(ConnectionInitializer.NAME,
                            new ChannelInboundHandlerAdapter()); //will be replaced
        }
    }

}
