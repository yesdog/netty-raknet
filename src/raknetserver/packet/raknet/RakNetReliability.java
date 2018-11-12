package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

import java.util.ArrayList;

public class RakNetReliability implements RakNetPacket {

	private REntry[] entries;

	public RakNetReliability() {
	}
	public RakNetReliability(IntSortedSet ids) {
		ArrayList<REntry> res = new ArrayList<>();
		//lets make our sparse array of ids here
		int startId = -1;
		int endId = -1;
		for(int i : ids) {
			if (startId == -1) {
				startId = i; //new region
				endId = i;
			} else if (i == (endId + 1)) {
				endId++; //continue region
			} else {
				res.add(new REntry(startId, endId));
				startId = i; //new region
				endId = i;
			}
		}
		if (startId != -1) {
			res.add(new REntry(startId, endId));
		}
		entries = res.toArray(new REntry[0]);
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
			if (entry.idStart == entry.idFinish) {
				buf.writeBoolean(true);
				buf.writeMediumLE(entry.idStart);
			} else {
				buf.writeBoolean(false);
				buf.writeMediumLE(entry.idStart);
				buf.writeMediumLE(entry.idFinish);
			}
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
		public RakNetACK(IntSortedSet ids) {
			super(ids);
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
		public RakNetNACK(IntSortedSet ids) {
			super(ids);
		}
		public RakNetNACK(int id) {
			this(id, id);
		}
		public RakNetNACK(int idstart, int idfinish) {
			super(idstart, idfinish);
		}
	}

}
