package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;

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
				entries[i] = new REntry(buf.readUnsignedMediumLE());
			} else {
				entries[i] = new REntry(buf.readUnsignedMediumLE(), buf.readUnsignedMediumLE());
			}
		}
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeShort(entries.length);
		for (REntry entry : entries) {
			buf.writeBoolean(false);
			buf.writeMediumLE(entry.idStart);
			buf.writeMediumLE(entry.idFinish);
		}
	}

	public REntry[] getEntries() {
		return entries;
	}

	public static class REntry {
		public final int idStart;
		public final int idFinish;
		public REntry(int id) {
			this(id, id);
		}
		public REntry(int idstart, int idfinish) {
			this.idStart = idstart;
			this.idFinish = idfinish;
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
