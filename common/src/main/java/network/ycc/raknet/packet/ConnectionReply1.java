package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.utils.DataSerializer;

public class ConnectionReply1 extends SimplePacket implements Packet {

    private static final boolean hasSecurity = false;

    private int mtu;
    private long serverId;

    public ConnectionReply1() {}

    public ConnectionReply1(int mtu, long serverId) {
        this.mtu = mtu;
        this.serverId = serverId;
    }

    @Override
    public void decode(ByteBuf buf) {
        DataSerializer.readMagic(buf);
        serverId = buf.readLong();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet"); //TODO: security i guess?
        }
        mtu = buf.readShort();
    }

    @Override
    public void encode(ByteBuf buf) {
        DataSerializer.writeMagic(buf);
        buf.writeLong(serverId);
        buf.writeBoolean(hasSecurity);
        buf.writeShort(mtu);
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
