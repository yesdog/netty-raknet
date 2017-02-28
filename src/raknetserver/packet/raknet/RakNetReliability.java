package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetDataSerializer;

public class RakNetReliability implements RakNetPacket {

	private REntry[] entries;

	public RakNetReliability() {
	}

	public RakNetReliability(int id) {
		this(id, id);
	}

	public RakNetReliability(int idstart, int idfinish) {
		entries = new REntry[1];
		entries[0] = new REntry(idstart, idfinish);
	}

	@Override
	public void decode(ByteBuf buf) {
		entries = new REntry[buf.readUnsignedShort()];
		for (int i = 0; i < entries.length; i++) {
			boolean single = buf.readBoolean();
			if (single) {
				entries[i] = new REntry(RakNetDataSerializer.readTriad(buf));
			} else {
				entries[i] = new REntry(RakNetDataSerializer.readTriad(buf), RakNetDataSerializer.readTriad(buf));
			}
		}
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeShort(entries.length);
		for (REntry entry : entries) {
			buf.writeBoolean(false);
			RakNetDataSerializer.writeTriad(buf, entry.idstart);
			RakNetDataSerializer.writeTriad(buf, entry.idfinish);
		}
	}

	public REntry[] getEntries() {
		return entries;
	}

	public static class REntry {
		public final int idstart;
		public final int idfinish;
		public REntry(int id) {
			this(id, id);
		}
		public REntry(int idstart, int idfinish) {
			this.idstart = idstart;
			this.idfinish = idfinish;
		}
	}

	public static class RakNetACK extends RakNetReliability {
		public RakNetACK() {
		}
		public RakNetACK(int id) {
			this(id, id);
		}
		public RakNetACK(int idstart, int idfinish) {
			super(idstart, idfinish);
		}
	}
	public static class RakNetNACK extends RakNetReliability {
		public RakNetNACK() {
		}
		public RakNetNACK(int id) {
			this(id, id);
		}
		public RakNetNACK(int idstart, int idfinish) {
			super(idstart, idfinish);
		}
	}

}
