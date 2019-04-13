package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;
import network.ycc.raknet.utils.DataSerializer;

public class ClientHandshake extends SimpleFramedPacket {

    protected Reliability reliability = Reliability.RELIABLE_ORDERED;

    long pongTimestamp;
    long timestamp;

    public ClientHandshake() {}

    public ClientHandshake(long pongTimestamp) {
        this(pongTimestamp, System.nanoTime());
    }

    public ClientHandshake(long pongTimestamp, long timestamp) {
        this.pongTimestamp = pongTimestamp;
        this.timestamp = timestamp;
    }

    @Override
    public void decode(ByteBuf buf) {
        for (int i = 0; i < 21; i++) {
            DataSerializer.readAddress(buf);
        }
        pongTimestamp = buf.readLong();
        timestamp = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf) {
        for (int i = 0; i < 21; i++) {
            DataSerializer.writeAddress(buf);
        }
        buf.writeLong(pongTimestamp);
        buf.writeLong(timestamp);
    }

    public long getPongTimestamp() {
        return pongTimestamp;
    }

    public void setPongTimestamp(long pongTimestamp) {
        this.pongTimestamp = pongTimestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
