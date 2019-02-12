package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import raknetserver.packet.internal.InternalPacket;
import raknetserver.packet.internal.InternalPacketData;
import raknetserver.utils.UINT;

import java.util.List;

//TODO: not really a packet, its a frame
public class EncapsulatedPacket extends AbstractReferenceCounted {

	private static final ResourceLeakDetector leakDetector =
			ResourceLeakDetectorFactory.instance().newResourceLeakDetector(EncapsulatedPacket.class);

	protected static final int SPLIT_FLAG = 0x10;

	public static EncapsulatedPacket read(ByteBuf buf) {
		final EncapsulatedPacket out = createRaw();
		out.decode(buf);
		return out;
	}

	public static EncapsulatedPacket create(InternalPacketData packet) {
		assert !packet.getReliability().isOrdered;
		final EncapsulatedPacket out = createRaw();
		out.packet = packet.retain();
		return out;
	}

	public static EncapsulatedPacket createOrdered(InternalPacketData packet, int orderIndex, int sequenceIndex) {
		assert packet.getReliability().isOrdered;
		final EncapsulatedPacket out = createRaw();
		out.packet = packet.retain();
		out.orderIndex = orderIndex;
		out.sequenceIndex = sequenceIndex;
		return out;
	}

	private static EncapsulatedPacket createRaw() {
		final EncapsulatedPacket out = new EncapsulatedPacket();
		out.reset();
		out.createTracker();
		return out;
	}

	protected boolean hasSplit;

	protected int reliableIndex;
	protected int sequenceIndex;

	protected int orderIndex;

	protected int splitCount;
	protected int splitID;
	protected int splitIndex;

	protected InternalPacketData packet = null;
	protected ResourceLeakTracker<EncapsulatedPacket> tracker = null;

	protected EncapsulatedPacket() {

	}

	@Override
	protected void deallocate() {
		if (packet != null) {
			packet.release();
			packet = null;
		}
		if (tracker != null) {
			tracker.close(this);
			tracker = null;
		}
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		if (tracker != null) {
			tracker.record(hint);
		}
		packet.touch(hint);
		return this;
	}

	private void reset() {
		assert packet == null;
		hasSplit = false;
		reliableIndex = sequenceIndex = orderIndex = splitCount = splitID = splitIndex = 0;
	}

	public EncapsulatedPacket completeFragment(ByteBuf fullData) {
		assert packet.isFragment();
		final EncapsulatedPacket out = createRaw();
		out.reliableIndex = reliableIndex;
		out.sequenceIndex = sequenceIndex;
		out.orderIndex = orderIndex;
		out.packet = InternalPacketData.read(fullData);
		out.packet.setOrderChannel(getOrderChannel());
		out.packet.setReliability(getReliability());
		return out;
	}

	public int fragment(ByteBufAllocator alloc, List<Object> outList, int splitID, int splitSize, int reliableIndex) {
		final CompositeByteBuf data = alloc.compositeDirectBuffer(2).addComponents(
				true, alloc.ioBuffer(1).writeByte(packet.getPacketId()), packet.retainedData());
		try {
			final int splitCount = (data.readableBytes() + splitSize - 1) / splitSize; //round up
			for (int splitIndex = 0; splitIndex < splitCount; splitIndex++) {
				final int length = Math.min(splitSize, data.readableBytes());
				final EncapsulatedPacket out = createRaw();
				out.reliableIndex = reliableIndex;
				out.sequenceIndex = sequenceIndex;
				out.orderIndex = orderIndex;
				out.splitCount = splitCount;
				out.splitID = splitID;
				out.splitIndex = splitIndex;
				out.hasSplit = true;
				out.packet = InternalPacketData.readFragment(data, length);
				out.packet.setOrderChannel(getOrderChannel());
				out.packet.setReliability(getReliability().makeReliable()); //reliable form only
				assert out.packet.isFragment();
				reliableIndex = UINT.B3.plus(reliableIndex, 1);
				outList.add(out);
			}
			assert !data.isReadable();
			return splitCount;
		} finally {
			data.release();
		}
	}

