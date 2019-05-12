package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.config.Magic;

public class ConnectionReply1 extends SimplePacket implements Packet {

    private static final boolean hasSecurity = false;

    private Magic magic;
    private int mtu;
    private long serverId;

    public ConnectionReply1() {}

    public ConnectionReply1(Magic magic, int mtu, long serverId) {
        this.magic = magic;
        this.mtu = mtu;
        this.serverId = serverId;
    }

    public void decode(ByteBuf buf) {
        magic = Magic.decode(buf);
        serverId = buf.readLong();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet"); //TODO: security i guess?
        }
        mtu = buf.readShort();
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(serverId);
        buf.writeBoolean(hasSecurity);
        buf.writeShort(mtu);
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

}
