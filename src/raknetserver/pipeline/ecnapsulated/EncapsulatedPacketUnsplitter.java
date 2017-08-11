package raknetserver.pipeline.ecnapsulated;

import java.util.HashMap;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.utils.Utils;

public class EncapsulatedPacketUnsplitter extends MessageToMessageDecoder<EncapsulatedPacket> {

	private final HashMap<Integer, SplittedPacket> notFullPackets = new HashMap<>();

	@Override
	protected void decode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) throws Exception {
		if (!packet.hasSplit()) {
			list.add(packet);
		} else {
			int splitID = packet.getSplitId();
			SplittedPacket partial = notFullPackets.get(splitID);
			if (partial == null) {
				notFullPackets.put(splitID, new SplittedPacket(packet));
			} else {
				partial.appendData(packet);
				if (partial.isComplete()) {
					notFullPackets.remove(splitID);
					list.add(partial.getFullPacket());
				}
			}
		}
	}

	private static final class SplittedPacket {

		private static final long maxSplits = 512;

		private int receivedSplits = 0;
		private final EncapsulatedPacket startpacket;
		private final byte[][] packets;

		public SplittedPacket(EncapsulatedPacket startpacket) {
			if (startpacket.getSplitCount() > maxSplits) {
				throw new IllegalStateException("Too many splits for single packet, max: " + maxSplits + ", packet: " + startpacket.getSplitCount());
			}
			this.startpacket = startpacket;
			this.packets = new byte[startpacket.getSplitCount()][];
			this.packets[0] = startpacket.getData();
		}

		public void appendData(EncapsulatedPacket packet) {
			if (packets[packet.getSplitIndex()] != null) {
				return;
			}
			receivedSplits++;
			packets[packet.getSplitIndex()] = packet.getData();
		}

		public boolean isComplete() {
			return (packets.length - receivedSplits) == 1;
		}

		public EncapsulatedPacket getFullPacket() {
			return new EncapsulatedPacket(Utils.readBytes(Unpooled.wrappedBuffer(packets)), 0, startpacket.getOrderChannel(), startpacket.getOrderIndex());
		}

	}

}
