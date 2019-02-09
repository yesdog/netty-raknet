package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketInboundOrderer;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketOutboundOrder;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketSplitter;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketUnsplitter;
import raknetserver.pipeline.internal.InternalPacketDecoder;
import raknetserver.pipeline.internal.InternalPacketEncoder;
import raknetserver.pipeline.internal.InternalPacketReadHandler;
import raknetserver.pipeline.internal.InternalPacketWriteHandler;
import raknetserver.pipeline.internal.InternalTickManager;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler.PingHandler;
import raknetserver.pipeline.raknet.RakNetPacketDecoder;
import raknetserver.pipeline.raknet.RakNetPacketEncoder;
import raknetserver.pipeline.raknet.RakNetPacketReliabilityHandler;
import raknetserver.utils.Constants;
import raknetserver.utils.DefaultMetrics;
import udpserversocketchannel.channel.UdpServerChannel;

public class RakNetServer {

	public static final AttributeKey<Integer> MTU = AttributeKey.valueOf("MTU");
	public static final AttributeKey<Integer> USER_DATA_ID = AttributeKey.valueOf("USER_DATA_ID");

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

	/* TODO:
	 userPacketId to channel attribute
	 InternalUserData converted to something generic for all unknown raknet packets
	 move tick manager to top/first
	 API for sending data with: specific raknet packet ID, reliability, channel
	 constants to channel attrs? still default to env vars with defaults?
	*/
	public void start() {
		ServerBootstrap bootstrap = new ServerBootstrap()
		.group(new DefaultEventLoopGroup())
		.channelFactory(() -> new UdpServerChannel(Constants.UDP_IO_THREADS))
		.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel channel) {
				channel.attr(RakNetServer.USER_DATA_ID).set(userPacketId);
				channel.pipeline()
				.addLast("rns-timeout", new ReadTimeoutHandler(10))
				.addLast("rns-rn-encoder", new RakNetPacketEncoder())
				.addLast("rns-rn-decoder", new RakNetPacketDecoder())
				.addLast("rns-rn-connect", new RakNetPacketConnectionEstablishHandler(pinghandler))
				.addLast("rns-rn-reliability", new RakNetPacketReliabilityHandler(metrics))
				.addLast("rns-e-ru", new EncapsulatedPacketUnsplitter())
				.addLast("rns-e-ro", new EncapsulatedPacketInboundOrderer())
				.addLast("rns-e-ws", new EncapsulatedPacketSplitter())
				.addLast("rns-e-wo", new EncapsulatedPacketOutboundOrder())
				.addLast("rns-i-encoder", new InternalPacketEncoder())
				.addLast("rns-i-decoder", new InternalPacketDecoder())
				.addLast("rns-i-writeh", new InternalPacketWriteHandler())
				.addLast("rns-i-readh", new InternalPacketReadHandler());
				userinit.init(channel);
				channel.pipeline().addLast("rns-i-tick", new InternalTickManager());
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

	public enum BackPressure {
		ON, OFF
	}

	public interface UserChannelInitializer {
		void init(Channel channel);
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
