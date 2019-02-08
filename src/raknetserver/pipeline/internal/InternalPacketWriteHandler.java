package raknetserver.pipeline.internal;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.internal.InternalUserData;

public class InternalPacketWriteHandler extends MessageToMessageEncoder<ByteBuf> {

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> list) {
		list.add(InternalUserData.read(buf, ctx.alloc()));
	}

}
