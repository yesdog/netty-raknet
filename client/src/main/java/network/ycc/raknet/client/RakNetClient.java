package network.ycc.raknet.client;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.client.pipeline.ConnectionInitializer;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.pipeline.FlushTickHandler;
import network.ycc.raknet.pipeline.RawPacketCodec;

public class RakNetClient extends RakNet {

    public static final Class<RakNetClientChannel> CHANNEL = RakNetClientChannel.class;

    public static class DefaultClientInitializer extends ChannelInitializer<RakNetClientChannel> {
        public static final DefaultClientInitializer INSTANCE = new DefaultClientInitializer();

        protected void initChannel(RakNetClientChannel channel) {
            channel.pipeline()
            .addLast(FlushTickHandler.NAME,         new FlushTickHandler())
            .addLast(RawPacketCodec.NAME,           RawPacketCodec.INSTANCE)
            .addLast(                               ReliableFrameHandling.INSTANCE)
            .addLast(                               PacketHandling.INSTANCE)
            .addLast(ConnectionInitializer.NAME,    new ChannelInboundHandlerAdapter()); //will be removed
        }
    }

}
