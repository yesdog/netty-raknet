package raknetserver.pipeline.internal;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.internal.InternalUserData;
import raknetserver.utils.Utils;

public class InternalPacketWriteHandler extends MessageToMessageEncoder<ByteBuf> {

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> list) throws Exception {
		list.add(new InternalUserData(Utils.readBytes(buf)));
	}

}
