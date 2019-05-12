package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.config.Magic;

import java.net.InetSocketAddress;

public class ConnectionReply2 extends SimplePacket implements Packet {

    private static final boolean needsSecurity = false;

    protected Magic magic;
    private int mtu;
    private long serverId;
    private InetSocketAddress address = null;

    public ConnectionReply2() {}

    public ConnectionReply2(Magic magic, int mtu, long serverId, InetSocketAddress address) {
        this.magic = magic;
        this.mtu = mtu;
        this.serverId = serverId;
        this.address = address;
    }

    public void decode(ByteBuf buf) {
        magic = Magic.decode(buf);
        serverId = buf.readLong();
        address = readAddress(buf);
        mtu = buf.readShort();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet"); //TODO: security i guess?
        }
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(serverId);
        if (address == null) {
            writeAddress(buf);
        } else {
            writeAddress(buf, address);
        }
        buf.writeShort(mtu);
        buf.writeBoolean(needsSecurity);
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

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

}
