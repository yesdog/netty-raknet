package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import raknetserver.utils.UINT;

import java.util.ArrayList;

public class Reliability extends SimplePacket implements Packet {

	private REntry[] entries;

    private Reliability() {
	}

    private Reliability(IntSortedSet ids) {
		entries = new REntry[0];
		if (ids.isEmpty()) {
			return;
		}
		ArrayList<REntry> res = new ArrayList<>();
		int startId = -1;
		int endId = -1;
		for (int i : ids) {
			if (startId == -1) {
				startId = i; //new region
				endId = i;
			} else if (i == UINT.B3.plus(endId, 1)) {
				endId = i; //continue region
			} else {
				res.add(new REntry(startId, endId));
				startId = i; //new region
				endId = i;
			}
		}
		res.add(new REntry(startId, endId));
		entries = res.toArray(entries);
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

    //TODO: iterator

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

	public static class ACK extends Reliability {
		public ACK() {
		}
		public ACK(IntSortedSet ids) {
			super(ids);
		}
	}

	public static class NACK extends Reliability {
		public NACK() {
		}
		public NACK(IntSortedSet ids) {
			super(ids);
		}
	}

}
