package raknetserver.pipeline;

import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import raknetserver.RakNetServer;
import raknetserver.packet.Ping;
import raknetserver.packet.ConnectionFailed;
import raknetserver.packet.ConnectionReply1;
import raknetserver.packet.ConnectionReply2;
import raknetserver.packet.ConnectionRequest1;
import raknetserver.packet.ConnectionRequest2;
import raknetserver.packet.InvalidVersion;
import raknetserver.packet.Packet;
import raknetserver.packet.UnconnectedPing;
import raknetserver.packet.UnconnectedPong;

public class ConnectionHandler extends SimpleChannelInboundHandler<Packet> {

	public static final String NAME = "rn-connect";

	protected final RakNetServer.PingHandler pinghandler;
    protected long guid;
	protected ScheduledFuture<?> pingTask;
    protected State state = State.NEW;

	public ConnectionHandler(RakNetServer.PingHandler pinghandler) {
		this.pinghandler = pinghandler;
	}

	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof ConnectionRequest1) {
			handleConnectionRequest1(ctx, (ConnectionRequest1) packet);
		} else if (packet instanceof ConnectionRequest2) {
			handleConnectionRequest2(ctx, (ConnectionRequest2) packet);
		} else if (packet instanceof UnconnectedPing) {
			handlePing(ctx, (UnconnectedPing) packet);
		} else {
			if (state != State.CONNECTED) {
				throw new IllegalStateException("Can't handle packet " + packet + ", connection is not established yet");
			}
			ctx.fireChannelRead(ReferenceCountUtil.retain(packet));
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (state == State.CONNECTED) {
			ctx.fireExceptionCaught(cause);
		} else if(ctx.channel().isOpen()) {
			ctx.writeAndFlush(new ConnectionFailed()).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (pingTask != null) {
			pingTask.cancel(true);
		}
		super.channelInactive(ctx);
	}

	protected void handleConnectionRequest1(ChannelHandlerContext ctx, ConnectionRequest1 connectionRequest1) {
		if (connectionRequest1.getRakNetProtocolVersion() == InvalidVersion.VALID_VERSION) {
			ctx.writeAndFlush(new ConnectionReply1(connectionRequest1.getMtu())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} else {
			ctx.writeAndFlush(new InvalidVersion()).addListener(ChannelFutureListener.CLOSE);
		}
	}

	protected void handleConnectionRequest2(ChannelHandlerContext ctx, ConnectionRequest2 connectionRequest2) {
		final long nguid = connectionRequest2.getGUID();
		if (state == State.NEW) {
			state = State.CONNECTED;
			guid = nguid;
			Channel channel = ctx.channel();
			channel.attr(RakNetServer.MTU).set(connectionRequest2.getMtu());
			ctx.writeAndFlush(new ConnectionReply2(connectionRequest2.getMtu())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			pingTask = channel.eventLoop().scheduleAtFixedRate(() -> {
				channel.writeAndFlush(new Ping()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			}, 100, 250, TimeUnit.MILLISECONDS);
		} else {
			//if guid matches then it means that reply2 packet didn't arrive to the clients
			//otherwise it means that it is actually a new client connecting using already taken ip+port
			if (guid == nguid) {
				ctx.writeAndFlush(new ConnectionReply2(ctx.channel().attr(RakNetServer.MTU).get())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			} else {
				ctx.writeAndFlush(new ConnectionFailed()).addListener(ChannelFutureListener.CLOSE);
			}
		}
	}

	protected void handlePing(ChannelHandlerContext ctx, UnconnectedPing unconnectedPing) {
		pinghandler.executeHandler(() -> {
			ctx.writeAndFlush(new UnconnectedPong(unconnectedPing.getClientTime(), pinghandler.getServerInfo(ctx.channel()))).addListener(ChannelFutureListener.CLOSE);
		});
	}

	protected enum State {
		NEW, CONNECTED
	}

}
