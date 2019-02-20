package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import raknetserver.pipeline.*;
import raknetserver.utils.Constants;
import udpchannel.UdpServerChannel;

public class RakNetServer {

	public static final AttributeKey<Integer> MTU = AttributeKey.valueOf("RN_MTU");
	public static final AttributeKey<Long> RTT = AttributeKey.valueOf("RN_RTT");
	public static final AttributeKey<Integer> USER_DATA_ID = AttributeKey.valueOf("RN_USER_DATA_ID");
	public static final AttributeKey<MetricsLogger> RN_METRICS = AttributeKey.valueOf("RN_METRICS");

	protected final InetSocketAddress local;
	protected final PingHandler pinghandler;
	protected final UserChannelInitializer userinit;
	protected final int userPacketId;
	protected final MetricsLogger metrics;

	private ChannelFuture channel = null;

	public RakNetServer(InetSocketAddress local, PingHandler pinghandler, UserChannelInitializer init, int userPacketId, MetricsLogger metrics) {
		this.local = local;
		this.pinghandler = pinghandler;
		this.userinit = init;
		this.userPacketId = userPacketId;
		this.metrics = metrics;
	}

	public RakNetServer(InetSocketAddress local, PingHandler pinghandler, UserChannelInitializer init, int userPacketId) {
		this(local, pinghandler, init, userPacketId, MetricsLogger.DEFAULT);
	}

	public void start() {
		ServerBootstrap bootstrap = new ServerBootstrap()
		.group(new DefaultEventLoopGroup())
		.channelFactory(() -> new UdpServerChannel(Constants.UDP_IO_THREADS))
		.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel channel) {
			    final FlushTickDriver tickDriver = new FlushTickDriver();
				channel.attr(RakNetServer.USER_DATA_ID).set(userPacketId);
				channel.attr(RakNetServer.RN_METRICS).set(metrics);
				channel.pipeline()
				.addLast("rn-timeout",        new ReadTimeoutHandler(10))
                .addLast(FlushTickDriver.NAME_IN,   tickDriver.inboundHandler)
				.addLast(PacketEncoder.NAME,        new PacketEncoder())
				.addLast(PacketDecoder.NAME,        new PacketDecoder())
				.addLast(ConnectionHandler.NAME,    new ConnectionHandler(pinghandler))
				.addLast(ReliabilityHandler.NAME,   new ReliabilityHandler())
				.addLast(FrameJoiner.NAME,          new FrameJoiner())
				.addLast(FrameOrderIn.NAME,         new FrameOrderIn())
				.addLast(FrameSplitter.NAME,        new FrameSplitter())
				.addLast(FrameOrderOut.NAME,        new FrameOrderOut())
				.addLast(WriteHandler.NAME,         new WriteHandler())
				.addLast(ReadHandler.NAME,          new ReadHandler());
				userinit.init(channel);
				channel.pipeline().addLast(
				         FlushTickDriver.NAME_OUT,  tickDriver.outboundHandler);
			}
		});
		channel = bootstrap.bind(local).syncUninterruptibly();
	}

	public void stop() {
		if (channel != null) {
			channel.channel().close();
			channel = null;
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

	public interface UserChannelInitializer {
		void init(Channel channel);
	}

	public interface PingHandler {
		void executeHandler(Runnable runnable);
		String getServerInfo(Channel channel);
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

	public interface Tick {
		int getTicks();
	}

}
