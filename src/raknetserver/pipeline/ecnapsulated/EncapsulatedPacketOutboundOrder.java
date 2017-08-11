package raknetserver.pipeline.ecnapsulated;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.utils.Utils;

public class EncapsulatedPacketOutboundOrder extends MessageToMessageEncoder<ByteBuf> {

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> list) throws Exception {
		list.add(new EncapsulatedPacket(Utils.readBytes(buffer), 0, 0, getNextOrderIndex()));
	}

	private int currentOrderIndex = 0;
	private int getNextOrderIndex() {
		return currentOrderIndex++ % 16777216;
	}

}
