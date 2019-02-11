package raknetserver.pipeline.encapsulated;

import java.util.Arrays;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		super.handlerRemoved(ctx);
		Arrays.stream(channels).forEach(OrderedChannelPacketQueue::clear);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) {
		if (packet.getReliability().isSequenced) {
			channels[packet.getOrderChannel()].decodeSequenced(packet.retain(), list);
		} else if (packet.getReliability().isOrdered) {
			channels[packet.getOrderChannel()].decodeOrdered(packet.retain(), list);
		} else {
			list.add(packet.retainedPacket());
		}
	}

	protected static class OrderedChannelPacketQueue {

		protected final Int2ObjectOpenHashMap<EncapsulatedPacket> queue = new Int2ObjectOpenHashMap<>();
		protected int lastOrderIndex = -1;
		protected int lastSequenceIndex = -1;

		protected void decodeSequenced(EncapsulatedPacket packet, List<Object> list) {
			if (UINT.B3.minusWrap(packet.getSequenceIndex(), lastSequenceIndex) > 0) {
				lastSequenceIndex = packet.getSequenceIndex();
				//remove earlier packets from queue
				while (UINT.B3.minusWrap(packet.getOrderIndex(), lastOrderIndex) > 1) {
					final EncapsulatedPacket removed = queue.remove(lastOrderIndex);
					if (removed != null) {
						removed.release();
					}
					lastOrderIndex = UINT.B3.plus(lastOrderIndex, 1);
				}
			}
			decodeOrdered(packet, list); //register packet as normal
		}

		protected void decodeOrdered(EncapsulatedPacket packet, List<Object> list) {
			final int indexDiff = UINT.B3.minusWrap(packet.getOrderIndex(), lastOrderIndex);
			if (indexDiff == 1) { //got next packet in line
				do { //process this packet, and any queued packets following in sequence
					lastOrderIndex = packet.getOrderIndex();
					list.add(packet.retainedPacket());
					packet.release();
					packet = queue.remove(UINT.B3.plus(packet.getOrderIndex(), 1));
				} while (packet != null);
			} else if (indexDiff > 1) { // only future data goes in the queue
				queue.put(packet.getOrderIndex(), packet);
			} else {
				packet.release();
			}
			Constants.packetLossCheck(queue.size(), "missed ordered packets");
			Constants.packetLossCheck(indexDiff, "ordered difference");
		}

		protected void clear() {
			queue.values().forEach(EncapsulatedPacket::release);
			queue.clear();
		}

	}

}
