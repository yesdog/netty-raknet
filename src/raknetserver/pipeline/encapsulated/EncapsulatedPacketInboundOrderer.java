package raknetserver.pipeline.encapsulated;

import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
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
	protected void decode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) {
		if (packet.getReliability() == 3) {
			channels[packet.getOrderChannel()].decodeOrdered(packet, list);
		} else {
			list.add(Unpooled.wrappedBuffer(packet.getData()));
		}
	}

	protected static class OrderedChannelPacketQueue {

		protected final Int2ObjectOpenHashMap<EncapsulatedPacket> queue = new Int2ObjectOpenHashMap<>();
		protected int lastReceivedIndex = -1;

		protected void decodeOrdered(EncapsulatedPacket packet, List<Object> list) {
			final int indexDiff = UINT.B3.minusWrap(packet.getOrderIndex(), lastReceivedIndex);
			if (indexDiff == 1) { //got next packet in line
				do { //process this packet, and any queued packets following in sequence
					lastReceivedIndex = packet.getOrderIndex();
					list.add(Unpooled.wrappedBuffer(packet.getData()));
					packet = queue.remove(UINT.B3.plus(packet.getOrderIndex(), 1));
				} while (packet != null);
			} else if (indexDiff > 1) { // only future data goes in the queue
				queue.put(packet.getOrderIndex(), packet);
			}
			if (queue.size() > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (missed ordered packets)");
			}
		}

	}

}
