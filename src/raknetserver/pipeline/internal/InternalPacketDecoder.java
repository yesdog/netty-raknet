package raknetserver.pipeline.internal;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import raknetserver.packet.internal.InternalPacket;
import raknetserver.packet.internal.InternalPacketRegistry;
import raknetserver.packet.internal.InternalPacketData;

public class InternalPacketDecoder extends MessageToMessageDecoder<InternalPacketData> {

	protected void decode(ChannelHandlerContext ctx, InternalPacketData inPacket, List<Object> list) {
		//TODO: reliability and order ID ?
		final InternalPacket packet = InternalPacketRegistry.getPacket(inPacket.getPacketId());
		final ByteBuf data = inPacket.retainedData();
		try {
			packet.decode(data);
			if (data.readableBytes() > 0) {
				ReferenceCountUtil.release(packet);
				throw new DecoderException(data.readableBytes() + " bytes left after decoding packet " + packet.getClass());
			}
			list.add(packet);
		} finally {
			data.release();
		}
	}

}
