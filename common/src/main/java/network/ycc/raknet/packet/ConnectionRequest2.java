package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.config.Magic;

import java.net.InetSocketAddress;

public class ConnectionRequest2 extends SimplePacket implements Packet {

    private Magic magic;
    private int mtu;
    private long clientId;
    private InetSocketAddress address;

    public ConnectionRequest2() {}

    public ConnectionRequest2(Magic magic, int mtu, long clientId, InetSocketAddress address) {
        this.magic = magic;
        this.mtu = mtu;
        this.clientId = clientId;
        this.address = address;
    }

    @Override
    public void decode(ByteBuf buf) {
        magic = Magic.decode(buf);
        address = readAddress(buf);
        mtu = buf.readShort();
        clientId = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf) {
        magic.write(buf);
        writeAddress(buf, address);
        buf.writeShort(mtu);
        buf.writeLong(clientId);
    }

    public Magic getMagic() {
        return magic;
    }

    public void setMagic(Magic magic) {
        this.magic = magic;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

}
