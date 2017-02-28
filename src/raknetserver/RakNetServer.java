package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import raknetserver.pipeline.ecnapsulated.EncapsulatedPacketReadHandler;
import raknetserver.pipeline.ecnapsulated.EncapsulatedPacketWriteHandler;
import raknetserver.pipeline.internal.InternalPacketDecoder;
import raknetserver.pipeline.internal.InternalPacketEncoder;
import raknetserver.pipeline.internal.InternalPacketReadHandler;
import raknetserver.pipeline.internal.InternalPacketWriteHandler;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler.PingHandler;
import raknetserver.pipeline.raknet.RakNetPacketDecoder;
import raknetserver.pipeline.raknet.RakNetPacketEncoder;
import raknetserver.pipeline.raknet.RakNetPacketReliabilityHandler;
import udpserversocketchannel.channel.NioUdpServerChannel;
import udpserversocketchannel.eventloop.UdpEventLoopGroup;

public class RakNetServer {

	protected final InetSocketAddress local;
	protected final PingHandler pinghandler;
	protected final UserChannelInitializer userinit;
	protected final int userPacketId;

	public RakNetServer(InetSocketAddress local, PingHandler pinghandler, UserChannelInitializer init, int userPacketId) {
		this.local = local;
		this.pinghandler = pinghandler;
		this.userinit = init;
		this.userPacketId = userPacketId;
	}

	private ChannelFuture channel = null;

	public void start() {
		ServerBootstrap bootstrap = new ServerBootstrap()
		.group(new NioEventLoopGroup(), new UdpEventLoopGroup())
		.channel(NioUdpServerChannel.class)
		.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel channel) throws Exception {
				channel.pipeline()
				.addLast(new ReadTimeoutHandler(30))
				.addLast(new RakNetPacketEncoder())
				.addLast(new RakNetPacketDecoder())
				.addLast(new RakNetPacketConnectionEstablishHandler(pinghandler))
				.addLast(new RakNetPacketReliabilityHandler())
				.addLast(new EncapsulatedPacketReadHandler())
				.addLast(new EncapsulatedPacketWriteHandler())
				.addLast(new InternalPacketEncoder(userPacketId))
				.addLast(new InternalPacketDecoder(userPacketId))
				.addLast(new InternalPacketReadHandler())
				.addLast(new InternalPacketWriteHandler());
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

	public static interface UserChannelInitializer {
		public void init(Channel channel);
	}

}
