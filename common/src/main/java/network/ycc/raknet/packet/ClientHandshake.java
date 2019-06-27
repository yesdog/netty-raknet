package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

public class ClientHandshake extends SimpleFramedPacket {

    private long pongTimestamp;
    private long timestamp;
    private InetSocketAddress address;
    private int nExtraAddresses;

    public ClientHandshake() {
        reliability = Reliability.RELIABLE_ORDERED;
    }

    public ClientHandshake(long pongTimestamp, InetSocketAddress address, int nExtraAddresses) {
        this(pongTimestamp, System.nanoTime(), address, nExtraAddresses);
    }

    public ClientHandshake(long pongTimestamp, long timestamp, InetSocketAddress address,
            int nExtraAddresses) {
        this();
        this.pongTimestamp = pongTimestamp;
        this.timestamp = timestamp;
        this.address = address;
        this.nExtraAddresses = nExtraAddresses;
    }

    @Override
    public void encode(ByteBuf buf) {
        writeAddress(buf, address);
        for (int i = 0; i < nExtraAddresses; i++) {
            writeAddress(buf);
        }
        buf.writeLong(pongTimestamp);
        buf.writeLong(timestamp);
    }

    @Override
    public void decode(ByteBuf buf) {
        address = readAddress(buf);
        for (nExtraAddresses = 0; buf.readableBytes() > 16; nExtraAddresses++) {
            readAddress(buf);
        }
        pongTimestamp = buf.readLong();
        timestamp = buf.readLong();
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

    public int getnExtraAddresses() {
        return nExtraAddresses;
    }

    public void setnExtraAddresses(int nExtraAddresses) {
        this.nExtraAddresses = nExtraAddresses;
    }

}
