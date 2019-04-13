package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.utils.DataSerializer;

public class ConnectionReply2 extends SimplePacket implements Packet {

    private static final boolean needsSecurity = false;

    private int mtu;
    private long serverId;

    public ConnectionReply2() {}
    
    public ConnectionReply2(int mtu, long serverId) {
        this.mtu = mtu;
        this.serverId = serverId;
    }

    @Override
    public void decode(ByteBuf buf) {
        DataSerializer.readMagic(buf);
        serverId = buf.readLong();
        DataSerializer.readAddress(buf);
        mtu = buf.readShort();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet"); //TODO: security i guess?
        }
    }

    @Override
    public void encode(ByteBuf buf) {
        DataSerializer.writeMagic(buf);
        buf.writeLong(serverId);
        DataSerializer.writeAddress(buf);
        buf.writeShort(mtu);
        buf.writeBoolean(needsSecurity);
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
