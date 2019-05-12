package network.ycc.raknet.client;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.client.pipeline.ConnectionInitializer;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.pipeline.FlushTickHandler;
import network.ycc.raknet.pipeline.RawPacketCodec;

public class RakNetClient {

    public static class DefaultInitializer extends ChannelInitializer<RakNetClientChannel> {
        public static final DefaultInitializer INSTANCE = new DefaultInitializer();
        protected void initChannel(RakNetClientChannel channel) {
            channel.pipeline()
            .addLast(FlushTickHandler.NAME,      new FlushTickHandler())
            .addLast(RawPacketCodec.NAME,           RawPacketCodec.INSTANCE)
            .addLast(RakNet.ReliableFrameHandling.INSTANCE)
            .addLast(ConnectionInitializer.NAME, new ChannelInboundHandlerAdapter()) //replace later
            .addLast(RakNet.PacketHandling.INSTANCE);
        }
    }

}
