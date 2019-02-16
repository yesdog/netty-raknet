package raknetserver.pipeline;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import raknetserver.frame.Frame;
import raknetserver.packet.PacketData;
import raknetserver.utils.Constants;

public class FrameJoiner extends MessageToMessageDecoder<Frame> {

	public static final String NAME = "rn-join";

	protected final Int2ObjectOpenHashMap<Builder> pendingPackets = new Int2ObjectOpenHashMap<>();

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		super.handlerRemoved(ctx);
		pendingPackets.values().forEach(Builder::release);
		pendingPackets.clear();
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, Frame frame, List<Object> list) {
		if (!frame.hasSplit()) {
			frame.touch("Not split");
			list.add(frame.retain());
		} else {
			final int splitID = frame.getSplitId();
			final Builder partial = pendingPackets.get(splitID);
			frame.touch("Is split");
			if (partial == null) {
				Constants.packetLossCheck(frame.getSplitCount(), "frame join elements");
				pendingPackets.put(splitID, Builder.create(ctx.alloc(), frame));
			} else {
				partial.add(frame);
				if (partial.isDone()) {
					pendingPackets.remove(splitID);
					list.add(partial.finish());
				}
			}
			Constants.packetLossCheck(pendingPackets.size(), "pending frame joins");
		}
	}

	protected static final class Builder {

		protected final Int2ObjectOpenHashMap<ByteBuf> queue;
		protected Frame samplePacket;
		protected CompositeByteBuf data;
		protected int splitIdx;
		protected int orderId;
		protected PacketData.Reliability reliability;

		private static Builder create(ByteBufAllocator alloc, Frame frame) {
			final Builder out = new Builder(frame.getSplitCount());
			out.init(alloc, frame);
			return out;
		}

		private Builder(int size) {
			queue = new Int2ObjectOpenHashMap<>(size);
		}

		void init(ByteBufAllocator alloc, Frame packet) {
			assert data == null;
			splitIdx = 0;
			data = alloc.compositeDirectBuffer(packet.getSplitCount());
			orderId = packet.getOrderChannel();
			reliability = packet.getReliability();
			samplePacket = packet.retain();
			add(packet);
		}

		void add(Frame packet) {
			assert packet.getReliability().equals(samplePacket.getReliability());
			assert packet.getOrderChannel() == samplePacket.getOrderChannel();
			assert packet.getOrderIndex() == samplePacket.getOrderIndex();
			if (!queue.containsKey(packet.getSplitIndex()) && packet.getSplitIndex() >= splitIdx) {
				queue.put(packet.getSplitIndex(), packet.retainedFragmentData());
				update();
			}
			Constants.packetLossCheck(queue.size(), "packet defragment queue");
		}

		void update() {
			ByteBuf fragment;
			while((fragment = queue.remove(splitIdx)) != null) {
				data.addComponent(true, fragment);
				splitIdx++;
			}
		}

		Frame finish() {
			assert isDone();
			assert queue.isEmpty();
			try {
				return samplePacket.completeFragment(data);
			} finally {
				release();
			}
		}

		boolean isDone() {
			assert samplePacket.getSplitCount() >= splitIdx;
			return samplePacket.getSplitCount() == splitIdx;
		}

		void release() {
			if (data != null) {
				data.release();
				data = null;
			}
			if (samplePacket != null) {
				samplePacket.release();
				samplePacket = null;
			}
			queue.values().forEach(ReferenceCountUtil::release);
			queue.clear();
		}

	}

}
