package raknetserver.pipeline;

import java.util.Arrays;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import raknetserver.frame.Frame;
import raknetserver.packet.FramedPacket;
import raknetserver.utils.Constants;
import raknetserver.utils.UINT;

public class FrameOrderIn extends MessageToMessageDecoder<Frame> {

	public static final String NAME = "rn-order-in";

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

	protected void decode(ChannelHandlerContext ctx, Frame frame, List<Object> list) {
		if (frame.getReliability().isSequenced) {
			frame.touch("Sequenced");
			channels[frame.getOrderChannel()].decodeSequenced(frame, list);
		} else if (frame.getReliability().isOrdered) {
			frame.touch("Ordered");
			channels[frame.getOrderChannel()].decodeOrdered(frame, list);
		} else {
			frame.touch("No order");
			list.add(frame.decodePacket());
		}
	}

	protected static class OrderedChannelPacketQueue {

		protected final Int2ObjectOpenHashMap<FramedPacket> queue = new Int2ObjectOpenHashMap<>();
		protected int lastOrderIndex = -1;
		protected int lastSequenceIndex = -1;

		protected void decodeSequenced(Frame frame, List<Object> list) {
			if (UINT.B3.minusWrap(frame.getSequenceIndex(), lastSequenceIndex) > 0) {
				lastSequenceIndex = frame.getSequenceIndex();
				//remove earlier packets from queue
				while (UINT.B3.minusWrap(frame.getOrderIndex(), lastOrderIndex) > 1) {
					ReferenceCountUtil.release(queue.remove(lastOrderIndex));
					lastOrderIndex = UINT.B3.plus(lastOrderIndex, 1);
				}
			}
			decodeOrdered(frame, list); //register packet as normal
		}

		protected void decodeOrdered(Frame frame, List<Object> list) {
			final int indexDiff = UINT.B3.minusWrap(frame.getOrderIndex(), lastOrderIndex);
			Constants.packetLossCheck(indexDiff, "ordered difference");
			if (indexDiff == 1) { //got next packet in line
				FramedPacket data = frame.decodePacket();
				do { //process this packet, and any queued packets following in sequence
					list.add(data);
					lastOrderIndex = UINT.B3.plus(lastOrderIndex, 1);
					data = queue.remove(UINT.B3.plus(lastOrderIndex, 1));
				} while (data != null);
			} else if (indexDiff > 1 && !queue.containsKey(frame.getOrderIndex())) {
				// only new future data goes in the queue
				queue.put(frame.getOrderIndex(), frame.decodePacket());
			}
			Constants.packetLossCheck(queue.size(), "missed ordered packets");
		}

		protected void clear() {
			queue.values().forEach(ReferenceCountUtil::release);
			queue.clear();
		}

	}

}
