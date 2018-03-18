package raknetserver.packet.raknet;

import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.EncapsulatedPacket;

public class RakNetEncapsulatedData implements RakNetPacket {

	private int seqId;
	private final ArrayList<EncapsulatedPacket> packets = new ArrayList<EncapsulatedPacket>();

	public RakNetEncapsulatedData() {
	}

	public RakNetEncapsulatedData(EncapsulatedPacket epacket) {
		packets.add(epacket);
	}

	@Override
	public void decode(ByteBuf buf) {
		seqId = buf.readUnsignedMediumLE();
		while (buf.isReadable()) {
			EncapsulatedPacket packet = new EncapsulatedPacket();
			packet.decode(buf);
			packets.add(packet);
		}
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeMediumLE(seqId);
		for (EncapsulatedPacket packet : packets) {
			packet.encode(buf);
		}
	}

	public int getSeqId() {
		return seqId;
	}

	public void setSeqId(int seqId) {
		this.seqId = seqId;
	}

	public ArrayList<EncapsulatedPacket> getPackets() {
		return packets;
	}

}
