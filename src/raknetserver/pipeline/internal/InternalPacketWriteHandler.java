package raknetserver.pipeline.internal;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.RakNetServer;
import raknetserver.packet.internal.InternalPacketData;

public class InternalPacketWriteHandler extends MessageToMessageEncoder<ByteBuf> {

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> list) {
		if (!buf.isReadable()) {
			return;
		}
		//TODO: default order channel?
		final Integer userDataId = ctx.channel().attr(RakNetServer.USER_DATA_ID).get();
		if (userDataId != null) {
			list.add(InternalPacketData.createRead(userDataId.intValue(), buf));
		}
	}

}
