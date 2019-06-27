package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

public class Ping extends SimpleFramedPacket {

    protected long timestamp;

    public Ping() {
        timestamp = System.nanoTime();
        reliability = Reliability.UNRELIABLE;
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeLong(timestamp);
    }

    @Override
    public void decode(ByteBuf buf) {
        timestamp = buf.readLong();
    }

    public long getTimestamp() {
        return timestamp;
    }

}