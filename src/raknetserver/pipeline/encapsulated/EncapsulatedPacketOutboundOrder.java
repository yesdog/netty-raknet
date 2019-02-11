package raknetserver.pipeline.encapsulated;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.internal.InternalPacketData;
import raknetserver.utils.UINT;

public class EncapsulatedPacketOutboundOrder extends MessageToMessageEncoder<InternalPacketData> {

	protected int[] nextOrderIndex = new int[8];
	protected int[] nextSequenceIndex = new int[8];

	@Override
	protected void encode(ChannelHandlerContext ctx, InternalPacketData data, List<Object> list) {
		if (data.getReliability().isOrdered) {
			final int channel = data.getOrderChannel();
			final int sequenceIndex = data.getReliability().isSequenced ? getNextSequenceIndex(channel) : 0;
			list.add(EncapsulatedPacket.createOrdered(data, getNextOrderIndex(channel), sequenceIndex));
		} else {
			list.add(EncapsulatedPacket.create(data));
		}
	}

	protected int getNextOrderIndex(int channel) {
		final int orderIndex = nextOrderIndex[channel];
		nextOrderIndex[channel] = UINT.B3.plus(nextOrderIndex[channel], 1);
		return orderIndex;
	}

	protected int getNextSequenceIndex(int channel) {
		final int sequenceIndex = nextSequenceIndex[channel];
		nextSequenceIndex[channel] = UINT.B3.plus(nextSequenceIndex[channel], 1);
		return sequenceIndex;
	}

}
