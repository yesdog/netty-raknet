package raknetserver.pipeline.ecnapsulated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import raknetserver.packet.EncapsulatedPacket;

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
			channels[packet.getOrderChannel()].getOrdered(packet).forEach(opacket -> list.add(opacket.getData()));
		} else {
			list.add(packet.getData());
		}
	}

	private static class OrderedChannelPacketQueue {

		private final HashMap<Integer, EncapsulatedPacket> queue = new HashMap<>(300);
		private int lastReceivedIndex = -1;
		private int lastOrderedIndex = -1;
	
		public Collection<EncapsulatedPacket> getOrdered(EncapsulatedPacket epacket) {
			if (queue.size() > 256) {
				throw new DecoderException("Too big packet loss");
			}
			int messageIndex = epacket.getOrderIndex();
			//duplicate packet, ignore it
			if (messageIndex <= lastOrderedIndex) {
				return Collections.emptyList();
			}
			//some packets were lost, put packet in queue and wait
			if ((messageIndex - lastReceivedIndex) > 1) {
				queue.put(messageIndex, epacket);
				lastReceivedIndex = messageIndex;
				return Collections.emptyList();
			}
			//no packets were lost since last received, we have two cases
			//1st - no missing packets - add packet to list
			//2nd - have missing packets - put packet in queue
			if ((messageIndex - lastReceivedIndex) == 1) {
				lastReceivedIndex = messageIndex;
				if (queue.isEmpty()) {
					lastOrderedIndex = lastReceivedIndex;
					return Collections.singletonList(epacket);
				} else {
					queue.put(messageIndex, epacket);
					return Collections.emptyList();
				}
			}
			//duplicate packet, ignore it
			if (queue.containsKey(messageIndex)) {
				return  Collections.emptyList();
			}
			//we received a missing packet, put packet in queue
			queue.put(messageIndex, epacket);
			//return as much ordered packets as we can
			ArrayList<EncapsulatedPacket> ordered = new ArrayList<>();
			EncapsulatedPacket foundPacket = null;
			while ((foundPacket = queue.remove(lastOrderedIndex + 1)) != null) {
				ordered.add(foundPacket);
				lastOrderedIndex++;
			}
			return ordered;
		}

	}

}
