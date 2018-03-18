package raknetserver.packet;

import io.netty.buffer.ByteBuf;
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

	public EncapsulatedPacket(byte[] data, int messageIndex, int orderChannel, int orderIndex) {
		this.data = data.clone();
		this.reliability = 3;
		this.messageIndex = messageIndex;
		this.orderChannel = orderChannel;
		this.orderIndex = orderIndex;
	}

	public EncapsulatedPacket(byte[] data, int messageIndex, int orderChannel, int orderIndex, int splitID, int splitCount, int splitIndex) {
		this(data, messageIndex, orderChannel, orderIndex);
		this.hasSplit = true;
		this.splitID = splitID;
		this.splitCount = splitCount;
		this.splitIndex = splitIndex;
	}

	public void decode(ByteBuf buf) {
		int flags = buf.readUnsignedByte();
		reliability = (flags & 0b11100000) >> 5;
		hasSplit = (flags & 0b00010000) > 0;

		int length = Utils.divideAndCeilWithBase(buf.readUnsignedShort(), 8);

		if (reliability > 0) {
			if ((reliability >= 2) && (reliability != 5)) {
				messageIndex = buf.readUnsignedMediumLE();
			}
			if ((reliability <= 4) && (reliability != 2)) {
				orderIndex = buf.readUnsignedMediumLE();
				orderChannel = buf.readUnsignedByte();
			}
		}

		if (hasSplit) {
			splitCount = buf.readInt();
			splitID = buf.readUnsignedShort();
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
				buf.writeMediumLE(messageIndex);
			}
			if ((reliability <= 4) && (reliability != 2)) {
				buf.writeMediumLE(orderIndex);
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

	public int getDataSize() {
		return data.length;
	}

	public byte[] getData() {
		return data.clone();
	}

}
