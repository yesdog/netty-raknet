package raknetserver.pipeline.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import raknetserver.packet.internal.InternalPacket;
import raknetserver.packet.internal.InternalPacketRegistry;
import raknetserver.packet.internal.InternalUserData;

public class InternalPacketEncoder extends MessageToByteEncoder<InternalPacket> {

	private final int userPacketId;
	public InternalPacketEncoder(int userPacketId) {
		this.userPacketId = userPacketId;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, InternalPacket packet, ByteBuf buf) {
		int packetId = packet instanceof InternalUserData ? userPacketId : InternalPacketRegistry.getId(packet);
		buf.writeByte(packetId);
		packet.encode(buf);
	}

}
