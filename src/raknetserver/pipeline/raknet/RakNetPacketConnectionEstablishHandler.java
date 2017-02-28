package raknetserver.pipeline.raknet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.raknet.RakNetAlreadyConnected;
import raknetserver.packet.raknet.RakNetConnectionReply1;
import raknetserver.packet.raknet.RakNetConnectionReply2;
import raknetserver.packet.raknet.RakNetConnectionRequest1;
import raknetserver.packet.raknet.RakNetConnectionRequest2;
import raknetserver.packet.raknet.RakNetEncapsulatedData;
import raknetserver.packet.raknet.RakNetInvalidVersion;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetUnconnectedPing;
import raknetserver.packet.raknet.RakNetUnconnectedPong;
import raknetserver.packet.raknet.RakNetReliability.RakNetACK;
import raknetserver.packet.raknet.RakNetReliability.RakNetNACK;
import raknetserver.utils.PacketHandlerRegistry;

public class RakNetPacketConnectionEstablishHandler extends SimpleChannelInboundHandler<RakNetPacket> {

	private static final PacketHandlerRegistry<RakNetPacketConnectionEstablishHandler, RakNetPacket> registry = new PacketHandlerRegistry<>();
	static {
		registry.register(RakNetConnectionRequest1.class, (ctx, handler, packet) -> handler.handleConnectionRequest1(ctx, packet));
		registry.register(RakNetConnectionRequest2.class, (ctx, handler, packet) -> handler.handleConnectionRequest2(ctx, packet));
		registry.register(RakNetUnconnectedPing.class, (ctx, handler, packet) -> handler.handlePing(ctx, packet));
		registry.register(RakNetEncapsulatedData.class, (ctx, handler, packet) -> handler.fireNext(ctx, packet));
		registry.register(RakNetACK.class, (ctx, handler, packet) -> handler.fireNext(ctx, packet));
		registry.register(RakNetNACK.class, (ctx, handler, packet) -> handler.fireNext(ctx, packet));
	}

	private final PingHandler pinghandler;
	public RakNetPacketConnectionEstablishHandler(PingHandler pinghandler) {
		this.pinghandler = pinghandler;
	}

	private State state = State.NEW;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, RakNetPacket packet) throws Exception {
		registry.handle(ctx, this, packet);
	}

	protected void handleConnectionRequest1(ChannelHandlerContext ctx, RakNetConnectionRequest1 connectionRequest1) {
		if (connectionRequest1.getRakNetProtocolVersion() == RakNetInvalidVersion.VALID_VERSION) {
			ctx.writeAndFlush(new RakNetConnectionReply1(connectionRequest1.getMtu())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} else {
			ctx.writeAndFlush(new RakNetInvalidVersion()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

	protected long guid;

	protected void handleConnectionRequest2(ChannelHandlerContext ctx, RakNetConnectionRequest2 connectionRequest2) {
		long nguid = connectionRequest2.getGUID();
		if (state == State.NEW) {
			state = State.CONNECTED;
			guid = nguid;
			ctx.channel().attr(RakNetConstants.MTU).set(connectionRequest2.getMtu());
			ctx.writeAndFlush(new RakNetConnectionReply2(connectionRequest2.getMtu())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} else {
			//if guid matches then it means that reply2 packet didn't arrive to the clients
			//otherwise it means that it is actually a new client connecting using already taken ip+port
			if (guid == nguid) {
				ctx.writeAndFlush(new RakNetConnectionReply2(ctx.channel().attr(RakNetConstants.MTU).get())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			} else {
				ctx.writeAndFlush(new RakNetAlreadyConnected()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			}
		}
	}

	//Always allow retrieving server info, but close channel if that was a new connection
	protected void handlePing(ChannelHandlerContext ctx, RakNetUnconnectedPing unconnectedPing) {
		String info = pinghandler.getServerInfo(ctx.channel());
		if (state == State.NEW) {
			ctx.writeAndFlush(new RakNetUnconnectedPong(unconnectedPing.getClientTime(), info)).addListener(ChannelFutureListener.CLOSE);
		} else {
			ctx.writeAndFlush(new RakNetUnconnectedPong(unconnectedPing.getClientTime(), info)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

	protected void fireNext(ChannelHandlerContext ctx, RakNetPacket packet) {
		if (state != State.CONNECTED) {
			throw new IllegalStateException("Can't handle packet " + packet.getClass() + ", connection is not established yet");
		}
		ctx.fireChannelRead(packet);
	}

	protected static enum State {
		NEW, CONNECTED
	}

	public static interface PingHandler {

		public String getServerInfo(Channel channel);

	}

}
