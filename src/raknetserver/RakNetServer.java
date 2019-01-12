package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketInboundOrderer;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketOutboundOrder;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketSplitter;
import raknetserver.pipeline.encapsulated.EncapsulatedPacketUnsplitter;
import raknetserver.pipeline.internal.InternalPacketDecoder;
import raknetserver.pipeline.internal.InternalPacketEncoder;
import raknetserver.pipeline.internal.InternalPacketReadHandler;
import raknetserver.pipeline.internal.InternalPacketWriteHandler;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler.PingHandler;
import raknetserver.pipeline.raknet.RakNetPacketDecoder;
import raknetserver.pipeline.raknet.RakNetPacketEncoder;
import raknetserver.pipeline.raknet.RakNetPacketReliabilityHandler;
import raknetserver.utils.Constants;
import raknetserver.utils.DefaultMetrics;
import udpserversocketchannel.channel.UdpServerChannel;

public class RakNetServer {

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
				channel.pipeline()
				.addLast("rns-timeout", new ReadTimeoutHandler(10))
				.addLast("rns-rn-encoder", new RakNetPacketEncoder())
				.addLast("rns-rn-decoder", new RakNetPacketDecoder())
				.addLast("rns-rn-connect", new RakNetPacketConnectionEstablishHandler(pinghandler))
				.addLast("rns-rn-reliability", new RakNetPacketReliabilityHandler(channel, metrics))
				.addLast("rns-e-ru", new EncapsulatedPacketUnsplitter())
				.addLast("rns-e-ro", new EncapsulatedPacketInboundOrderer())
				.addLast("rns-e-ws", new EncapsulatedPacketSplitter())
				.addLast("rns-e-wo", new EncapsulatedPacketOutboundOrder())
				.addLast("rns-i-encoder", new InternalPacketEncoder(userPacketId))
				.addLast("rns-i-decoder", new InternalPacketDecoder(userPacketId))
				.addLast("rns-i-readh", new InternalPacketReadHandler())
				.addLast("rns-i-writeh", new InternalPacketWriteHandler());
				userinit.init(channel);
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

	public interface UserChannelInitializer {
		void init(Channel channel);
	}

	public interface Metrics {
		void incrOutPacket(int n);
		void incrInPacket(int n);
		void incrJoin(int n);
		void incrSend(int n);
		void incrResend(int n);
		void incrAckSend(int n);
		void incrNackSend(int n);
		void incrAckRecv(int n);
		void incrNackRecv(int n);
	}

}
