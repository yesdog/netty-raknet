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
import raknetserver.utils.DefaultMetrics;
import udpserversocketchannel.channel.UdpServerChannel;

public class RakNetServer {

	public static final AttributeKey<Integer> MTU = AttributeKey.valueOf("RN_MTU");
	public static final AttributeKey<Long> RTT = AttributeKey.valueOf("RN_RTT");
	public static final AttributeKey<Integer> USER_DATA_ID = AttributeKey.valueOf("RN_USER_DATA_ID");
	public static final AttributeKey<RakNetServer.Metrics> RN_METRICS = AttributeKey.valueOf("RN_METRICS");

	protected final InetSocketAddress local;
	protected final PingHandler pinghandler;
	protected final UserChannelInitializer userinit;
	protected final int userPacketId;
	protected final Metrics metrics;

	private ChannelFuture channel = null;

	public RakNetServer(InetSocketAddress local, PingHandler pinghandler, UserChannelInitializer init, int userPacketId, Metrics metrics) {
		this.local = local;
		this.pinghandler = pinghandler;
		this.userinit = init;
		this.userPacketId = userPacketId;
		this.metrics = metrics;
	}

	public RakNetServer(InetSocketAddress local, PingHandler pinghandler, UserChannelInitializer init, int userPacketId) {
		this(local, pinghandler, init, userPacketId, new DefaultMetrics());
	}

	public void start() {
		ServerBootstrap bootstrap = new ServerBootstrap()
		.group(new DefaultEventLoopGroup())
		.channelFactory(() -> new UdpServerChannel(Constants.UDP_IO_THREADS))
		.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel channel) {
				channel.attr(RakNetServer.USER_DATA_ID).set(userPacketId);
				channel.attr(RakNetServer.RN_METRICS).set(metrics);
				channel.pipeline()
				.addLast("rn-timeout",        new ReadTimeoutHandler(10))
				.addLast(PacketEncoder.NAME,        PacketEncoder.INSTANCE)
				.addLast(PacketDecoder.NAME,        PacketDecoder.INSTANCE)
				.addLast(ConnectionHandler.NAME,    new ConnectionHandler(pinghandler))
				.addLast(ReliabilityHandler.NAME,   new ReliabilityHandler())
				.addLast(FrameJoiner.NAME,          new FrameJoiner())
				.addLast(FrameOrderIn.NAME,         new FrameOrderIn())
				.addLast(FrameSplitter.NAME,        new FrameSplitter())
				.addLast(FrameOrderOut.NAME,        new FrameOrderOut())
				.addLast(WriteHandler.NAME,         WriteHandler.INSTANCE)
				.addLast(ReadHandler.NAME,          new ReadHandler());
				userinit.init(channel);
				channel.pipeline().addLast(
				         FlushTickDriver.NAME,      new FlushTickDriver());
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

	public interface Metrics {
		void incrSend(int n);
		void incrOutPacket(int n);
		void incrRecv(int n);
		void incrInPacket(int n);
		void incrJoin(int n);
		void incrResend(int n);
		void incrAckSend(int n);
		void incrNackSend(int n);
		void incrAckRecv(int n);
		void incrNackRecv(int n);
		void measureSendAttempts(int n);
		void measureRTTns(long n);
	}

	public interface Tick {
		int getTicks();
	}

}
