package raknetserver.pipeline.internal;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.internal.InternalPacket;

import java.util.List;

public class InternalPacketEncoder extends MessageToMessageEncoder<InternalPacket> {

	protected void encode(ChannelHandlerContext ctx, InternalPacket msg, List<Object> out) {
		out.add(msg.toInternalPacketData(ctx.alloc()));
	}

}
