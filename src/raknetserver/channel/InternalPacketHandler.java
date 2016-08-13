package raknetserver.channel;

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import raknetserver.packet.InternalPacket;
import raknetserver.packet.InternalPacketRegistry;
import raknetserver.packet.impl.InternalConnectionRequest;
import raknetserver.packet.impl.InternalKeepAlive.InternalPing;
import raknetserver.packet.impl.InternalKeepAlive.InternalPong;
import raknetserver.packet.impl.InternalServerHandshake;

public class InternalPacketHandler extends SimpleChannelInboundHandler<ByteBuf> {

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
		buffer.markReaderIndex();
		InternalPacket ipacket = InternalPacketRegistry.getPacket(buffer.readUnsignedByte());
		if (ipacket == null) {
			buffer.resetReaderIndex();
			ctx.fireChannelRead(buffer.retain());
			return;
		}
		ipacket.decode(buffer);
		if (ipacket instanceof InternalConnectionRequest) {
			sendInternalPacket(ctx, new InternalServerHandshake((InetSocketAddress) ctx.channel().remoteAddress(), ((InternalConnectionRequest) ipacket).getTimeStamp()));
		} else if (ipacket instanceof InternalPing) {
			sendInternalPacket(ctx, new InternalPong(((InternalPing) ipacket).getKeepAlive()));
		}
	}

	private void sendInternalPacket(ChannelHandlerContext ctx, InternalPacket ipacket) {
		ByteBuf buffer = ctx.alloc().buffer();
		buffer.writeByte(InternalPacketRegistry.getId(ipacket));
		ipacket.encode(buffer);
		ctx.writeAndFlush(buffer);
	}

}
