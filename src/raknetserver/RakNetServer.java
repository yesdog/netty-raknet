package raknetserver;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import raknetserver.channel.EncapsulatedPacketReadHandler;
import raknetserver.channel.EncapsulatedPacketWriteHandler;
import raknetserver.channel.InternalPacketHandler;
import raknetserver.channel.RakNetConnectionEstablishHandler;
import raknetserver.channel.RakNetDecoder;
import raknetserver.channel.RakNetEncoder;
import raknetserver.channel.RakNetReliabilityHandler;
import udpserversocketchannel.channel.NioUdpServerChannel;
import udpserversocketchannel.eventloop.UdpEventLoopGroup;

public class RakNetServer {

	private final InetSocketAddress local;
	private final UserHandler.Factory userHandlerFactory;

	public RakNetServer(InetSocketAddress local, UserHandler.Factory userHandler) {
		this.local = local;
		this.userHandlerFactory = userHandler;
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
				.addLast(new RakNetEncoder())
				.addLast(new RakNetDecoder())
				.addLast(new RakNetConnectionEstablishHandler())
				.addLast(new RakNetReliabilityHandler())
				.addLast(new EncapsulatedPacketWriteHandler())
				.addLast(new EncapsulatedPacketReadHandler())
				.addLast(new InternalPacketHandler())
				.addLast(UserHandler.PIPELINE_NAME, userHandlerFactory.create());
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

}
