package network.ycc.raknet.server;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.pipeline.FlushTickHandler;
import network.ycc.raknet.pipeline.RawPacketCodec;
import network.ycc.raknet.server.channel.RakNetServerChannel;
import network.ycc.raknet.server.channel.RakNetChildChannel;
import network.ycc.raknet.server.pipeline.ConnectionInitializer;
import network.ycc.raknet.server.pipeline.ConnectionListener;

public final class RakNetServer {

    public static class DefaultIoInitializer extends ChannelInitializer<RakNetServerChannel> {
        public static final ChannelInitializer<RakNetServerChannel> INSTANCE = new DefaultIoInitializer();

        protected void initChannel(RakNetServerChannel channel) {
            //TODO: blackhole unhandled Datagram messages. respond with disconnect?
            channel.pipeline()
                    .addLast(ConnectionListener.NAME, new ConnectionListener());
        }
    }

    public static class DefaultChildInitializer extends ChannelInitializer<RakNetChildChannel> {
        public static final ChannelInitializer<RakNetChildChannel> INSTANCE = new DefaultChildInitializer();

        protected void initChannel(RakNetChildChannel channel) {
            channel.pipeline()
                    .addLast(FlushTickHandler.NAME,      new FlushTickHandler())
                    .addLast(RawPacketCodec.NAME,           RawPacketCodec.INSTANCE)
                    .addLast(RakNet.ReliableFrameHandling.INSTANCE)
                    .addLast(ConnectionInitializer.NAME, new ChannelInboundHandlerAdapter()) //replace later
                    .addLast(RakNet.PacketHandling.INSTANCE);
        }
    }

}
