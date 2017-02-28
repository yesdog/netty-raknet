package raknetserver.pipeline.internal;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import raknetserver.packet.internal.InternalPacket;
import raknetserver.packet.internal.InternalPacketRegistry;
import raknetserver.packet.internal.InternalUserData;

public class InternalPacketDecoder extends ByteToMessageDecoder {

	private final int userPacketId;
	public InternalPacketDecoder(int userPacketId) {
		this.userPacketId = userPacketId;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> list) throws Exception {
		if (!buf.isReadable()) {
			return;
		}
		int packetId = buf.readUnsignedByte();
		InternalPacket packet = packetId == userPacketId ? new InternalUserData() : InternalPacketRegistry.getPacket(packetId);
		packet.decode(buf);
		if (buf.readableBytes() > 0) {
			throw new DecoderException(buf.readableBytes() + " bytes left after decoding packet " + packet.getClass());
		}
		list.add(packet);
	}

}