	@SuppressWarnings("unchecked")
	protected void createTracker() {
		assert tracker == null;
		tracker = leakDetector.track(this);
	}

	public InternalPacketData retainedPacket() {
		assert !packet.isFragment();
		return packet.retain();
	}

	public ByteBuf retainedFragmentData() {
		assert packet.isFragment();
		return packet.retainedData();
	}

	public EncapsulatedPacket retain() {
		return (EncapsulatedPacket) super.retain();
	}

	public void decode(ByteBuf buf) {
		final int flags = buf.readUnsignedByte();
		final int bitLength = buf.readUnsignedShort();
		final int length = (bitLength + 7) / 8; //round up
		int orderChannel = 0;

		InternalPacket.Reliability reliability = InternalPacket.Reliability.get(flags >> 5);
		hasSplit = (flags & SPLIT_FLAG) != 0;

		assert !(hasSplit && !reliability.isReliable);

		if (reliability.isReliable) {
			reliableIndex = buf.readUnsignedMediumLE();
		}
		if (reliability.isSequenced) {
			sequenceIndex = buf.readUnsignedMediumLE();
		}
		if (reliability.isOrdered) {
			orderIndex = buf.readUnsignedMediumLE();
			orderChannel = buf.readUnsignedByte();
		}
		if (hasSplit) {
			splitCount = buf.readInt();
			splitID = buf.readUnsignedShort();
			splitIndex = buf.readInt();
		}

		assert packet == null;
		if (hasSplit) {
			packet = InternalPacketData.readFragment(buf, length);
		} else{
			packet = InternalPacketData.read(buf, length);
		}
		packet.setReliability(reliability);
		packet.setOrderChannel(orderChannel);
	}

	public void encode(ByteBuf buf) {
		buf.writeByte((getReliability().code() << 5) | (hasSplit ? SPLIT_FLAG : 0));
		buf.writeShort(packet.getDataSize() * 8);

		assert !(hasSplit && !getReliability().isReliable);

		if (getReliability().isReliable) {
			buf.writeMediumLE(reliableIndex);
		}
		if (getReliability().isSequenced) {
			buf.writeMediumLE(sequenceIndex);
		}
		if (getReliability().isOrdered) {
			buf.writeMediumLE(orderIndex);
			buf.writeByte(getOrderChannel());
		}
		if (hasSplit) {
			buf.writeInt(splitCount);
			buf.writeShort(splitID);
			buf.writeInt(splitIndex);
		}
		final int preWriterIndex = buf.writerIndex();
		if (hasSplit) {
			packet.encode(buf);
		} else {
			packet.encodeFull(buf);
		}
		assert buf.writerIndex() - preWriterIndex == packet.getDataSize();
	}

	public InternalPacket.Reliability getReliability() {
		return packet.getReliability();
	}

	public int getReliableIndex() {
		return reliableIndex;
	}

	public int getSequenceIndex() {
		return sequenceIndex;
	}

	public int getOrderChannel() {
		return packet.getOrderChannel();
	}

	public int getOrderIndex() {
		return orderIndex;
	}

	public boolean hasSplit() {
		return hasSplit;
	}

	public int getSplitId() {
		return splitID;
	}

	public int getSplitIndex() {
		return splitIndex;
	}

	public int getSplitCount() {
		return splitCount;
	}

	public int getDataSize() {
		return packet.getDataSize();
	}

	public int getRoughPacketSize() {
		return getDataSize() + 18;
	}

	public void setReliableIndex(int reliableIndex) {
		this.reliableIndex = reliableIndex;
	}

	public void setSequenceIndex(int sequenceIndex) {
		this.sequenceIndex = sequenceIndex;
	}

}
