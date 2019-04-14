package network.ycc.raknet.packet;

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.utils.DataSerializer;

public class ServerHandshake extends SimpleFramedPacket {

    private InetSocketAddress clientAddr;
    private long timestamp;
    private int nExtraAddresses;

    public ServerHandshake() {
        reliability = Reliability.RELIABLE;
    }

    public ServerHandshake(InetSocketAddress clientAddr, long timestamp) {
        this(clientAddr, timestamp, 20);
    }

    public ServerHandshake(InetSocketAddress clientAddr, long timestamp, int nExtraAddresses) {
        this();
        this.clientAddr = clientAddr;
        this.timestamp = timestamp;
        this.nExtraAddresses = nExtraAddresses;
    }

    @Override
    public void decode(ByteBuf buf) {
        clientAddr = DataSerializer.readAddress(buf);
        buf.readShort();
        for (nExtraAddresses = 0 ; buf.readableBytes() > 16 ; nExtraAddresses++) {
            DataSerializer.readAddress(buf);
        }
        timestamp = buf.readLong();
        timestamp = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf) {
        DataSerializer.writeAddress(buf, clientAddr);
        buf.writeShort(0);
        for (int i = 0 ; i < nExtraAddresses ; i++) {
            DataSerializer.writeAddress(buf);
        }
        buf.writeLong(timestamp);
        buf.writeLong(System.currentTimeMillis());
    }

    public InetSocketAddress getClientAddr() {
        return clientAddr;
    }

    public void setClientAddr(InetSocketAddress clientAddr) {
        this.clientAddr = clientAddr;
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
