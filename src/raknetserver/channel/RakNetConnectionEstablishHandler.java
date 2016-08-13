package raknetserver.channel;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import raknetserver.UserHandler;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetPacket;
import raknetserver.packet.impl.RakNetAlreadyConnected;
import raknetserver.packet.impl.RakNetConnectionReply1;
import raknetserver.packet.impl.RakNetConnectionReply2;
import raknetserver.packet.impl.RakNetConnectionRequest1;
import raknetserver.packet.impl.RakNetConnectionRequest2;
import raknetserver.packet.impl.RakNetInvalidVersion;
import raknetserver.packet.impl.RakNetUnconnectedPing;
import raknetserver.packet.impl.RakNetUnconnectedPong;

public class RakNetConnectionEstablishHandler extends SimpleChannelInboundHandler<RakNetPacket> {

	private State state = State.NEW;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, RakNetPacket packet) throws Exception {
		if (packet instanceof RakNetConnectionRequest1) {
			handle(ctx, (RakNetConnectionRequest1) packet);
		} else if (packet instanceof RakNetConnectionRequest2) {
			handle(ctx, (RakNetConnectionRequest2) packet);
		} else if (packet instanceof RakNetUnconnectedPing) {
			handle(ctx, (RakNetUnconnectedPing) packet);
		}
		if (state == State.CONNECTED) {
			ctx.fireChannelRead(packet);
		}
	}

	public void handle(ChannelHandlerContext ctx, RakNetConnectionRequest1 connectionRequest1) {
		if (connectionRequest1.getRakNetProtocolVersion() == RakNetInvalidVersion.VALID_VERSION) {
			ctx.writeAndFlush(new RakNetConnectionReply1(connectionRequest1.getMtu())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} else {
			ctx.writeAndFlush(new RakNetInvalidVersion()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

	private long guid;

	public void handle(ChannelHandlerContext ctx, RakNetConnectionRequest2 connectionRequest2) {
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
	public void handle(ChannelHandlerContext ctx, RakNetUnconnectedPing unconnectedPing) {
		String info = ((UserHandler) ctx.pipeline().get(UserHandler.PIPELINE_NAME)).getPingInfo();
		if (state == State.NEW) {
			ctx.writeAndFlush(new RakNetUnconnectedPong(unconnectedPing.getClientTime(), info)).addListener(ChannelFutureListener.CLOSE);
		} else {
			ctx.writeAndFlush(new RakNetUnconnectedPong(unconnectedPing.getClientTime(), info)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

	private static enum State {
		NEW, CONNECTED
	}

}
