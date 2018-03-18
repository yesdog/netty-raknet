package raknetserver.pipeline.internal;

import java.net.InetSocketAddress;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import raknetserver.packet.internal.InternalClientHandshake;
import raknetserver.packet.internal.InternalConnectionRequest;
import raknetserver.packet.internal.InternalDisconnect;
import raknetserver.packet.internal.InternalPacket;
import raknetserver.packet.internal.InternalPing;
import raknetserver.packet.internal.InternalPong;
import raknetserver.packet.internal.InternalServerHandshake;
import raknetserver.packet.internal.InternalUserData;
import raknetserver.utils.PacketHandlerRegistry;

public class InternalPacketReadHandler extends SimpleChannelInboundHandler<InternalPacket> {

	private static final PacketHandlerRegistry<InternalPacketReadHandler, InternalPacket> registry = new PacketHandlerRegistry<>();
	static {
		registry.register(InternalConnectionRequest.class, (ctx, handler, packet) -> handler.handleConnectionRequest(ctx, packet));
		registry.register(InternalClientHandshake.class, (ctx, handler, packet) -> handler.handleHandshake(ctx, packet));
		registry.register(InternalPing.class, (ctx, handler, packet) -> handler.handlePing(ctx, packet));
		registry.register(InternalPong.class, (ctx, handler, packet) -> handler.handlePong(ctx, packet));
		registry.register(InternalUserData.class, (ctx, handler, packet) -> handler.handleUserData(ctx, packet));
		registry.register(InternalDisconnect.class, (ctx, handler, packet) -> handler.handleDisconnect(ctx, packet));
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, InternalPacket packet) throws Exception {
		registry.handle(ctx, this, packet);
	}

	protected void handleConnectionRequest(ChannelHandlerContext ctx, InternalConnectionRequest packet) {
		ctx.writeAndFlush(new InternalServerHandshake((InetSocketAddress) ctx.channel().remoteAddress(), packet.getTimeStamp()));
	}

	protected void handleHandshake(ChannelHandlerContext ctx, InternalClientHandshake packet) {
	}

	protected void handlePing(ChannelHandlerContext ctx, InternalPing packet) {
		ctx.writeAndFlush(new InternalPong(packet.getTimestamp()));
	}

	protected void handlePong(ChannelHandlerContext ctx, InternalPong packet) {
	}

	protected void handleUserData(ChannelHandlerContext ctx, InternalUserData packet) {
		ctx.fireChannelRead(Unpooled.wrappedBuffer(packet.getData()));
	}

	protected void handleDisconnect(ChannelHandlerContext ctx, InternalDisconnect packet) {
		ctx.channel().close();
	}

}
