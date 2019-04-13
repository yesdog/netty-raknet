package network.ycc.raknet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import network.ycc.raknet.channel.RakNetUDPChannel;
import network.ycc.raknet.client.channel.RakNetClientChannel;
import network.ycc.raknet.pipeline.DisconnectHandler;
import network.ycc.raknet.pipeline.FrameJoiner;
import network.ycc.raknet.pipeline.FrameOrderIn;
import network.ycc.raknet.pipeline.FrameOrderOut;
import network.ycc.raknet.pipeline.FrameSplitter;
import network.ycc.raknet.pipeline.PacketDecoder;
import network.ycc.raknet.pipeline.PacketEncoder;
import network.ycc.raknet.pipeline.PingHandler;
import network.ycc.raknet.pipeline.PongHandler;
import network.ycc.raknet.pipeline.ReadHandler;
import network.ycc.raknet.pipeline.ReliabilityHandler;
import network.ycc.raknet.pipeline.WriteHandler;
import network.ycc.raknet.server.channel.RakNetServerChannel;

public class RakNet {

    public static final Class<? extends RakNetUDPChannel> SERVER_CHANNEL = RakNetServerChannel.class;
    public static final Class<? extends RakNetUDPChannel> CLIENT_CHANNEL = RakNetClientChannel.class;

    public static final AttributeKey<Boolean> WRITABLE = AttributeKey.valueOf("RN_WRITABLE");
    public static final ChannelOption<Long> SERVER_ID = ChannelOption.valueOf("RN_SERVER_ID");
    public static final ChannelOption<MetricsLogger> METRICS = ChannelOption.valueOf("RN_METRICS");
    public static final ChannelOption<Integer> USER_DATA_ID = ChannelOption.valueOf("RN_USER_DATA_ID");
    public static final ChannelOption<Integer> MTU = ChannelOption.valueOf("RN_MTU");
    public static final ChannelOption<Long> RTT = ChannelOption.valueOf("RN_RTT");

    public static final Config config(ChannelHandlerContext ctx) {
        return (Config) ctx.channel().config();
    }

    public static final MetricsLogger metrics(ChannelHandlerContext ctx) {
        return config(ctx).getMetrics();
    }

    public static class PacketCodec extends ChannelInitializer<Channel> {
        public static final PacketCodec INSTANCE = new PacketCodec();

        protected void initChannel(Channel channel) {
            channel.pipeline()
                    .addLast(PacketEncoder.NAME,        PacketEncoder.INSTANCE)
                    .addLast(PacketDecoder.NAME,        PacketDecoder.INSTANCE);
        }
    }

    public static class ReliableFrameHandling extends ChannelInitializer<Channel> {
        public static final ReliableFrameHandling INSTANCE = new ReliableFrameHandling();

        protected void initChannel(Channel channel) {
            channel.pipeline()
                    .addLast(ReliabilityHandler.NAME,   new ReliabilityHandler())
                    .addLast(FrameJoiner.NAME,          new FrameJoiner())
                    .addLast(FrameOrderIn.NAME,         new FrameOrderIn())
                    .addLast(FrameSplitter.NAME,        new FrameSplitter())
                    .addLast(FrameOrderOut.NAME,        new FrameOrderOut());
        }
    }

    public static class PacketHandling extends ChannelInitializer<Channel> {
        public static final PacketHandling INSTANCE = new PacketHandling();

        protected void initChannel(Channel channel) {
            channel.pipeline()
                    .addLast(DisconnectHandler.NAME,    DisconnectHandler.INSTANCE)
                    .addLast(PingHandler.NAME,          PingHandler.INSTANCE)
                    .addLast(PongHandler.NAME,          PongHandler.INSTANCE)
                    .addLast(WriteHandler.NAME,         WriteHandler.INSTANCE)
                    .addLast(ReadHandler.NAME,          ReadHandler.INSTANCE);
        }
    }

    public interface MetricsLogger {
        MetricsLogger DEFAULT = new MetricsLogger() {};

        default void packetsIn(int delta) {}
        default void framesIn(int delta) {}
        default void bytesIn(int delta) {}
        default void packetsOut(int delta) {}
        default void framesOut(int delta) {}
        default void bytesOut(int delta) {}
        default void bytesRecalled(int delta) {}
        default void bytesACKd(int delta) {}
        default void bytesNACKd(int delta) {}
        default void acksSent(int delta) {}
        default void nacksSent(int delta) {}
        default void measureRTTns(long n) {}
        default void measureRTTnsStdDev(long n) {}
        default void measureBurstTokens(int n) {}
    }

    public interface Config extends ChannelConfig {
        MetricsLogger getMetrics();
        void setMetrics(MetricsLogger metrics);
        long getServerId();
        void setServerId(long serverId);
        int getUserDataId();
        void setUserDataId(int userDataId);
        int getMTU();
        void setMTU(int mtu);
        long getRetryDelay();
        void setRetryDelay(long retryDelay);
        long getRTT();
        void setRTT(long rtt);
        long getRTTStdDev();
        void updateRTT(long rttSample);
        int getMaxPendingFrameSets();
        void setMaxPendingFrameSets(int maxPendingFrameSets);
        int getDefaultPendingFrameSets();
        void setDefaultPendingFrameSets(int defaultPendingFrameSets);
    }
}
