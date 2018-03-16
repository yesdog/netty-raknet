package raknetserver.pipeline.ecnapsulated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.utils.Constants;
import raknetserver.utils.UINT;

public class EncapsulatedPacketInboundOrderer extends MessageToMessageDecoder<EncapsulatedPacket> {

	private final OrderedChannelPacketQueue[] channels = new OrderedChannelPacketQueue[8];
	{
		for (int i = 0; i < channels.length; i++) {
			channels[i] = new OrderedChannelPacketQueue();
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) throws Exception {
		if (packet.getReliability() == 3) {
			channels[packet.getOrderChannel()].getOrdered(packet).forEach(opacket -> list.add(Unpooled.wrappedBuffer(opacket.getData())));
		} else {
			list.add(Unpooled.wrappedBuffer(packet.getData()));
		}
	}

	//TODO: handle wrap ids
	protected static class OrderedChannelPacketQueue {

		protected static final int HALF_WINDOW = UINT.B3.MAX_VALUE / 2;

		protected final HashMap<Integer, EncapsulatedPacket> queue = new HashMap<>();
		protected int lastReceivedIndex = -1;
		protected int lastOrderedIndex = -1;

		public Collection<EncapsulatedPacket> getOrdered(EncapsulatedPacket epacket) {
			Collection<EncapsulatedPacket> ordered = getOrdered0(epacket);
			if (queue.size() > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (missed ordered packets)");
			}
			return ordered;
		}

		protected Collection<EncapsulatedPacket> getOrdered0(EncapsulatedPacket epacket) {
			int orderIndex = epacket.getOrderIndex();
			int orderIdLastOrderedDiff = UINT.B3.minus(orderIndex, lastOrderedIndex);
			int orderIdLastReceivedDiff = UINT.B3.minus(orderIndex, lastReceivedIndex);
			//ignore duplicate packet
			if ((orderIdLastOrderedDiff == 0) || (orderIdLastOrderedDiff > HALF_WINDOW)) {
				return Collections.emptyList();
			}
			//some packets were lost this time, put packet in queue and wait
			if (orderIdLastReceivedDiff > 1) {
				queue.put(orderIndex, epacket);
				lastReceivedIndex = orderIndex;
				return Collections.emptyList();
			}
			//no packets were lost since last received, we have two cases
			//1st - no missing packets from last time - add packet to list
			//2nd - have missing packets from last time - put packet in queue
			if (orderIdLastReceivedDiff == 1) {
				lastReceivedIndex = orderIndex;
				if (queue.isEmpty()) {
					lastOrderedIndex = lastReceivedIndex;
					return Collections.singletonList(epacket);
				} else {
					queue.put(orderIndex, epacket);
					return Collections.emptyList();
				}
			}
			//ignore duplicate packet
			if (queue.containsKey(orderIndex)) {
				return  Collections.emptyList();
			}
			//we received a missing packet, put packet in queue
			queue.put(orderIndex, epacket);
			//grab as much ordered packets as we can from queue
			ArrayList<EncapsulatedPacket> ordered = new ArrayList<>();
			while (true) {
				int fOrderedIndex = UINT.B3.plus(lastOrderedIndex, 1);
				EncapsulatedPacket foundPacket = queue.remove(fOrderedIndex);
				if (foundPacket == null) {
					break;
				}
				ordered.add(foundPacket);
				lastOrderedIndex = fOrderedIndex;
			}
			return ordered;
		}

	}

}
