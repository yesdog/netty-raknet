package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import raknetserver.utils.Utils;
public class EncapsulatedPacket {

	protected int reliability;
	protected boolean hasSplit;

	protected int messageIndex;

	protected int orderChannel;
	protected int orderIndex;

	protected int splitCount;
	protected int splitID;
	protected int splitIndex;

	protected byte[] data;

	public EncapsulatedPacket() {
	}

	public EncapsulatedPacket(ByteBuf data) {
		this.reliability = 0;
		this.data = Utils.readBytes(data);
	}

	public EncapsulatedPacket(ByteBuf data, int splitID, int splitCount, int splitIndex) {
		this(data);
		setSplitInfo(splitID, splitCount, splitIndex);
	}

	public EncapsulatedPacket(ByteBuf data, int messageIndex) {
		this(data);
		this.reliability = 2;
		this.messageIndex = messageIndex;
	}

	public EncapsulatedPacket(ByteBuf data, int messageIndex, int splitID, int splitCount, int splitIndex) {
		this(data, messageIndex);
		setSplitInfo(splitID, splitCount, splitIndex);
	}

	public EncapsulatedPacket(ByteBuf data, int messageIndex, int orderIndex) {
		this(data, messageIndex);
		this.reliability = 3;
		this.orderIndex = orderIndex;
	}

	public EncapsulatedPacket(ByteBuf data, int messageIndex, int orderIndex, int splitID, int splitCount, int splitIndex) {
		this(data, messageIndex, orderIndex);
		setSplitInfo(splitID, splitCount, splitIndex);
	}

	private void setSplitInfo(int splitID, int splitCount, int splitIndex) {
		this.hasSplit = true;
		this.splitID = splitID;
		this.splitCount = splitCount;
		this.splitIndex = splitIndex;
	}

	public void decode(ByteBuf buf) {
		int flags = buf.readByte();
		reliability = (flags & 0b11100000) >> 5;
		hasSplit = (flags & 0b00010000) > 0;

		int length = Utils.divideAndCeilWithBase(buf.readShort(), 8);

		if (reliability > 0) {
			if ((reliability >= 2) && (reliability != 5)) {
				messageIndex = RakNetDataSerializer.readTriad(buf);
			}
			if ((reliability <= 4) && (reliability != 2)) {
				orderIndex = RakNetDataSerializer.readTriad(buf);
				orderChannel = buf.readUnsignedByte();
			}
		}

		if (hasSplit) {
			splitCount = buf.readInt();
			splitID = buf.readShort();
			splitIndex = buf.readInt();
		}

		data = Utils.readBytes(buf, length);
	}

	public void encode(ByteBuf buf) {
		byte flag = 0;
		flag = (byte) (flag | (reliability << 5));
		if (hasSplit) {
			flag = (byte) ((flag & 0xFF) | 0x10);
		}
		buf.writeByte(flag);

		buf.writeShort((data.length << 3) & 0xFFFF);

		if (reliability > 0) {
			if ((reliability >= 2) && (reliability != 5)) {
				RakNetDataSerializer.writeTriad(buf, messageIndex);
			}
			if ((reliability <= 4) && (reliability != 2)) {
				RakNetDataSerializer.writeTriad(buf, orderIndex);
				buf.writeByte(orderChannel);
			}
		}

		if (hasSplit) {
			buf.writeInt(splitCount);
			buf.writeShort(splitID & 0xFFFF);
			buf.writeInt(splitIndex);
		}

		buf.writeBytes(data);
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

	public ByteBuf getData() {
		return Unpooled.wrappedBuffer(data.clone());
	}

}
