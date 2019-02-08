package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;

public class EncapsulatedPacket extends AbstractReferenceCounted {

	//TODO: flag statics

	protected int reliability;
	protected boolean hasSplit;

	protected int messageIndex;

	protected int orderChannel;
	protected int orderIndex;

	protected int splitCount;
	protected int splitID;
	protected int splitIndex;

	protected ByteBuf data = null; //assumed reference held

	public EncapsulatedPacket() {
	}

	public EncapsulatedPacket(ByteBuf data, int messageIndex, int orderChannel, int orderIndex) {
		this.data = data;
		this.reliability = 3;
		this.messageIndex = messageIndex;
		this.orderChannel = orderChannel;
		this.orderIndex = orderIndex;
	}

	public EncapsulatedPacket(ByteBuf data, int messageIndex, int orderChannel, int orderIndex, int splitID, int splitCount, int splitIndex) {
		this(data, messageIndex, orderChannel, orderIndex);
		this.hasSplit = true;
		this.splitID = splitID;
		this.splitCount = splitCount;
		this.splitIndex = splitIndex;
	}

	@Override
	protected void deallocate() {
		if (data != null) {
			data.release();
			data = null;
		}
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		return this;
	}

	@Override
	public void finalize() throws Throwable {
		if (data != null) {
			System.err.println(String.format("EncapsulatedPacket data leak length: %s, reliability: %s, class: %s", data.readableBytes(), reliability, getClass()));
		}
		super.finalize();
	}

	public ByteBuf retainedData() {
		return data.retainedDuplicate();
	}

	public EncapsulatedPacket retain() {
		return (EncapsulatedPacket) super.retain();
	}

	public void decode(ByteBuf buf) {
		final int flags = buf.readUnsignedByte();
		final int bitLength = buf.readUnsignedShort();
		final int length = (bitLength + 7) / 8; //round up

		reliability = flags >> 5;
		hasSplit = (flags & 0x10) != 0;

		if (reliability > 0) {
			if (reliability >= 2 && reliability != 5) {
				messageIndex = buf.readUnsignedMediumLE();
			}
			if (reliability <= 4 && reliability != 2) {
				orderIndex = buf.readUnsignedMediumLE();
				orderChannel = buf.readUnsignedByte();
			}
		}

		if (hasSplit) {
			splitCount = buf.readInt();
			splitID = buf.readUnsignedShort();
			splitIndex = buf.readInt();
		}

		assert data == null;
		data = buf.readRetainedSlice(length);
	}

	public void encode(ByteBuf buf) {
		buf.writeByte((reliability << 5) | (hasSplit ? 0x10 : 0));
		buf.writeShort(data.readableBytes() * 8);

		if (reliability > 0) {
			if (reliability >= 2 && reliability != 5) {
				buf.writeMediumLE(messageIndex);
			}
			if (reliability <= 4 && reliability != 2) {
				buf.writeMediumLE(orderIndex);
				buf.writeByte(orderChannel);
			}
		}

		if (hasSplit) {
			buf.writeInt(splitCount);
			buf.writeShort(splitID);
			buf.writeInt(splitIndex);
		}

		data.markReaderIndex();
		buf.writeBytes(data);
		data.resetReaderIndex();
	}

	public int getReliability() {
		return reliability;
	}

	public int getMessageIndex() {
		return messageIndex;
	}

	public int getOrderChannel() {
		return orderChannel;
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
		return data.readableBytes();
	}

	public int getRoughPacketSize() {
		return getDataSize() + 18;
	}

}
