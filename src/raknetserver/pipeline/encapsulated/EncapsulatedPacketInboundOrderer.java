package raknetserver.pipeline.encapsulated;

import java.util.Arrays;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.internal.InternalPacketData;
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
			packet.touch("Sequenced");
			channels[packet.getOrderChannel()].decodeSequenced(packet, list);
		} else if (packet.getReliability().isOrdered) {
			packet.touch("Ordered");
			channels[packet.getOrderChannel()].decodeOrdered(packet, list);
		} else {
			packet.touch("No order");
			list.add(packet.retainedPacket());
		}
	}

	protected static class OrderedChannelPacketQueue {

		protected final Int2ObjectOpenHashMap<InternalPacketData> queue = new Int2ObjectOpenHashMap<>();
		protected int lastOrderIndex = -1;
		protected int lastSequenceIndex = -1;

		protected void decodeSequenced(EncapsulatedPacket packet, List<Object> list) {
			if (UINT.B3.minusWrap(packet.getSequenceIndex(), lastSequenceIndex) > 0) {
				lastSequenceIndex = packet.getSequenceIndex();
				//remove earlier packets from queue
				while (UINT.B3.minusWrap(packet.getOrderIndex(), lastOrderIndex) > 1) {
					ReferenceCountUtil.release(queue.remove(lastOrderIndex));
					lastOrderIndex = UINT.B3.plus(lastOrderIndex, 1);
				}
			}
			decodeOrdered(packet, list); //register packet as normal
		}

		protected void decodeOrdered(EncapsulatedPacket packet, List<Object> list) {
			final int indexDiff = UINT.B3.minusWrap(packet.getOrderIndex(), lastOrderIndex);
			Constants.packetLossCheck(indexDiff, "ordered difference");
			if (indexDiff == 1) { //got next packet in line
				InternalPacketData data = packet.retainedPacket();
				do { //process this packet, and any queued packets following in sequence
					list.add(data);
					lastOrderIndex = UINT.B3.plus(lastOrderIndex, 1);
					data = queue.remove(UINT.B3.plus(lastOrderIndex, 1));
				} while (data != null);
			} else if (indexDiff > 1 && !queue.containsKey(packet.getOrderIndex())) {
				// only new future data goes in the queue
				queue.put(packet.getOrderIndex(), packet.retainedPacket());
			}
			Constants.packetLossCheck(queue.size(), "missed ordered packets");
		}

		protected void clear() {
			queue.values().forEach(ReferenceCountUtil::release);
			queue.clear();
		}

	}

}
