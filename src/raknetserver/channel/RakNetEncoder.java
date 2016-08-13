package raknetserver.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import raknetserver.packet.RakNetPacket;
import raknetserver.packet.RakNetPacketRegistry;

public class RakNetEncoder extends MessageToByteEncoder<RakNetPacket> {

	@Override
	protected void encode(ChannelHandlerContext ctx, RakNetPacket packet, ByteBuf buffer) {
		buffer.writeByte(RakNetPacketRegistry.getId(packet));
		packet.encode(buffer);
	}

}
