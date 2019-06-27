package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

public class Pong extends SimpleFramedPacket {

    private long pingTimestamp;
    private long pongTimestamp;

    public Pong() {
        reliability = Reliability.UNRELIABLE;
    }

    public Pong(long pingTimestamp, Reliability reliability) {
        this(pingTimestamp);
        this.reliability = reliability;
    }

    public Pong(long pingTimestamp) {
        this();
        this.pingTimestamp = pingTimestamp;
        this.pongTimestamp = System.nanoTime();
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeLong(pingTimestamp);
        buf.writeLong(pongTimestamp);
    }

    @Override
    public void decode(ByteBuf buf) {
        pingTimestamp = buf.readLong();
        if (buf.isReadable()) {
            pongTimestamp = buf.readLong();
        }
    }

    public long getPingTimestamp() {
        return pingTimestamp;
    }

    public long getPongTimestamp() {
        return pongTimestamp;
    }

    public long getRTT() {
        return System.nanoTime() - pingTimestamp;
    }

}