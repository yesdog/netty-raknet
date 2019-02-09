package raknetserver.pipeline.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetPacketRegistry;

public class RakNetPacketEncoder extends MessageToByteEncoder<RakNetPacket> {

	@Override
	protected void encode(ChannelHandlerContext ctx, RakNetPacket packet, ByteBuf bytebuf) {
		bytebuf.writeByte(RakNetPacketRegistry.getId(packet));
		packet.encode(bytebuf);
	}

}
