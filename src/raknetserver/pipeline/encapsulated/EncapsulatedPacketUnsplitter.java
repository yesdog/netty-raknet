package raknetserver.pipeline.encapsulated;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import raknetserver.packet.EncapsulatedPacket;

public class EncapsulatedPacketUnsplitter extends MessageToMessageDecoder<EncapsulatedPacket> {

	//TODO: stashed reference teardown on remove

	protected final Int2ObjectOpenHashMap<UnSplitPacket> pendingPackets = new Int2ObjectOpenHashMap<>();

	@Override
	protected void decode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) {
		packet.retain();
		if (!packet.hasSplit()) {
			list.add(packet);
		} else {
			final int splitID = packet.getSplitId();
			final UnSplitPacket partial = pendingPackets.get(splitID);
			if (partial == null) {
				pendingPackets.put(splitID, UnSplitPacket.create(ctx.alloc(), packet));
			} else {
				partial.add(packet);
				if (partial.isDone()) {
					pendingPackets.remove(splitID);
					list.add(partial);
				}
			}
		}
	}

	protected static final class UnSplitPacket extends EncapsulatedPacket {

		private static UnSplitPacket create(ByteBufAllocator alloc, EncapsulatedPacket packet) {
			final UnSplitPacket unSplit = new UnSplitPacket();
			unSplit.init(alloc, packet);
			return unSplit;
		}

		private Int2ObjectOpenHashMap<EncapsulatedPacket> queue = new Int2ObjectOpenHashMap<>();
		private int splitIdx = 0;
		private int splitCount = 0;

		private void init(ByteBufAllocator alloc, EncapsulatedPacket packet) {
			assert data == null;
			data = alloc.ioBuffer(2048);
			orderChannel = packet.getOrderChannel();
			orderIndex = packet.getOrderIndex();
			splitCount = packet.getSplitCount();
			reliability = 3;
			messageIndex = 0;
			add(packet);
		}

		private void add(EncapsulatedPacket packet) {
			assert packet.getOrderChannel() == orderChannel;
			assert packet.getOrderIndex() == orderIndex;
			queue.put(packet.getSplitIndex(), packet);
			update();
		}

		private void update() {
			EncapsulatedPacket packet;
			while((packet = queue.remove(splitIdx)) != null) {
				final ByteBuf newData = packet.retainedData();
				try {
					data.writeBytes(newData);
				} finally {
					newData.release();
					packet.release();
				}
				splitIdx++;
			}
		}

		private boolean isDone() {
			return splitIdx == splitCount;
		}

	}

}
