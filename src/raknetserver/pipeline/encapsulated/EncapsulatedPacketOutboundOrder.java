package raknetserver.pipeline.encapsulated;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.utils.UINT;

public class EncapsulatedPacketOutboundOrder extends MessageToMessageEncoder<ByteBuf> {

	protected int nextOrderIndex = 0;

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> list) {
		list.add(new EncapsulatedPacket(buffer.retainedSlice(), 0, 0, getNextOrderIndex()));
	}

	protected int getNextOrderIndex() {
		int orderIndex = nextOrderIndex;
		nextOrderIndex = UINT.B3.plus(nextOrderIndex, 1);
		return orderIndex;
	}

}
