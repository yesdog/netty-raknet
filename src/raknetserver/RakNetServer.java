package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;

import raknetserver.pipeline.*;
import raknetserver.udp.UdpChildChannel;
import raknetserver.udp.UdpServerChannel;
import raknetserver.udp.UdpServerHandler;

public class RakNetServer {

    //TODO: switch some of these to ChannelOptions
    public static final AttributeKey<Integer> MTU = AttributeKey.valueOf("RN_MTU");
    public static final AttributeKey<Long> RTT = AttributeKey.valueOf("RN_RTT");
    public static final ChannelOption<Long> SERVER_ID = ChannelOption.valueOf("RN_SERVER_ID");
    public static final ChannelOption<Integer> USER_DATA_ID = ChannelOption.valueOf("RN_USER_DATA_ID");
    public static final ChannelOption<MetricsLogger> METRICS = ChannelOption.valueOf("RN_METRICS");

    public static ChannelFuture createSimple(InetSocketAddress listen, ChannelInitializer childInit, ChannelInitializer ioInit) {
        ServerBootstrap bootstrap = new ServerBootstrap()
        .group(UdpServerChannel.DEFAULT_CHANNEL_EVENT_GROUP.get(), new DefaultEventLoopGroup())
        .channelFactory(() -> new UdpServerChannel(UdpServerChannel.DEFAULT_CHANNEL_CLASS))
        .handler(new DefaultIoInitializer(ioInit))
        .childHandler(new DefaultChildInitializer(childInit));
        return bootstrap.bind(listen);
    }

    public static class DefaultIoInitializer extends ChannelInitializer<UdpServerChannel> {
        final ChannelInitializer ioInit;

        public DefaultIoInitializer(ChannelInitializer childInit) {
            this.ioInit = childInit;
        }

        protected void initChannel(UdpServerChannel channel) {
            channel.pipeline()
                    .addLast(new UdpServerHandler())
                    .addLast(ConnectionInitializer.NAME, new ConnectionInitializer())
                    .addLast(ioInit);
        }
    }

    public static class DefaultChildInitializer extends ChannelInitializer<UdpChildChannel> {
        final ChannelInitializer childInit;

        public DefaultChildInitializer(ChannelInitializer childInit) {
            this.childInit = childInit;
        }

        protected void initChannel(UdpChildChannel channel) {
            channel.pipeline()
                    .addLast("rn-timeout",        new ReadTimeoutHandler(5))
                    .addLast(PacketEncoder.NAME,        new PacketEncoder())
                    .addLast(PacketDecoder.NAME,        new PacketDecoder())
                    .addLast(ConnectionHandler.NAME,    new ConnectionHandler())
                    .addLast(ReliabilityHandler.NAME,   new ReliabilityHandler())
                    .addLast(FrameJoiner.NAME,          new FrameJoiner())
                    .addLast(FrameOrderIn.NAME,         new FrameOrderIn())
                    .addLast(FrameSplitter.NAME,        new FrameSplitter())
                    .addLast(FrameOrderOut.NAME,        new FrameOrderOut())
                    .addLast(WriteHandler.NAME,         new WriteHandler())
                    .addLast(ReadHandler.NAME,          new ReadHandler())
                    .addLast(childInit)
                    .addLast(FlushTickHandler.NAME_OUT,  new FlushTickHandler());
        }
    }

    /**
     * Sent down pipeline when backpressure should be
     * applied or removed.
     */
    public enum BackPressure {
        ON, OFF
    }

    /**
     * Influences stream control.
     * SYNC - Send up the pipeline to block new frames until all pending ones are ACKd.
     * TODO: this...
     */
    public enum StreamControl {
        SYNC
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
        default void measureBurstTokens(int n) {}
    }

}
