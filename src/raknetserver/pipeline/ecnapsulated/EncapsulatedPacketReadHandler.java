package raknetserver.pipeline.ecnapsulated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import raknetserver.packet.EncapsulatedPacket;

public class EncapsulatedPacketReadHandler extends MessageToMessageDecoder<Collection<EncapsulatedPacket>> {

	@Override
	protected void decode(ChannelHandlerContext ctx, Collection<EncapsulatedPacket> epackets, List<Object> list) throws Exception {
		//order packets or just add based on reliability
		ArrayList<EncapsulatedPacket> orderedPackets = new ArrayList<>();
		for (EncapsulatedPacket epacket : epackets) {
			if ((epacket.getReliability() == 2) || (epacket.getReliability() == 3)) {
				orderedPackets.addAll(getOrdered(epacket));
			} else {
				orderedPackets.add(epacket);
			}
		}
		//unsplit packets
		Collection<EncapsulatedPacket> fullEPackets = getFullPackets(orderedPackets);
		//add buffers
		fullEPackets.forEach(fillEPacket -> list.add(fullEPackets));
	}

	private final HashMap<Integer, EncapsulatedPacket> queue = new HashMap<>(300);
	private int lastReceivedIndex = -1;
	private int lastOrderedIndex = -1;

	public Collection<EncapsulatedPacket> getOrdered(EncapsulatedPacket epacket) {
		int messageIndex = epacket.getMessageIndex();
		if (queue.size() > 256) {
			throw new DecoderException("Too big packet loss");
		}
		//duplicate packet, ignore it, return empty packet list
		if (messageIndex <= lastOrderedIndex) {
			return Collections.emptyList();
		}
		//in case if received index more than last we put packet to queue
		//return empty packet list
		if ((messageIndex - lastReceivedIndex) > 1) {
			queue.put(messageIndex, epacket);
			lastReceivedIndex = messageIndex;
			return Collections.emptyList();
		}
		//in case if received index is after the last one we have to cases:
		//1st - no missing packets - just return list containing this packet
		//2nd - have missing packets - put packet in queue, return empty packet list
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
		//duplicate packet, ignore it, return empty packet list
		if (queue.containsKey(messageIndex)) {
			return Collections.emptyList();
		}
		//add packet to queue
		queue.put(messageIndex, epacket);
		//return as much ordered packets as we can
		ArrayList<EncapsulatedPacket> opackets = new ArrayList<EncapsulatedPacket>();
		EncapsulatedPacket foundPacket = null;
		while ((foundPacket = queue.remove(lastOrderedIndex + 1)) != null) {
			opackets.add(foundPacket);
			lastOrderedIndex++;
		}
		return opackets;
	}

	private final HashMap<Integer, SplittedPacket> notFullPackets = new HashMap<>();

	private Collection<EncapsulatedPacket> getFullPackets(Collection<EncapsulatedPacket> epackets) {
		ArrayList<EncapsulatedPacket> result = new ArrayList<EncapsulatedPacket>();
		for (EncapsulatedPacket epacket : epackets) {
			if (!epacket.hasSplit()) {
				result.add(epacket);
			} else {
				int splitID = epacket.getSplitId();
				SplittedPacket partial = notFullPackets.get(splitID);
				if (partial == null) {
					notFullPackets.put(splitID, new SplittedPacket(epacket));
				} else {
					partial.appendData(epacket);
					if (partial.isComplete()) {
						notFullPackets.remove(splitID);
						result.add(partial.getFullPacket());
					}
				}
			}
		}
		return result;
	}

	private static final class SplittedPacket {

		private static final long maxSplits = 512;

		private int receivedSplits = 0;
		private final EncapsulatedPacket[] packets;

		public SplittedPacket(EncapsulatedPacket startpacket) {
			if (startpacket.getSplitCount() > maxSplits) {
				throw new IllegalStateException("Too many splits for single packet, max: "+maxSplits+", packet: "+startpacket.getSplitCount());
			}
			packets = new EncapsulatedPacket[startpacket.getSplitCount()];
			packets[0] = startpacket;
		}

		public void appendData(EncapsulatedPacket packet) {
			if (packets[packet.getSplitIndex()] != null) {
				return;
			}
			receivedSplits++;
			packets[packet.getSplitIndex()] = packet;
		}

		public boolean isComplete() {
			return (packets.length - receivedSplits) == 1;
		}

		public EncapsulatedPacket getFullPacket() {
			ByteBuf fulldata = Unpooled.buffer();
			for (EncapsulatedPacket bufferpacket : packets) {
				fulldata.writeBytes(bufferpacket.getData());
			}
			return new EncapsulatedPacket(fulldata);
		}

	}

}
