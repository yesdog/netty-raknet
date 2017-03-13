package raknetserver.pipeline.raknet;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetPacketRegistry;

public class RakNetPacketDecoder extends ReplayingDecoder<ByteBuf> {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> list) throws Exception {
		if (!buffer.isReadable()) {
			return;
		}
		RakNetPacket packet = RakNetPacketRegistry.getPacket(buffer.readUnsignedByte());
		packet.decode(buffer);
		list.add(packet);
	}

}
